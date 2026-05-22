package com.mermes.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mermes.app.ui.screens.splash.SplashScreen
import com.mermes.app.ui.screens.splash.WelcomeScreen
import com.mermes.app.ui.screens.chat.ChatScreen
import com.mermes.app.ui.screens.sessions.SessionsScreen
import com.mermes.app.ui.screens.memory.MemoryScreen
import com.mermes.app.ui.screens.kanban.KanbanScreen
import com.mermes.app.ui.screens.gateway.GatewayScreen
import com.mermes.app.ui.screens.skills.SkillsScreen
import com.mermes.app.ui.screens.soul.SoulScreen
import com.mermes.app.ui.screens.models.ModelsScreen
import com.mermes.app.ui.screens.providers.ProvidersScreen
import com.mermes.app.ui.screens.schedules.SchedulesScreen
import com.mermes.app.ui.screens.tools.ToolsScreen
import com.mermes.app.ui.screens.settings.SettingsScreen
import com.mermes.app.ui.screens.settings.SshConfigsScreen
import com.mermes.app.ui.screens.settings.SshConfigEditScreen
import com.mermes.app.ui.screens.office.OfficeScreen

@Composable
fun MermesNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route
) {
    var isAuthenticated by remember { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 启动屏
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToWelcome = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    isAuthenticated = true
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // 欢迎页 (连接模式选择)
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onConnected = {
                    isAuthenticated = true
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToSshConfigs = {
                    navController.navigate(Screen.SshConfigs.route)
                }
            )
        }

        // 主页 (底部导航)
        composable(Screen.Home.route) {
            MainScreen(navController = navController)
        }

        // 聊天
        composable(Screen.Chat.route) {
            ChatScreen(
                sessionId = null,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSessions = { navController.navigate(Screen.Sessions.route) }
            )
        }

        composable(Screen.ChatSession.route) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId")
            ChatScreen(
                sessionId = sessionId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSessions = { navController.navigate(Screen.Sessions.route) }
            )
        }

        // 会话管理
        composable(Screen.Sessions.route) {
            SessionsScreen(
                onNavigateBack = { navController.popBackStack() },
                onSessionSelected = { sessionId ->
                    navController.navigate(Screen.ChatSession.createRoute(sessionId))
                }
            )
        }

        // 长期记忆
        composable(Screen.Memory.route) {
            MemoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 任务看板
        composable(Screen.Kanban.route) {
            KanbanScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 平台网关
        composable(Screen.Gateway.route) {
            GatewayScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 插件技能
        composable(Screen.Skills.route) {
            SkillsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 灵魂设定
        composable(Screen.Soul.route) {
            SoulScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 模型库
        composable(Screen.Models.route) {
            ModelsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 提供商配置
        composable(Screen.Providers.route) {
            ProvidersScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 定时任务
        composable(Screen.Schedules.route) {
            SchedulesScreen(
                onNavigateBack = { navController.popBackStack() },
                onCreateNew = { navController.navigate(Screen.ScheduleCreate.route) }
            )
        }

        // 工具与 MCP
        composable(Screen.Tools.route) {
            ToolsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 全局设置
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSshConfigs = { navController.navigate(Screen.SshConfigs.route) },
                onNavigateToProviders = { navController.navigate(Screen.Providers.route) },
                onNavigateToModels = { navController.navigate(Screen.Models.route) }
            )
        }

        // 办公协同
        composable(Screen.Office.route) {
            OfficeScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // SSH 配置管理列表
        composable(Screen.SshConfigs.route) {
            SshConfigsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { configId ->
                    navController.navigate(Screen.SshConfigEdit.createRoute(configId ?: "null"))
                }
            )
        }

        // SSH 配置编辑/新建
        composable(Screen.SshConfigEdit.route) { backStackEntry ->
            val configId = backStackEntry.arguments?.getString("configId")
            SshConfigEditScreen(
                configId = if (configId == "null" || configId == "new" || configId.isNullOrEmpty()) null else configId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
