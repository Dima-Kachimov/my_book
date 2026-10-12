package com.mybook.sanctify.ui

import androidx.compose.ui.text.font.FontFamily

/** Шрифты текста Библии. Системные семейства Android: поддерживают кириллицу на любом устройстве. */
enum class FontOption(val title: String, val family: FontFamily) {
    SERIF("С засечками", FontFamily.Serif),
    SANS("Без засечек", FontFamily.SansSerif),
    SYSTEM("Системный", FontFamily.Default),
    MONO("Моноширинный", FontFamily.Monospace),
}
