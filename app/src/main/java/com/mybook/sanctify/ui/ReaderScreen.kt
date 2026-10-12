package com.mybook.sanctify.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mybook.sanctify.AppViewModel
import com.mybook.sanctify.Pos
import com.mybook.sanctify.data.*
import kotlinx.coroutines.launch

private data class NoteDraft(val note: NoteEntity?, val from: Int, val to: Int)

@Composable
fun ReaderScreen(vm: AppViewModel, nav: NavController) {
    val pal = LocalPalette.current
    val translations by vm.translations.collectAsStateWithLifecycle()
    val trId by vm.trId.collectAsStateWithLifecycle()
    val pos by vm.pos.collectAsStateWithLifecycle()
    val verses by vm.verses.collectAsStateWithLifecycle()
    val highlights by vm.highlights.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var selected by remember(pos) { mutableStateOf(setOf<Int>()) }
    var noteDraft by remember { mutableStateOf<NoteDraft?>(null) }
    var fragmentVerse by remember { mutableStateOf<VerseEntity?>(null) }
    var showSectionPicker by remember { mutableStateOf(false) }

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    Column(Modifier.fillMaxSize().background(pal.bg)) {
        ReaderTopBar(vm, nav, translations, trId, pos)
        Box(Modifier.weight(1f)) {
            if (translations.isEmpty()) {
                EmptyBible(nav)
            } else key(pos) {
                val listState = rememberLazyListState()
                val target = vm.scrollTo
                LaunchedEffect(target, verses) {
                    if (target != null) {
                        val idx = verses.indexOfFirst { it.verse == target }
                        if (idx >= 0) {
                            listState.scrollToItem(idx + 1)
                            vm.scrollTo = null
                        }
                    }
                }
                val bookmarked = remember(bookmarks) { bookmarks.map { it.verse }.toSet() }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    item {
                        Text(
                            "${Books.name(pos.book)} ${pos.chapter}",
                            color = pal.heading, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    if (verses.isEmpty()) {
                        item {
                            Text(
                                "В выбранном переводе нет этой главы.",
                                color = pal.text, modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    items(verses, key = { it.verse }) { v ->
                        VerseRow(
                            v = v,
                            hls = highlights.filter { it.verse == v.verse },
                            notes = notes.filter { it.verseEnd == v.verse },
                            bookmarked = v.verse in bookmarked,
                            selected = v.verse in selected,
                            trId = trId,
                            fontSize = vm.fontSize,
                            onClick = {
                                selected = if (v.verse in selected) selected - v.verse else selected + v.verse
                            },
                            onNoteClick = { n -> noteDraft = NoteDraft(n, n.verseStart, n.verseEnd) },
                        )
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            OutlinedButton(onClick = { vm.step(-1) }) { Text("← Предыдущая") }
                            OutlinedButton(onClick = { vm.step(1) }) { Text("Следующая →") }
                        }
                    }
                }
            }
        }
        if (selected.isNotEmpty()) {
            SelectionBar(
                count = selected.size,
                onColor = { c -> vm.highlight(selected, c, false); selected = emptySet() },
                onUnderline = { vm.highlight(selected, UNDERLINE_COLOR, true); selected = emptySet() },
                onClear = { vm.clearHighlights(selected); selected = emptySet() },
                onNote = {
                    val from = selected.min()
                    val to = selected.max()
                    noteDraft = NoteDraft(notes.firstOrNull { it.verseStart == from && it.verseEnd == to }, from, to)
                },
                onBookmark = {
                    selected.forEach { vm.toggleBookmark(it) }
                    selected = emptySet()
                },
                onCopy = {
                    val text = verses.filter { it.verse in selected }.joinToString(" ") { it.text }
                    val ref = refText(pos.book, pos.chapter, selected.min(), selected.max())
                    clipboard.setText(AnnotatedString("«$text»\n$ref"))
                    selected = emptySet()
                },
                onDiary = { showSectionPicker = true },
                onFragment = if (selected.size == 1) {
                    { fragmentVerse = verses.firstOrNull { it.verse == selected.first() } }
                } else null,
                onClose = { selected = emptySet() },
            )
        }
    }

    noteDraft?.let { d ->
        NoteDialog(
            title = "Заметка · " + refText(pos.book, pos.chapter, d.from, d.to),
            initial = d.note?.text ?: "",
            onSave = { text ->
                vm.saveNote(d.note, d.from, d.to, text)
                noteDraft = null
                selected = emptySet()
            },
            onDelete = d.note?.let { n -> { vm.deleteNote(n.id); noteDraft = null; selected = emptySet() } },
            onDismiss = { noteDraft = null },
        )
    }

    fragmentVerse?.let { v ->
        FragmentDialog(
            text = v.text,
            onApply = { s, e, color, underline ->
                vm.highlightRange(v.verse, s, e, color, underline)
                fragmentVerse = null
                selected = emptySet()
            },
            onDismiss = { fragmentVerse = null },
        )
    }

    if (showSectionPicker) {
        AlertDialog(
            onDismissRequest = { showSectionPicker = false },
            title = { Text("Добавить в раздел дневника") },
            text = {
                Column {
                    sections.forEach { s ->
                        TextButton(
                            onClick = {
                                showSectionPicker = false
                                scope.launch {
                                    val id = vm.createEntry(s.id, selected)
                                    selected = emptySet()
                                    nav.navigate("entry/$id")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(s.name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSectionPicker = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ReaderTopBar(
    vm: AppViewModel,
    nav: NavController,
    translations: List<TranslationEntity>,
    trId: String,
    pos: Pos,
) {
    val pal = LocalPalette.current
    var trMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(52.dp).background(pal.topBar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(onClick = { trMenu = true }) {
                Text(trId.uppercase().take(6).ifBlank { "—" }, color = Color.White, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(expanded = trMenu, onDismissRequest = { trMenu = false }) {
                translations.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.name) },
                        onClick = { vm.setTranslation(t.id); trMenu = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Скачать другие…") },
                    onClick = { trMenu = false; nav.navigate("translations") },
                )
            }
        }
        TextButton(onClick = { nav.navigate("picker") }, modifier = Modifier.weight(1f)) {
            Text("${Books.short(pos.book)} ${pos.chapter}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        BarIcon(Icons.Default.Search, "Поиск", true) { nav.navigate("search") }
        BarIcon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", vm.canBack) { vm.goBack() }
        BarIcon(Icons.AutoMirrored.Filled.ArrowForward, "Вперёд", vm.canForward) { vm.goForward() }
        Box {
            BarIcon(Icons.Default.MoreVert, "Меню", true) { moreMenu = true }
            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                DropdownMenuItem(text = { Text("Переводы") }, onClick = { moreMenu = false; nav.navigate("translations") })
                DropdownMenuItem(text = { Text("Настройки") }, onClick = { moreMenu = false; nav.navigate("settings") })
            }
        }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = desc, tint = if (enabled) Color.White else Color(0xFF777777))
    }
}

@Composable
private fun EmptyBible(nav: NavController) {
    val pal = LocalPalette.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Пока нет ни одного перевода Библии.",
            color = pal.text, fontSize = 18.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { nav.navigate("translations") }) { Text("Скачать перевод") }
    }
}

@Composable
private fun VerseRow(
    v: VerseEntity,
    hls: List<HighlightEntity>,
    notes: List<NoteEntity>,
    bookmarked: Boolean,
    selected: Boolean,
    trId: String,
    fontSize: Float,
    onClick: () -> Unit,
    onNoteClick: (NoteEntity) -> Unit,
) {
    val pal = LocalPalette.current
    val annotated = remember(v, hls, trId, pal) { styledVerse(v.verse, v.text, hls, trId, pal) }
    Column(
        Modifier.fillMaxWidth()
            .background(if (selected) pal.verseNum.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                annotated, color = pal.text, fontSize = fontSize.sp, lineHeight = (fontSize * 1.4f).sp,
                modifier = Modifier.weight(1f),
            )
            if (bookmarked) {
                Icon(Icons.Default.Bookmark, "Закладка", tint = pal.heading, modifier = Modifier.size(20.dp))
            }
        }
        notes.forEach { n ->
            Surface(
                color = pal.card, shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(start = 18.dp, top = 3.dp, bottom = 3.dp).clickable { onNoteClick(n) },
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.EditNote, null, tint = pal.verseNum, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        (if (n.verseStart != n.verseEnd) "${n.verseStart}–${n.verseEnd}: " else "") + n.text,
                        color = pal.text, fontSize = (fontSize * 0.8f).sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onColor: (Int) -> Unit,
    onUnderline: () -> Unit,
    onClear: () -> Unit,
    onNote: () -> Unit,
    onBookmark: () -> Unit,
    onCopy: () -> Unit,
    onDiary: () -> Unit,
    onFragment: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val pal = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(pal.topBar).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Выбрано: $count", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(end = 10.dp))
            HL_COLORS.forEach { c ->
                Box(
                    Modifier.padding(horizontal = 4.dp).size(30.dp).clip(CircleShape)
                        .background(Color(c)).clickable { onColor(c) },
                )
            }
            Spacer(Modifier.weight(1f))
            ActionIcon(Icons.Default.FormatUnderlined, "Подчерк.", onUnderline)
            ActionIcon(Icons.Default.FormatColorReset, "Снять", onClear)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionIcon(Icons.Default.EditNote, "Заметка", onNote)
            ActionIcon(Icons.Default.Bookmark, "Закладка", onBookmark)
            ActionIcon(Icons.Default.ContentCopy, "Копия", onCopy)
            ActionIcon(Icons.Default.MenuBook, "Дневник", onDiary)
            if (onFragment != null) ActionIcon(Icons.Default.SelectAll, "Фрагмент", onFragment)
            ActionIcon(Icons.Default.Close, "Отмена", onClose)
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(label, color = Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun NoteDialog(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text("Ваши мысли") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            )
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim()) }) { Text("Сохранить") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

/** Выделение фрагмента внутри стиха: пользователь выделяет текст маркерами, затем выбирает цвет. */
@Composable
private fun FragmentDialog(
    text: String,
    onApply: (start: Int, end: Int, color: Int, underline: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val pal = LocalPalette.current
    var tf by remember { mutableStateOf(TextFieldValue(text, TextRange(0, 0))) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun apply(color: Int, underline: Boolean) {
        val s = minOf(tf.selection.start, tf.selection.end)
        val e = maxOf(tf.selection.start, tf.selection.end)
        if (e <= s) {
            android.widget.Toast.makeText(ctx, "Сначала выделите слова в тексте", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            onApply(s, e, color, underline)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выделить фрагмент") },
        text = {
            Column {
                Text("Удерживайте слово и потяните маркеры, затем выберите цвет.", fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Surface(color = pal.card, shape = RoundedCornerShape(8.dp)) {
                    BasicTextField(
                        value = tf,
                        onValueChange = { tf = it.copy(text = text) },
                        readOnly = true,
                        textStyle = TextStyle(fontSize = 20.sp, color = pal.text),
                        cursorBrush = SolidColor(Color.Transparent),
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HL_COLORS.forEach { c ->
                        Box(
                            Modifier.padding(end = 8.dp).size(34.dp).clip(CircleShape)
                                .background(Color(c)).clickable { apply(c, false) },
                        )
                    }
                    IconButton(onClick = { apply(UNDERLINE_COLOR, true) }) {
                        Icon(Icons.Default.FormatUnderlined, "Подчеркнуть")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
