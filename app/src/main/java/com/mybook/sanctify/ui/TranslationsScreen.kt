package com.mybook.sanctify.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mybook.sanctify.*
import com.mybook.sanctify.data.CatalogItem

@Composable
fun TranslationsScreen(vm: AppViewModel, nav: NavController) {
    val pal = LocalPalette.current
    val installed by vm.translations.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val current by vm.trId.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("ru") }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { if (catalog !is CatalogState.Ready) vm.loadCatalog() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri)
    }

    Column(Modifier.fillMaxSize().background(pal.bg)) {
        ScreenHeader("Переводы Библии", onBack = { nav.popBackStack() })
        LazyColumn(Modifier.fillMaxSize()) {
            item { SectionLabel("Установленные") }
            if (installed.isEmpty()) {
                item { Text("Пока ничего не установлено.", color = pal.text, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(installed, key = { "i" + it.id }) { t ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = t.id == current, onClick = { vm.setTranslation(t.id) })
                    Column(Modifier.weight(1f)) {
                        Text(t.name, color = pal.text, fontWeight = FontWeight.Bold)
                        Text("${t.language} · ${t.id}", color = pal.text.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                    IconButton(onClick = { vm.deleteTranslation(t.id) }) { Icon(Icons.Default.Delete, "Удалить", tint = pal.text) }
                }
            }
            item {
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Импорт из файла (JSON)") }
            }

            item { SectionLabel("Доступно для скачивания") }
            item {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ru" to "Русский", "en" to "English", "all" to "Все").forEach { (k, label) ->
                        FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Фильтр по названию") },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
            when (val c = catalog) {
                is CatalogState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                is CatalogState.Error -> item {
                    Column(Modifier.padding(16.dp)) {
                        Text("Не удалось загрузить список: ${c.message}", color = pal.text)
                        Button(onClick = { vm.loadCatalog() }, modifier = Modifier.padding(top = 8.dp)) { Text("Повторить") }
                    }
                }
                is CatalogState.Ready -> {
                    val shown = c.items.filter {
                        (filter == "all" || (filter == "ru" && it.isRussian()) || (filter == "en" && it.isEnglish())) &&
                            (query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true))
                    }
                    if (shown.isEmpty()) {
                        item { Text("Ничего не найдено.", color = pal.text, modifier = Modifier.padding(16.dp)) }
                    }
                    items(shown, key = { "c" + it.id }) { item ->
                        CatalogRow(item, installed.any { it.id == item.id }, downloads[item.id]) { vm.download(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(item: CatalogItem, installed: Boolean, progress: Float?, onDownload: () -> Unit) {
    val pal = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name, color = pal.text, fontWeight = FontWeight.Bold)
            Text("${item.language} · ${item.id}", color = pal.text.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        when {
            progress != null -> CircularProgressIndicator(
                progress = { progress }, modifier = Modifier.size(28.dp), strokeWidth = 3.dp,
            )
            installed -> Icon(Icons.Default.Check, "Установлен", tint = pal.verseNum)
            else -> IconButton(onClick = onDownload) { Icon(Icons.Default.Download, "Скачать", tint = pal.text) }
        }
    }
}
