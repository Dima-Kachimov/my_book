package com.mybook.sanctify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mybook.sanctify.data.HighlightEntity

/** Цвета маркера. */
val HL_COLORS: List<Int> = listOf(0xFFFFE066, 0xFFA5E6A1, 0xFF9CD3FF, 0xFFFFB3C7, 0xFFFFC27A).map { it.toInt() }
val UNDERLINE_COLOR: Int = 0xFFE53935.toInt()

/** Текст стиха со стилями выделений. [num] — номер стиха (зелёный) в начале, если нужен. */
fun styledVerse(
    num: Int?,
    text: String,
    hls: List<HighlightEntity>,
    trId: String,
    pal: ReaderPalette,
): AnnotatedString = buildAnnotatedString {
    if (num != null) {
        withStyle(
            SpanStyle(
                color = pal.verseNum, fontWeight = FontWeight.Bold, fontSize = 0.65.em,
                fontFamily = FontFamily.SansSerif, baselineShift = BaselineShift.Superscript,
            )
        ) { append("$num") }
        append(" ")
    }
    val base = length
    append(text)
    for (h in hls) {
        val whole = h.startChar < 0
        if (!whole && h.translationId != trId) continue
        val s = if (whole) 0 else h.startChar.coerceIn(0, text.length)
        val e = if (whole) text.length else h.endChar.coerceIn(0, text.length)
        if (e <= s) continue
        val style = if (h.underline) {
            SpanStyle(textDecoration = TextDecoration.Underline, color = Color(h.color))
        } else {
            SpanStyle(background = Color(h.color).copy(alpha = pal.highlightAlpha))
        }
        addStyle(style, base + s, base + e)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val pal = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(pal.bg)) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = pal.text)
                }
            } else {
                Spacer(Modifier.width(22.dp))
            }
            Text(
                title, color = pal.text, fontFamily = FontFamily.Serif, fontSize = 24.sp,
                modifier = Modifier.weight(1f), maxLines = 1,
            )
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides pal.text) { actions() }
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
    }
}

@Composable
fun TextPromptDialog(
    title: String,
    initial: String = "",
    label: String = "",
    onOk: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, label = { Text(label) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onOk(text.trim()) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text, fontWeight = FontWeight.Bold, color = LocalPalette.current.heading,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

fun NavController.openReader() {
    navigate("reader") {
        popUpTo("reader") { inclusive = false }
        launchSingleTop = true
    }
}

@Composable
fun ClickableCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPalette.current
    Surface(
        color = pal.card,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        shadowElevation = if (pal.dark) 0.dp else 2.dp,
        border = if (pal.dark) androidx.compose.foundation.BorderStroke(1.dp, pal.line) else null,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}
