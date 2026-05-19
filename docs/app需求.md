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

**已安装检测**: 若 `$PREFIX/bin/bash` 已存在且可执行，跳过安装直接进入终端。

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

## 非功能性需求

- 启动初始化在 IO 线程执行，不阻塞 UI
- 安装进度实时更新到 UI
- 支持 Android API 24+
