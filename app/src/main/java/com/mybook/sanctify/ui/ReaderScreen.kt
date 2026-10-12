package com.mybook.sanctify.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
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
    val parallelId by vm.parallelId.collectAsStateWithLifecycle()
    val parallel by vm.parallelVerses.collectAsStateWithLifecycle()
    val allTags by vm.tags.collectAsStateWithLifecycle()
    val verseTags by vm.verseTags.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var selected by remember(pos) { mutableStateOf(setOf<Int>()) }
    var noteDraft by remember { mutableStateOf<NoteDraft?>(null) }
    var fragmentVerse by remember { mutableStateOf<VerseEntity?>(null) }
    var showSectionPicker by remember { mutableStateOf(false) }
    var sheetVerse by remember { mutableStateOf<VerseEntity?>(null) }
    var showTags by remember { mutableStateOf(false) }

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    Column(Modifier.fillMaxSize().background(pal.bg)) {
        ReaderTopBar(vm, nav, translations, trId, pos, parallelId)
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
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                ) {
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
                            fontFamily = vm.fontOption.family,
                            lineSpacing = vm.lineSpacing,
                            justify = vm.justify,
                            bold = vm.boldText,
                            parallelText = parallel.firstOrNull { it.verse == v.verse }?.text,
                            parallelId = parallelId,
                            tags = verseTags.filter { it.verse == v.verse }
                                .mapNotNull { vt -> allTags.firstOrNull { it.id == vt.tagId } },
                            onSwipeLeft = { sheetVerse = v },
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
                ref = refText(pos.book, pos.chapter, selected.min(), selected.max()),
                preview = verses.filter { it.verse in selected }.joinToString(" ") { it.text },
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
                onTags = { showTags = true },
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

    sheetVerse?.let { v ->
        VerseSheet(
            vm = vm, pos = pos, verse = v, currentTr = trId,
            bookmarked = bookmarks.any { it.verse == v.verse },
            note = notes.firstOrNull { it.verseStart == v.verse && it.verseEnd == v.verse },
            onClose = { sheetVerse = null },
        )
    }

    if (showTags && selected.isNotEmpty()) {
        TagDialog(vm, allTags, verseTags, selected) { showTags = false }
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
    parallelId: String,
) {
    val pal = LocalPalette.current
    var parallelDialog by remember { mutableStateOf(false) }
    var trMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(pal.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).clickable { nav.navigate("picker") }) {
                Text(
                    Books.name(pos.book).uppercase(), color = pal.muted, fontSize = 12.sp,
                    letterSpacing = 1.sp, maxLines = 1,
                )
                Text(
                    "Глава ${pos.chapter}", color = pal.text, fontFamily = FontFamily.Serif,
                    fontSize = 30.sp, maxLines = 1,
                )
            }
            Box {
                Surface(
                    color = pal.soft, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.clickable { trMenu = true },
                ) {
                    Text(
                        trId.uppercase().take(6).ifBlank { "—" } + " ▾", color = pal.heading,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
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
            IconButton(onClick = { nav.navigate("search") }) {
                Icon(Icons.Default.Search, "Поиск", tint = pal.text)
            }
            Box {
                IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "Меню", tint = pal.text) }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("← Назад по истории") }, enabled = vm.canBack,
                        onClick = { moreMenu = false; vm.goBack() },
                    )
                    DropdownMenuItem(
                        text = { Text("Вперёд по истории →") }, enabled = vm.canForward,
                        onClick = { moreMenu = false; vm.goForward() },
                    )
                    DropdownMenuItem(
                        text = { Text("Параллельный перевод" + if (parallelId.isNotBlank()) ": ${parallelId.uppercase()}" else "") },
                        onClick = { moreMenu = false; parallelDialog = true },
                    )
                    DropdownMenuItem(text = { Text("Переводы") }, onClick = { moreMenu = false; nav.navigate("translations") })
                    DropdownMenuItem(text = { Text("Настройки") }, onClick = { moreMenu = false; nav.navigate("settings") })
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
    }
    if (parallelDialog) {
        AlertDialog(
            onDismissRequest = { parallelDialog = false },
            title = { Text("Параллельный перевод") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(
                        onClick = { vm.setParallel(""); parallelDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (parallelId.isBlank()) "✓ Выключен" else "Выключить", modifier = Modifier.fillMaxWidth()) }
                    translations.filter { it.id != trId }.forEach { t ->
                        TextButton(
                            onClick = { vm.setParallel(t.id); parallelDialog = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text((if (t.id == parallelId) "✓ " else "") + t.name, modifier = Modifier.fillMaxWidth()) }
                    }
                    if (translations.size < 2) {
                        Text("Установлен только один перевод. Добавьте ещё в разделе «Переводы».", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { parallelDialog = false }) { Text("Закрыть") } },
        )
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
    fontFamily: FontFamily,
    lineSpacing: Float,
    justify: Boolean,
    bold: Boolean,
    parallelText: String?,
    parallelId: String,
    tags: List<TagEntity>,
    onSwipeLeft: () -> Unit,
    onClick: () -> Unit,
    onNoteClick: (NoteEntity) -> Unit,
) {
    val swipe by rememberUpdatedState(onSwipeLeft)
    val pal = LocalPalette.current
    val annotated = remember(v, hls, trId, pal) { styledVerse(v.verse, v.text, hls, trId, pal) }
    Column(
        Modifier.fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) pal.verseNum.copy(alpha = 0.18f) else Color.Transparent)
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -80f) swipe() },
                    onDragCancel = { total = 0f },
                    onHorizontalDrag = { _, dx -> total += dx },
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                annotated, color = pal.text, fontSize = fontSize.sp, lineHeight = (fontSize * lineSpacing).sp,
                fontFamily = fontFamily, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
                textAlign = if (justify) TextAlign.Justify else TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
            if (bookmarked) {
                Icon(Icons.Default.Bookmark, "Закладка", tint = pal.verseNum, modifier = Modifier.size(20.dp))
            }
        }
        if (parallelText != null) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = pal.verseNum, fontWeight = FontWeight.Bold, fontSize = 0.8.em)) {
                        append(parallelId.uppercase() + "  ")
                    }
                    append(parallelText)
                },
                color = pal.muted, fontSize = (fontSize * 0.88f).sp, lineHeight = (fontSize * lineSpacing * 0.88f).sp,
                fontFamily = fontFamily, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
        if (tags.isNotEmpty()) {
            Row(Modifier.padding(start = 4.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { t ->
                    Surface(color = Color(t.color).copy(alpha = 0.3f), shape = RoundedCornerShape(10.dp)) {
                        Text(
                            t.name, fontSize = 11.sp, color = pal.text,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        notes.forEach { n ->
            Row(
                Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 14.dp, bottomEnd = 14.dp))
                    .background(pal.soft)
                    .height(IntrinsicSize.Min)
                    .clickable { onNoteClick(n) },
            ) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(pal.verseNum))
                Text(
                    (if (n.verseStart != n.verseEnd) "${n.verseStart}–${n.verseEnd}: " else "") + n.text,
                    color = pal.text, fontSize = (fontSize * 0.78f).sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private class Tile(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** Панель действий над выбранными стихами (нижний «лист»). */
@Composable
private fun SelectionBar(
    ref: String,
    preview: String,
    onColor: (Int) -> Unit,
    onUnderline: () -> Unit,
    onClear: () -> Unit,
    onNote: () -> Unit,
    onBookmark: () -> Unit,
    onCopy: () -> Unit,
    onDiary: () -> Unit,
    onTags: () -> Unit,
    onFragment: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val pal = LocalPalette.current
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val tiles = listOfNotNull(
        Tile(Icons.Default.EditNote, "Заметка", onNote),
        Tile(Icons.Default.Bookmark, "Закладка", onBookmark),
        Tile(Icons.Default.FormatUnderlined, "Подчерк.", onUnderline),
        onFragment?.let { Tile(Icons.Default.SelectAll, "Фрагмент", it) },
        Tile(Icons.Default.MenuBook, "В дневник", onDiary),
        Tile(Icons.Default.Sell, "Теги", onTags),
        Tile(Icons.Default.ContentCopy, "Копия", onCopy),
        Tile(Icons.Default.FormatColorReset, "Снять", onClear),
        Tile(Icons.Default.Close, "Отмена", onClose),
    )
    Column(
        Modifier.fillMaxWidth().shadow(12.dp, shape).background(pal.card, shape)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(5.dp)
                .clip(RoundedCornerShape(3.dp)).background(pal.line),
        )
        Spacer(Modifier.height(12.dp))
        Text(ref.uppercase(), color = pal.muted, fontSize = 12.sp, letterSpacing = 1.sp)
        Text(
            preview, color = pal.text, fontFamily = FontFamily.Serif, fontSize = 15.sp,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HL_COLORS.forEach { c ->
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(c)).clickable { onColor(c) })
            }
        }
        tiles.chunked(5).forEach { rowTiles ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTiles.forEach { t -> ActionTile(t, Modifier.weight(1f)) }
                repeat(5 - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ActionTile(t: Tile, modifier: Modifier) {
    val pal = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(pal.soft).clickable(onClick = t.onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(t.icon, t.label, tint = pal.heading, modifier = Modifier.size(24.dp))
        Text(t.label, color = pal.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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


/** Меню по свайпу влево: другие переводы, закладка, мысль. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerseSheet(
    vm: AppViewModel,
    pos: Pos,
    verse: VerseEntity,
    currentTr: String,
    bookmarked: Boolean,
    note: NoteEntity?,
    onClose: () -> Unit,
) {
    val pal = LocalPalette.current
    var tab by remember { mutableIntStateOf(0) }
    var text by remember(note?.id) { mutableStateOf(note?.text ?: "") }
    val others by produceState<List<Pair<TranslationEntity, String?>>>(emptyList(), verse.verse, pos) {
        value = vm.otherTranslations(pos.book, pos.chapter, verse.verse)
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = pal.card) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(
                refText(pos.book, pos.chapter, verse.verse).uppercase(), color = pal.muted,
                fontSize = 12.sp, letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))
            TabRow(selectedTabIndex = tab, containerColor = pal.card) {
                listOf("Переводы", "Закладка", "Мысль").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            Spacer(Modifier.height(14.dp))
            when (tab) {
                0 -> {
                    others.forEach { (t, txt) ->
                        Text(t.name, color = pal.heading, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            txt ?: "— нет этого стиха в переводе —", color = if (txt == null) pal.muted else pal.text,
                            fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 25.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        if (t.id != currentTr && txt != null) {
                            TextButton(onClick = { vm.setTranslation(t.id); onClose() }) { Text("Читать в этом переводе") }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (others.size < 2) {
                        Text("Добавьте ещё переводы в разделе «Переводы», чтобы сравнивать.", color = pal.muted, fontSize = 13.sp)
                    }
                }
                1 -> {
                    Text(if (bookmarked) "Этот стих в закладках." else "Этот стих не в закладках.", color = pal.text)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { vm.toggleBookmark(verse.verse); onClose() }) {
                        Text(if (bookmarked) "Убрать закладку" else "Добавить в закладки")
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, label = { Text("Ваши мысли") },
                        minLines = 3, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            if (text.isNotBlank()) { vm.saveNote(note, verse.verse, verse.verse, text.trim()); onClose() }
                        }) { Text("Сохранить") }
                        if (note != null) {
                            OutlinedButton(onClick = { vm.deleteNote(note.id); onClose() }) { Text("Удалить") }
                        }
                    }
                }
            }
        }
    }
}

/** Выбор тегов для выделенных стихов; можно создать новый тег. */
@Composable
private fun TagDialog(
    vm: AppViewModel,
    tags: List<TagEntity>,
    verseTags: List<VerseTagEntity>,
    selected: Set<Int>,
    onClose: () -> Unit,
) {
    var newTag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Теги") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                tags.forEach { t ->
                    val checked = selected.all { v -> verseTags.any { it.tagId == t.id && it.verse == v } }
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.toggleTag(t.id, selected) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(t.color)))
                        Spacer(Modifier.width(8.dp))
                        Text(t.name)
                    }
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTag, onValueChange = { newTag = it }, singleLine = true,
                        placeholder = { Text("Новый тег") }, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.addTag(newTag); newTag = "" }) { Text("Добавить") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Готово") } },
    )
}
