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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
    if (num != null) withStyle(SpanStyle(color = pal.verseNum, fontWeight = FontWeight.Bold)) { append("$num ") }
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
    Row(
        Modifier.fillMaxWidth().height(52.dp).background(pal.topBar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Text(
            title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            modifier = Modifier.weight(1f), maxLines = 1,
        )
        actions()
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
    Surface(
        color = LocalPalette.current.card,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), content = content)
    }
}
