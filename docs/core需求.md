# Core 模块需求

## 功能概述

`core` 模块是 Mermes 项目的核心基础 AAR 库模块，包名为 `com.mermes.core`。该模块负责实现 Linux 环境解包部署、Debian 预置依赖包原子级增量安装、伪终端底层 JNI 通信与 UI 渲染组件。

本次更新引入三大核心改造需求：
1. **终端 Fragment 与 Session 支持移入 Core**：将原 `terminal` 模块下的 `TerminalFragment` 及其相关资源移入 `core` 模块，并**完全实现 Termux 式多 Session 管理**：可创建多个会话、可切换当前活跃会话、可重命名会话、可关闭单个会话。
2. **快捷键布局与 Termux 完全一致**：`TerminalFragment` 的底部辅助快捷键布局必须**严格参照** `download/termux-app` 中 `DEFAULT_IVALUE_EXTRA_KEYS` 的官方双行排列，键位排列和行为与 Termux 官方 App 完全一致。
3. **代码级直接命令执行器**：在 `core` 模块中暴露出一个不通过 PTY 终端 UI 界面、直接通过代码调用外部命令的接口，以 `Flow<String>` 实时流式输出，支持随时中断。

---

## 模块规格

- **模块类型**: Android AAR Library
- **语言**: Kotlin
- **构建系统**: Gradle KTS
- **命名空间**: `com.mermes.core`
- **最低支持 Android 版本**: API 24 (Android 7.0)

---

## 功能点

### 1. Bootstrap 安装流程 (installBootstrap)
- 保持原 bootstrap 解压部署规范。
- 运行时通过 JNI `getZip()` 获取嵌入的 zip 字节数组，解密解压到 `$PREFIX` 目录，重建符号链接与权限设置。

### 2. 预安装 Deb 包功能 (DebInstaller)
- 保持原 deb 预置依赖增量安装规范，使用单独的 `libmermes-deb.so` 数据源。
- 支持依赖树拓扑排序解析与安装，自动记录并增量跳过已安装包。

### 3. 代码级直接命令执行器 (ShellCommandExecutor) [已实现]

**功能描述**:
提供一个不需要创建 PTY 终端界面，直接由 Kotlin 代码配置和调用 Linux 命令行脚本的执行器。

**实现要求**:
- **直接执行支持**：允许传入任意字符串命令行（例如：`"ls -la"`, `"python3 -m http.server"`），在后台 ProcessBuilder 起子进程独立运行。
- **自适应环境变量**：自动注入通过 `ShellEnvironment.getEnvironment(context)` 组装的 Termux 环境参数。
- **流式实时输出**：利用 Kotlin Flow 以 Line-by-Line 形式实时发射 stdout 与 stderr 的混合输出流。
- **支持主动中断**：提供句柄 `CommandHandle`，允许调用者随时调用 `interrupt()` 强制终止子进程。

### 4. 伪终端 GUI 交互界面与多 Session 管理 (TerminalFragment) [重构]

**功能描述**:
将 `TerminalFragment` 完全移入 `core` 模块，彻底重构为支持 **Termux 式多 Session** 的完整终端界面。

#### 4.1 快捷键布局 — 严格遵照 Termux 官方 DEFAULT_IVALUE_EXTRA_KEYS

参照 Termux 官方 `TermuxPropertyConstants.java` 中定义的：
```
DEFAULT_IVALUE_EXTRA_KEYS = "[['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','PGUP'], ['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]"
```

**双行按键排列**（必须严格一致）：
```
Row 1: [ESC]  [/]  [-]  [HOME]  [UP]    [END]   [PGUP]
Row 2: [TAB]  [CTRL] [ALT] [LEFT]  [DOWN]  [RIGHT] [PGDN]
```

注意：
- `-` 键有 popup 键 `|`（Termux 原版实现为长按弹出 `|`，我们可以简化为两者都单独显示，`-` 和 `|` 各自一个按钮）
- 不再有 `ENTER`、`SPACE`、`KEYBOARD`、`PASTE` 单独行，这些移到顶部操作栏或通过其他方式实现
- 当前已有实现与此不符，需重新对齐

#### 4.2 多 Session 支持 — 参照 Termux 的 Session 管理

`TerminalFragment` 必须支持：
- **创建新 Session**：通过 `TerminalManager.createSession(...)` 新建 PTY 会话并注册到内部列表
- **切换当前 Session**：切换 `TerminalView` 绑定的活跃 Session，并通知底层 PTY 适配
- **重命名 Session**：允许为每个会话设置自定义名称（如 "[1] bash", "[2] bash"）
- **关闭 Session**：关闭当前活跃会话，自动切换到邻近会话；如果是最后一个 session，则退出或显示提示
- **Session 列表 UI**：提供一个可显示/隐藏的会话列表抽屉或面板（可以是简单的顶部 Tab 条或侧滑列表）

**Session 数据模型扩展**：
`TerminalSession` 需要增加：
- `name: String`：会话自定义名称
- `title: String`：Shell 动态标题（由 OSC 8 等终端转义序列更新）
- `isRunning: Boolean`：会话是否仍在运行

**Session 行为规则**（参照 Termux）：
- 会话关闭后（Shell 退出）显示 `[Process completed (exit X) - Press Enter to close]` 提示
- 支持 `Ctrl+C` 发送中断信号
- 支持通过会话标题（`mSessionName`）在 UI 中识别区分多个会话

#### 4.3 修饰键行为 (CTRL / ALT 三态锁定)
- 采用和 Termux 相同的三态（普通 → 激活 → 锁定）修饰状态：
  - **单击**：激活（下次按键后自动复位）
  - **双击**：锁定（持续激活，不自动复位）
  - **再次点击**：取消锁定/激活
- 视觉上用 ToggleButton 高亮区分激活状态

#### 4.4 按键重复触发
- 导航键（方向键、PGUP/PGDN、HOME/END、ESC、TAB）首次按下立即发送，长按 400ms 后以 80ms 间隔重复发送
- 文本键（`/`、`-`、`|`）不需要长按重复，单击发送

#### 4.5 PTY 窗口 Resize 自适应
- 软键盘召唤/关闭，Activity 尺寸改变时，TerminalView 自动重算行高列宽，通知底层 PTY 执行 `setwinsize`

---

## 非功能性需求

- 所有 UI 文案支持中英文双语（`values/strings.xml` + `values-en/strings.xml`）
- 所有 UI 组件遵循 Material Design 3 色彩规范，支持明暗主题自动切换
- `TerminalView` 背景色为黑色 `#000000`，快捷键栏背景为深色 `#1E1E1E`
- Session 最大数量建议限制为 8 个（参照 Termux 的软限制）
