package com.mybook.sanctify.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class NavItem(val route: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("today", "Сегодня", Icons.Default.Today),
    NavItem("reader", "Библия", Icons.Default.MenuBook),
    NavItem("library", "Книги", Icons.Default.LibraryBooks),
    NavItem("diary", "Дневник", Icons.Default.EditNote),
    NavItem("marks", "Пометки", Icons.Default.Bookmark),
)

val bottomBarRoutes = navItems.map { it.route }

/** Компактная «плавающая» нижняя навигация: у выбранного пункта появляется подпись на плашке. */
@Composable
fun AppBottomBar(route: String?, onNavigate: (String) -> Unit) {
    val pal = LocalPalette.current
    Box(
        Modifier.fillMaxWidth().background(pal.bg).padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = pal.card,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = if (pal.dark) 0.dp else 6.dp,
            border = if (pal.dark) BorderStroke(1.dp, pal.line) else BorderStroke(1.dp, pal.line.copy(alpha = 0.6f)),
        ) {
            Row(
                Modifier.padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navItems.forEach { item ->
                    val selected = route == item.route
                    Row(
                        Modifier.clip(RoundedCornerShape(19.dp))
                            .background(if (selected) pal.soft else Color.Transparent)
                            // без эффекта нажатия (ripple)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onNavigate(item.route) },
                            )
                            .padding(horizontal = if (selected) 11.dp else 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            item.icon, item.label,
                            tint = if (selected) pal.heading else pal.muted,
                            modifier = Modifier.size(21.dp),
                        )
                        if (selected) {
                            Spacer(Modifier.width(6.dp))
                            Text(item.label, color = pal.heading, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
