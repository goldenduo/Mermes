package com.mermes.app.ui.screens.splash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mermes.app.data.model.InitStatus
import com.mermes.app.data.repository.impl.ConnectionRepositoryImpl
import com.mermes.common.log.MermesLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SplashViewModel(application: Application) : AndroidViewModel(application) {

    private val connectionRepository = ConnectionRepositoryImpl(application)

    private val _initStatus = MutableStateFlow<InitStatus>(InitStatus.Idle)
    val initStatus: StateFlow<InitStatus> = _initStatus.asStateFlow()

    private val _splashState = MutableStateFlow(SplashState())
    val splashState: StateFlow<SplashState> = _splashState.asStateFlow()

    init {
        checkEnvironment()
    }

    private fun checkEnvironment() {
        viewModelScope.launch {
            _splashState.value = _splashState.value.copy(isLoading = true, progress = 0f)

            try {
                // 第一阶段：检查环境变量
                MermesLog.i("SplashVM", "Phase 1: Checking environment variables")
                _splashState.value = _splashState.value.copy(
                    progress = 0.1f,
                    statusText = "检查环境变量..."
                )
                delay(300)

                val prefixExists = checkPrefixExists()
                if (!prefixExists) {
                    MermesLog.w("SplashVM", "PREFIX not found, need bootstrap installation")
                    _splashState.value = _splashState.value.copy(
                        needsBootstrap = true,
                        progress = 0.2f
                    )
                } else {
                    _splashState.value = _splashState.value.copy(progress = 0.3f)
                }

                // 第二阶段：检查依赖包
                MermesLog.i("SplashVM", "Phase 2: Checking dependencies")
                _splashState.value = _splashState.value.copy(
                    progress = 0.4f,
                    statusText = "检查依赖包..."
                )
                delay(300)

                if (_splashState.value.needsBootstrap) {
                    // 需要安装 bootstrap
                    _initStatus.value = InitStatus.InstallingBootstrap(
                        progress = 0,
                        retryCount = 0,
                        maxRetries = 3
                    )
                    _splashState.value = _splashState.value.copy(
                        progress = 0.5f,
                        statusText = "安装 Bootstrap 环境..."
                    )
                    // TODO: 实际的 bootstrap 安装逻辑
                    delay(1000)
                }

                // 第三阶段：检查连接配置
                MermesLog.i("SplashVM", "Phase 3: Checking connection config")
                _splashState.value = _splashState.value.copy(
                    progress = 0.7f,
                    statusText = "检查连接配置..."
                )
                delay(300)

                val hasConfig = checkConnectionConfig()
                _splashState.value = _splashState.value.copy(
                    hasExistingConfig = hasConfig,
                    progress = 0.9f
                )

                // 完成
                MermesLog.i("SplashVM", "Environment check completed")
                _splashState.value = _splashState.value.copy(
                    progress = 1.0f,
                    statusText = "初始化完成",
                    isLoading = false,
                    isReady = true
                )
                _initStatus.value = InitStatus.Success

            } catch (e: Exception) {
                MermesLog.e("SplashVM", "Environment check failed", e)
                _splashState.value = _splashState.value.copy(
                    isLoading = false,
                    error = e.message
                )
                _initStatus.value = InitStatus.Failed(
                    stage = InitStatus.Failed.Stage.BOOTSTRAP,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    private suspend fun checkPrefixExists(): Boolean {
        // 检查 $PREFIX 环境变量是否存在
        val prefix = System.getenv("PREFIX")
        if (prefix != null) {
            val prefixDir = java.io.File(prefix)
            return prefixDir.exists() && prefixDir.isDirectory
        }
        // 检查默认 Termux 路径
        val termuxPrefix = java.io.File("/data/data/com.termux/files/usr")
        return termuxPrefix.exists()
    }

    private suspend fun checkConnectionConfig(): Boolean {
        // 检查是否有已保存的连接配置
        val sshConfigs = connectionRepository.getAllSshConfigs()
        return sshConfigs.isNotEmpty()
    }

    fun retryCheck() {
        checkEnvironment()
    }
}

data class SplashState(
    val isLoading: Boolean = true,
    val progress: Float = 0f,
    val statusText: String = "初始化中...",
    val needsBootstrap: Boolean = false,
    val hasExistingConfig: Boolean = false,
    val isReady: Boolean = false,
    val error: String? = null
)
