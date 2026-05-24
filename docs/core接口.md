# Core 模块接口

## 模块信息

- **包名**: `com.mermes.core`
- **类型**: Android AAR Library
- **语言**: Kotlin + JNI (C/C++)

---

## 1. Bootstrap 安装接口
保持原 `com.mermes.core.bootstrap.MermesBootstrap` 与 `NativeBootstrapLib` 接口定义不变。

---

## 2. 伪终端与 Shell 环境接口
保持原 `com.mermes.core.terminal.ShellEnvironment` 和 `NativeTerminalLib` 接口定义不变。

---

## 3. 代码级直接命令执行器 [已实现]

### 3.1 ShellCommandExecutor

```kotlin
package com.mermes.core.utils

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ShellCommandExecutor(private val context: Context) {

    class CommandHandle internal constructor(private val process: Process) {
        fun interrupt() { process.destroy() }
        fun destroyForcibly() { process.destroyForcibly() }
        fun isAlive(): Boolean = process.isAlive
    }

    fun execute(
        command: String,
        cwd: String? = null,
        environment: Map<String, String> = emptyMap(),
        onHandleCreated: ((CommandHandle) -> Unit)? = null
    ): Flow<String>
}
```

---

## 4. TerminalSession 数据模型 [扩展]

```kotlin
package com.mermes.core.terminal

import java.util.UUID

class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    val masterFd: Int,
    val pid: Int
) {
    enum class State { RUNNING, FINISHED, ERROR }

    var state: State = State.RUNNING
        internal set

    var exitCode: Int = 0
        internal set

    /** 用户自定义会话名称，如 "bash" 或 "server" */
    var name: String = ""

    /** Shell 动态标题（由终端转义序列 OSC 0/2 更新） */
    var title: String = ""

    /** 是否仍在运行 */
    val isRunning: Boolean get() = state == State.RUNNING
}

interface TerminalSessionCallback {
    fun onTextChanged(session: TerminalSession, data: ByteArray)
    fun onSessionFinished(session: TerminalSession, exitCode: Int)
    /** 当 Shell 更新动态标题时回调（可选） */
    fun onTitleChanged(session: TerminalSession, title: String) {}
}
```

---

## 5. TerminalManager 多 Session 管理接口 [扩展]

```kotlin
package com.mermes.core.terminal

object TerminalManager {

    /** 创建新 Shell Session（bash/zsh/sh，自动查找） */
    fun createSession(
        context: Context,
        executable: String? = null,
        arguments: Array<String> = emptyArray(),
        cwd: String? = null,
        environment: Map<String, String> = emptyMap(),
        callback: TerminalSessionCallback
    ): TerminalSession

    /** 创建安全兜底 Session（/system/bin/sh） */
    fun createFailsafeSession(context: Context, callback: TerminalSessionCallback): TerminalSession

    /** 向指定 Session 写入字节数据（用于键盘输入） */
    fun writeToSession(session: TerminalSession, data: ByteArray)

    /** 向指定 Session 写入文本（可选自动追加换行） */
    fun writeToSession(session: TerminalSession, text: String, newline: Boolean = false)

    /** 关闭指定 Session（发送 SIGTERM，清理 fd 和线程） */
    fun closeSession(session: TerminalSession)

    /** 关闭全部 Session */
    fun closeAllSessions()

    /** 获取所有活跃 Session 列表 */
    fun getActiveSessions(): List<TerminalSession>

    /** 设置 PTY 窗口大小（行列数）*/
    fun setPtyWindowSize(session: TerminalSession, rows: Int, cols: Int)
}
```

---

## 6. TerminalFragment 多 Session UI 接口 [重构]

```kotlin
package com.mermes.core.terminal

import androidx.fragment.app.Fragment

/**
 * 伪终端界面片段，包含与 Termux 完全一致的双行快捷键布局及多 Session 管理逻辑。
 * 可以无缝嵌入任何 Activity 中。
 */
class TerminalFragment : Fragment() {

    companion object {
        /**
         * @param failsafe 是否进入安全模式（/system/bin/sh）
         */
        fun newInstance(failsafe: Boolean = false): TerminalFragment
    }

    // ── Session 管理 API ──────────────────────────────────────────

    /** 创建并切换到一个新 Session */
    fun createNewSession()

    /** 切换到指定 Session */
    fun switchToSession(session: TerminalSession)

    /** 关闭当前活跃 Session */
    fun closeCurrentSession()

    /** 重命名当前活跃 Session */
    fun renameCurrentSession(newName: String)

    /** 获取当前活跃 Session */
    fun getCurrentSession(): TerminalSession?

    /** 获取所有 Session 列表 */
    fun getSessions(): List<TerminalSession>

    // ── 返回键处理 ────────────────────────────────────────────────

    /**
     * 宿主 Activity 收到返回键时调用此方法。
     * 若 Session 还在运行则发送 ESC 信号消费返回键，返回 true；
     * 否则返回 false，由宿主处理。
     */
    fun onBackPressed(): Boolean
}
```

---

## 7. 快捷键布局规范 [更新]

严格遵照 Termux 官方 `DEFAULT_IVALUE_EXTRA_KEYS` 的双行排列：

```
Row 1: [ESC]  [/]  [-]  [HOME]  [UP]    [END]   [PGUP]
Row 2: [TAB]  [CTRL] [ALT] [LEFT]  [DOWN]  [RIGHT] [PGDN]
```

XML 文件：`core/src/main/res/layout/fragment_terminal.xml`

CTRL / ALT 为 ToggleButton，其他为普通 Button，所有导航键支持长按重复发送。

---

## 8. Deb 包安装与预置依赖包接口
保持原 `com.mermes.core.deb.DebInstaller` 等接口定义不变。

---

## 9. TerminalView 多 Session 辅助方法 [NEW]

```kotlin
// 挂接一个已有 Session（多 Session 切换专用，不关闭旧 Session）
fun attachSession(newSession: TerminalSession)

// 向终端 Emulator buffer 直接注入文本（不通过 PTY），用于显示退出提示等
fun printText(text: String)

// 完全关闭当前 Session 并清理（终止使用时调用）
fun detachSession()
```

