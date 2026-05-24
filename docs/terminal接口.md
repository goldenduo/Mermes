# terminal 接口

## 架构概览

```
┌─────────────────────────────────────────────────┐
│                   TerminalView                    │
│  inputCodePoint() ←── onKeyDown / commitText     │
│         │                                        │
│         ▼                                        │
│  ┌─────────────┐    ┌───────────────────────┐   │
│  │  KeyHandler  │    │  TerminalEmulator     │   │
│  │  getCode()   │───▶│  processByte()        │   │
│  │  getKeyCode()│    │  processCodePoint()   │   │
│  └─────────────┘    │  emitCodePoint()       │   │
│                      └───────────────────────┘   │
│  ┌─────────────────┐                             │
│  │  ExtraKeysView   │  GridLayout + touch        │
│  │  SpecialButton   │  long-press lock           │
│  │  ExtraKeyButton  │  popup support             │
│  └─────────────────┘                             │
└─────────────────────────────────────────────────┘
```

## 核心类接口

### 1. KeyHandler

独立的 VT100 转义序列生成器，不依赖 Android View。

```kotlin
object KeyHandler {
    // Modifier 编码常量
    const val MODIFIER_SHIFT = 2
    const val MODIFIER_ALT = 3
    const val MODIFIER_CTRL = 5

    /**
     * 处理 Ctrl+字母的特殊映射
     * Space→NUL(0), [/3→ESC(27), ]/\→FS(28), ?→DEL(127)
     * A-Z→1-26
     *
     * @param keyCode Android KeyEvent keyCode
     * @param metaState KeyEvent.metaState
     * @return 映射后的字符码点，-1 表示无特殊映射
     */
    fun getKeyCode(keyCode: Int, metaState: Int): Int

    /**
     * 生成 VT100 转义序列
     *
     * @param keyCode Android KeyEvent keyCode
     * @param appMode 终端 application cursor keys 模式
     * @param cursorKeyMode DECCKM cursor key mode
     * @param modifiers 组合修饰符编码 (Shift=2, Alt=3, Ctrl=5, 组合相加)
     * @return 转义序列字节数组，null 表示未处理
     */
    fun getCode(keyCode: Int, appMode: Boolean, cursorKeyMode: Boolean, modifiers: Int): ByteArray?
}
```

映射表（部分）：

| KeyCode | 无修饰符 | Application Mode |
|---------|---------|-----------------|
| KEYCODE_DPAD_UP | ESC [ A | ESC O A |
| KEYCODE_DPAD_DOWN | ESC [ B | ESC O B |
| KEYCODE_DPAD_RIGHT | ESC [ C | ESC O C |
| KEYCODE_DPAD_LEFT | ESC [ D | ESC O D |
| KEYCODE_MOVE_HOME | ESC [ H | ESC O H |
| KEYCODE_MOVE_END | ESC [ F | ESC O F |
| KEYCODE_PAGE_UP | ESC [ 5 ~ | ESC [ 5 ~ |
| KEYCODE_PAGE_DOWN | ESC [ 6 ~ | ESC [ 6 ~ |
| KEYCODE_ESCAPE | ESC | ESC |
| KEYCODE_ENTER | \r | \r |
| KEYCODE_TAB | \t | \t |
| KEYCODE_DEL | ESC [ 3 ~ | ESC [ 3 ~ |
| KEYCODE_FORWARD_DEL | ESC [ 3 ~ | ESC [ 3 ~ |

带修饰符时格式：`ESC [ 1 ; {mod+1} {final}`

### 2. TerminalView 键盘输入管道

```kotlin
class TerminalView : View {
    // 中央输入入口
    fun inputCodePoint(codePoint: Int, controlDown: Boolean, leftAltDown: Boolean)

    // 硬件键盘处理
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean
    override fun onKeyMultiple(keyCode: Int, count: Int, event: KeyEvent): Boolean
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean

    // 软键盘 (InputConnection)
    fun createInputConnection(outAttrs: EditorInfo): InputConnection

    // 发送转义序列到 PTY
    fun sendCode(code: ByteArray, doInsert: Boolean)

    // 修饰键状态
    var isCtrlToggled: Boolean
    var isAltToggled: Boolean
    var onModifierStatusChanged: ((ctrl: Boolean, alt: Boolean) -> Unit)?

    // 功能方法
    fun sendControlKey(keyCode: Int)
    fun sendText(text: String)
    fun pasteFromClipboard()
    fun toggleKeyboard()
    fun showKeyboard()
}
```

`inputCodePoint` 流程：
1. 如果 `controlDown`，尝试 Ctrl+key 映射（A→1, Space→0 等）
2. 如果 `leftAltDown`，先发送 ESC (0x1B) 前缀
3. 将 codePoint 编码为 UTF-8 字节数组
4. 调用 `TerminalManager.writeToSession()` 发送到 PTY

### 3. SpecialButtonState (修饰键三态)

```kotlin
class SpecialButtonState {
    val isCreated: Boolean  // 是否已创建
    var isActive: Boolean   // 是否激活（按下）
    var isLocked: Boolean   // 是否锁定（长按锁定）

    /**
     * 切换状态：inactive → active → locked → inactive
     * 短按：toggle active
     * 长按：toggle lock
     */
    fun toggle()
    fun toggleLock()
}
```

### 4. ExtraKeyButton (按钮模型)

```kotlin
data class ExtraKeyButton(
    val key: String,           // 主键值（如 "CTRL", "A", "ESC"）
    val display: String?,      // 显示文本（默认等于 key）
    val macro: List<String>?,  // 宏：点击时依次发送多个键
    val popup: List<ExtraKeyButton>?  // 上滑弹出按钮
)
```

JSON 配置示例：
```json
[
  {"key": "ESC"},
  {"key": "CTRL"},
  {"key": "ALT"},
  {"key": "-"},
  {"key": "/"},
  {"key": "|"},
  {"key": "TAB"},
  {"key": "↑", "key": "DPAD_UP"},
  {"key": "↓", "key": "DPAD_DOWN"},
  {"key": "←", "key": "DPAD_LEFT"},
  {"key": "→", "key": "DPAD_RIGHT"},
  {"key": "HOME"},
  {"key": "END"},
  {"key": "PGUP"},
  {"key": "PGDN"},
  {"key": "PASTE"},
  {"key": "ENTER"},
  {"key": "SPACE"},
  {"key": "KEYBOARD"}
]
```

### 5. ExtraKeysView (触摸层)

```kotlin
class ExtraKeysView : GridLayout {
    // 按钮配置
    var extraButtons: List<List<ExtraKeyButton>>

    // 触摸处理
    // - 按下：执行 key (或激活 modifier)
    // - 长按 400ms：锁定 modifier / 开始 key repeat
    // - 上滑：显示 popup
    // - 松手：停止 repeat / 关闭 popup

    // ScheduledExecutorService 参数
    companion object {
        const val LONG_PRESS_TIMEOUT = 400L  // ms
        const val REPEAT_INTERVAL = 100L     // ms
    }
}
```

### 6. TerminalExtraKeys (路由层)

```kotlin
object TerminalExtraKeys {
    /**
     * 处理 ExtraKeyButton 点击
     *
     * @param view TerminalView 实例
     * @param key 按钮 key 值
     * @param controlDown Ctrl 是否激活
     * @param altDown Alt 是否激活
     */
    fun onKey(view: TerminalView, key: String, controlDown: Boolean, altDown: Boolean)

    /**
     * 判断 key 是否为控制键（需要通过 KeyEvent 发送）
     * 控制键：ESC, TAB, DPAD_*, HOME, END, PGUP, PGDN, ENTER, SPACE
     * 文本键：-, /, |, 其他字符
     */
    fun isControlKey(key: String): Boolean

    /**
     * 将 key 名称映射为 Android KeyEvent keyCode
     */
    fun mapKeyToKeyCode(key: String): Int
}
```

### 7. TerminalSessionCallback

```kotlin
interface TerminalSessionCallback {
    fun onTextChanged(session: TerminalSession, data: ByteArray)
    fun onSessionFinished(session: TerminalSession, exitCode: Int)
}
```

### 8. TerminalRenderer 渲染优化

```kotlin
class TerminalRenderer(
    private val textSize: Float,
    private val fontWidth: Float
) {
    // ASCII 宽度缓存
    private val asciiMeasures = FloatArray(127)

    // 初始化时预计算 ASCII 字符宽度
    init {
        for (i in 32..126) {
            asciiMeasures[i] = paint.measureText(String(charArrayOf(i.toChar())))
        }
    }

    /**
     * 渲染一行文本
     * 对宽字符使用 canvas.scale() 强制缩放到 2x 字符宽度
     * 对组合字符叠加渲染到前一字符上方
     */
    fun drawTextRun(
        canvas: Canvas,
        charArray: CharArray,
        startCharIndex: Int,
        endCharIndex: Int,
        y: Float,
        startCharPosX: Float
    )
}
```

## 模块依赖关系

```
terminal (Application)
  ├── core
  │     └── terminal
  │           ├── TerminalManager      (会话管理)
  │           ├── TerminalSession      (会话模型)
  │           ├── TerminalEmulator     (VT100 解析 + UTF-8 解码)
  │           ├── WcWidth             (字符宽度计算)
  │           ├── ShellEnvironment    (环境变量)
  │           └── view
  │                 ├── TerminalView   (显示 + 键盘输入)
  │                 ├── TerminalRenderer (渲染)
  │                 └── KeyHandler     (转义序列生成) [新增]
  └── terminal-extrakeys [新增]
        ├── ExtraKeysView             (GridLayout 触摸)
        ├── ExtraKeyButton            (按钮模型)
        ├── SpecialButtonState        (三态修饰键)
        └── TerminalExtraKeys         (键路由)
```
