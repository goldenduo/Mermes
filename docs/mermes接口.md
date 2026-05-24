# Mermes 接口与脚本契约

## 模块信息

- **包名**: `com.mermes`
- **类型**: Android Application
- **语言**: Kotlin + Jetpack Compose

---

## 1. 资产初始化脚本契约 (init_script.sh)

在 App 内部 `assets` 中预置的引导运行脚本。解包安装完成后，复制到本地 `$HOME/init_script.sh` 执行。

### 1.1 脚本内容规范 (参考测试脚本)
```bash
#!/bin/bash
# init_script.sh
# 自动在后台启动本地 Web 控制台服务，监听 20265 端口

echo "[Init] Starting Mermes Local Service on port 20265..."

# 1. 开启一个简单的 Python 内置 Web 服务器用于测试
#（在生产环境中，这可以是用户自定义的后端或可执行文件守护进程）
python3 -m http.server 20265 > "$HOME/web_server.log" 2>&1 &

# 保存子进程 PID 以供外部控制或检测
echo $! > "$HOME/web_server.pid"

echo "[Init] Mermes Local Service successfully launched."
```

---

## 2. 初始化进度与状态实体 (InitStatus)

```kotlin
package com.mermes.app.data.model

/**
 * 启动屏环境自检与极简安装解压状态
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
    
    object LaunchingScript : InitStatus() // 正在执行初始化脚本并等待端口就绪
    
    object Success : InitStatus() // 成功就绪，可以显示 WebView
    
    data class Failed(
        val error: String
    ) : InitStatus()
}
```

---

## 3. WebView 网页支持参数规范

全屏挂载的原生 `WebView` 必须在 Kotlin 中进行以下 API 契约配置：

```kotlin
// 启用最高标准网页交互控制，允许本地 JavaScript 程序流畅运作
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    databaseEnabled = true
    useWideViewPort = true
    loadWithOverviewMode = true
    allowFileAccess = true
    allowContentAccess = true
    builtInZoomControls = true
    displayZoomControls = false
}
```
