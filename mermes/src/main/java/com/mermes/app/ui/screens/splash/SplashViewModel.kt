package com.mermes.app.ui.screens.splash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mermes.app.data.model.InitStatus
import com.mermes.app.data.repository.impl.ConnectionRepositoryImpl
import com.mermes.common.log.MermesLog
import com.mermes.core.bootstrap.MermesBootstrap
import com.mermes.core.deb.DebInstaller
import com.mermes.common.i18n.MermesI18nTranslator
import java.util.Locale
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
            _splashState.value = _splashState.value.copy(isLoading = true, progress = 0f, error = null)
            val translator = MermesI18nTranslator()
            val locale = Locale.getDefault()
            val maxRetries = 3

            try {
                // 启动零等待自适应流：优先检测是否已经完整部署
                val isBootInstalled = MermesBootstrap.isBootstrapInstalled(getApplication())
                val isDebsInstalled = DebInstaller.isAllPresetInstalled(getApplication())
                if (isBootInstalled && isDebsInstalled) {
                    MermesLog.i("SplashVM", "Environment is fully intact. Skipping boot setup steps.")
                    _splashState.value = _splashState.value.copy(
                        progress = 0.9f,
                        statusText = "系统环境已就绪..."
                    )
                    val hasConfig = checkConnectionConfig()
                    _splashState.value = _splashState.value.copy(
                        hasExistingConfig = hasConfig,
                        progress = 1.0f,
                        statusText = "初始化完成",
                        isLoading = false,
                        isReady = true
                    )
                    _initStatus.value = InitStatus.Success
                    return@launch
                }

                // 第一阶段：检查环境变量
                MermesLog.i("SplashVM", "Phase 1: Checking environment variables")
                _splashState.value = _splashState.value.copy(
                    progress = 0.1f,
                    statusText = "检查系统环境..."
                )
                delay(200)

                val hasBootstrap = MermesBootstrap.isBootstrapInstalled(getApplication())
                if (!hasBootstrap) {
                    MermesLog.w("SplashVM", "Bootstrap not found, need installation")
                    _splashState.value = _splashState.value.copy(
                        needsBootstrap = true,
                        progress = 0.2f
                    )
                } else {
                    _splashState.value = _splashState.value.copy(progress = 0.3f)
                }

                // 第二阶段：安装 Bootstrap 并重试自愈
                MermesLog.i("SplashVM", "Phase 2: Verifying and installing Bootstrap")
                var bootstrapRetry = 0
                var bootstrapSuccess = false

                while (bootstrapRetry < maxRetries && !bootstrapSuccess) {
                    if (MermesBootstrap.isBootstrapInstalled(getApplication())) {
                        bootstrapSuccess = true
                        break
                    }

                    _initStatus.value = InitStatus.InstallingBootstrap(
                        progress = (bootstrapRetry * 33).coerceAtMost(100),
                        retryCount = bootstrapRetry,
                        maxRetries = maxRetries
                    )
                    _splashState.value = _splashState.value.copy(
                        progress = 0.3f,
                        statusText = "安装 Bootstrap 环境..." + if (bootstrapRetry > 0) " (重试 $bootstrapRetry/$maxRetries)" else ""
                    )

                    val res = MermesBootstrap.installBootstrap(getApplication()) { p ->
                        _splashState.value = _splashState.value.copy(
                            progress = 0.3f + p * 0.2f
                        )
                    }

                    if (res.success) {
                        bootstrapSuccess = true
                    } else {
                        bootstrapRetry++
                        if (bootstrapRetry < maxRetries) {
                            val rawErr = res.error ?: "Unknown bootstrap error"
                            val friendlyErr = translator.translate(rawErr, locale)
                            _initStatus.value = InitStatus.InstallingBootstrap(
                                progress = 0,
                                retryCount = bootstrapRetry,
                                maxRetries = maxRetries,
                                lastError = friendlyErr
                            )
                            val backoffMs = (Math.pow(2.0, bootstrapRetry.toDouble()).toLong() * 1000).coerceAtMost(5000)
                            _splashState.value = _splashState.value.copy(
                                statusText = "安装失败，${backoffMs / 1000}秒后重试: $friendlyErr"
                            )
                            delay(backoffMs)
                        } else {
                            val rawErr = res.error ?: "Unknown bootstrap error"
                            val friendlyErr = translator.translate(rawErr, locale)
                            _initStatus.value = InitStatus.Failed(
                                stage = InitStatus.Failed.Stage.BOOTSTRAP,
                                error = friendlyErr
                            )
                            throw Exception(friendlyErr)
                        }
                    }
                }

                // 第三阶段：检查与增量安装依赖包
                MermesLog.i("SplashVM", "Phase 3: Verifying and installing preset DEBs")
                var debsRetry = 0
                var debsSuccess = false

                while (debsRetry < maxRetries && !debsSuccess) {
                    if (DebInstaller.isAllPresetInstalled(getApplication())) {
                        debsSuccess = true
                        break
                    }

                    val results = DebInstaller.installPresetPackages(getApplication()) { pkgName, cur, tot ->
                        _initStatus.value = InitStatus.InstallingDebs(
                            currentPackage = pkgName,
                            currentIndex = cur,
                            totalCount = tot,
                            retryCount = debsRetry,
                            maxRetries = maxRetries
                        )
                        _splashState.value = _splashState.value.copy(
                            progress = 0.5f + (cur.toFloat() / tot) * 0.3f,
                            statusText = "正在安装依赖包 ($cur/$tot): $pkgName"
                        )
                    }

                    if (DebInstaller.isAllPresetInstalled(getApplication())) {
                        debsSuccess = true
                    } else {
                        debsRetry++
                        if (debsRetry < maxRetries) {
                            val failedPkgs = results.filter { !it.success }
                            val rawErr = failedPkgs.firstOrNull()?.error ?: "Dependency installation failed"
                            val friendlyErr = translator.translate(rawErr, locale)

                            _initStatus.value = InitStatus.InstallingDebs(
                                currentPackage = failedPkgs.firstOrNull()?.packageName ?: "unknown",
                                currentIndex = 0,
                                totalCount = 0,
                                retryCount = debsRetry,
                                maxRetries = maxRetries,
                                lastError = friendlyErr
                            )

                            val backoffMs = (Math.pow(2.0, debsRetry.toDouble()).toLong() * 1000).coerceAtMost(5000)
                            _splashState.value = _splashState.value.copy(
                                statusText = "依赖包安装失败，${backoffMs / 1000}秒后重试: $friendlyErr"
                            )
                            delay(backoffMs)
                        } else {
                            val failedPkgs = results.filter { !it.success }
                            val rawErr = failedPkgs.firstOrNull()?.error ?: "Dependency installation failed"
                            val friendlyErr = translator.translate(rawErr, locale)
                            _initStatus.value = InitStatus.Failed(
                                stage = InitStatus.Failed.Stage.DEBS,
                                error = friendlyErr
                            )
                            throw Exception(friendlyErr)
                        }
                    }
                }

                // 第四阶段：检查连接配置
                MermesLog.i("SplashVM", "Phase 4: Checking connection config")
                _splashState.value = _splashState.value.copy(
                    progress = 0.85f,
                    statusText = "正在加载连接配置..."
                )
                delay(200)

                val hasConfig = checkConnectionConfig()
                _splashState.value = _splashState.value.copy(
                    hasExistingConfig = hasConfig,
                    progress = 1.0f,
                    statusText = "初始化完成",
                    isLoading = false,
                    isReady = true
                )
                _initStatus.value = InitStatus.Success

            } catch (e: Exception) {
                MermesLog.e("SplashVM", "Environment check failed: ${e.message}", e)
                _splashState.value = _splashState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private suspend fun checkConnectionConfig(): Boolean {
        return connectionRepository.getPersistedConnectionMode() != null
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
