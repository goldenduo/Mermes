package com.mermes.app.data.model

/**
 * 初始化进度与状态实体
 */
sealed class InitStatus {
    object Idle : InitStatus()

    data class InstallingBootstrap(
        val progress: Int,
        val retryCount: Int,
        val maxRetries: Int,
        val lastError: String? = null
    ) : InitStatus()

    data class InstallingDebs(
        val currentPackage: String,
        val currentIndex: Int,
        val totalCount: Int,
        val retryCount: Int,
        val maxRetries: Int,
        val lastError: String? = null
    ) : InitStatus()

    object Success : InitStatus()

    data class Failed(
        val stage: Stage,
        val error: String
    ) : InitStatus() {
        enum class Stage { BOOTSTRAP, DEBS }
    }
}

/**
 * Bootstrap 安装结果
 */
sealed class BootstrapResult {
    object Success : BootstrapResult()
    data class Error(val message: String, val exception: Throwable? = null) : BootstrapResult()
}

/**
 * Deb 安装结果
 */
sealed class DebInstallResult {
    data class Success(val packageName: String) : DebInstallResult()
    data class Error(val packageName: String, val message: String) : DebInstallResult()
}
