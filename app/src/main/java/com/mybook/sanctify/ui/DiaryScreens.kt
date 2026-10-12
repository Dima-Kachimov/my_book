package com.mybook.sanctify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mybook.sanctify.AppViewModel
import com.mybook.sanctify.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                    Icon(Icons.Default.Add, "Новый раздел", tint = Color.White)
                }
                if (current != null) {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, "Раздел", tint = Color.White)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Переименовать раздел") }, onClick = { menu = false; renaming = true })
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
                                fontWeight = FontWeight.Bold, fontSize = 17.sp,
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
        if (current != null) {
            FloatingActionButton(
                onClick = { scope.launch { nav.navigate("entry/${vm.createEntry(current.id)}") } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) { Icon(Icons.Default.Add, "Новая запись") }
        }
    }

    if (addSection) {
        TextPromptDialog("Новый раздел", label = "Название", onOk = { vm.addSection(it); addSection = false }, onDismiss = { addSection = false })
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
    val verses by remember(id) { vm.entryVerses(id) }.collectAsStateWithLifecycle(emptyList())
    var addVerse by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        vm.loadEntry(id)?.let { title = it.title; body = it.body }
        loaded = true
    }
    // Автосохранение с небольшой задержкой + сохранение при выходе.
    LaunchedEffect(title, body, loaded) {
        if (loaded) {
            delay(600)
            vm.saveEntry(id, title, body)
        }
    }
    val latest by rememberUpdatedState(Triple(loaded, title, body))
    DisposableEffect(id) {
        onDispose { if (latest.first) vm.saveEntry(id, latest.second, latest.third) }
    }

    Column(Modifier.fillMaxSize().background(pal.bg)) {
        ScreenHeader("Запись", onBack = { nav.popBackStack() }) {
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Удалить", tint = Color.White) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text("Заголовок") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            SectionLabel("Стихи")
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
            Spacer(Modifier.height(8.dp))
            SectionLabel("Мои мысли")
            OutlinedTextField(
                value = body, onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
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
