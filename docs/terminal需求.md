# terminal 需求

## 功能概述
`terminal` 模块是 Mermes 项目的终端仿真显示器和应用的绝对启动入口。它作为一个可交互的 Android 终端客户端，利用底层 `core` 模块提供的 bootstrap 环境部署、`.deb` 包预安装以及 PTY 伪终端控制能力，向用户展现一个包含辅助快捷栏、软键盘无感联动以及屏幕手势高亮选取文本的专业终端仿真界面。

## 模块规格
- **模块类型**: Android Application (可生成 APK 包)
- **物理路径**: `terminal/`
- **包名**: `com.mermes` (applicationId，与 bootstrap/deb 制作时的包名一致)
- **最低支持 Android 版本**: API 24 (Android 7.0)

## 功能点

### 1. 启动初始化流程 (SplashActivity)
- **UI 呈现**: 启动页 + 进度条 + 动态国际化文案。
- **顺序执行流**:
  1. 动态检测并依次执行：
     - **Bootstrap 安装**：展示“正在安装 bootstrap...” / "Installing bootstrap..."，提取 `.so` 汇编数据源进行极速解压。
     - **Deb 预置包安装**：展示“正在安装 {包名} ({当前}/{总数})...” / "Installing {包名} ({当前}/{总数})..."。
  2. 部署结束后，自动进入主终端界面（MainActivity）。
- **智能免装检测**: 若 Bootstrap 已部署（`$PREFIX/bin/bash` 存在）且所有预置 deb 均已安装成功，启动时跳过初始化页直达主界面。若有部分 deb 未装，仅增量安装缺少的 deb 包。

### 2. 终端界面仿真交互 (TerminalFragment)
- **MainActivity**: 承载 TerminalFragment，接管处理 Android 系统物理返回键（在活动的终端会话中发送 ESC 字符，或关闭当前会话）。
- **TerminalFragment**: 内部动态渲染 `TerminalView`，并绑定对应的终端会话 `TerminalSession`。在 Fragment 视图销毁时自动注销并回收会话资源。

### 3. 未捕获崩溃与错误容灾处理
- **Bootstrap 部署容灾**: 若环境安装发生非预期错误，自动通过 `Toast` 向用户展现国际化提示，并安全回退到 Failsafe 模式（基于 `/system/bin/sh`），确保终端基本可用。
- **崩溃日志联动**: 应用启动时自动调用 `MermesCrashHandler` 对全局未捕获异常进行接管。

### 4. 辅助快捷栏 (Extra Keys) 与键盘输入系统

#### 4.1 架构概述（参考 Termux）

键盘输入系统分为三层：
1. **ExtraKeysView** — 触摸层：GridLayout 布局，处理触摸/长按/弹出按钮
2. **TerminalExtraKeys** — 路由层：区分控制键（→KeyEvent）和文本键（→inputCodePoint）
3. **KeyHandler** — 编码层：将 keycode + modifier 编码为 VT100 转义序列

#### 4.2 辅助快捷栏布局
- 在终端底部集成一排可水平滑动的辅助按键
- 默认按钮：`ESC`, `CTRL`, `ALT`, `TAB`, `-`, `/`, `|`, `HOME`, `END`, `PGUP`, `PGDN`, `PASTE`, 方向键, `ENTER`, `SPACE` 以及键盘切换键
- 支持 JSON 配置自定义按钮（key/macro/display/popup 字段）

#### 4.3 修饰键状态锁定（参考 Termux SpecialButtonState）
- `CTRL` 和 `ALT` 采用三态模型：`isCreated` / `isActive` / `isLocked`
- 点击一次激活，长按锁定（保持高亮直到再次点击）
- 与软键盘输入配合：发送按键时读取当前 modifier 状态构建 metaState
- 快捷栏发送按键时必须读取当前 `isCtrlToggled`/`isAltToggled` 状态，不可硬编码修饰符

#### 4.4 长按重复触发（参考 Termux ExtraKeysView）
- 使用 `ScheduledExecutorService` 实现（非 Handler postDelayed）
- 首次按下立即执行一次，400ms 后开始重复，间隔 100ms
- 仅对控制键生效（ESC/Tab/方向键/Enter/Home/End/PgUp/PgDn）
- 文本键（- / | 等）不参与长按重复
- `PASTE` 和键盘切换键使用普通 OnClickListener

#### 4.5 弹出按钮（Popup Buttons）
- 每个 ExtraKeyButton 可配置 popup 字段（JSON 数组）
- 手指上滑时弹出 popup 按钮供选择
- popup 按钮与主按钮共享相同的行为规则

#### 4.6 键盘输入管道（参考 Termux inputCodePoint 架构）
- `TerminalView.inputCodePoint(codePoint, controlDown, leftAltDown)` 作为中央入口
- 硬件键盘：`onKeyDown` → `handleKeyCode` → `KeyHandler.getCode` → 发送转义序列
- 软键盘：`InputConnection.commitText` → 逐字符调用 `inputCodePoint`
- Ctrl+字母映射：A→0x01, B→0x02, ..., Z→0x1A；特殊映射 Space→NUL, [/3→ESC, ]/\→FS, ?→DEL
- Alt 前缀：发送 ESC (0x1B) 后跟字符（ESC 前缀表示 Meta 键）

#### 4.7 KeyHandler 转义序列生成（参考 Termux KeyHandler.java）
- 独立类，维护 keycode → VT100 序列映射表
- 支持 modifier 编码：Shift=2, Alt=3, Ctrl=5, Shift+Alt=4, Shift+Ctrl=6, Alt+Ctrl=7, Shift+Alt+Ctrl=8
- `getKeyCode(keyCode, metaState)` 处理 Ctrl+字母特殊映射
- `getCode(keyCode, appMode, cursorKey, modifiers)` 生成最终转义序列

### 4b. 终端文本渲染优化（参考 Termux TerminalRenderer）
- **字体宽度修正**: 通过 `Paint.measureText` 检测字体实际字符宽度与 monospace 假设的偏差
- **Canvas 缩放**: 对宽字符（CJK/Emoji）使用 `canvas.scale()` 强制缩放到 2 个字符宽度
- **组合字符处理**: 检测 Unicode 组合标记（U+0300+），叠加渲染到前一字符上方
- **ASCII 宽度缓存**: 预计算 ASCII 可见字符宽度数组 `asciiMeasures[]`，避免重复测量

### 5. 局部文本选取与半透明高亮复制
- **文本选取**: 支持长按触控区域开启高亮选取，通过手指拖拽（ACTION_MOVE）改变选取范围。
- **视觉反馈**: 选中范围覆盖一层精致的半透明蓝色高亮。
- **无感复制**: 手指抬起（ACTION_UP）后，自动收集该区域的文本写入系统剪贴板，弹出国际化 Toast 提示（如：“选中文本已复制”/“Selected text copied”），并清除高亮选取。

### 6. 终端文本渲染与编码 (Terminal Rendering & Encoding)
- **UTF-8 多字节解码（参考 Termux TerminalEmulator.java）**: 终端模拟器必须内置 UTF-8 状态机，使用 `mUtf8ToFollow` + `mUtf8InputBuffer[4]` 模式。处理流程：`processByte` → `processCodePoint` → `emitCodePoint`。需验证过度编码（overlong encoding）和 C1 控制字符（0x80-0x9F）过滤。
- **宽字符支持 (WcWidth)**: 使用表驱动 + 二分查找实现（参考 Termux WcWidth.java），覆盖 ZERO_WIDTH 表和 WIDE_EASTASIAN 表。必须支持 CJK 统一汉字扩展区（C-H）、Emoji 补充区（U+1F000+）、组合字符和变体选择符。
- **字体回退**: Paint 使用 `Typeface.MONOSPACE` 时，Android minikin 引擎会自动回退到系统 CJK 字体，无需额外配置，但需确保 `drawTextRun` 正确处理混合脚本。
- **PTY UTF-8 环境**: 普通会话和 Failsafe 会话均须设置 `LANG=en_US.UTF-8`，普通会话额外设置 `LC_ALL=en_US.UTF-8` 以兼容 musl libc 环境。
- **输入编码**: 所有发送到 PTY 的文本必须显式使用 `Charsets.UTF_8` 编码，不可依赖平台默认编码。

### 7. 设置选项配置
- **自定义参数**: 允许用户动态调整终端字体大小（默认 14sp）、更换颜色主题、调整光标渲染样式（块状/下划线/竖线）。

## 非功能性需求
- **多线程并发**: 所有 Bootstrap 和 DEB 包安装全部在非 UI 异步 IO 协程中执行，进度实时推送到主界面。
- **国际化 (I18N)**: 所有文字（Toast、快捷键标识、初始化进度文案等）必须完整支持中英文一键切换。
