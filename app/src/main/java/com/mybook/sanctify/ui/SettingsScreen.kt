package com.mybook.sanctify.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mybook.sanctify.AppViewModel

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    val pal = LocalPalette.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }

    Column(Modifier.fillMaxSize().background(pal.bg)) {
        ScreenHeader("Настройки", onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Label("Предпросмотр")
            Surface(color = pal.card, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Слово Твоё — светильник ноге моей и свет стезе моей. Преобразуйтесь обновлением ума вашего.",
                    color = pal.text, fontSize = vm.fontSize.sp, lineHeight = (vm.fontSize * vm.lineSpacing).sp,
                    fontFamily = vm.fontOption.family,
                    fontWeight = if (vm.boldText) FontWeight.Medium else FontWeight.Normal,
                    textAlign = if (vm.justify) TextAlign.Justify else TextAlign.Start,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Spacer(Modifier.height(18.dp))
            Label("Шрифт")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FontOption.values().forEach { o ->
                    FilterChip(
                        selected = vm.fontOption == o, onClick = { vm.changeFontOption(o) },
                        label = { Text(o.title, fontFamily = o.family) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Размер текста: ${vm.fontSize.toInt()}", color = pal.text)
            Slider(value = vm.fontSize, onValueChange = { vm.changeFontSize(it) }, valueRange = 14f..36f)

            Text("Межстрочный интервал: ${"%.2f".format(vm.lineSpacing)}", color = pal.text)
            Slider(value = vm.lineSpacing, onValueChange = { vm.changeLineSpacing(it) }, valueRange = 1.2f..2.0f)

            SwitchRow("Выравнивание по ширине", vm.justify) { vm.changeJustify(it) }
            SwitchRow("Полужирное начертание", vm.boldText) { vm.changeBold(it) }

            Spacer(Modifier.height(14.dp))
            Label("Тема")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.values().forEach { m ->
                    FilterChip(selected = vm.themeMode == m, onClick = { vm.setTheme(m) }, label = { Text(m.title) })
                }
            }

            Spacer(Modifier.height(22.dp))
            Label("Резервная копия")
            Text(
                "Сохраняет дневник, заметки, закладки и выделения в файл (тексты Библий не входят — их можно скачать заново).",
                color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { export.launch("sanctification_diary_backup.json") }) { Text("Сохранить") }
                OutlinedButton(onClick = { import.launch(arrayOf("*/*")) }) { Text("Восстановить") }
            }
            Text(
                "Восстановление заменяет текущие данные дневника.",
                color = pal.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text, color = LocalPalette.current.heading, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LocalPalette.current.text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
