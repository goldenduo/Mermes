# terminal 需求

## 功能概述
`terminal` 模块是 Mermes 项目的独立终端仿真 APK 应用。它作为一个可交互的 Android 终端客户端，利用底层 `core` 模块提供的 bootstrap 环境部署、`.deb` 包预安装以及 PTY 伪终端控制能力，向用户展现一个包含辅助快捷栏、软键盘无感联动以及屏幕手势高亮选取文本的专业终端仿真界面。

> **重要架构规则**：`terminal` 模块**不得自行实现 `TerminalFragment`**。
> 终端 UI Fragment（含快捷键栏、多 Session 管理）统一由 `core` 模块的 `com.mermes.core.terminal.TerminalFragment` 提供。
> `terminal` 模块的职责是宿主（Application、Activity），直接嵌入并使用来自 `core` 的 `TerminalFragment`。

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
     - **Bootstrap 安装**：展示"正在安装 bootstrap..." / "Installing bootstrap..."，提取 `.so` 汇编数据源进行极速解压。
     - **Deb 预置包安装**：展示"正在安装 {包名} ({当前}/{总数})..." / "Installing {包名} ({当前}/{总数})..."。
  2. 部署结束后，自动进入主终端界面（MainActivity）。
- **智能免装检测**: 若 Bootstrap 已部署（`$PREFIX/bin/bash` 存在）且所有预置 deb 均已安装成功，启动时跳过初始化页直达主界面。若有部分 deb 未装，仅增量安装缺少的 deb 包。

### 2. 终端界面 (MainActivity + core.TerminalFragment)

- **MainActivity**:
  - 承载来自 `core` 模块的 `com.mermes.core.terminal.TerminalFragment`
  - 接管 Android 系统物理返回键，委托给 `TerminalFragment.onBackPressed()` 处理
  - `TerminalFragment` 的全部终端渲染、多 Session 管理、快捷键布局，均由 `core` 模块统一实现，`terminal` 模块不得重复实现

- **不再在 terminal 模块中定义 TerminalFragment**: `terminal/src/main/java/.../TerminalFragment.kt` 已删除，引用改为 `com.mermes.core.terminal.TerminalFragment`

### 3. 未捕获崩溃与错误容灾处理
- **Bootstrap 部署容灾**: 若环境安装发生非预期错误，自动通过 `Toast` 向用户展现国际化提示，并安全回退到 Failsafe 模式（基于 `/system/bin/sh`），确保终端基本可用。
- **崩溃日志联动**: 应用启动时自动调用 `MermesCrashHandler` 对全局未捕获异常进行接管。

### 4. 快捷键与终端 UI
所有快捷键布局、Session 管理、修饰键行为均由 `core.TerminalFragment` 统一实现，遵照 Termux `DEFAULT_IVALUE_EXTRA_KEYS` 标准：
```
Row 1: ESC  /  -  HOME  UP  END  PGUP
Row 2: TAB  CTRL  ALT  LEFT  DOWN  RIGHT  PGDN
```
详见 [core 需求文档](core需求.md)。

### 5. 局部文本选取与半透明高亮复制
由 `core.TerminalView` 统一实现，`terminal` 模块无需额外处理。

### 6. 设置选项配置 (SettingsActivity)
- 允许用户动态调整终端字体大小（默认 14sp）、更换颜色主题、调整光标渲染样式（块状/下划线/竖线）。

## 非功能性需求
- **多线程并发**: 所有 Bootstrap 和 DEB 包安装全部在非 UI 异步 IO 协程中执行，进度实时推送到主界面。
- **国际化 (I18N)**: 所有文字（Toast、快捷键标识、初始化进度文案等）必须完整支持中英文一键切换。
