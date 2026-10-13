package com.mybook.sanctify

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mybook.sanctify.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val ctx = LocalContext.current
            LaunchedEffect(Unit) { Reminders.rescheduleAll(ctx) }
            LaunchedEffect(Unit) {
                vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
            }
            AppTheme(vm.themeMode) {
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
                        composable("diary") { DiaryScreen(vm, nav) }
                        composable("marks") { MarksScreen(vm, nav) }
                        composable("picker") { PickerScreen(vm, nav) }
                        composable("search") { SearchScreen(vm, nav) }
                        composable("translations") { TranslationsScreen(vm, nav) }
                        composable("settings") { SettingsScreen(vm, nav) }
                        composable(
                            "entry/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType }),
                        ) { EntryScreen(vm, nav, it.arguments!!.getLong("id")) }
                    }
                }
            }
        }
    }
}
