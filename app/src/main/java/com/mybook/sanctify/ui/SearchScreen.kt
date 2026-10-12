package com.mybook.sanctify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mybook.sanctify.AppViewModel
import com.mybook.sanctify.data.VerseEntity
import com.mybook.sanctify.data.refText
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(vm: AppViewModel, nav: NavController) {
    val pal = LocalPalette.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<VerseEntity>()) }

    LaunchedEffect(query) {
        delay(300)
        results = vm.search(query)
    }

    Column(Modifier.fillMaxSize().background(pal.bg)) {
        ScreenHeader("Поиск", onBack = { nav.popBackStack() })
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Слово или фраза") },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        if (query.trim().length >= 2) {
            Text(
                "Найдено: ${results.size}" + if (results.size >= 300) "+" else "",
                color = pal.text, modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(results, key = { "${it.book}:${it.chapter}:${it.verse}" }) { v ->
                ClickableCard(onClick = {
                    vm.goTo(v.book, v.chapter, v.verse)
                    nav.openReader()
                }) {
                    Text(refText(v.book, v.chapter, v.verse), color = pal.heading, fontWeight = FontWeight.Bold)
                    Text(v.text, color = pal.text, fontSize = 16.sp)
                }
            }
        }
    }
}
