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
            LaunchedEffect(Unit) {
                vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
            }
            AppTheme(vm.themeMode) {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                val tabs = listOf(
                    Triple("reader", "Библия", Icons.Default.MenuBook),
                    Triple("diary", "Дневник", Icons.Default.EditNote),
                    Triple("marks", "Пометки", Icons.Default.Bookmark),
                )
                Scaffold(
                    bottomBar = {
                        if (tabs.any { it.first == route }) {
                            NavigationBar {
                                tabs.forEach { (r, label, icon) ->
                                    NavigationBarItem(
                                        selected = route == r,
                                        onClick = {
                                            nav.navigate(r) {
                                                popUpTo("reader") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(icon, label) },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    },
                ) { pad ->
                    NavHost(nav, startDestination = "reader", modifier = Modifier.padding(pad)) {
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
