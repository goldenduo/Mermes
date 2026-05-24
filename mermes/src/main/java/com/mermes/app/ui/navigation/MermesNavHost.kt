package com.mermes.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mermes.app.ui.screens.splash.SplashScreen
import com.mermes.app.ui.screens.webview.WebViewScreen

@Composable
fun MermesNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 启动与安装自检屏
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.WebView.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // 高性能 Web 主控制台界面
        composable(Screen.WebView.route) {
            WebViewScreen()
        }
    }
}
