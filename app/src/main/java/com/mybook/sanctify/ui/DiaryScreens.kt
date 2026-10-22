package com.mybook.sanctify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mybook.sanctify.AppViewModel
import com.mybook.sanctify.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val weekDays = arrayOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
private val datePrefix = Regex("^\\d{2}\\.\\d{2}\\.\\d{2}\\s+\\p{L}{2}")

/** Сегодняшняя дата для заголовка записи: «20.10.26 Вт». */
private fun todayTitle(): String {
    val c = Calendar.getInstance()
    return String.format(
        Locale.US, "%02d.%02d.%02d %s",
        c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR) % 100,
        weekDays[c.get(Calendar.DAY_OF_WEEK) - 1],
    )
}

/** Пустой заголовок → дата; если уже начинается с даты — обновляет её; иначе ставит дату перед текстом. */
private fun withTodayDate(title: String): String {
    val d = todayTitle()
    return when {
        title.isBlank() -> d
        datePrefix.containsMatchIn(title) -> title.replaceFirst(datePrefix, d)
        else -> "$d $title"
    }
}

private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale("ru"))

@Composable
fun DiaryScreen(vm: AppViewModel, nav: NavController) {
    val pal = LocalPalette.current
    val sections by vm.sections.collectAsStateWithLifecycle()
    val selId by vm.selectedSection.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val current = sections.firstOrNull { it.id == selId } ?: sections.firstOrNull()
    var addSection by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(pal.bg)) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Дневник") {
                IconButton(onClick = { addSection = true }) {
                    Icon(Icons.Default.Add, "Новый раздел", tint = pal.text)
                }
                if (current != null) {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, "Раздел", tint = pal.text)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Переименовать раздел") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(
                                text = { Text(if (current.isList) "Показывать как записи" else "Показывать как нумерованный список") },
                                onClick = { menu = false; vm.setSectionList(current, !current.isList) },
                            )
                            if (!current.isDefault) {
                                DropdownMenuItem(text = { Text("Удалить раздел") }, onClick = { menu = false; deleting = true })
                            }
                        }
                    }
                }
            }
            if (current != null) {
                ScrollableTabRow(
                    selectedTabIndex = sections.indexOf(current).coerceAtLeast(0),
                    containerColor = pal.bg, edgePadding = 8.dp,
                ) {
                    sections.forEach { s ->
                        Tab(
                            selected = s.id == current.id,
                            onClick = { vm.selectedSection.value = s.id },
                            text = { Text(s.name, maxLines = 1) },
                        )
                    }
                }
                if (current.isList) {
                    NumberedList(vm, current, entries, Modifier.weight(1f))
                } else {
                    if (entries.isEmpty()) {
                        Text(
                            "В этом разделе пока нет записей.\nНажмите «+», чтобы добавить, или выберите стихи в Библии и нажмите «Дневник».",
                            color = pal.text, modifier = Modifier.padding(24.dp),
                        )
                    }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp)) {
                        items(entries, key = { it.id }) { e ->
                            ClickableCard(onClick = { nav.navigate("entry/${e.id}") }) {
                                Text(
                                    e.title.ifBlank { "Без названия" }, color = pal.text,
                                    fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                )
                                if (e.body.isNotBlank()) {
                                    Text(e.body, color = pal.text, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
                                }
                                Text(
                                    dateFmt.format(Date(e.updatedAt)),
                                    color = pal.text.copy(alpha = 0.6f), fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
        if (current != null && !current.isList) {
            FloatingActionButton(
                onClick = { scope.launch { nav.navigate("entry/${vm.createEntry(current.id)}") } },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(Icons.Default.Add, "Новая запись") }
        }
    }

    if (addSection) {
        var name by remember { mutableStateOf("") }
        var asList by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { addSection = false },
            title = { Text("Новый раздел") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Название") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Нумерованный список (1, 2, 3…)", modifier = Modifier.weight(1f))
                        Switch(checked = asList, onCheckedChange = { asList = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { if (name.isNotBlank()) { vm.addSection(name, asList); addSection = false } }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { addSection = false }) { Text("Отмена") } },
        )
    }
    if (renaming && current != null) {
        TextPromptDialog(
            "Переименовать раздел", initial = current.name, label = "Название",
            onOk = { vm.renameSection(current, it); renaming = false }, onDismiss = { renaming = false },
        )
    }
    if (deleting && current != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Удалить раздел «${current.name}»?") },
            text = { Text("Все записи этого раздела будут удалены.") },
            confirmButton = { TextButton(onClick = { vm.deleteSection(current); deleting = false }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Отмена") } },
        )
    }
}

@Composable
fun EntryScreen(vm: AppViewModel, nav: NavController, id: Long) {
    val pal = LocalPalette.current
    var loaded by remember(id) { mutableStateOf(false) }
    var title by remember(id) { mutableStateOf("") }
    var body by remember(id) { mutableStateOf("") }
    var rich by remember(id) { mutableStateOf("") }
    var initialRich by remember(id) { mutableStateOf("") }
    var initialPlain by remember(id) { mutableStateOf("") }
    var editorFocused by remember { mutableStateOf(false) }
    var showTop by remember { mutableStateOf(false) }
    val verses by remember(id) { vm.entryVerses(id) }.collectAsStateWithLifecycle(emptyList())
    var addVerse by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val controller = remember { RichEditorController() }
    val pickImage = rememberImageInserter(vm, controller)

    LaunchedEffect(id) {
        vm.loadEntry(id)?.let {
            title = it.title; body = it.body; rich = it.bodyRich
            initialRich = it.bodyRich; initialPlain = it.body
        }
        loaded = true
    }
    // Автосохранение с небольшой задержкой + сохранение при выходе.
    LaunchedEffect(title, body, rich, loaded) {
        if (loaded) {
            delay(600)
            vm.saveEntry(id, title, body, rich)
        }
    }
    val latest by rememberUpdatedState(arrayOf(loaded, title, body, rich))
    DisposableEffect(id) {
        onDispose { if (latest[0] as Boolean) vm.saveEntry(id, latest[1] as String, latest[2] as String, latest[3] as String) }
    }

    Column(Modifier.fillMaxSize().background(pal.bg)) {
        ScreenHeader("Запись", onBack = { nav.popBackStack() }) {
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Удалить", tint = pal.text) }
        }
        // Пока набирается текст, заголовок и стихи сворачиваются, чтобы освободить место.
        if (editorFocused && !showTop) {
            Row(
                Modifier.fillMaxWidth().clickable { showTop = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title.ifBlank { "Без названия" } + if (verses.isNotEmpty()) " · стихов: ${verses.size}" else "",
                    color = pal.muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f),
                )
                Text("изменить ▾", color = pal.heading, fontSize = 13.sp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
        } else {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("Заголовок") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { title = withTodayDate(title) }) {
                            Icon(Icons.Default.Event, "Подставить сегодняшнюю дату", tint = pal.heading)
                        }
                    },
                    supportingText = { Text("Значок календаря — подставить дату: " + todayTitle()) },
                )
                AttachmentsSection(vm, nav, id)
                if (verses.isNotEmpty()) SectionLabel("Стихи")
                verses.forEach { v ->
                    ClickableCard(onClick = {
                        vm.setTranslation(v.translationId)
                        vm.goTo(v.book, v.chapter, v.verseStart)
                        nav.openReader()
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                refText(v.book, v.chapter, v.verseStart, v.verseEnd) + " · " + v.translationId.uppercase(),
                                color = pal.heading, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { vm.deleteEntryVerse(v.id) }) {
                                Icon(Icons.Default.Close, "Убрать", tint = pal.text)
                            }
                        }
                        Text(v.text, color = pal.text, fontSize = 17.sp)
                    }
                }
                TextButton(onClick = { addVerse = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Добавить стих")
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
        }
        if (loaded) {
            RichTextEditor(
                initialRich = initialRich, initialPlain = initialPlain, fontSize = vm.fontSize, controller = controller,
                onChange = { r, t -> rich = r; body = t },
                onFocusChange = { f -> editorFocused = f; if (f) showTop = false },
                onPickImage = pickImage,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }

    if (addVerse) {
        AddVerseDialog(
            vm = vm,
            onAdd = { b, c, f, t ->
                scope.launch { if (vm.addEntryVerse(id, b, c, f, t)) addVerse = false }
            },
            onDismiss = { addVerse = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить запись?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteEntry(id)
                    nav.popBackStack()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun AddVerseDialog(
    vm: AppViewModel,
    onAdd: (book: Int, chapter: Int, from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pos = vm.pos.value
    var book by remember { mutableIntStateOf(pos.book) }
    var chapter by remember { mutableStateOf(pos.chapter.toString()) }
    var from by remember { mutableStateOf("1") }
    var to by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить стих") },
        text = {
            Column {
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(Books.name(book)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        (1..Books.COUNT).forEach { n ->
                            DropdownMenuItem(text = { Text(Books.name(n)) }, onClick = { book = n; menu = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(chapter, { chapter = it }, label = { Text("Глава") }, singleLine = true, keyboardOptions = num)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(from, { from = it }, label = { Text("Стих") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                    OutlinedTextField(to, { to = it }, label = { Text("до (необяз.)") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = chapter.toIntOrNull()
                val f = from.toIntOrNull()
                if (c != null && f != null) onAdd(book, c, f, (to.toIntOrNull() ?: f).coerceAtLeast(f))
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}


/** Простой нумерованный список (молитвенные нужды и т.п.): 1, 2, 3… */
@Composable
private fun NumberedList(vm: AppViewModel, section: SectionEntity, entries: List<EntryEntity>, modifier: Modifier) {
    val pal = LocalPalette.current
    val items = remember(entries) { entries.sortedBy { it.createdAt } }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var draft by remember(section.id) { mutableStateOf("") }
    var editing by remember { mutableStateOf<EntryEntity?>(null) }

    fun add() {
        if (draft.isNotBlank()) {
            vm.addListItem(section.id, draft)
            draft = ""
        }
    }

    Column(modifier.fillMaxWidth()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (items.isEmpty()) {
                item {
                    Text(
                        "Пока пусто. Впишите первый пункт внизу и нажмите «Готово».",
                        color = pal.muted, modifier = Modifier.padding(24.dp),
                    )
                }
            }
            itemsIndexed(items, key = { _, e -> e.id }) { i, e ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = e }.padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        "${i + 1}.", color = pal.verseNum, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        modifier = Modifier.width(36.dp),
                    )
                    Column(Modifier.weight(1f).padding(top = 1.dp)) {
                        Text(
                            e.title.ifBlank { "Без названия" },
                            color = if (e.done) pal.muted else pal.text, fontSize = 18.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            textDecoration = if (e.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                        )
                        if (e.dueAt > 0) {
                            val overdue = !e.done && e.dueAt < System.currentTimeMillis()
                            Text(
                                "срок: " + dateFmt.format(Date(e.dueAt)) + if (overdue) " · просрочено" else "",
                                color = if (overdue) Color(0xFFE53935) else pal.muted, fontSize = 12.sp,
                            )
                        }
                    }
                    IconButton(onClick = { pickDate(ctx, e.dueAt) { vm.setDue(e, it) } }) {
                        Icon(Icons.Default.Event, "Срок", tint = if (e.dueAt > 0) pal.heading else pal.line)
                    }
                    IconButton(onClick = { vm.toggleDone(e) }) {
                        Icon(Icons.Default.Check, "Отвечено", tint = if (e.done) pal.verseNum else pal.line)
                    }
                    IconButton(onClick = { vm.deleteEntry(e.id) }) {
                        Icon(Icons.Default.Close, "Удалить", tint = pal.muted)
                    }
                }
                Box(Modifier.fillMaxWidth().padding(start = 56.dp).height(1.dp).background(pal.line))
            }
        }
        Row(
            Modifier.fillMaxWidth().background(pal.card).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                placeholder = { Text("Новый пункт №${items.size + 1}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { add() }) { Icon(Icons.Default.Add, "Добавить", tint = pal.heading) }
        }
    }

    editing?.let { e ->
        TextPromptDialog(
            "Изменить пункт", initial = e.title, label = "Текст", singleLine = false,
            onOk = { vm.editListItem(e.id, it); editing = null }, onDismiss = { editing = null },
        )
    }
}
