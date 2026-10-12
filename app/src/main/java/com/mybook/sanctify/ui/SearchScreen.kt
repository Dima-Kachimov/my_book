package com.mybook.sanctify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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

    Column(Modifier.fillMaxSize().background(pal.bg)) {
        ScreenHeader("Поиск", onBack = { nav.popBackStack() }) {
            TextButton(onClick = { showOptions = !showOptions }) {
                Text(if (showOptions) "Скрыть параметры" else "Параметры", color = pal.heading)
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Слово, фраза или ссылка (Ин 3:16)") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Очистить") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        )
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
