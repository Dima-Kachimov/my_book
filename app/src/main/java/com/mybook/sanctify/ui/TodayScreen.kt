package com.mybook.sanctify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mybook.sanctify.AppViewModel
import com.mybook.sanctify.data.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TodayScreen(vm: AppViewModel, nav: NavController) {
    val pal = LocalPalette.current
    val pos by vm.pos.collectAsStateWithLifecycle()
    val prayers by vm.openPrayers.collectAsStateWithLifecycle()
    val memos by vm.memos.collectAsStateWithLifecycle()
    val latest by vm.latestEntry.collectAsStateWithLifecycle()
    val trId by vm.trId.collectAsStateWithLifecycle()

    val cal = remember { Calendar.getInstance() }
    val votd = remember { VERSES_OF_DAY[(cal.get(Calendar.DAY_OF_YEAR) + cal.get(Calendar.YEAR)) % VERSES_OF_DAY.size] }
    val votdText by produceState<String?>(null, votd, trId) { value = vm.verseText(votd.first, votd.second, votd.third) }
    val dueMemos = memos.count { it.dueAt <= System.currentTimeMillis() }
    val dateLabel = remember { SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date()).replaceFirstChar { it.uppercase() } }

    Column(Modifier.fillMaxSize().background(pal.bg).verticalScroll(rememberScrollState())) {
        Text(dateLabel, color = pal.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 22.dp, top = 18.dp))
        Text(
            "Сегодня", color = pal.text, fontFamily = FontFamily.Serif, fontSize = 32.sp,
            modifier = Modifier.padding(start = 22.dp, bottom = 12.dp),
        )

        // Стих дня
        Box(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2F3A8F), Color(0xFF5B3C9E), Color(0xFF8A4B9B))))
                .clickable { vm.goTo(votd.first, votd.second, votd.third); nav.openReader() },
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("СТИХ ДНЯ", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, letterSpacing = 2.sp)
                Text(
                    votdText?.let { "«$it»" } ?: "Установите перевод Библии, чтобы увидеть стих дня.",
                    color = Color.White, fontFamily = FontFamily.Serif, fontSize = 19.sp, lineHeight = 27.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Text(
                    refText(votd.first, votd.second, votd.third) + " · " + trId.uppercase(),
                    color = Color(0xFFF3D58E), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // Продолжить чтение
        TodayCard(onClick = { nav.openReader() }) {
            Label("ПРОДОЛЖИТЬ ЧТЕНИЕ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${Books.name(pos.book)} ${pos.chapter}", color = pal.text, fontFamily = FontFamily.Serif,
                        fontSize = 20.sp,
                    )
                }
                Button(onClick = { nav.openReader() }) { Text("Читать") }
            }
        }

        // Молитвенные нужды
        TodayCard(onClick = { nav.navigate("diary") }) {
            Label("МОЛИТВЕННЫЕ НУЖДЫ" + if (prayers.isNotEmpty()) " · ${prayers.size}" else "")
            if (prayers.isEmpty()) {
                Text("Список пуст. Добавьте нужды в разделе «Дневник».", color = pal.muted, fontSize = 14.sp)
            }
            prayers.take(4).forEachIndexed { i, e ->
                val overdue = e.dueAt in 1 until System.currentTimeMillis()
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("${i + 1}.", color = pal.verseNum, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                    Column {
                        Text(e.title.ifBlank { "Без названия" }, color = pal.text, fontSize = 16.sp)
                        if (e.dueAt > 0) {
                            Text(
                                "срок: " + SimpleDateFormat("d MMM", Locale("ru")).format(Date(e.dueAt)) + if (overdue) " (просрочено)" else "",
                                color = if (overdue) Color(0xFFE53935) else pal.muted, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            if (prayers.size > 4) Text("и ещё ${prayers.size - 4}…", color = pal.muted, fontSize = 13.sp)
        }

        // Заучивание
        TodayCard(onClick = { nav.navigate("memo") }) {
            Label("ЗАУЧИВАНИЕ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (memos.isEmpty()) "Выделите стих в чтении и нажмите «Заучивать»"
                        else if (dueMemos > 0) "К повторению сегодня: $dueMemos" else "На сегодня всё повторено ✓",
                        color = pal.text, fontSize = 16.sp,
                    )
                    if (memos.isNotEmpty()) Text("Всего стихов: ${memos.size}", color = pal.muted, fontSize = 13.sp)
                }
                if (dueMemos > 0) Button(onClick = { nav.navigate("memo") }) { Text("Повторять") }
            }
        }

        // Последняя запись
        latest?.let { e ->
            TodayCard(onClick = { vm.selectedSection.value = e.sectionId; nav.navigate("entry/${e.id}") }) {
                Label("ПОСЛЕДНЯЯ ЗАПИСЬ")
                Text(
                    e.title.ifBlank { "Без названия" }, color = pal.text, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    fontFamily = FontFamily.Serif,
                )
                if (e.body.isNotBlank()) Text(e.body, color = pal.muted, maxLines = 2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = LocalPalette.current.muted, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun TodayCard(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ClickableCard(onClick = onClick) { content() }
}
