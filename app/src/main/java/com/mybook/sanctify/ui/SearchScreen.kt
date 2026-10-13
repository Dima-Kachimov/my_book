package com.mybook.sanctify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mybook.sanctify.*
import com.mybook.sanctify.data.Books
import com.mybook.sanctify.data.parseRef
import com.mybook.sanctify.data.refText
import kotlinx.coroutines.delay

private enum class Scope(val title: String) {
    ALL("Вся Библия"), OT("Ветхий Завет"), NT("Новый Завет"), BOOK("Текущая книга"), CUSTOM("Свой диапазон")
}

@Composable
fun SearchScreen(vm: AppViewModel, nav: NavController) {
    val pal = LocalPalette.current
    var area by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(pal.bg)) {
        ScreenHeader("Поиск", onBack = { nav.popBackStack() })
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Библия", "Дневник", "Теги").forEachIndexed { i, t ->
                FilterChip(selected = area == i, onClick = { area = i }, label = { Text(t) })
            }
        }
        when (area) {
            0 -> BibleSearch(vm, nav)
            1 -> DiarySearch(vm, nav)
            else -> TagSearch(vm, nav)
        }
    }
}

@Composable
private fun BibleSearch(vm: AppViewModel, nav: NavController) {
    val pal = LocalPalette.current
    val installed by vm.translations.collectAsStateWithLifecycle()
    val currentTr by vm.trId.collectAsStateWithLifecycle()
    val pos by vm.pos.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var modeIdx by rememberSaveable { mutableIntStateOf(0) }
    var scopeIdx by rememberSaveable { mutableIntStateOf(0) }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    var wholeWord by rememberSaveable { mutableStateOf(false) }
    var fromBook by rememberSaveable { mutableIntStateOf(1) }
    var toBook by rememberSaveable { mutableIntStateOf(66) }
    var trId by rememberSaveable { mutableStateOf("") }
    var showOptions by rememberSaveable { mutableStateOf(true) }
    var results by remember { mutableStateOf(emptyList<SearchHit>()) }
    var searching by remember { mutableStateOf(false) }

    val tr = trId.ifBlank { currentTr }
    val scope = Scope.values()[scopeIdx]
    val (b1, b2) = when (scope) {
        Scope.ALL -> 1 to Books.COUNT
        Scope.OT -> 1 to Books.NT_START - 1
        Scope.NT -> Books.NT_START to Books.COUNT
        Scope.BOOK -> pos.book to pos.book
        Scope.CUSTOM -> fromBook to toBook
    }
    val options = SearchOptions(query, SearchMode.values()[modeIdx], matchCase, wholeWord, tr, b1, b2)

    LaunchedEffect(options) {
        if (query.trim().length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        searching = true
        results = vm.search(options)
        searching = false
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Слово, фраза или ссылка (Ин 3:16)") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Очистить") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showOptions = !showOptions }) {
                Text(if (showOptions) "Скрыть параметры" else "Параметры поиска", color = pal.heading)
            }
        }
        if (showOptions) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OptionLabel("Режим")
                ChipRow {
                    SearchMode.values().forEachIndexed { i, m ->
                        FilterChip(selected = modeIdx == i, onClick = { modeIdx = i }, label = { Text(m.title) })
                    }
                }
                OptionLabel("Где искать")
                ChipRow {
                    Scope.values().forEachIndexed { i, sc ->
                        FilterChip(selected = scopeIdx == i, onClick = { scopeIdx = i }, label = { Text(sc.title) })
                    }
                }
                if (scope == Scope.CUSTOM) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BookDropdown("С", fromBook) { fromBook = it }
                        BookDropdown("По", toBook) { toBook = it }
                    }
                }
                OptionLabel("Параметры")
                ChipRow {
                    FilterChip(selected = wholeWord, onClick = { wholeWord = !wholeWord }, label = { Text("Целые слова") })
                    FilterChip(selected = matchCase, onClick = { matchCase = !matchCase }, label = { Text("С учётом регистра") })
                }
                if (installed.size > 1) {
                    OptionLabel("Перевод")
                    ChipRow {
                        installed.forEach { t ->
                            FilterChip(
                                selected = t.id == tr, onClick = { trId = t.id },
                                label = { Text(t.id.uppercase().take(8)) },
                            )
                        }
                    }
                }
            }
        }
        val target = remember(query) { parseRef(query) }
        if (target != null) {
            ClickableCard(onClick = {
                vm.goTo(target.book, target.chapter, target.verse)
                nav.openReader()
            }) {
                Text("Перейти к месту", color = pal.muted, fontSize = 12.sp)
                Text(
                    Books.name(target.book) + " " + target.chapter + (target.verse?.let { ":$it" } ?: ""),
                    color = pal.heading, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    fontFamily = FontFamily.Serif,
                )
            }
        }
        if (query.trim().length >= 2) {
            Text(
                if (searching) "Поиск…" else "Найдено стихов: ${results.size}" + if (results.size >= SearchHit.LIMIT) "+" else "",
                color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            items(results, key = { "${it.verse.book}:${it.verse.chapter}:${it.verse.verse}" }) { hit ->
                val v = hit.verse
                ClickableCard(onClick = {
                    vm.setTranslation(tr)
                    vm.goTo(v.book, v.chapter, v.verse)
                    nav.openReader()
                }) {
                    Text(
                        Books.name(v.book) + " " + refText(v.book, v.chapter, v.verse).substringAfter(' '),
                        color = pal.heading, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    )
                    val text = remember(hit, pal) {
                        buildAnnotatedString {
                            append(v.text)
                            hit.ranges.forEach { r ->
                                addStyle(
                                    SpanStyle(background = pal.verseNum.copy(alpha = 0.4f), fontWeight = FontWeight.Bold),
                                    r.first.coerceAtLeast(0), (r.last + 1).coerceAtMost(v.text.length),
                                )
                            }
                        }
                    }
                    Text(text, color = pal.text, fontSize = 16.sp, fontFamily = FontFamily.Serif, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** Поиск по названиям, тексту записей, прикреплённым стихам и пунктам списков. */
@Composable
private fun DiarySearch(vm: AppViewModel, nav: NavController) {
    val pal = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<AppViewModel.DiaryHit>()) }
    LaunchedEffect(query) {
        delay(250)
        hits = vm.searchDiary(query)
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Поиск по дневнику") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Очистить") } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (query.trim().length >= 2) {
            Text("Найдено записей: ${hits.size}", color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        } else {
            Text(
                "Ищет в заголовках, мыслях, прикреплённых стихах и пунктах списков (молитвенные нужды и др.).",
                color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            items(hits, key = { it.entry.id }) { h ->
                ClickableCard(onClick = {
                    vm.selectedSection.value = h.entry.sectionId
                    if (h.isList) nav.navigate("diary") else nav.navigate("entry/${h.entry.id}")
                }) {
                    Text(
                        h.section + if (h.isList) " · список" else "", color = pal.muted, fontSize = 12.sp,
                    )
                    Text(
                        h.entry.title.ifBlank { "Без названия" }, color = pal.text, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif, fontSize = 17.sp,
                    )
                    Text(h.snippet, color = pal.muted, fontSize = 14.sp, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** Стихи с выбранными тегами (должны совпасть все выбранные теги). */
@Composable
private fun TagSearch(vm: AppViewModel, nav: NavController) {
    val pal = LocalPalette.current
    val tags by vm.tags.collectAsStateWithLifecycle()
    val allVerseTags by vm.allVerseTags.collectAsStateWithLifecycle()
    val trId by vm.trId.collectAsStateWithLifecycle()
    var chosen by remember { mutableStateOf(setOf<Long>()) }

    val verses = remember(allVerseTags, chosen) {
        if (chosen.isEmpty()) emptyList()
        else allVerseTags.groupBy { Triple(it.book, it.chapter, it.verse) }
            .filter { (_, list) -> list.map { it.tagId }.toSet().containsAll(chosen) }.keys.toList()
    }
    Column(Modifier.fillMaxSize()) {
        Text("Выберите один или несколько тегов:", color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { t ->
                FilterChip(
                    selected = t.id in chosen,
                    onClick = { chosen = if (t.id in chosen) chosen - t.id else chosen + t.id },
                    label = { Text(t.name) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(t.color))) },
                )
            }
        }
        if (chosen.isNotEmpty()) {
            Text("Найдено стихов: ${verses.size}", color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            items(verses, key = { "${it.first}:${it.second}:${it.third}" }) { (b, c, v) ->
                val text by produceState<String?>(null, b, c, v, trId) { value = vm.verseText(b, c, v) }
                ClickableCard(onClick = { vm.goTo(b, c, v); nav.openReader() }) {
                    Text(refText(b, c, v), color = pal.heading, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text ?: "— нет в выбранном переводе —", color = if (text == null) pal.muted else pal.text,
                        fontFamily = FontFamily.Serif, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionLabel(text: String) {
    Text(text, color = LocalPalette.current.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun BookDropdown(label: String, value: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label: ${Books.short(value)}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (1..Books.COUNT).forEach { n ->
                DropdownMenuItem(text = { Text(Books.name(n)) }, onClick = { onPick(n); open = false })
            }
        }
    }
}
