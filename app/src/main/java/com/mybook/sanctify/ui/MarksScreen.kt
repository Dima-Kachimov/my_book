package com.mybook.sanctify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mybook.sanctify.AppViewModel
import com.mybook.sanctify.data.*

@Composable
fun MarksScreen(vm: AppViewModel, nav: NavController) {
    val pal = LocalPalette.current
    var tab by remember { mutableIntStateOf(0) }
    val bookmarks by vm.allBookmarks.collectAsStateWithLifecycle()
    val notes by vm.allNotes.collectAsStateWithLifecycle()
    val highlights by vm.allHighlights.collectAsStateWithLifecycle()
    val trId by vm.trId.collectAsStateWithLifecycle()

    fun open(book: Int, chapter: Int, verse: Int) {
        vm.goTo(book, chapter, verse)
        nav.openReader()
    }

    Column(Modifier.fillMaxSize().background(pal.bg)) {
        ScreenHeader("Мои пометки")
        TabRow(selectedTabIndex = tab, containerColor = pal.bg) {
            listOf("Закладки (${bookmarks.size})", "Заметки (${notes.size})", "Выделения (${highlights.size})")
                .forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1, fontSize = 13.sp) }) }
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            when (tab) {
                0 -> items(bookmarks, key = { it.id }) { b ->
                    ClickableCard(onClick = { open(b.book, b.chapter, b.verse) }) {
                        MarkHeader(refText(b.book, b.chapter, b.verse)) { vm.deleteBookmark(b.id) }
                        VersePreview(vm, b.book, b.chapter, b.verse, emptyList(), trId)
                    }
                }
                1 -> items(notes, key = { it.id }) { n ->
                    ClickableCard(onClick = { open(n.book, n.chapter, n.verseStart) }) {
                        MarkHeader(refText(n.book, n.chapter, n.verseStart, n.verseEnd)) { vm.deleteNote(n.id) }
                        Text(n.text, color = pal.text, fontSize = 16.sp)
                    }
                }
                else -> items(highlights, key = { it.id }) { h ->
                    ClickableCard(onClick = { open(h.book, h.chapter, h.verse) }) {
                        MarkHeader(refText(h.book, h.chapter, h.verse)) { vm.deleteHighlight(h.id) }
                        VersePreview(vm, h.book, h.chapter, h.verse, listOf(h), trId)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkHeader(ref: String, onDelete: () -> Unit) {
    val pal = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(ref, color = pal.heading, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Удалить", tint = pal.text) }
    }
}

@Composable
private fun VersePreview(vm: AppViewModel, book: Int, chapter: Int, verse: Int, hls: List<HighlightEntity>, trId: String) {
    val pal = LocalPalette.current
    val text by produceState<String?>(null, book, chapter, verse, trId) { value = vm.verseText(book, chapter, verse) }
    val t = text
    if (t != null) {
        Text(styledVerse(null, t, hls, trId, pal), color = pal.text, fontSize = 16.sp)
    } else {
        Text("Текст недоступен в выбранном переводе", color = pal.text.copy(alpha = 0.5f), fontSize = 13.sp)
    }
}
