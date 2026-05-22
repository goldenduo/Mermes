package com.mermes.app.ui.navigation

/**
 * 定义所有屏幕路由
 */
sealed class Screen(val route: String) {
    // 启动相关
    object Splash : Screen("splash")
    object Welcome : Screen("welcome")
    object Setup : Screen("setup")

    // 主要功能
    object Home : Screen("home")
    object Chat : Screen("chat")
    object ChatSession : Screen("chat/{sessionId}") {
        fun createRoute(sessionId: String) = "chat/$sessionId"
    }

    // 会话管理
    object Sessions : Screen("sessions")

    // 长期记忆
    object Memory : Screen("memory")

    // 任务看板
    object Kanban : Screen("kanban")

    // 平台网关
    object Gateway : Screen("gateway")

    // 插件技能
    object Skills : Screen("skills")

    // 灵魂设定
    object Soul : Screen("soul")

    // 模型库
    object Models : Screen("models")

    // 提供商配置
    object Providers : Screen("providers")

    // 定时任务
    object Schedules : Screen("schedules")
    object ScheduleCreate : Screen("schedules/create")

    // 工具与 MCP
    object Tools : Screen("tools")

    // 全局设置
    object Settings : Screen("settings")

    // 办公协同
    object Office : Screen("office")

    // SSH 配置
    object SshConfigs : Screen("ssh_configs")
    object SshConfigEdit : Screen("ssh_config/{configId}") {
        fun createRoute(configId: String) = "ssh_config/$configId"
    }
}
