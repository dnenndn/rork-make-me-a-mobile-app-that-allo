package com.rork.plcpanelstudio.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rork.plcpanelstudio.ui.devices.DevicesScreen
import com.rork.plcpanelstudio.ui.editor.EditorScreen
import com.rork.plcpanelstudio.ui.monitor.MonitorScreen
import com.rork.plcpanelstudio.ui.panels.PanelsScreen
import com.rork.plcpanelstudio.ui.theme.Ink
import com.rork.plcpanelstudio.ui.theme.SignalOrange
import com.rork.plcpanelstudio.ui.theme.TextLow

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val TABS = listOf(
    TabItem("panels", "Panels", Icons.Default.GridView),
    TabItem("editor", "Editor", Icons.Default.Edit),
    TabItem("devices", "Devices", Icons.Default.Memory)
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showTabBar = TABS.any { tab -> currentRoute?.startsWith(tab.route) == true }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            if (showTabBar) {
                NavigationBar(containerColor = Ink, tonalElevation = 0.dp) {
                    TABS.forEach { tab ->
                        val selected = backStackEntry?.destination?.hierarchy?.any {
                            it.route?.startsWith(tab.route) == true
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SignalOrange,
                                selectedTextColor = SignalOrange,
                                indicatorColor = SignalOrange.copy(alpha = 0.16f),
                                unselectedIconColor = TextLow,
                                unselectedTextColor = TextLow
                            )
                        )
                    }
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = "panels",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("panels") {
                PanelsScreen(
                    onOpenMonitor = { id -> navController.navigate("monitor/$id") },
                    onEditPanel = { id -> navController.navigate("editor?panelId=$id") },
                    contentPadding = inner
                )
            }
            composable("editor") {
                EditorScreen(
                    panelId = null,
                    onBack = null,
                    contentPadding = inner
                )
            }
            composable("editor?panelId={panelId}") { entry ->
                EditorScreen(
                    panelId = entry.arguments?.getString("panelId"),
                    onBack = { navController.popBackStack() },
                    contentPadding = inner
                )
            }
            composable("devices") {
                DevicesScreen(contentPadding = inner)
            }
            composable("monitor/{panelId}") { entry ->
                val panelId = entry.arguments?.getString("panelId").orEmpty()
                MonitorScreen(
                    panelId = panelId,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate("editor?panelId=$id") }
                )
            }
        }
    }
}
