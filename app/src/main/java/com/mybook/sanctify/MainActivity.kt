package com.mybook.sanctify

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mybook.sanctify.ui.*

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ctx = LocalContext.current
            LaunchedEffect(Unit) { Reminders.rescheduleAll(ctx) }
            LaunchedEffect(Unit) {
                vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
            }
            // «Скрывать от скриншотов и в списке приложений» (только при включённом PIN)
            LaunchedEffect(vm.secureScreen, vm.pinEnabled) {
                if (vm.secureScreen && vm.pinEnabled) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            AppTheme(vm.themeMode) {
                Box {
                    AppContent(vm)
                    if (vm.locked) LockScreen(vm)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && vm.pinEnabled && !vm.locked &&
            SystemClock.elapsedRealtime() - stoppedAt >= vm.lockDelaySec * 1000L
        ) {
            vm.locked = true
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppContent(vm: AppViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        bottomBar = {
            if (route in bottomBarRoutes) {
                AppBottomBar(route) { r ->
                    nav.navigate(r) {
                        popUpTo("today") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "today", modifier = Modifier.padding(pad)) {
            composable("today") { TodayScreen(vm, nav) }
            composable("memo") { MemoScreen(vm, nav) }
            composable("reader") { ReaderScreen(vm, nav) }
            composable("library") { LibraryScreen(vm, nav) }
            composable("book/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                BookReaderScreen(vm, nav, it.arguments!!.getLong("id"))
            }
            composable("diary") { DiaryScreen(vm, nav) }
            composable("marks") { MarksScreen(vm, nav) }
            composable("picker") { PickerScreen(vm, nav) }
            composable("search") { SearchScreen(vm, nav) }
            composable("translations") { TranslationsScreen(vm, nav) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("entry/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                EntryScreen(vm, nav, it.arguments!!.getLong("id"))
            }
        }
    }
}
