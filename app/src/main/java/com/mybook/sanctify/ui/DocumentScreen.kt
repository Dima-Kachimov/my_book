package com.mybook.sanctify.ui

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.mybook.sanctify.AppViewModel
import com.mybook.sanctify.data.AttachmentEntity
import com.mybook.sanctify.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Документ" }

/** Картинки документа, лежащие в приложении, подставляются прямо в HTML (иначе вне приложения они не откроются). */
private fun inlineImages(ctx: android.content.Context, html: String): String =
    Regex("src=\"${Regex.escape(Media.HOST)}/media/([^\"]+)\"").replace(html) { m ->
        val f = File(Media.dir(ctx), m.groupValues[1])
        if (!f.exists()) return@replace m.value
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension) ?: "image/png"
        "src=\"data:$mime;base64,${Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)}\""
    }

private fun wordHtml(title: String, body: String) =
    "<html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' " +
        "xmlns='http://www.w3.org/TR/REC-html40'><head><meta charset='utf-8'><title>$title</title>" +
        "<style>body{font-family:Calibri,Arial,sans-serif;font-size:12pt} table{border-collapse:collapse} " +
        "td{border:1px solid #888;padding:4px} img{max-width:100%}</style></head><body>$body</body></html>"

/** Документ внутри записи: полноэкранный редактор, автосохранение, экспорт. */
@Composable
fun DocumentScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { RichEditorController() }
    var att by remember(id) { mutableStateOf<AttachmentEntity?>(null) }
    var loaded by remember(id) { mutableStateOf(false) }
    var title by remember(id) { mutableStateOf("") }
    var rich by remember(id) { mutableStateOf("") }
    var text by remember(id) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pickImage = rememberImageInserter(vm, controller)

    LaunchedEffect(id) {
        vm.attachment(id)?.let { att = it; title = it.title; rich = it.docRich; text = it.docText }
        loaded = true
    }
    LaunchedEffect(title, rich, text, loaded) {
        if (loaded && att != null) {
            delay(600)
            vm.saveDoc(id, title, rich, text)
        }
    }
    val latest by rememberUpdatedState(arrayOf(loaded && att != null, title, rich, text))
    DisposableEffect(id) {
        onDispose { if (latest[0] as Boolean) vm.saveDoc(id, latest[1] as String, latest[2] as String, latest[3] as String) }
    }

    fun export(ext: String, mime: String) {
        val name = safeName(title) + ext
        if (ext == ".txt") {
            val f = File(Media.exportDir(ctx), name).apply { writeText(text) }
            shareFile(ctx, f, mime)
            return
        }
        controller.getHtml { html ->
            val body = inlineImages(ctx, html)
            val content = if (ext == ".doc") wordHtml(title, body) else
                "<!doctype html><html><head><meta charset='utf-8'><title>$title</title></head><body>$body</body></html>"
            val f = File(Media.exportDir(ctx), name).apply { writeText(content) }
            shareFile(ctx, f, mime)
        }
    }

    Column(Modifier.fillMaxSize().background(pal.bg)) {
        ScreenHeader(title.ifBlank { "Документ" }, onBack = { nav.popBackStack() }) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Меню", tint = pal.text) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; rename = true })
                    DropdownMenuItem(text = { Text("Печать / сохранить в PDF") }, onClick = { menu = false; controller.print(safeName(title)) })
                    DropdownMenuItem(text = { Text("Отправить как Word (.doc)") }, onClick = { menu = false; export(".doc", "application/msword") })
                    DropdownMenuItem(text = { Text("Отправить как HTML") }, onClick = { menu = false; export(".html", "text/html") })
                    DropdownMenuItem(text = { Text("Отправить как текст") }, onClick = { menu = false; export(".txt", "text/plain") })
                    DropdownMenuItem(text = { Text("Удалить документ") }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
        val a = att
        if (loaded && a != null) {
            RichTextEditor(
                initialRich = a.docRich, initialPlain = a.docText, fontSize = vm.fontSize, controller = controller,
                onChange = { r, t -> rich = r; text = t },
                onFocusChange = { },
                onPickImage = pickImage,
                onPickBible = {
                    controller.getCaret { caret ->
                        vm.startBiblePick(0, caret)
                        nav.navigate("reader") { launchSingleTop = true }
                    }
                },
                onMessage = { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show() },
                onReady = {
                    vm.takePendingQuote()?.let { controller.insertQuote(it.text, it.ref, it.caret) }
                    // Документ, добавленный файлом, открывается в редакторе при первом входе
                    if (a.docRich.isBlank() && a.fileName.isNotBlank()) {
                        val ext = a.fileName.substringAfterLast('.', "").lowercase()
                        when (ext) {
                            "docx" -> controller.importDocx(Media.url(a.fileName))
                            "txt", "md", "html", "htm" -> scope.launch {
                                val raw = withContext(Dispatchers.IO) { vm.mediaFile(a).readBytes().toString(Charsets.UTF_8) }
                                if (ext.startsWith("htm")) controller.importHtml(raw) else controller.importText(raw)
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }

    if (rename) {
        TextPromptDialog("Название документа", initial = title, label = "Название", onOk = { title = it; rename = false }, onDismiss = { rename = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить документ?") },
            text = { Text("Документ и его текст будут удалены из записи.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    att?.let { vm.deleteAttachment(it) }
                    nav.popBackStack()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}
