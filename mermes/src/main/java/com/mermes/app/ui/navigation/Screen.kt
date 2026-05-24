package com.mermes.app.ui.navigation

/**
 * 定义核心控制台屏幕路由
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object WebView : Screen("webview")
}
