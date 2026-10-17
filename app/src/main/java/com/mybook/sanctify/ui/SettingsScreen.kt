package com.mybook.sanctify.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.mybook.sanctify.ReminderConfig
import com.mybook.sanctify.Reminders
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
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
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }

    var pinDialog by remember { mutableStateOf<PinDialog?>(null) }
    val ctx = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val needPermission = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setBackupFolder(uri)
    }

    pinDialog?.let { PinDialogs(vm, it) { pinDialog = null } }

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
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.values().forEach { m ->
                    val p = palette(m)
                    FilterChip(
                        selected = vm.themeMode == m, onClick = { vm.setTheme(m) }, label = { Text(m.title) },
                        leadingIcon = {
                            Box(
                                Modifier.size(18.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.bg)
                                    .border(2.dp, p.heading, androidx.compose.foundation.shape.CircleShape),
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Label("Резервная копия")
            Text(
                "Один ZIP-файл: записи дневника, заметки, закладки, выделения, теги, заучивание, все настройки (темы, шрифты, " +
                    "напоминания, PIN-код) и, по выбору, книги библиотеки и тексты скачанных Библий. " +
                    "Файл не зашифрован — храните его в надёжном месте.",
                color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp),
            )
            SwitchRow("Включать книги библиотеки", vm.backupIncludeBooks) { vm.setBackupOptions(it, vm.backupIncludeBibles) }
            SwitchRow("Включать тексты Библий (быстрее, чем качать заново)", vm.backupIncludeBibles) { vm.setBackupOptions(vm.backupIncludeBooks, it) }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { export.launch("sanctification_diary_backup.zip") }) { Text("Сохранить") }
                OutlinedButton(onClick = { import.launch(arrayOf("*/*")) }) { Text("Восстановить") }
            }
            Text(
                "Восстановление заменяет текущие данные и настройки данными из файла. Подходят и старые копии (.json).",
                color = pal.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(22.dp))
            Label("Автокопия")
            Text(
                "Раз в неделю копия дневника сохраняется в выбранную папку (хранятся 5 последних, с теми же настройками состава). " +
                    "Выберите папку, которая синхронизируется с облаком (например, Google Диск), — так копии переживут смену телефона.",
                color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp),
            )
            Text("Папка: " + (vm.backupFolder ?: "не выбрана"), color = pal.text, fontSize = 14.sp)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { folderPicker.launch(null) }) { Text("Выбрать папку") }
                OutlinedButton(onClick = { vm.backupNow() }, enabled = vm.backupFolder != null) { Text("Копия сейчас") }
            }
            SwitchRow("Делать копию раз в неделю", vm.autoBackupOn && vm.backupFolder != null) {
                if (vm.backupFolder == null) folderPicker.launch(null) else vm.setAutoBackup(it)
            }
            if (vm.lastBackup > 0) {
                Text(
                    "Последняя копия: " + SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(vm.lastBackup)),
                    color = pal.muted, fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(22.dp))
            Label("Напоминания")
            ReminderRow("Утреннее чтение Библии", vm.readingReminder, needPermission) { vm.setReminder(Reminders.READING, it) }
            ReminderRow("Молитва", vm.prayerReminder, needPermission) { vm.setReminder(Reminders.PRAYER, it) }
            Text(
                "Срок для молитвенной нужды задаётся значком календаря у пункта списка — в этот день придёт напоминание. " +
                    "Время срабатывания может смещаться на несколько минут (экономия заряда).",
                color = pal.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(22.dp))
            Label("Защита PIN-кодом")
            if (!vm.pinEnabled) {
                Text(
                    "PIN-код закрывает дневник, заметки и книги. Если забудете PIN, доступ можно восстановить ответом на секретный вопрос.",
                    color = pal.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp),
                )
                Button(onClick = { pinDialog = PinDialog.SETUP }) { Text("Включить PIN-код") }
            } else {
                Text("Блокировать приложение:", color = pal.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Сразу", 60 to "Через 1 мин", 300 to "Через 5 мин").forEach { (sec, label) ->
                        FilterChip(selected = vm.lockDelaySec == sec, onClick = { vm.setLockDelay(sec) }, label = { Text(label) })
                    }
                }
                SwitchRow("Скрывать в списке приложений и запретить скриншоты", vm.secureScreen) { vm.changeSecureScreen(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pinDialog = PinDialog.CHANGE }) { Text("Сменить PIN") }
                    OutlinedButton(onClick = { pinDialog = PinDialog.SECRET }) { Text("Секретный вопрос") }
                }
                TextButton(onClick = { pinDialog = PinDialog.DISABLE }) { Text("Отключить PIN-код") }
                Text("Вопрос: " + vm.secretQuestion, color = pal.muted, fontSize = 12.sp)
            }

            Spacer(Modifier.height(22.dp))
            Label("Источники данных")
            Text(
                "Переводы: getBible (getbible.net) и репозиторий bibleapi; тексты принадлежат их правообладателям.\n" +
                    "Перекрёстные ссылки: OpenBible.info, лицензия CC BY.\n" +
                    "Слова оригинала и словарь: STEPBible.org / Tyndale House Cambridge, лицензия CC BY 4.0 " +
                    "(лексиконы основаны на Abbott-Smith и BDB).\n" +
                    "Редактор текста записей: Quill (лицензия BSD-3).",
                color = pal.muted, fontSize = 12.sp,
            )
            Spacer(Modifier.height(24.dp))
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


@Composable
private fun ReminderRow(label: String, cfg: ReminderConfig, needPermission: () -> Unit, onChange: (ReminderConfig) -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = LocalPalette.current.text)
            TextButton(onClick = {
                android.app.TimePickerDialog(ctx, { _, h, m -> onChange(cfg.copy(hour = h, minute = m)) }, cfg.hour, cfg.minute, true).show()
            }) { Text("%02d:%02d".format(cfg.hour, cfg.minute)) }
        }
        Switch(checked = cfg.on, onCheckedChange = { on ->
            if (on) needPermission()
            onChange(cfg.copy(on = on))
        })
    }
}


private enum class PinDialog { SETUP, CHANGE, SECRET, DISABLE }

@Composable
private fun PinDialogs(vm: AppViewModel, kind: PinDialog, onClose: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var q by remember { mutableStateOf(if (kind == PinDialog.SECRET) vm.secretQuestion else "") }
    var a by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val needOld = kind != PinDialog.SETUP
    val needNew = kind == PinDialog.SETUP || kind == PinDialog.CHANGE
    val needSecret = kind == PinDialog.SETUP || kind == PinDialog.SECRET
    val num = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                when (kind) {
                    PinDialog.SETUP -> "Включить PIN-код"
                    PinDialog.CHANGE -> "Сменить PIN-код"
                    PinDialog.SECRET -> "Секретный вопрос"
                    PinDialog.DISABLE -> "Отключить PIN-код"
                }
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (needOld) OutlinedTextField(
                    old, { old = it.filter(Char::isDigit).take(8) }, label = { Text("Текущий PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = num, modifier = Modifier.fillMaxWidth(),
                )
                if (needNew) {
                    OutlinedTextField(
                        p1, { p1 = it.filter(Char::isDigit).take(8) }, label = { Text("Новый PIN (4–8 цифр)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = num,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        p2, { p2 = it.filter(Char::isDigit).take(8) }, label = { Text("Повторите PIN") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = num,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                if (needSecret) {
                    OutlinedTextField(
                        q, { q = it }, label = { Text("Секретный вопрос") }, placeholder = { Text("Например: кличка первой собаки") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        a, { a = it }, label = { Text("Ответ") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        "Ответ нужен, если забудете PIN. Регистр и лишние пробелы не важны.",
                        fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (error != null) Text(error!!, color = androidx.compose.ui.graphics.Color(0xFFE53935), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    needOld && !vm.checkPin(old) -> error = "Текущий PIN неверный"
                    needNew && p1.length < 4 -> error = "PIN должен быть не короче 4 цифр"
                    needNew && p1 != p2 -> error = "PIN-коды не совпадают"
                    needSecret && (q.trim().length < 3 || a.trim().isEmpty()) -> error = "Заполните вопрос и ответ"
                    else -> {
                        when (kind) {
                            PinDialog.SETUP -> vm.setPin(p1, q, a)
                            PinDialog.CHANGE -> vm.setPin(p1)
                            PinDialog.SECRET -> vm.setSecret(q, a)
                            PinDialog.DISABLE -> vm.disablePin()
                        }
                        onClose()
                    }
                }
            }) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } },
    )
}
