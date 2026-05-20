# App 模块需求

## 功能概述

新建 app 模块，使用 core 模块提供的 bootstrap 安装、deb 预安装和伪终端 GUI 能力，实现一个可交互的终端应用。启动时完成环境初始化，然后进入终端界面。

## 模块规格

- **模块类型**: Android Application
- **包名**: com.mermes（applicationId，与 bootstrap/deb 制作时的包名一致）
- **最低支持 Android 版本**: API 24 (Android 7.0)

## 功能点

### 1. 启动初始化流程

**UI 形式**: 启动页 + 进度条

**流程**:
1. App 启动进入 SplashActivity
2. 显示进度条和当前步骤文字
3. 按顺序执行：
   - Bootstrap 安装（显示 "正在安装 bootstrap..."）
   - Deb 预置包安装（显示 "正在安装 {包名} ({当前}/{总数})..."）
4. 安装完成后自动跳转到 MainActivity（终端界面）

**已安装检测**: 若 bootstrap 已安装（`$PREFIX/bin/bash` 存在）且所有预置 deb 包已安装（`isAllPresetInstalled()` 返回 true），跳过全部初始化直接进入终端。若 bootstrap 已安装但 deb 未全部安装，跳过 bootstrap 步骤继续安装 deb。

### 2. 终端界面

**架构**: MainActivity + TerminalFragment

**MainActivity**:
- 承载 TerminalFragment
- 处理返回键（终端中发送 ESC 或关闭当前会话）
- Toolbar/ActionBar 可选（全屏沉浸模式）

**TerminalFragment**:
- 包含 TerminalView
- 绑定 TerminalSession
- 处理生命周期（onDestroyView 时 detach session）

### 3. 错误处理

**Bootstrap 安装失败**:
- Toast 提示错误信息
- 自动回退到 Failsafe 模式（使用 /system/bin/sh）
- 终端界面显示 "Bootstrap 安装失败，已进入安全模式"

**Deb 安装失败**:
- Toast 提示失败的包名
- 继续安装剩余包（不阻塞启动）
- 记录失败日志

### 4. 终端设置页

**设置项**:
- 字体大小（sp）: 10~24，默认 14
- 颜色主题: 默认/浅色/自定义
- 光标样式: 块状/下划线/竖线

**入口**: 终端界面菜单或右上角设置图标

### 5. 终端快捷键栏与软键盘优化

**快捷键栏 (Extra Keys Row)**:
- 在终端界面底部提供一排水平滚动快捷键，包括：`ESC`, `CTRL`, `ALT`, `TAB`, `-`, `/`, `|`, `HOME`, `END`, `PGUP`, `PGDN`, `PASTE` (粘贴), 方向键 (↑, ↓, ←, →), `ENTER`, `SPACE`, 以及”键盘”切换键。
- `CTRL` 与 `ALT` 快捷键作为状态开关，点击后高亮处于激活状态，与物理键盘修饰键或软键盘输入组合使用，并在输入字符后自动复位。
- `PASTE` 点击可将系统剪贴板的内容直接粘贴到当前终端会话。
- 所有 UI 涉及字符及组件（例如“键盘”/“KEYBOARD”切换键）需要支持中英文国际化自动切换。

**软键盘体验优化**:
- 点击终端空白区域时，自动呼出软键盘并聚焦。
- 提供“键盘”快捷键，支持快捷显示/收起键盘。

### 6. 文本选取与复制输出

- **局部选取复制**：支持在终端屏幕上进行长按（Long Press）触发文本选择状态，开启后通过手指拖动（ACTION_MOVE）改变选择区域大小。
- **高亮展示**：处于选取状态的单元格使用半透明高亮颜色（例如半透明蓝色）覆盖渲染，提供极佳的交互视觉效果。
- **自动复制与通知**：手指抬起（ACTION_UP）时自动将选取区域的文本复制到系统剪贴板，并弹出国际化 Toast 提示（如：“选中文本已复制到剪贴板” / “Selected text copied to clipboard”），同时自动复位高亮选取状态。

### 6. 网络能力

- 支持网络访问权限，以便在终端中执行 curl、wget 或 ping 等网络操作。

## 非功能性需求

- 启动初始化在 IO 线程执行，不阻塞 UI
- 安装进度实时更新到 UI
- 支持 Android API 24+
