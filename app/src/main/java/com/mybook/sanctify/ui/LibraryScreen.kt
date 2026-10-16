package com.mybook.sanctify.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mybook.sanctify.AppViewModel
import com.mybook.sanctify.data.BookEntity
import com.mybook.sanctify.data.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val coverColors = listOf(0xFF2F3A8F, 0xFF5B3C9E, 0xFF2E6B5A, 0xFF8B1A10, 0xFFA9742A, 0xFF1F5FA8).map { Color(it) }

@Composable
fun LibraryScreen(vm: AppViewModel, nav: NavController) {
    val pal = LocalPalette.current
    val books by vm.books.collectAsStateWithLifecycle()
    var addMenu by remember { mutableStateOf(false) }
    var urlDialog by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<BookEntity?>(null) }
    var deleting by remember { mutableStateOf<BookEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importBooks(uris)
    }

    Column(Modifier.fillMaxSize().background(pal.bg)) {
        ScreenHeader("Библиотека") {
            Box {
                IconButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, "Добавить книгу", tint = pal.text) }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(text = { Text("Из файла…") }, onClick = { addMenu = false; picker.launch(arrayOf("*/*")) })
                    DropdownMenuItem(text = { Text("По ссылке…") }, onClick = { addMenu = false; urlDialog = true })
                }
            }
        }
        if (books.isEmpty()) {
            Column(Modifier.padding(24.dp)) {
                Text("Здесь будут ваши христианские книги", color = pal.text, fontFamily = FontFamily.Serif, fontSize = 22.sp)
                Text(
                    "Нажмите «+» и добавьте файл: EPUB, FB2 (в том числе .fb2.zip), PDF или TXT. Книги хранятся на телефоне, " +
                        "читаются без интернета, а место чтения запоминается.",
                    color = pal.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    "Где брать: свободные христианские классики — Christian Classics Ethereal Library (ccel.org) и " +
                        "Project Gutenberg (gutenberg.org) дают EPUB и TXT; многие издательства и сайты церквей — PDF и EPUB. " +
                        "Проверяйте, что книга распространяется свободно.",
                    color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp),
                )
                Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.padding(top = 16.dp)) { Text("Добавить книгу") }
            }
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(books, key = { it.id }) { b ->
                BookCard(b, onOpen = { nav.navigate("book/${b.id}") }, onRename = { renaming = b }, onDelete = { deleting = b })
            }
        }
    }

    if (urlDialog) {
        TextPromptDialog(
            "Скачать книгу по ссылке", label = "https://…",
            onOk = { vm.importBookUrl(it); urlDialog = false }, onDismiss = { urlDialog = false },
        )
    }
    renaming?.let { b ->
        var title by remember(b.id) { mutableStateOf(b.title) }
        var author by remember(b.id) { mutableStateOf(b.author) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("О книге") },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(author, { author = it }, label = { Text("Автор") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { vm.renameBook(b, title, author); renaming = null }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Отмена") } },
        )
    }
    deleting?.let { b ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить «${b.title}»?") },
            text = { Text("Файл книги будет удалён с телефона.") },
            confirmButton = { TextButton(onClick = { vm.deleteBook(b); deleting = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun BookCard(b: BookEntity, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val pal = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    ClickableCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 52.dp, height = 72.dp).clip(RoundedCornerShape(8.dp))
                    .background(coverColors[(b.title.hashCode() and 0x7FFFFFFF) % coverColors.size]),
                contentAlignment = Alignment.Center,
            ) {
                Text(b.title.firstOrNull()?.uppercase() ?: "К", color = Color(0xFFF3D58E), fontSize = 26.sp, fontFamily = FontFamily.Serif)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(b.title, color = pal.text, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
                if (b.author.isNotBlank()) Text(b.author, color = pal.muted, fontSize = 13.sp, maxLines = 1)
                val pct = if (b.total > 0) ((b.lastIndex + 1) * 100 / b.total).coerceIn(0, 100) else 0
                Text(
                    b.format.uppercase() + " · " + "%.1f МБ".format(b.sizeBytes / 1048576.0) + if (b.lastIndex > 0) " · $pct%" else "",
                    color = pal.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                )
                if (b.lastIndex > 0 && b.total > 0) {
                    LinearProgressIndicator(
                        progress = { pct / 100f }, color = pal.verseNum, trackColor = pal.line,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Меню", tint = pal.muted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Название и автор") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun BookReaderScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val pal = LocalPalette.current
    val book by produceState<BookEntity?>(null, id) { value = vm.book(id) }
    val b = book
    Column(Modifier.fillMaxSize().background(pal.bg)) {
        ScreenHeader(b?.title ?: "Книга", onBack = { nav.popBackStack() }) {
            if (b != null && b.format != "pdf") {
                TextButton(onClick = { vm.changeFontSize((vm.fontSize - 2f).coerceAtLeast(14f)) }) { Text("A−", color = pal.text, fontSize = 16.sp) }
                TextButton(onClick = { vm.changeFontSize((vm.fontSize + 2f).coerceAtMost(36f)) }) { Text("A+", color = pal.text, fontSize = 20.sp) }
            }
        }
        when {
            b == null -> Text("Загрузка…", color = pal.muted, modifier = Modifier.padding(24.dp))
            b.format == "pdf" -> PdfBody(vm, b)
            else -> TextBody(vm, b)
        }
    }
}

@Composable
private fun TextBody(vm: AppViewModel, b: BookEntity) {
    val pal = LocalPalette.current
    val state by produceState<Result<List<Library.Para>>?>(null, b.id) { value = runCatching { vm.loadBook(b) } }
    val res = state
    when {
        res == null -> Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        res.isFailure -> Text("Не удалось открыть книгу: ${res.exceptionOrNull()?.message}", color = pal.text, modifier = Modifier.padding(24.dp))
        else -> {
            val paras = res.getOrThrow()
            val list = rememberLazyListState(initialFirstVisibleItemIndex = b.lastIndex.coerceIn(0, (paras.size - 1).coerceAtLeast(0)))
            LaunchedEffect(list) {
                snapshotFlow { list.firstVisibleItemIndex }.collectLatest {
                    delay(700)
                    vm.saveBookProgress(b.id, it, paras.size)
                }
            }
            DisposableEffect(list) { onDispose { vm.saveBookProgress(b.id, list.firstVisibleItemIndex, paras.size) } }
            LazyColumn(state = list, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                items(paras.size) { i ->
                    val p = paras[i]
                    if (p.heading) {
                        Text(
                            p.text, color = pal.heading, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            fontSize = (vm.fontSize * 1.2f).sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                        )
                    } else {
                        Text(
                            p.text, color = pal.text, fontFamily = vm.fontOption.family, fontSize = vm.fontSize.sp,
                            lineHeight = (vm.fontSize * vm.lineSpacing).sp,
                            fontWeight = if (vm.boldText) FontWeight.Medium else FontWeight.Normal,
                            textAlign = if (vm.justify) TextAlign.Justify else TextAlign.Start,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfBody(vm: AppViewModel, b: BookEntity) {
    val pal = LocalPalette.current
    val file = remember(b.id) { vm.bookFile(b) }
    val renderer = remember(file) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull()
    }
    if (renderer == null) {
        Text("Не удалось открыть PDF.", color = pal.text, modifier = Modifier.padding(24.dp))
        return
    }
    DisposableEffect(renderer) { onDispose { runCatching { renderer.close() } } }
    val mutex = remember { Mutex() }
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val count = renderer.pageCount
    val list = rememberLazyListState(initialFirstVisibleItemIndex = b.lastIndex.coerceIn(0, (count - 1).coerceAtLeast(0)))
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex }.collectLatest {
            delay(700)
            vm.saveBookProgress(b.id, it, count)
        }
    }
    DisposableEffect(list) { onDispose { vm.saveBookProgress(b.id, list.firstVisibleItemIndex, count) } }
    LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(count) { i ->
            val bmp by produceState<Bitmap?>(null, i) {
                value = withContext(Dispatchers.IO) {
                    mutex.withLock {
                        runCatching {
                            renderer.openPage(i).use { page ->
                                val h = (widthPx.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
                                val out = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                                out.eraseColor(android.graphics.Color.WHITE)
                                page.render(out, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                out
                            }
                        }.getOrNull()
                    }
                }
            }
            val image = bmp
            if (image != null) {
                Image(image.asImageBitmap(), contentDescription = "Страница ${i + 1}", modifier = Modifier.fillMaxWidth())
            } else {
                Box(Modifier.fillMaxWidth().height(420.dp).background(Color.White), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
