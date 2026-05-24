package com.mermes.app.ui.screens.splash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mermes.app.data.model.InitStatus
import com.mermes.common.log.MermesLog
import com.mermes.core.bootstrap.MermesBootstrap
import com.mermes.core.deb.DebInstaller
import com.mermes.common.i18n.MermesI18nTranslator
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SplashViewModel(application: Application) : AndroidViewModel(application) {

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
                // 优先检查：如果本地 20265 端口已经是活跃状态，则说明服务已经在运行，直接进入控制台！
                if (isPortActive(20265)) {
                    MermesLog.i("SplashVM", "Local service is already active on port 20265. Skipping boot steps.")
                    _splashState.value = _splashState.value.copy(
                        progress = 1.0f,
                        statusText = "服务已启动，正在进入控制台...",
                        isLoading = false,
                        isReady = true
                    )
                    _initStatus.value = InitStatus.Success
                    return@launch
                }

                // 启动零等待自适应流：优先检测环境是否已经完整部署
                val isBootInstalled = MermesBootstrap.isBootstrapInstalled(getApplication())
                val isDebsInstalled = DebInstaller.isAllPresetInstalled(getApplication())

                if (isBootInstalled && isDebsInstalled) {
                    MermesLog.i("SplashVM", "Environment is fully intact. Skipping boot setup steps.")
                } else {
                    // 第一阶段：检查并引导环境
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
                }

                // 第四阶段：部署并静默执行启动脚本
                MermesLog.i("SplashVM", "Phase 4: Deploying and executing startup script")
                _splashState.value = _splashState.value.copy(
                    progress = 0.85f,
                    statusText = "正在部署启动脚本..."
                )
                delay(200)

                val homeDir = com.mermes.core.MermesPaths.getHomeDir(getApplication())
                val scriptFile = File(homeDir, "init_script.sh")
                val deploySuccess = com.mermes.app.utils.AssetUtils.deployAssetScript(
                    getApplication(),
                    "init_script.sh",
                    scriptFile
                )

                if (!deploySuccess) {
                    throw Exception("部署引导脚本失败")
                }

                _splashState.value = _splashState.value.copy(
                    progress = 0.9f,
                    statusText = "正在后台拉起本地网站服务..."
                )

                // 异步拉起脚本，使用 ShellCommandExecutor
                val executor = com.mermes.core.utils.ShellCommandExecutor(getApplication())
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        executor.execute(
                            command = "bash ${scriptFile.absolutePath}",
                            cwd = homeDir.absolutePath
                        ).collect { line ->
                            MermesLog.i("LocalServiceLog", line)
                        }
                    } catch (e: Exception) {
                        MermesLog.e("LocalServiceLog", "Execution error", e)
                    }
                }

                // 循环探测 20265 端口是否就绪，最多尝试 15 次 (每次间隔 500ms，共 7.5 秒)
                var portReady = false
                val maxAttempts = 15
                for (attempt in 1..maxAttempts) {
                    _splashState.value = _splashState.value.copy(
                        progress = 0.9f + (attempt.toFloat() / maxAttempts) * 0.08f,
                        statusText = "正在等待本地控制台就绪... ($attempt/$maxAttempts)"
                    )
                    if (isPortActive(20265)) {
                        portReady = true
                        break
                    }
                    delay(500)
                }

                if (!portReady) {
                    throw Exception("本地服务端口 (20265) 启动超时，请重试")
                }

                _splashState.value = _splashState.value.copy(
                    progress = 1.0f,
                    statusText = "服务已就绪，正在进入控制台...",
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

    private suspend fun isPortActive(port: Int, host: String = "127.0.0.1"): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(host, port), 200)
                    true
                }
            } catch (e: Exception) {
                false
            }
        }
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
    val isReady: Boolean = false,
    val error: String? = null
)
