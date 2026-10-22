package com.mybook.sanctify.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mybook.sanctify.AppViewModel
import com.mybook.sanctify.data.AttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------- вспомогательные функции ----------

private val thumbCache = LruCache<String, Bitmap>(60)

private fun decodeSampled(file: File, target: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
    val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val rotation = try {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }
    return if (rotation == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
}

private fun videoFrame(file: File): Bitmap? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(file.path)
        r.getFrameAtTime(0)
    } catch (e: Exception) {
        null
    } finally {
        r.release()
    }
}

private suspend fun thumbnail(file: File, video: Boolean): Bitmap? = withContext(Dispatchers.IO) {
    val key = file.path + if (video) "#v" else ""
    thumbCache.get(key) ?: (if (video) videoFrame(file) else decodeSampled(file, 320))?.also { thumbCache.put(key, it) }
}

fun openExternal(ctx: Context, file: File, mime: String) {
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(i)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Нет приложения, которое открывает этот файл", Toast.LENGTH_LONG).show()
    }
}

fun shareFile(ctx: Context, file: File, mime: String) {
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Отправить"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Не удалось отправить файл", Toast.LENGTH_LONG).show()
    }
}

private fun sizeLabel(bytes: Long): String =
    if (bytes >= 1048576) "%.1f МБ".format(bytes / 1048576.0) else "%d КБ".format(maxOf(1, bytes / 1024))

// ---------- блок «Вложения» в записи ----------

@Composable
fun AttachmentsSection(vm: AppViewModel, nav: NavController, entryId: Long) {
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by remember(entryId) { vm.attachments(entryId) }.collectAsStateWithLifecycle(emptyList())
    var viewing by remember { mutableStateOf<AttachmentEntity?>(null) }
    var renaming by remember { mutableStateOf<AttachmentEntity?>(null) }
    var deleting by remember { mutableStateOf<AttachmentEntity?>(null) }

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) vm.addAttachments(entryId, uris, "photo")
    }
    val videos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if (uris.isNotEmpty()) vm.addAttachments(entryId, uris, "video")
    }
    val docs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addAttachments(entryId, uris)
    }

    Column(Modifier.fillMaxWidth()) {
        SectionLabel("Вложения" + if (items.isNotEmpty()) " · ${items.size}" else "")
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                label = { Text("Фото") }, leadingIcon = { Icon(Icons.Default.Image, null, Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = { videos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                label = { Text("Видео") }, leadingIcon = { Icon(Icons.Default.Videocam, null, Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = { scope.launch { nav.navigate("doc/${vm.createDocument(entryId)}") } },
                label = { Text("Создать документ") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = { docs.launch(arrayOf("*/*")) },
                label = { Text("Добавить документ") }, leadingIcon = { Icon(Icons.Default.Description, null, Modifier.size(18.dp)) },
            )
        }
        if (items.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { a ->
                    AttachmentCard(
                        a, vm,
                        onOpen = {
                            when (a.kind) {
                                "doc" -> nav.navigate("doc/${a.id}")
                                "photo", "video" -> viewing = a
                                else -> openExternal(ctx, vm.mediaFile(a), a.mime)
                            }
                        },
                        onExternal = { openExternal(ctx, vm.mediaFile(a), a.mime) },
                        onRename = { renaming = a },
                        onDelete = { deleting = a },
                    )
                }
            }
        } else {
            Text(
                "Добавьте фото, видео или документ. «Создать документ» открывает редактор, как в Google Документах.",
                color = pal.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp),
            )
        }
    }

    viewing?.let { a ->
        if (a.kind == "photo") PhotoDialog(vm.mediaFile(a)) { viewing = null } else VideoDialog(vm.mediaFile(a)) { viewing = null }
    }
    renaming?.let { a ->
        TextPromptDialog("Название", initial = a.title, label = "Название", onOk = { vm.renameAttachment(a, it); renaming = null }, onDismiss = { renaming = null })
    }
    deleting?.let { a ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить «${a.title}»?") },
            text = { Text(if (a.kind == "doc") "Документ и его текст будут удалены." else "Файл будет удалён с телефона.") },
            confirmButton = { TextButton(onClick = { vm.deleteAttachment(a); deleting = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun AttachmentCard(
    a: AttachmentEntity,
    vm: AppViewModel,
    onOpen: () -> Unit,
    onExternal: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val pal = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.width(108.dp)) {
        Box(
            Modifier.size(108.dp, 88.dp).clip(RoundedCornerShape(14.dp)).background(pal.card).clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            when (a.kind) {
                "photo", "video" -> {
                    val file = vm.mediaFile(a)
                    val bmp by produceState<Bitmap?>(null, file.path) { value = thumbnail(file, a.kind == "video") }
                    val b = bmp
                    if (b != null) {
                        Image(b.asImageBitmap(), a.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(if (a.kind == "video") Icons.Default.Videocam else Icons.Default.Image, null, tint = pal.muted, modifier = Modifier.size(32.dp))
                    }
                    if (a.kind == "video") {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, "Видео", tint = Color.White)
                        }
                    }
                }
                "doc" -> Icon(Icons.Default.Description, null, tint = pal.heading, modifier = Modifier.size(40.dp))
                else -> Icon(
                    if (a.mime.contains("pdf")) Icons.Default.PictureAsPdf else Icons.Default.Description, null,
                    tint = pal.verseNum, modifier = Modifier.size(40.dp),
                )
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.MoreVert, "Меню",
                        tint = if (a.kind == "photo" || a.kind == "video") Color.White else pal.muted, modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (a.fileName.isNotBlank() && a.kind != "doc") {
                        DropdownMenuItem(text = { Text("Открыть в другом приложении") }, onClick = { menu = false; onExternal() })
                    }
                    DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Text(a.title, color = pal.text, fontSize = 12.sp, maxLines = 2, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
        if (a.kind == "file" || (a.kind == "doc" && a.fileName.isNotBlank())) {
            Text(sizeLabel(a.sizeBytes), color = pal.muted, fontSize = 11.sp)
        }
    }
}

// ---------- просмотр фото и видео ----------

@Composable
private fun PhotoDialog(file: File, onClose: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, file.path) { value = withContext(Dispatchers.IO) { decodeSampled(file, 2200) } }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val b = bmp
            if (b != null) {
                Image(
                    b.asImageBitmap(), null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset = if (scale == 1f) Offset.Zero else offset + pan
                            }
                        }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                )
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Default.Close, "Закрыть", tint = Color.White)
            }
        }
    }
}

@Composable
private fun VideoDialog(file: File, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().aspectRatioFallback(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(file.path)
                        val mc = MediaController(ctx)
                        mc.setAnchorView(this)
                        setMediaController(mc)
                        setOnPreparedListener { start() }
                    }
                },
            )
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Default.Close, "Закрыть", tint = Color.White)
            }
        }
    }
}

private fun Modifier.aspectRatioFallback(): Modifier = this.heightIn(min = 240.dp, max = 640.dp)
