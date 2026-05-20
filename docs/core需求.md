# Core 模块需求

## 功能概述

新增一个 Android AAR 模块 `core`，使用 Kotlin + Gradle KTS 构建。该模块将 Termux 核心功能 SDK 化，包名为 `com.mermes`，提供 bootstrap 安装、伪终端创建、deb 包预安装等核心能力。

## 模块规格

- **模块类型**: Android AAR Library
- **语言**: Kotlin
- **构建系统**: Gradle KTS
- **包名**: `com.mermes`
- **最低支持 Android 版本**: API 24 (Android 7.0)

## 功能点

### 1. Bootstrap 安装流程 (installBootstrap)

**功能描述**:
实现类似 Termux 的 bootstrap 安装流程，将预置的 bootstrap zip 解压部署到应用私有目录，创建完整的 Linux 环境。

**实现要求**:
- 参考 `docs/ex/bootstrap流程.md` 中的 Termux bootstrap 流程
- bootstrap zip 文件已预先放置在 `download/mermes_bootstrap/` 目录，包含四个架构:
  - `bootstrap-aarch64.zip` (ARM64)
  - `bootstrap-arm.zip` (ARM32)
  - `bootstrap-i686.zip` (x86)
  - `bootstrap-x86_64.zip` (x86_64)
- 通过 JNI 将 zip 嵌入 `.so` 的 `.rodata` 段（使用 `.incbin` 汇编指令）
- 运行时通过 JNI 方法获取 zip 字节数组

**核心流程**:
1. **前置检查**: 检查 `$PREFIX` 目录是否存在且有效，若已存在则跳过安装
2. **环境清理**: 删除残留的 `usr-staging` 临时目录和损坏的 `usr` 目录
3. **加载 Native 库**: `System.loadLibrary("mermes-bootstrap")`
4. **获取 ZIP 字节**: 通过 JNI `getZip()` 方法获取嵌入的 zip 字节数组
5. **解压 ZIP**: 使用 `ZipInputStream` 逐项解压到 `usr-staging` 目录
6. **符号链接处理**: 解析 `SYMLINKS.txt`，格式为 `oldPath←linkPath`
7. **权限设置**: 对 `bin/`、`libexec/`、`lib/apt/` 等目录下的文件设置 `0700` 权限
8. **创建符号链接**: 使用 `Os.symlink()` 批量创建
9. **原子替换**: 将 `usr-staging` 重命名为 `usr`，确保原子性
10. **写入环境变量**: 生成 `PREFIX`、`HOME`、`PATH`、`TMPDIR` 等环境变量配置文件

**目录结构**:
```
/data/data/com.mermes/files/
├── usr/                    # PREFIX 目录 (bootstrap 解压目标)
│   ├── bin/
│   ├── lib/
│   ├── etc/
│   └── tmp/
└── home/                   # HOME 目录
```

### 2. 伪终端与 Bash 工具方法

**功能描述**:
实现伪终端(PTY)创建和管理，提供启动 Bash 等 Shell 进程的能力，以及相关的工具方法。

**实现要求**:
- 参考 `docs/ex/bash流程.md` 中的 Termux bash 启动流程
- 通过 JNI 实现 PTY 创建和进程管理
- 提供 Kotlin 层的会话管理 API

**核心功能**:

#### 2.1 PTY 创建 (JNI 层)
- 打开 `/dev/ptmx` 获取 Master Fd
- 调用 `grantpt()`、`unlockpt()`、`ptsname_r()` 获取 Slave 设备名
- 配置 termios: 启用 `IUTF8`，禁用 `IXON | IXOFF`
- 返回 Master Fd 和 Slave 设备名

#### 2.2 子进程创建 (JNI 层)
- `fork()` 创建子进程
- 子进程:
  - `setsid()` 创建新会话
  - 打开 Slave Fd
  - `dup2()` 重定向 stdin/stdout/stderr 到 Slave PTY
  - 关闭多余 fd (遍历 `/proc/self/fd`)
  - `clearenv()` + `putenv()` 设置干净环境变量
  - `chdir()` 切换工作目录
  - `execvp()` 执行目标程序
- 父进程: 返回 Master Fd 和子进程 PID

#### 2.3 Kotlin 层 API
- `createSession(command, cwd, env)`: 创建新的终端会话
- `writeToSession(sessionId, data)`: 向会话写入数据
- `readFromSession(sessionId)`: 从会话读取输出
- `waitForSession(sessionId)`: 等待会话结束
- `closeSession(sessionId)`: 关闭会话

#### 2.4 环境变量组装
- `HOME`: `/data/data/com.mermes/files/home`
- `PREFIX`: `/data/data/com.mermes/files/usr`
- `PATH`: `/data/data/com.mermes/files/usr/bin`
- `TMPDIR`: `/data/data/com.mermes/files/usr/tmp`
- `TERM`: `xterm-256color`

#### 2.5 Shebang 脚本处理
- ELF 二进制 (`0x7F ELF`): 直接执行
- Shebang 脚本 (`#!`): 解析解释器路径，重定向到 `$PREFIX/bin/`
- 纯文本脚本: 使用 `$PREFIX/bin/sh` 包裹执行

#### 2.6 Failsafe 安全模式
- 使用 `/system/bin/sh` 作为解释器
- 不加载 Termux 环境变量
- 用于诊断和修复损坏的环境

### 3. 预安装 Deb 包功能

**功能描述**:
支持将预先下载好的 deb 包安装到 bootstrap 环境中，自动处理依赖关系，通过 Android assets 加载离线 deb 包。

**实现要求**:
- deb 包源文件在 `download/mermes_deb/` 目录，按架构分类:
  - `arm64/` (aarch64)
  - `arm32/` (arm)
  - `x64/` (x86_64)
  - `x86/` (i686)
- 构建时通过 Gradle task `copyDebFiles` 将 deb 文件复制到 `src/main/assets/mermes_deb/{arch}/`
- 包名已改为 `com.mermes` 兼容格式
- 支持依赖树分析，从叶子节点开始安装
- 从 Android assets 目录加载 deb 包（无需 JNI/SO 嵌入）
- 自动检测已安装的包，支持增量安装（跳过已安装的包）
- 提供 `isAllPresetInstalled()` 方法判断是否所有预置包已安装

**预置 deb 包列表 (以 arm64 为例)**:
- `ca-certificates` - SSL 证书
- `gdbm` - 数据库
- `libandroid-posix-semaphore` - POSIX 信号量
- `libandroid-support` - Android 支持库
- `libbz2` - bzip2 压缩库
- `libcrypt` - 加密库
- `libexpat` - XML 解析库
- `libffi` - 外部函数接口库
- `liblzma` - LZMA 压缩库
- `libsqlite` - SQLite 数据库
- `libtalloc` - 内存分配库
- `ncurses` / `ncurses-ui-libs` - 终端 UI 库
- `openssl` - SSL/TLS 库
- `proot` / `proot-distro` - 用户空间 root 工具
- `python` - Python 解释器
- `readline` - 命令行编辑库
- `zlib` - 压缩库

**核心流程**:

#### 3.1 依赖树解析
- 解析每个 deb 包的 `control` 文件获取依赖信息
- 构建依赖图 (DAG)
- 使用拓扑排序算法，从叶子节点（无依赖的包）开始安装

#### 3.2 Deb 包解压安装
- 解析 deb 文件结构 (ar 格式):
  - `control.tar.xz`: 包含包元数据和控制脚本
  - `data.tar.xz`: 包含实际文件
- 解压 `data.tar.xz` 到 `$PREFIX` 目录
- 执行 `preinst`、`postinst` 等安装脚本

#### 3.3 从 Assets 加载
- 构建时 Gradle task `copyDebFiles` 将 `download/mermes_deb/{arch}/*.deb` 复制到 `src/main/assets/mermes_deb/{arch}/`
- 运行时通过 `context.assets.list()` 发当前架构目录下的所有 deb 文件
- 通过 `context.assets.open()` 读取 deb 文件字节数组
- 架构映射: aarch64→arm64, arm→arm32, i686→x86, x86_64→x64

#### 3.4 安装状态管理
- 记录已安装的包和版本到 `$PREFIX/etc/mermes/installed_packages.txt`（格式: `name|version`）
- 支持增量安装（跳过已安装的包）
- 提供 `isAllPresetInstalled()` 方法，判断所有预置包是否已安装
- App 模块在启动时自动检测: 若 bootstrap 已安装且所有 preset deb 已安装，直接跳过初始化
- 每个 deb 包安装时通过 logcat 打印进度和结果（TAG: `DebInstaller`），格式: `[当前/总数] Installed/Failed 包名 版本 (文件数)`

### 4. 伪终端 GUI 交互界面

**功能描述**:
在 core 模块中实现伪终端的 Android View 组件，参考 Termux 的 TerminalView/TerminalEmulator/TerminalRenderer 架构，提供可嵌入的终端交互界面。

**参考文档**: `docs/ex/伪终端界面实现.md`

**实现要求**:
- 遵循 MVC 架构：TerminalEmulator（模型）、TerminalView（视图）、TerminalRenderer（渲染）
- 支持 ANSI 转义序列解析（颜色、光标移动、清屏等）
- 支持中文等双宽字符（使用 WcWidth 计算字符宽度）
- 支持软键盘输入和物理键盘输入，支持发送控制键（Ctrl、Esc、上下左右等）
- 支持显示（召唤）与隐藏（关闭）软键盘
- 键盘变化时，UI 视图必须自适应变化（触发尺寸重算与底层 PTY 行列自动 resize）
- 支持文本选择和复制粘贴
- 光标闪烁动画，界面不可见时停止以节省电量

**核心组件**:

#### 4.1 TerminalEmulator（模型层）
- 维护终端屏幕缓冲区（TerminalBuffer / TerminalRow）
- 解析 ANSI 转义序列，更新字符内容和样式
- 管理光标位置、滚动区域
- 字符样式：前景色、背景色、粗体、下划线、反色

#### 4.2 TerminalView（视图层）
- 继承 Android View
- 处理 onSizeChanged 计算行列数，并在键盘弹出/收起等尺寸变化时正确触发 resize，调整底层 PTY 窗口大小
- 委托 TerminalRenderer 绘制
- 捕获键盘事件（onKeyDown/onKeyUp）和触摸事件，支持 Ctrl 等修饰键的状态维护
- 提供显示和隐藏软键盘的 API 方法
- 输入法连接（InputConnection）支持软键盘
- 文本选择与坐标转换（像素 ↔ 行列）

#### 4.3 TerminalRenderer（渲染引擎）
- 计算字体度量（fontWidth、fontLineSpacing）
- 优化渲染：合并相同样式连续字符为 Text Run，批量 canvas.drawTextRun()
- 绘制光标和选中文本高亮
- 处理中英文字体宽度不匹配的 Scale 修正

#### 4.4 滚动条
- 终端右侧显示自动隐藏的滚动条指示器
- 滚动时显示，停止滚动 1.5 秒后渐隐消失
- 滚动条高度按可见内容比例计算，位置反映当前视口在历史中的位置

#### 4.5 滚动与缓存
- TerminalBuffer 维护滚动回滚缓冲区（scrollback），默认保留 1000 行历史
- 超出缓冲区的旧行自动丢弃（环形缓冲区淘汰）
- 支持触摸滑动手势上下滚动查看历史输出
- 滚动回顶部/底部时自动跟随新输出

#### 4.5 与 TerminalSession 集成
- TerminalView 绑定 TerminalSession
- 接收输出 → TerminalEmulator 解析 → TerminalView 重绘
- 用户输入 → 转义序列 → TerminalManager.writeToSession()

## 非功能性需求

### 性能要求
- Bootstrap 安装应在 30 秒内完成（中端设备）
- Deb 包安装应支持并行解压（不超过 4 线程）
- PTY I/O 延迟应低于 10ms

### 兼容性要求
- 支持 Android API 24+ (Android 7.0+)
- 支持 ARM64、ARM32、x86_64、x86 四种架构
- 兼容 Termux 的目录结构和环境变量约定

### 安全要求
- 所有解压的可执行文件必须设置正确的权限
- 不泄露应用私有目录路径
- JNI 层做好异常处理，避免 native crash

### 存储要求
- Bootstrap 解压后约 100-200MB
- Deb 包安装后额外占用空间视具体包而定
- 需要预留足够的存储空间检查
