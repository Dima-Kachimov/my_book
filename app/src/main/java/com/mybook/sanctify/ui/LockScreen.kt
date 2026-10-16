package com.mybook.sanctify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mybook.sanctify.AppViewModel
import kotlinx.coroutines.delay

/** Экран PIN-кода поверх всего приложения. Восстановление — через секретный вопрос. */
@Composable
fun LockScreen(vm: AppViewModel) {
    val pal = LocalPalette.current
    var mode by remember { mutableIntStateOf(0) } // 0 — PIN, 1 — секретный вопрос, 2 — новый PIN
    var entered by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableIntStateOf(vm.lockoutSeconds()) }

    LaunchedEffect(wait) {
        if (wait > 0) {
            delay(1000)
            wait = vm.lockoutSeconds()
        }
    }

    Box(
        Modifier.fillMaxSize().background(pal.bg).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (mode) {
                0 -> {
                    Text("Дневник освящения", color = pal.muted, fontSize = 14.sp)
                    Text(
                        "Введите PIN-код", color = pal.text, fontFamily = FontFamily.Serif, fontSize = 26.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(vm.pinLength) { i ->
                            Box(
                                Modifier.size(16.dp).clip(CircleShape)
                                    .background(if (i < entered.length) pal.heading else pal.line),
                            )
                        }
                    }
                    Text(
                        when {
                            wait > 0 -> "Слишком много попыток. Подождите $wait сек."
                            message != null -> message!!
                            else -> " "
                        },
                        color = Color(0xFFE53935), fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 14.dp).heightIn(min = 20.dp),
                    )
                    Keypad(
                        enabled = wait == 0,
                        onDigit = { d ->
                            if (entered.length < vm.pinLength) entered += d
                            if (entered.length == vm.pinLength) {
                                if (vm.unlock(entered)) {
                                    message = null
                                } else {
                                    message = "Неверный PIN-код"
                                    wait = vm.lockoutSeconds()
                                }
                                entered = ""
                            }
                        },
                        onBack = { if (entered.isNotEmpty()) entered = entered.dropLast(1) },
                    )
                    if (vm.secretQuestion.isNotBlank()) {
                        TextButton(onClick = { mode = 1; message = null }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Забыли PIN-код?", color = pal.heading)
                        }
                    }
                }
                1 -> {
                    var answer by remember { mutableStateOf("") }
                    Text("Восстановление доступа", color = pal.text, fontFamily = FontFamily.Serif, fontSize = 24.sp)
                    Text(
                        "Ответьте на секретный вопрос:", color = pal.muted, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        vm.secretQuestion, color = pal.text, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    OutlinedTextField(
                        value = answer, onValueChange = { answer = it }, singleLine = true, label = { Text("Ответ") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        when {
                            wait > 0 -> "Слишком много попыток. Подождите $wait сек."
                            message != null -> message!!
                            else -> " "
                        },
                        color = Color(0xFFE53935), fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Button(
                        onClick = {
                            if (vm.checkAnswer(answer)) { mode = 2; message = null }
                            else { message = "Неверный ответ"; wait = vm.lockoutSeconds() }
                        },
                        enabled = wait == 0 && answer.isNotBlank(), modifier = Modifier.fillMaxWidth(),
                    ) { Text("Проверить") }
                    TextButton(onClick = { mode = 0; message = null }) { Text("Назад", color = pal.heading) }
                }
                else -> {
                    var p1 by remember { mutableStateOf("") }
                    var p2 by remember { mutableStateOf("") }
                    Text("Новый PIN-код", color = pal.text, fontFamily = FontFamily.Serif, fontSize = 24.sp)
                    Text("От 4 до 8 цифр", color = pal.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedTextField(
                        value = p1, onValueChange = { p1 = it.filter(Char::isDigit).take(8) }, singleLine = true,
                        label = { Text("Новый PIN") }, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = p2, onValueChange = { p2 = it.filter(Char::isDigit).take(8) }, singleLine = true,
                        label = { Text("Повторите PIN") }, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(message ?: " ", color = Color(0xFFE53935), fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                    Button(
                        onClick = {
                            when {
                                p1.length < 4 -> message = "PIN должен быть не короче 4 цифр"
                                p1 != p2 -> message = "PIN-коды не совпадают"
                                else -> { vm.setPin(p1); vm.locked = false }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Сохранить и войти") }
                }
            }
        }
    }
}

@Composable
private fun Keypad(enabled: Boolean, onDigit: (String) -> Unit, onBack: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(72.dp))
                    } else {
                        val pal = LocalPalette.current
                        Box(
                            Modifier.size(72.dp).clip(CircleShape).background(pal.card)
                                .clickable(enabled = enabled) { if (key == "⌫") onBack() else onDigit(key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(key, color = pal.text, fontSize = 26.sp, fontFamily = FontFamily.Serif)
                        }
                    }
                }
            }
        }
    }
}
