package com.mybook.sanctify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mybook.sanctify.AppViewModel
import com.mybook.sanctify.data.*

private const val MAX_BOX = 6

/** Прячет часть слов: чем выше ступень, тем больше скрыто. Выбор слов стабилен для одного стиха. */
private fun hiddenIndices(words: List<String>, box: Int, seed: Long): Set<Int> {
    val fraction = listOf(0.25, 0.4, 0.55, 0.7, 0.85, 1.0, 1.0)[box.coerceIn(0, MAX_BOX)]
    val picked = words.indices.filter { i -> ((i * 31L + seed * 17L) % 100) < (fraction * 100).toLong() }.toMutableSet()
    if (picked.isEmpty() && words.isNotEmpty()) picked.add(0)
    return picked
}

@Composable
fun MemoScreen(vm: AppViewModel, nav: NavController) {
    val pal = LocalPalette.current
    val memos by vm.memos.collectAsStateWithLifecycle()
    val trId by vm.trId.collectAsStateWithLifecycle()
    var reviewing by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val due = memos.filter { it.dueAt <= now }

    Column(Modifier.fillMaxSize().background(pal.bg)) {
        ScreenHeader("Заучивание", onBack = { if (reviewing) reviewing = false else nav.popBackStack() })
        if (reviewing) {
            ReviewSession(vm, due.toList(), onDone = { reviewing = false })
        } else {
            if (due.isNotEmpty()) {
                Button(onClick = { reviewing = true }, modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Начать повторение (${due.size})")
                }
            } else if (memos.isNotEmpty()) {
                Text("Сегодня повторять нечего ✓", color = pal.muted, modifier = Modifier.padding(16.dp))
            }
            if (memos.isEmpty()) {
                Text(
                    "Здесь будут стихи, которые вы учите. В чтении выделите стих и нажмите «Заучивать». " +
                        "Приложение само покажет, когда повторить: через 1, 3, 7, 14 дней и так далее.",
                    color = pal.muted, modifier = Modifier.padding(20.dp),
                )
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(memos, key = { it.id }) { m ->
                    val text by produceState<String?>(null, m, trId) { value = vm.rangeText(m.book, m.chapter, m.verseStart, m.verseEnd) }
                    ClickableCard(onClick = { vm.goTo(m.book, m.chapter, m.verseStart); nav.openReader() }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                refText(m.book, m.chapter, m.verseStart, m.verseEnd), color = pal.heading,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { vm.deleteMemo(m.id) }) { Icon(Icons.Default.Close, "Убрать", tint = pal.muted) }
                        }
                        Text(text ?: "…", color = pal.text, fontFamily = FontFamily.Serif, fontSize = 16.sp, maxLines = 3)
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            repeat(MAX_BOX) { i ->
                                Box(
                                    Modifier.padding(end = 4.dp).size(8.dp).clip(CircleShape)
                                        .background(if (i < m.box) pal.verseNum else pal.line),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(dueLabel(m.dueAt, now), color = pal.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun dueLabel(dueAt: Long, now: Long): String {
    if (dueAt <= now) return "повторить сегодня"
    val days = ((dueAt - now) / (24L * 60 * 60 * 1000)).toInt()
    return if (days <= 0) "повтор сегодня позже" else "через $days дн."
}

@Composable
private fun ReviewSession(vm: AppViewModel, initial: List<MemoEntity>, onDone: () -> Unit) {
    val pal = LocalPalette.current
    val queue = remember { mutableStateListOf<MemoEntity>().also { it.addAll(initial) } }
    var revealed by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }

    if (queue.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Повторение завершено ✓", color = pal.text, fontFamily = FontFamily.Serif, fontSize = 24.sp)
            Text("Повторено стихов: $done", color = pal.muted, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = onDone, modifier = Modifier.padding(top = 20.dp)) { Text("Готово") }
        }
        return
    }

    val m = queue.first()
    val text by produceState<String?>(null, m.id) { value = vm.rangeText(m.book, m.chapter, m.verseStart, m.verseEnd) }
    val words = remember(text) { text?.split(' ')?.filter { it.isNotBlank() } ?: emptyList() }
    val hidden = remember(words, m.box, m.id) { hiddenIndices(words, m.box, m.id) }

    fun rate(r: Int) {
        vm.review(m, r)
        queue.removeAt(0)
        if (r == 0) queue.add(m.copy(box = 0, dueAt = Long.MAX_VALUE)) // «не помню» — вернётся в конец этой сессии
        if (r != 0) done++
        revealed = false
    }

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Осталось: ${queue.size}", color = pal.muted, fontSize = 13.sp)
        Text(
            refText(m.book, m.chapter, m.verseStart, m.verseEnd), color = pal.heading, fontWeight = FontWeight.Bold,
            fontSize = 22.sp, fontFamily = FontFamily.Serif, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        Surface(color = pal.card, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                buildAnnotatedString {
                    words.forEachIndexed { i, w ->
                        if (i > 0) append(" ")
                        if (i in hidden && !revealed) {
                            append("_".repeat(w.count { it.isLetterOrDigit() }.coerceIn(2, 9)))
                            append(w.filterNot { it.isLetterOrDigit() })
                        } else if (i in hidden) {
                            withStyle(SpanStyle(color = pal.verseNum, fontWeight = FontWeight.Bold)) { append(w) }
                        } else {
                            append(w)
                        }
                    }
                },
                color = pal.text, fontFamily = FontFamily.Serif, fontSize = 21.sp, lineHeight = 32.sp,
                modifier = Modifier.padding(20.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        if (!revealed) {
            Text("Вспомните пропущенные слова, затем нажмите «Показать».", color = pal.muted, fontSize = 13.sp)
            Button(onClick = { revealed = true }, modifier = Modifier.padding(top = 10.dp).fillMaxWidth()) { Text("Показать") }
        } else {
            Text("Как получилось?", color = pal.muted, fontSize = 13.sp)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { rate(0) }, modifier = Modifier.weight(1f)) { Text("Не помню") }
                OutlinedButton(onClick = { rate(1) }, modifier = Modifier.weight(1f)) { Text("Трудно") }
                Button(onClick = { rate(2) }, modifier = Modifier.weight(1f)) { Text("Помню") }
            }
        }
    }
}
