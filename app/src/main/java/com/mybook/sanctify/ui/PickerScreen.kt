package com.mybook.sanctify.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.ui.text.input.ImeAction
import com.mybook.sanctify.data.parseRef
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mybook.sanctify.AppViewModel
import com.mybook.sanctify.data.Books

@Composable
fun PickerScreen(vm: AppViewModel, nav: NavController) {
    val pal = LocalPalette.current
    var book by remember { mutableStateOf<Int?>(null) }
    BackHandler(enabled = book != null) { book = null }

    Column(Modifier.fillMaxSize().background(pal.bg)) {
        ScreenHeader(
            title = book?.let { Books.name(it) } ?: "Выберите книгу",
            onBack = { if (book != null) book = null else nav.popBackStack() },
        )
        val b = book
        if (b == null) {
            var jump by remember { mutableStateOf("") }
            val jumpTarget = parseRef(jump)
            fun go() {
                val t = jumpTarget ?: return
                vm.goTo(t.book, t.chapter, t.verse)
                nav.popBackStack()
            }
            OutlinedTextField(
                value = jump, onValueChange = { jump = it }, singleLine = true,
                label = { Text("Перейти к месту: Ин 3:16") },
                isError = jump.isNotBlank() && jumpTarget == null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
                trailingIcon = { if (jumpTarget != null) TextButton(onClick = { go() }) { Text("Открыть") } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                item { SectionLabel("Ветхий Завет") }
                items((1 until Books.NT_START).toList()) { n -> BookRow(n) { book = n } }
                item { SectionLabel("Новый Завет") }
                items((Books.NT_START..Books.COUNT).toList()) { n -> BookRow(n) { book = n } }
            }
        } else {
            val count by produceState(0, b) { value = vm.chapterCount(b) }
            if (count == 0) {
                Text(
                    "Для этой книги нет текста в выбранном переводе.",
                    color = pal.text, modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(64.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items((1..count).toList()) { ch ->
                        Surface(
                            color = pal.card, shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.clickable {
                                vm.goTo(b, ch)
                                nav.popBackStack()
                            },
                        ) {
                            Box(Modifier.height(52.dp), contentAlignment = Alignment.Center) {
                                Text("$ch", color = pal.text)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookRow(n: Int, onClick: () -> Unit) {
    Text(
        Books.name(n), color = LocalPalette.current.text,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
    )
}
