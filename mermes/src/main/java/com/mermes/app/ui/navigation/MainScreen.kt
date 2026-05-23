package com.mermes.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mermes.app.ui.screens.chat.ChatScreen
import com.mermes.app.ui.screens.kanban.KanbanScreen
import com.mermes.app.ui.screens.memory.MemoryScreen
import com.mermes.app.ui.screens.settings.SettingsScreen

data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
)

@Composable
fun MainScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val innerNavController = rememberNavController()

    val bottomNavItems = listOf(
        BottomNavItem(Screen.Chat.route, Icons.Default.Chat, "Chat"),
        BottomNavItem(Screen.Kanban.route, Icons.Default.Dashboard, "Kanban"),
        BottomNavItem(Screen.Memory.route, Icons.Default.Memory, "Memory"),
        BottomNavItem(Screen.Settings.route, Icons.Default.Settings, "Settings")
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by innerNavController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            innerNavController.navigate(item.route) {
                                popUpTo(innerNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = innerNavController,
            startDestination = Screen.Chat.route,
            modifier = modifier.padding(innerPadding)
        ) {
            composable(Screen.Chat.route) {
                ChatScreen(
                    sessionId = null,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSessions = { navController.navigate(Screen.Sessions.route) }
                )
            }

            composable(Screen.Kanban.route) {
                KanbanScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Memory.route) {
                MemoryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToSshConfigs = { navController.navigate(Screen.SshConfigs.route) },
                    onNavigateToProviders = { navController.navigate(Screen.Providers.route) },
                    onNavigateToModels = { navController.navigate(Screen.Models.route) },
                    onNavigateToWelcome = {
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
