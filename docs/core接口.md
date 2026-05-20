# Core 模块接口

## 模块信息

- **包名**: `com.mermes.core`
- **类型**: Android AAR Library
- **语言**: Kotlin + JNI (C/C++)

---

## 1. Bootstrap 安装接口

### 1.1 MermesBootstrap

Bootstrap 安装器主类。

```kotlin
package com.mermes.core.bootstrap

object MermesBootstrap {

    /**
     * 安装 bootstrap 环境
     *
     * @param context Android Context
     * @param progressCallback 进度回调 (0.0 - 1.0)
     * @return 安装结果
     * @throws BootstrapInstallException 安装失败时抛出
     */
    suspend fun installBootstrap(
        context: Context,
        progressCallback: ((Float) -> Unit)? = null
    ): BootstrapResult

    /**
     * 检查 bootstrap 是否已安装
     *
     * @param context Android Context
     * @return true 表示已安装且有效
     */
    fun isBootstrapInstalled(context: Context): Boolean

    /**
     * 获取 PREFIX 目录路径
     *
     * @param context Android Context
     * @return PREFIX 目录路径 (通常为 /data/data/com.mermes/files/usr)
     */
    fun getPrefixDir(context: Context): File

    /**
     * 获取 HOME 目录路径
     *
     * @param context Android Context
     * @return HOME 目录路径 (通常为 /data/data/com.mermes/files/home)
     */
    fun getHomeDir(context: Context): File

    /**
     * 清除 bootstrap 环境（用于重装）
     *
     * @param context Android Context
     */
    fun clearBootstrap(context: Context)
}

/**
 * Bootstrap 安装结果
 */
data class BootstrapResult(
    val success: Boolean,
    val duration: Long, // 耗时（毫秒）
    val extractedFiles: Int, // 解压文件数
    val createdSymlinks: Int, // 创建符号链接数
    val error: String? = null
)

/**
 * Bootstrap 安装异常
 */
class BootstrapInstallException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
```

### 1.2 NativeBootstrapLib

JNI Native 方法声明。

```kotlin
package com.mermes.core.bootstrap

internal object NativeBootstrapLib {

    /**
     * 加载 native 库
     */
    fun load() {
        System.loadLibrary("mermes-bootstrap")
    }

    /**
     * 获取嵌入的 bootstrap zip 字节数组
     *
     * @return zip 文件的字节数组
     */
    external fun getZip(): ByteArray
}
```

---

## 2. 伪终端接口

### 2.1 TerminalSession

终端会话管理类。

```kotlin
package com.mermes.core.terminal

/**
 * 终端会话
 */
class TerminalSession(
    val id: String,
    val masterFd: Int,
    val pid: Int
) {
    /**
     * 会话状态
     */
    enum class State {
        RUNNING,
        FINISHED,
        ERROR
    }

    /**
     * 当前状态
     */
    var state: State = State.RUNNING
        internal set

    /**
     * 退出码（仅在 FINISHED 状态有效）
     */
    var exitCode: Int = 0
        internal set
}

/**
 * 终端会话回调
 */
interface TerminalSessionCallback {
    /**
     * 收到输出数据
     *
     * @param session 会话
     * @param data 输出数据
     */
    fun onTextChanged(session: TerminalSession, data: ByteArray)

    /**
     * 会话结束
     *
     * @param session 会话
     * @param exitCode 退出码
     */
    fun onSessionFinished(session: TerminalSession, exitCode: Int)
}
```

### 2.2 TerminalManager

终端管理器，负责创建和管理终端会话。

```kotlin
package com.mermes.core.terminal

object TerminalManager {

    /**
     * 创建新的终端会话
     *
     * @param context Android Context
     * @param executable 可执行文件路径（默认为 $PREFIX/bin/bash）
     * @param arguments 命令参数
     * @param cwd 工作目录（默认为 HOME）
     * @param environment 额外环境变量
     * @param callback 会话回调
     * @return 终端会话
     */
    fun createSession(
        context: Context,
        executable: String? = null,
        arguments: Array<String> = emptyArray(),
        cwd: String? = null,
        environment: Map<String, String> = emptyMap(),
        callback: TerminalSessionCallback
    ): TerminalSession

    /**
     * 创建 Failsafe 安全模式会话
     *
     * @param context Android Context
     * @param callback 会话回调
     * @return 终端会话
     */
    fun createFailsafeSession(
        context: Context,
        callback: TerminalSessionCallback
    ): TerminalSession

    /**
     * 向会话写入数据
     *
     * @param session 终端会话
     * @param data 要写入的数据
     */
    fun writeToSession(session: TerminalSession, data: ByteArray)

    /**
     * 向会话写入字符串
     *
     * @param session 终端会话
     * @param text 要写入的字符串
     * @param newline 是否追加换行符
     */
    fun writeToSession(session: TerminalSession, text: String, newline: Boolean = false)

    /**
     * 关闭会话
     *
     * @param session 终端会话
     */
    fun closeSession(session: TerminalSession)

    /**
     * 获取所有活跃会话
     *
     * @return 活跃会话列表
     */
    fun getActiveSessions(): List<TerminalSession>

    /**
     * 关闭所有会话
     */
    fun closeAllSessions()
}
```

### 2.3 ShellEnvironment

Shell 环境变量管理。

```kotlin
package com.mermes.core.terminal

object ShellEnvironment {

    /**
     * 获取标准 Termux 环境变量
     *
     * @param context Android Context
     * @return 环境变量 Map
     */
    fun getEnvironment(context: Context): Map<String, String>

    /**
     * 获取 PATH 值
     *
     * @param context Android Context
     * @return PATH 字符串
     */
    fun getPath(context: Context): String

    /**
     * 写入环境变量文件
     *
     * @param context Android Context
     */
    fun writeEnvironmentToFile(context: Context)
}
```

### 2.4 NativeTerminalLib

JNI Native 方法声明。

```kotlin
package com.mermes.core.terminal

internal object NativeTerminalLib {

    /**
     * 加载 native 库
     */
    fun load() {
        System.loadLibrary("mermes-terminal")
    }

    /**
     * 创建子进程
     *
     * @param executable 可执行文件路径
     * @param args 参数数组 (args[0] 为进程名)
     * @param cwd 工作目录
     * @param environment 环境变量数组 ["KEY=VALUE", ...]
     * @param masterFd 输出参数，Master PTY fd
     * @return 子进程 PID
     */
    external fun createSubprocess(
        executable: String,
        args: Array<String>,
        cwd: String,
        environment: Array<String>,
        masterFd: IntArray
    ): Int

    /**
     * 等待子进程结束
     *
     * @param pid 子进程 PID
     * @return 退出码
     */
    external fun waitFor(pid: Int): Int

    /**
     * 发送信号到子进程
     *
     * @param pid 子进程 PID
     * @param signal 信号值 (如 SIGINT, SIGTERM)
     */
    external fun sendSignal(pid: Int, signal: Int)

    /**
     * 设置 PTY 窗口大小
     *
     * @param masterFd Master PTY fd
     * @param rows 行数
     * @param cols 列数
     */
    external fun setPtyWindowSize(masterFd: Int, rows: Int, cols: Int)

    /**
     * 关闭 Master PTY fd
     *
     * @param masterFd Master PTY fd
     */
    external fun closeFd(masterFd: Int)
}
```

---

### 2.5 TerminalView

伪终端 Android View 组件。

```kotlin
package com.mermes.core.terminal.view

/**
 * 伪终端视图
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * 绑定终端会话
     *
     * @param session 终端会话
     */
    fun attachSession(session: TerminalSession)

    /**
     * 解绑当前会话
     */
    fun detachSession()

    /**
     * 获取当前绑定的会话
     */
    fun getSession(): TerminalSession?

    /**
     * 设置字体大小（sp）
     */
    fun setTextSize(sizeSp: Float)

    /**
     * 设置字体颜色方案
     */
    fun setColorScheme(scheme: TerminalColorScheme)

    /**
     * 复制选中文本到剪贴板
     */
    fun copySelection(): String?

    /**
     * 粘贴剪贴板内容到终端
     */
    fun pasteFromClipboard()

    /**
     * 召唤（显示）软键盘
     */
    fun showKeyboard()

    /**
     * 关闭（隐藏）软键盘
     */
    fun hideKeyboard()

    /**
     * 切换软键盘显示/隐藏状态
     */
    fun toggleKeyboard()

    /**
     * 模拟发送控制键（如 Ctrl+C, 上下左右）
     *
     * @param keyCode 虚拟键码
     */
    fun sendControlKey(keyCode: Int)

    /**
     * Ctrl 修饰键是否处于锁定状态（用于虚拟键盘组合输入）
     */
    var isCtrlToggled: Boolean

    /**
     * Alt 修饰键是否处于锁定状态（用于虚拟键盘组合输入）
     */
    var isAltToggled: Boolean

    /**
     * 当修饰键锁定状态被消费或改变时触发的回调
     */
    var onModifierStatusChanged: ((ctrl: Boolean, alt: Boolean) -> Unit)?
}
```

### 2.6 TerminalEmulator

终端模拟器，解析 ANSI 转义序列并维护屏幕状态。

```kotlin
package com.mermes.core.terminal.view

/**
 * 终端模拟器
 */
class TerminalEmulator(
    private val columns: Int,
    private val rows: Int
) {
    /**
     * 处理终端输出数据
     *
     * @param data 输出字节数组
     */
    fun append(data: ByteArray)

    /**
     * 获取指定行的内容
     *
     * @param row 行号 (0-based)
     * @return 行数据
     */
    fun getLine(row: Int): TerminalRow

    /**
     * 获取光标列位置
     */
    fun getCursorCol(): Int

    /**
     * 获取光标行位置
     */
    fun getCursorRow(): Int

    /**
     * 调整终端大小
     *
     * @param columns 新列数
     * @param rows 新行数
     */
    fun resize(columns: Int, rows: Int)

    /**
     * 重置终端状态
     */
    fun reset()
}
```

### 2.7 TerminalRenderer

终端渲染引擎。

```kotlin
package com.mermes.core.terminal.view

/**
 * 终端渲染器
 */
class TerminalRenderer(
    private val context: Context,
    private val emulator: TerminalEmulator
) {
    /**
     * 渲染终端内容到 Canvas
     *
     * @param canvas 目标画布
     * @param cursorVisible 光标是否可见
     */
    fun render(canvas: Canvas, cursorVisible: Boolean)

    /**
     * 设置字体大小
     *
     * @param sizeSp 字体大小 (sp)
     */
    fun setTextSize(sizeSp: Float)

    /**
     * 获取字符宽度（像素）
     */
    fun getFontWidth(): Float

    /**
     * 获取行高（像素）
     */
    fun getFontLineSpacing(): Float

    /**
     * 将屏幕坐标转换为终端行列
     *
     * @param x 屏幕 x 坐标
     * @param y 屏幕 y 坐标
     * @return Pair<col, row>
     */
    fun coordToColRow(x: Float, y: Float): Pair<Int, Int>
}
```

### 2.8 TerminalColorScheme

终端颜色方案。

```kotlin
package com.mermes.core.terminal.view

/**
 * 终端颜色方案
 */
data class TerminalColorScheme(
    val foreground: Int,    // 默认前景色
    val background: Int,    // 默认背景色
    val cursor: Int,        // 光标颜色
    val selection: Int,     // 选中背景色
    val ansiColors: IntArray // 16 色 ANSI 调色板
)
```

---

## 3. Deb 包安装接口

### 3.1 DebInstaller

Deb 包安装器。

```kotlin
package com.mermes.core.deb

/**
 * Deb 包安装器
 */
object DebInstaller {

    /**
     * 安装所有预置的 deb 包
     *
     * @param context Android Context
     * @param progressCallback 进度回调 (包名, 当前进度, 总数)
     * @return 安装结果列表
     */
    suspend fun installPresetPackages(
        context: Context,
        progressCallback: ((packageName: String, current: Int, total: Int) -> Unit)? = null
    ): List<DebInstallResult>

    /**
     * 安装单个 deb 包
     *
     * @param context Android Context
     * @param debData deb 文件字节数组
     * @param packageName 包名（用于日志）
     * @return 安装结果
     */
    suspend fun installPackage(
        context: Context,
        debData: ByteArray,
        packageName: String
    ): DebInstallResult

    /**
     * 获取已安装的包列表
     *
     * @param context Android Context
     * @return 已安装的包名和版本
     */
    fun getInstalledPackages(context: Context): Map<String, String>

    /**
     * 检查包是否已安装
     *
     * @param context Android Context
     * @param packageName 包名
     * @return true 表示已安装
     */
    fun isPackageInstalled(context: Context, packageName: String): Boolean

    /**
     * 检查所有预置包是否已全部安装
     *
     * @param context Android Context
     * @return true 表示所有预置包已安装
     */
    fun isAllPresetInstalled(context: Context): Boolean

    /**
     * 获取预置包名称列表（从 assets 读取）
     *
     * @param context Android Context
     * @return 包名列表
     */
    fun getPresetPackageNames(context: Context): List<String>

    /**
     * 获取预置包列表（按安装顺序）
     *
     * @param context Android Context
     * @return 按依赖顺序排列的包名列表
     */
    fun getPresetPackageOrder(context: Context): List<String>
}

/**
 * Deb 安装结果
 */
data class DebInstallResult(
    val packageName: String,
    val version: String,
    val success: Boolean,
    val installedFiles: Int,
    val error: String? = null
)
```

### 3.2 DebParser

Deb 文件解析器。

```kotlin
package com.mermes.core.deb

internal object DebParser {

    /**
     * 解析 deb 文件
     *
     * @param debData deb 文件字节数组
     * @return 解析结果
     */
    fun parse(debData: ByteArray): DebPackage

    /**
     * 解析 control 文件
     *
     * @param controlData control.tar.xz 字节数组
     * @return 包控制信息
     */
    fun parseControl(controlData: ByteArray): DebControl

    /**
     * 解压 data 文件到目标目录
     *
     * @param dataData data.tar.xz 字节数组
     * @param targetDir 目标目录
     * @return 解压的文件数
     */
    fun extractData(dataData: ByteArray, targetDir: File): Int
}

/**
 * Deb 包信息
 */
data class DebPackage(
    val control: DebControl,
    val controlData: ByteArray,
    val dataData: ByteArray
)

/**
 * Deb 包控制信息
 */
data class DebControl(
    val packageName: String,
    val version: String,
    val depends: List<String>, // 依赖列表
    val preDepends: List<String>, // 预依赖列表
    val provides: List<String>, // 提供的虚拟包
    val description: String
)
```

### 3.3 DependencyResolver

依赖解析器。

```kotlin
package com.mermes.core.deb

internal object DependencyResolver {

    /**
     * 解析依赖树并返回安装顺序
     *
     * @param packages 包信息列表
     * @return 按拓扑排序的安装顺序（叶子节点在前）
     * @throws CircularDependencyException 存在循环依赖时抛出
     */
    fun resolveInstallationOrder(packages: List<DebControl>): List<String>

    /**
     * 检查是否存在循环依赖
     *
     * @param packages 包信息列表
     * @return 循环依赖的包列表，空列表表示无循环依赖
     */
    fun detectCircularDependencies(packages: List<DebControl>): List<List<String>>
}

/**
 * 循环依赖异常
 */
class CircularDependencyException(
    val cycle: List<String>,
    message: String
) : Exception(message)
```

### 3.4 NativeDebLib

JNI Native 方法声明（用于从 SO 加载 deb）。

```kotlin
package com.mermes.core.deb

internal object NativeDebLib {

    /**
     * 加载 native 库
     */
    fun load() {
        System.loadLibrary("mermes-deb")
    }

    /**
     * 获取预置 deb 包的数量
     *
     * @return deb 包数量
     */
    external fun getDebCount(): Int

    /**
     * 根据索引获取 deb 包数据
     *
     * @param index 包索引 (0 到 getDebCount()-1)
     * @return deb 文件字节数组
     */
    external fun getDebByIndex(index: Int): ByteArray

    /**
     * 根据架构获取 deb 包数据
     *
     * @param arch 架构名称 (aarch64, arm, i686, x86_64)
     * @param packageName 包名
     * @return deb 文件字节数组，若不存在返回 null
     */
    external fun getDebByArchAndName(arch: String, packageName: String): ByteArray?

    /**
     * 获取所有预置 deb 包的名称列表
     *
     * @return 包名列表
     */
    external fun getDebNames(): Array<String>
}
```

---

## 4. 工具类接口

### 4.1 NativeUtils

JNI 工具方法。

```kotlin
package com.mermes.core.utils

internal object NativeUtils {

    /**
     * 加载 native 库
     */
    fun load() {
        System.loadLibrary("mermes-utils")
    }

    /**
     * 设置文件权限
     *
     * @param path 文件路径
     * @param mode 权限模式 (如 0700)
     */
    external fun chmod(path: String, mode: Int)

    /**
     * 创建符号链接
     *
     * @param target 链接目标
     * @param linkPath 链接路径
     */
    external fun symlink(target: String, linkPath: String)

    /**
     * 获取当前设备架构
     *
     * @return 架构名称 (aarch64, arm, i686, x86_64)
     */
    external fun getArch(): String

    /**
     * 检查文件是否为 ELF 二进制
     *
     * @param path 文件路径
     * @return true 表示是 ELF 二进制
     */
    external fun isElfBinary(path: String): Boolean

    /**
     * 获取文件的 shebang 解释器路径
     *
     * @param path 文件路径
     * @return 解释器路径，若无 shebang 返回 null
     */
    external fun getShebang(path: String): String?
}
```

### 4.2 FileUtils

文件操作工具类。

```kotlin
package com.mermes.core.utils

object FileUtils {

    /**
     * 递归删除目录
     *
     * @param dir 目录路径
     */
    fun deleteRecursive(dir: File)

    /**
     * 创建目录及父目录
     *
     * @param dir 目录路径
     * @param mode 目录权限
     */
    fun createDir(dir: File, mode: Int = 0755)

    /**
     * 解压 zip 到目标目录
     *
     * @param zipData zip 字节数组
     * @param targetDir 目标目录
     * @param filter 过滤器（返回 true 表示解压）
     * @return 解压的文件数
     */
    fun extractZip(
        zipData: ByteArray,
        targetDir: File,
        filter: ((String) -> Boolean)? = null
    ): Int

    /**
     * 读取 zip 中的 SYMLINKS.txt
     *
     * @param zipData zip 字节数组
     * @return 符号链接列表 (target, linkPath)
     */
    fun parseSymlinksFile(zipData: ByteArray): List<Pair<String, String>>

    /**
     * 解析 shebang 行
     *
     * @param file 文件
     * @return 解释器路径，若无 shebang 返回 null
     */
    fun parseShebang(file: File): String?
}
```

---

## 5. 架构相关常量

```kotlin
package com.mermes.core

/**
 * 支持的架构
 */
enum class Arch(val value: String) {
    AARCH64("aarch64"),
    ARM("arm"),
    I686("i686"),
    X86_64("x86_64");

    companion object {
        /**
         * 从系统属性获取当前架构
         */
        fun current(): Arch {
            return when (android.os.Build.SUPPORTED_ABIS[0]) {
                "arm64-v8a" -> AARCH64
                "armeabi-v7a", "armeabi" -> ARM
                "x86_64" -> X86_64
                "x86" -> I686
                else -> throw IllegalStateException("Unsupported architecture")
            }
        }
    }
}

/**
 * 路径常量
 */
object MermesPaths {
    const val PREFIX_DIR_NAME = "usr"
    const val HOME_DIR_NAME = "home"
    const val STAGING_DIR_NAME = "usr-staging"
    const val SYMLINKS_FILE = "SYMLINKS.txt"

    fun getPrefixDir(context: Context): File =
        File(context.filesDir, PREFIX_DIR_NAME)

    fun getHomeDir(context: Context): File =
        File(context.filesDir, HOME_DIR_NAME)

    fun getStagingDir(context: Context): File =
        File(context.filesDir, STAGING_DIR_NAME)

    fun getBinDir(context: Context): File =
        File(getPrefixDir(context), "bin")

    fun getTmpDir(context: Context): File =
        File(getPrefixDir(context), "tmp")
}
```
