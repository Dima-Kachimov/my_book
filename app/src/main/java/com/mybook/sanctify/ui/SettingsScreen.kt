package com.mybook.sanctify.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            Text("Размер шрифта: ${vm.fontSize.toInt()}", color = pal.text)
            Slider(value = vm.fontSize, onValueChange = { vm.changeFontSize(it) }, valueRange = 14f..34f)
            Text("Так выглядит текст Библии при чтении.", color = pal.text, fontSize = vm.fontSize.sp)

            Spacer(Modifier.height(20.dp))
            Text("Тема", color = pal.heading)
            ThemeMode.values().forEach { m ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = vm.themeMode == m, onClick = { vm.setTheme(m) })
                    Text(m.title, color = pal.text)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Резервная копия", color = pal.heading)
            Text(
                "Сохраняет дневник, заметки, закладки и выделения в файл (тексты Библий не входят — их можно скачать заново).",
                color = pal.text, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { export.launch("sanctification_diary_backup.json") }) { Text("Сохранить") }
                OutlinedButton(onClick = { import.launch(arrayOf("*/*")) }) { Text("Восстановить") }
            }
            Text(
                "Восстановление заменяет текущие данные дневника.",
                color = pal.text.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
