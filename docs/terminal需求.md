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

### 4. 辅助快捷栏 (Extra Keys Row) 与键盘优化
- **辅助快捷栏**: 在终端底部集成一排可水平滑动的辅助按键，包含 `ESC`, `CTRL`, `ALT`, `TAB`, `-`, `/`, `|`, `HOME`, `END`, `PGUP`, `PGDN`, `PASTE`, 方向键, `ENTER`, `SPACE` 以及键盘切换键。
- **修饰键状态锁定**: `CTRL` 和 `ALT` 点击后锁定高亮，配合后续软键盘输入并能在发射后自动复位。快捷栏发送按键时必须读取当前 `isCtrlToggled`/`isAltToggled` 状态构造 KeyEvent，不可硬编码修饰符。
- **长按重复触发**: 除 `PASTE` 和键盘切换键外，所有快捷键按钮支持长按连续触发。按下立即执行一次，持续按住 400ms 后以 80ms 间隔重复触发，松手停止。
- **粘贴功能**: `PASTE` 点击后直接读取系统剪贴板并粘贴到 PTY 输入流。
- **软键盘联动**: 点击终端空白区域自动拉起输入焦点与软键盘，支持快捷按键手动显示/收起键盘。

### 5. 局部文本选取与半透明高亮复制
- **文本选取**: 支持长按触控区域开启高亮选取，通过手指拖拽（ACTION_MOVE）改变选取范围。
- **视觉反馈**: 选中范围覆盖一层精致的半透明蓝色高亮。
- **无感复制**: 手指抬起（ACTION_UP）后，自动收集该区域的文本写入系统剪贴板，弹出国际化 Toast 提示（如：“选中文本已复制”/“Selected text copied”），并清除高亮选取。

### 6. 终端文本渲染与编码 (Terminal Rendering & Encoding)
- **UTF-8 多字节解码**: 终端模拟器必须内置 UTF-8 状态机，正确将 2-4 字节的 UTF-8 序列重组为完整 Unicode 码点后再渲染，不可将每个 0x80+ 字节作为独立字符处理。
- **宽字符支持 (WcWidth)**: 光标宽度计算必须覆盖 CJK 统一汉字扩展区（C-H）、Emoji 补充区（U+1F000+）、组合字符（U+0300-U+036F，宽度 0）和变体选择符（U+FE00-U+FE0F，宽度 0）。
- **字体回退**: Paint 使用 `Typeface.MONOSPACE` 时，Android minikin 引擎会自动回退到系统 CJK 字体，无需额外配置，但需确保 `drawTextRun` 正确处理混合脚本。
- **PTY UTF-8 环境**: 普通会话和 Failsafe 会话均须设置 `LANG=en_US.UTF-8`，普通会话额外设置 `LC_ALL=en_US.UTF-8` 以兼容 musl libc 环境。
- **输入编码**: 所有发送到 PTY 的文本必须显式使用 `Charsets.UTF_8` 编码，不可依赖平台默认编码。

### 7. 设置选项配置
- **自定义参数**: 允许用户动态调整终端字体大小（默认 14sp）、更换颜色主题、调整光标渲染样式（块状/下划线/竖线）。

## 非功能性需求
- **多线程并发**: 所有 Bootstrap 和 DEB 包安装全部在非 UI 异步 IO 协程中执行，进度实时推送到主界面。
- **国际化 (I18N)**: 所有文字（Toast、快捷键标识、初始化进度文案等）必须完整支持中英文一键切换。
