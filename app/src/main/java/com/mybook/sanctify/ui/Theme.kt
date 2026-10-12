package com.mybook.sanctify.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val title: String) {
    PARCHMENT("Пергамент"), NIGHT("Ночная"), WHITE("Светлая")
}

class ReaderPalette(
    val bg: Color,
    val text: Color,
    val verseNum: Color,
    val heading: Color,
    val topBar: Color,
    val card: Color,
    val highlightAlpha: Float,
)

fun palette(m: ThemeMode) = when (m) {
    ThemeMode.PARCHMENT -> ReaderPalette(
        bg = Color(0xFFEBDDB9), text = Color(0xFF1A1A1A), verseNum = Color(0xFF1B8A6B),
        heading = Color(0xFF8B1A10), topBar = Color(0xFF3B3B3B), card = Color(0xFFF6EDD3),
        highlightAlpha = 0.75f,
    )
    ThemeMode.NIGHT -> ReaderPalette(
        bg = Color(0xFF1C1B18), text = Color(0xFFD8D0BC), verseNum = Color(0xFF4CC3A0),
        heading = Color(0xFFE57A6D), topBar = Color(0xFF2A2A28), card = Color(0xFF2B2A26),
        highlightAlpha = 0.35f,
    )
    ThemeMode.WHITE -> ReaderPalette(
        bg = Color(0xFFFFFFFF), text = Color(0xFF1A1A1A), verseNum = Color(0xFF1B8A6B),
        heading = Color(0xFF8B1A10), topBar = Color(0xFF3B3B3B), card = Color(0xFFF1F1EC),
        highlightAlpha = 0.75f,
    )
}

val LocalPalette = staticCompositionLocalOf { palette(ThemeMode.PARCHMENT) }

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val p = palette(mode)
    val scheme = if (mode == ThemeMode.NIGHT) {
        darkColorScheme(
            primary = Color(0xFF4CC3A0), background = p.bg, surface = p.bg,
            onBackground = p.text, onSurface = p.text,
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1B8A6B), background = p.bg, surface = p.bg,
            onBackground = p.text, onSurface = p.text,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
