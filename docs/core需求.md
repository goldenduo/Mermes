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

#### 4.1 快捷键布局 — 响应式满屏等宽对齐与精细布局

为了让快捷键栏在任何不同尺寸、分辨率的 Android 设备（从折叠屏到窄屏手机）上均能**百分之百完美占满屏幕宽度（与屏幕等宽），且两排按键从左到右像素级绝对对齐**，我们引入了基于 Android `LinearLayout` 响应式比例（`layout_weight`）的黄金权重设计：

**双行按键响应式比例（Weight）排布**（Row 1 与 Row 2 各 8 个按键，左右两端对齐占满屏幕）：
```
Row 1: [ESC:1.2] [/:1.2]    [-:1.2]   [|:1.0]    [UP:1.0]     [HOME:1.0]  [END:1.2]      [PGUP:1.4]
Row 2: [TAB:1.2] [CTRL:1.2] [ALT:1.2] [LEFT:1.0] [DOWN:1.0]   [RIGHT:1.0] [KEYBOARD:1.2] [PGDN:1.4]
```
*注：每一个按键的 `android:layout_width` 均设为 `0dp`，并根据上述列权重（Col 1 至 Col 8）分配对应的 `android:layout_weight`，间距（marginEnd）统一为 `3dp`。*

**对齐与尺寸优化设计规则**：
- **百分之百满屏等宽**：双行外层容器均设为 `match_parent` 占满屏幕。通过给 Row 1 和 Row 2 的第 $i$ 列按键配置**完全相同的 layout_weight**，无论屏幕多宽，两排对应的按键其物理起始 X 轴、宽度和终点均百分之百重合，两行与屏幕完全等宽，彻底杜绝参差不齐的观感。
- **ALT 极简等宽瘦身**：第二行的 `ALT` 权重缩减为 **`1.2`**，与同一行前导的 `TAB` (1.2) 和 `CTRL` (1.2) 权重完全相同、等宽并排。这使 `ALT` 彻底告别臃肿，形成高度统一的紧凑修饰键模块。
- **方向键十字星绝对对齐**：
  - Row 1 的 `UP` (↑) 位于第 5 列，权重为 **`1.0`**；Row 2 的 `DOWN` (↓) 同样位于第 5 列，权重为 **`1.0`**。两按键在垂直投影线上百分之百像素重合对齐。
  - 第二行搭配第 4 列的 `LEFT` (←:1.0) 与第 6 列的 `RIGHT` (→:1.0)。这四个方向控制键的权重全部相等（均为 1.0），在任何宽度下均为绝对等宽，并与 Row 1 的 `UP` 拼装出完美的十字方向星。
- **翻页键等宽对齐**：行尾第 8 列的 `PGUP` (上页) 和 `PGDN` (下页) 均分配最大的 **`1.4`** 权重，在确保中英文长文本完美直显的同时，上下完全投射等宽对齐。

#### 4.2 多 Session 支持 — 参照 Termux 的 Session 管理

`TerminalFragment` 必须支持：
- **创建新 Session**：通过 `TerminalManager.createSession(...)` 新建 PTY 会话并注册到内部列表
- **切换当前 Session**：切换 `TerminalView` 绑定的活跃 Session，并通知底层 PTY 适配
- **重命名 Session**：允许为每个会话设置自定义名称（如 "[1] bash", "[2] bash"）
- **关闭 Session**：关闭当前活跃会话，自动切换到邻近会话；如果是最后一个 session，则退出或显示提示
- **Session 列表 UI**：提供一个可显示/隐藏的会话列表抽屉或面板（可以是简单的顶部 Tab 条或侧滑列表）

**Session 数据模型扩展与仿真器绑定**：
`TerminalSession` 需要增加：
- `name: String`：会话自定义名称
- `title: String`：Shell 动态标题（由 OSC 8 等终端转义序列更新）
- `isRunning: Boolean`：会话是否仍在运行
- `emulator: TerminalEmulator?`：每一个 `TerminalSession` 实例自主独立持有一个专属的仿真器状态及屏幕缓冲区（`TerminalEmulator`）。这样在切换会话时，后台会话能继续接收并流式更新仿真器缓存，而切回前台时界面可无损展现历史状态。

**Session 行为与数据流通路规则**（参照 Termux）：
- **数据转发归宿**：`TerminalFragment` 在创建会话时注入的回调，其 `onTextChanged` 接收到底层 I/O 线程从 PTY 读入的数据后，必须先将其喂给对应的 `session.emulator`；若该 Session 处于当前前台激活状态，则调度 UI 线程使 `TerminalView` 执行重绘（`invalidate()`）。
- **仿真器复用与 Resize**：`TerminalView` 切换挂载 Session (`attachSession`) 时，应复用该 Session 内已保存的 `emulator`，若尚未创建则进行初始化挂载，并立即重算其行列宽，向底层 PTY 申请 `setwinsize` 自适应宿主空间。
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
