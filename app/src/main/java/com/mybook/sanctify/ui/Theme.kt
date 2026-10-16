package com.mybook.sanctify.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode(val title: String) {
    PAPER("Бумага"), NIGHT("Ночь"), PARCHMENT("Пергамент"),
    FOREST("Лес"), SKY("Небо"), LAVENDER("Лаванда"), DUSK("Закат"), AMOLED("Полночь")
}

/**
 * Палитра «Индиго и золото».
 * verseNum — золотой акцент (номера стихов), heading — индиго (заголовки, ссылки),
 * card — поверхность карточек, soft — подложка чипов/заметок.
 */
class ReaderPalette(
    val bg: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val heading: Color,
    val verseNum: Color,
    val soft: Color,
    val line: Color,
    val highlightAlpha: Float,
    val dark: Boolean,
) {
    val topBar: Color get() = bg
}

fun palette(m: ThemeMode) = when (m) {
    ThemeMode.PAPER -> ReaderPalette(
        bg = Color(0xFFF7F3EA), card = Color(0xFFFFFFFF), text = Color(0xFF1E2140),
        muted = Color(0xFF6B6F8A), heading = Color(0xFF2F3A8F), verseNum = Color(0xFFC8963E),
        soft = Color(0xFFECE8F7), line = Color(0xFFE6E0D0), highlightAlpha = 0.8f, dark = false,
    )
    ThemeMode.NIGHT -> ReaderPalette(
        bg = Color(0xFF12142B), card = Color(0xFF1C1F40), text = Color(0xFFEDE9DD),
        muted = Color(0xFF9A9DC0), heading = Color(0xFF8E9BFF), verseNum = Color(0xFFE3B35C),
        soft = Color(0xFF242852), line = Color(0xFF2A2E58), highlightAlpha = 0.4f, dark = true,
    )
    ThemeMode.PARCHMENT -> ReaderPalette(
        bg = Color(0xFFEBDDB9), card = Color(0xFFF6EDD3), text = Color(0xFF1A1A1A),
        muted = Color(0xFF6B5F45), heading = Color(0xFF2F3A8F), verseNum = Color(0xFFB07A1E),
        soft = Color(0xFFE3D3A8), line = Color(0xFFD6C69A), highlightAlpha = 0.75f, dark = false,
    )
    ThemeMode.FOREST -> ReaderPalette(
        bg = Color(0xFFEAF1E4), card = Color(0xFFF7FBF3), text = Color(0xFF1F2A1C),
        muted = Color(0xFF5F7058), heading = Color(0xFF2E6B3F), verseNum = Color(0xFFB8862B),
        soft = Color(0xFFDCE9D2), line = Color(0xFFCBDABF), highlightAlpha = 0.75f, dark = false,
    )
    ThemeMode.SKY -> ReaderPalette(
        bg = Color(0xFFE8F0F8), card = Color(0xFFFFFFFF), text = Color(0xFF1B2A3B),
        muted = Color(0xFF5B6E84), heading = Color(0xFF1F5FA8), verseNum = Color(0xFFC2862E),
        soft = Color(0xFFD9E6F4), line = Color(0xFFC9D8E8), highlightAlpha = 0.75f, dark = false,
    )
    ThemeMode.LAVENDER -> ReaderPalette(
        bg = Color(0xFFF1ECF8), card = Color(0xFFFFFFFF), text = Color(0xFF2A2140),
        muted = Color(0xFF6E6685), heading = Color(0xFF6A3FB5), verseNum = Color(0xFFC08A2E),
        soft = Color(0xFFE6DEF4), line = Color(0xFFD9CFEA), highlightAlpha = 0.75f, dark = false,
    )
    ThemeMode.DUSK -> ReaderPalette(
        bg = Color(0xFF221A1F), card = Color(0xFF2E232B), text = Color(0xFFF0E4DA),
        muted = Color(0xFFB7A5A8), heading = Color(0xFFF2A272), verseNum = Color(0xFFE6B450),
        soft = Color(0xFF3A2C34), line = Color(0xFF46363F), highlightAlpha = 0.4f, dark = true,
    )
    ThemeMode.AMOLED -> ReaderPalette(
        bg = Color(0xFF000000), card = Color(0xFF111111), text = Color(0xFFE6E6E6),
        muted = Color(0xFF9A9A9A), heading = Color(0xFF8AB4FF), verseNum = Color(0xFFE3B35C),
        soft = Color(0xFF1C1C1C), line = Color(0xFF2A2A2A), highlightAlpha = 0.4f, dark = true,
    )
}

val LocalPalette = staticCompositionLocalOf { palette(ThemeMode.PAPER) }

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val p = palette(mode)
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.heading, onPrimary = p.bg,
            primaryContainer = p.soft, onPrimaryContainer = p.heading,
            secondaryContainer = p.soft, onSecondaryContainer = p.heading,
            background = p.bg, onBackground = p.text,
            surface = p.bg, onSurface = p.text, onSurfaceVariant = p.muted,
            surfaceVariant = p.soft, surfaceContainer = p.card, surfaceContainerHigh = p.card,
            surfaceContainerHighest = p.soft, outline = p.muted, outlineVariant = p.line,
        )
    } else {
        lightColorScheme(
            primary = p.heading, onPrimary = Color.White,
            primaryContainer = p.soft, onPrimaryContainer = p.heading,
            secondaryContainer = p.soft, onSecondaryContainer = p.heading,
            background = p.bg, onBackground = p.text,
            surface = p.bg, onSurface = p.text, onSurfaceVariant = p.muted,
            surfaceVariant = p.soft, surfaceContainer = p.card, surfaceContainerHigh = p.card,
            surfaceContainerHighest = p.soft, outline = p.muted, outlineVariant = p.line,
        )
    }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            w.statusBarColor = p.bg.toArgb()
            w.navigationBarColor = p.card.toArgb()
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !p.dark
            c.isAppearanceLightNavigationBars = !p.dark
        }
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
