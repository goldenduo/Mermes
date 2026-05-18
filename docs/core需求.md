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
支持将预先下载好的 deb 包安装到 bootstrap 环境中，自动处理依赖关系，并支持从 SO readonly 中加载。

**实现要求**:
- deb 包已预先放置在 `download/mermes_deb/` 目录，按架构分类:
  - `arm64/` (aarch64)
  - `arm32/` (arm)
  - `x64/` (x86_64)
  - `x86/` (i686)
- 包名已改为 `com.mermes` 兼容格式
- 支持依赖树分析，从叶子节点开始安装
- 支持从 `.so` 的 readonly 段加载 deb 包

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

#### 3.3 从 SO Readonly 加载
- 将 deb 包通过 `.incbin` 嵌入到专用 `.so` 文件的 `.rodata` 段
- 提供 JNI 方法按包名获取 deb 字节数组
- 支持动态发现和加载所有预置的 deb 包

#### 3.4 安装状态管理
- 记录已安装的包和版本
- 支持增量安装（跳过已安装的包）
- 安装失败时的回滚机制

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
