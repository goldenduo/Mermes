# Termux Bootstrap 安装与部署流程分析

本文档根据 `termux-app` 项目源码，详细梳理了 **Bootstrap** 从编译集成、C++ 静态嵌入，到运行时 Java 解压与权限分配的完整安装过程。

---

## 1. 什么是 Bootstrap？
在 Termux 中，**Bootstrap** 是指包含核心 Linux 工具链、基础命令、包管理器（`apt`/`pkg`）以及默认 Shell 解释器（`bin/bash`、`bin/sh`）的初始压缩包（通常为 `.zip` 格式）。它被直接编译并嵌入到应用的 `.so` 共享库中，以确保用户在首次安装并启动 Termux 时，无需依靠网络即可立刻拥有一个开箱即用的本地 Linux 终端环境。

---

## 2. 核心源码文件一览
本分析涉及以下核心源码文件：
- **构建脚本与配置**：
  - [app/build.gradle](file:///Users/duoduo/Documents/Code/termux-app/app/build.gradle) — 负责构建期自动下载对应架构的 Bootstrap ZIP 并进行哈希校验。
- **C++ 汇编与 JNI 桥梁**：
  - [app/src/main/cpp/Android.mk](file:///Users/duoduo/Documents/Code/termux-app/app/src/main/cpp/Android.mk) — 定义 native 库的编译规则。
  - [app/src/main/cpp/termux-bootstrap-zip.S](file:///Users/duoduo/Documents/Code/termux-app/app/src/main/cpp/termux-bootstrap-zip.S) — 使用 `.incbin` 指令直接在汇编层将 ZIP 嵌入数据段。
  - [app/src/main/cpp/termux-bootstrap.c](file:///Users/duoduo/Documents/Code/termux-app/app/src/main/cpp/termux-bootstrap.c) — 实现 JNI 方法，把汇编段中的二进制数据转为 Java 字节数组返回。
- **Java 安装器逻辑**：
  - [app/src/main/java/com/termux/app/TermuxInstaller.java](file:///Users/duoduo/Documents/Code/termux-app/app/src/main/java/com/termux/app/TermuxInstaller.java) — 运行时核心安装器，负责检查环境、解压、权限变更、软链接构建等全套初始化流程。

---

## 3. 完整安装流程梳理

整个 Bootstrap 的生命周期可以划分为 **“构建期集成”** 与 **“运行时解压安装”** 两大阶段：

```mermaid
graph TD
    subgraph 编译构建期 (Gradle & NDK)
        A[Gradle Task: downloadBootstraps] -->|从 GitHub 下载各架构 ZIP| B[src/main/cpp/bootstrap-arch.zip]
        B -->|汇编 .incbin 静态引入| C[termux-bootstrap-zip.S]
        C -->|JNI 桥接传递数据| D[libtermux-bootstrap.so]
    end
    
    subgraph 运行时启动 (Termux App)
        E[App 启动: setupBootstrapIfNeeded] -->|前置权限与主用户校验| F{检查 $PREFIX 是否有效且非空?}
        F -->|已存在且有效| G[直接进入 Terminal, 略过安装]
        F -->|不存在或为空| H[启动工作线程, 弹出 Installing... 弹窗]
        H -->|清理与重新创建| I[Staging & PREFIX 目录创建与授权]
        I -->|动态加载 native 库| J[System.loadLibrary'termux-bootstrap']
        J -->|调用 native getZip 获取字节流| K[ZipInputStream 逐项读取 Entry]
        K -->|若 Entry 为 SYMLINKS.txt| L[解析 oldPath←newPath 存入缓存]
        K -->|常规文件| M[解压至 usr-staging]
        M -->|若路径是 bin/ 等可执行目录| N[Os.chmod 赋予 0700 权限]
        L --> O[Os.symlink 创建符号链接]
        N --> O
        O -->|重命名确保原子性| P[usr-staging 命名为 usr]
        P -->|导出环境变量文件| Q[TermuxShellEnvironment.writeEnvironmentToFile]
        Q -->|回调主线程| R[完成, 准备启动 Bash]
    end
```

---

## 4. 详细流程分解

### 第一阶段：构建编译期的自动集成

#### 1. Gradle 自动拉取与哈希校验
在 [app/build.gradle](file:///Users/duoduo/Documents/Code/termux-app/app/build.gradle) 中，定义了构建任务：
- 根据 `packageVariant`（如默认的 `"apt-android-7"` 或 `"apt-android-5"`），指定特定的 Bootstrap 发布版本号（例如 `2026.02.12-r1%2Bapt.android-7`）。
- `downloadBootstraps` 任务负责根据目标 CPU 架构（`aarch64`、`arm`、`i686`、`x86_64`）自动拉取官方编译的 ZIP 文件：
  `https://github.com/termux/termux-packages/releases/download/bootstrap-${version}/bootstrap-${arch}.zip`
- 并在本地通过 **SHA-256 哈希比对** 验证完整性。如果匹配失败，则会删除重新下载或抛出编译异常，防范供应链污染。
- 任务执行完毕后，ZIP 文件会被保存在本地：`app/src/main/cpp/bootstrap-${arch}.zip`。

#### 2. C++ 汇编直接物理嵌入
在 [app/src/main/cpp/termux-bootstrap-zip.S](file:///Users/duoduo/Documents/Code/termux-app/app/src/main/cpp/termux-bootstrap-zip.S) 中，使用汇编指令将文件直接装载到编译后的库文件中：
```assembly
    .global blob
    .global blob_size
    .section .rodata
blob:
#if defined __i686__
    .incbin "bootstrap-i686.zip"
#elif defined __x86_64__
    .incbin "bootstrap-x86_64.zip"
...
```
- `.incbin` 会在编译时直接把架构对应的 `bootstrap-[arch].zip` 原始二进制数据嵌入到只读数据段 (`.rodata`) 中。
- 最终汇编与 JNI 代码被编译成共享库 `libtermux-bootstrap.so`。

#### 3. JNI 字节导出
在 [app/src/main/cpp/termux-bootstrap.c](file:///Users/duoduo/Documents/Code/termux-app/app/src/main/cpp/termux-bootstrap.c) 中，实现 Native JNI 函数：
```c
extern jbyte blob[];
extern int blob_size;

JNIEXPORT jbyteArray JNICALL Java_com_termux_app_TermuxInstaller_getZip(JNIEnv *env, jobject This)
{
    jbyteArray ret = (*env)->NewByteArray(env, blob_size);
    (*env)->SetByteArrayRegion(env, ret, 0, blob_size, blob);
    return ret;
}
```
通过该接口，Java 层的安装器可以直接免网络以内存字节数组的形式读取整个 Bootstrap ZIP。

---

### 第二阶段：运行时解压安装（Java 层核心逻辑）

当用户下载并首次启动 Termux App 时，[TermuxInstaller.java](file:///Users/duoduo/Documents/Code/termux-app/app/src/main/java/com/termux/app/TermuxInstaller.java) 的 `setupBootstrapIfNeeded` 会被调用。其核心解压逻辑如下：

#### 1. 前置条件安全检查
- **主用户检测**：为了确保路径映射正确，通过 `PackageUtils.isCurrentUserThePrimaryUser` 校验当前是否为主用户（设备所有者）。如果不是，将由于安卓路径访问权限限制弹出报错并安全退出。
- **目标目录空校验**：检测当前系统的 `$PREFIX` 目录（即 `/data/data/com.termux/files/usr`）是否存在且不为空。如果是已经安装妥当的目录，直接跳过并执行 `whenDone.run()`；否则说明是首次运行或环境已被破坏，启动重新解压安装。

#### 2. 环境清理与目录预创
- 在后台子线程中，先彻底删除任何残留的临时解压 staging 目录（`usr-staging`）和损坏的目标前缀目录（`usr`），避免脏文件残留。
- 使用 `TermuxFileUtils` 安全创建目标目录及 staging 目录，并修正文件夹权限。

#### 3. 动态加载 Native 二进制与 ZIP 遍历
- 动态调用 `System.loadLibrary("termux-bootstrap")`。
- 通过 Java 声明的本地方法 `getZip()` 获取 ZIP 二进制数组，并配合 `ZipInputStream` 开始提取每一个 ZIP 实体（Entry）：
```java
final byte[] zipBytes = loadZipBytes();
try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
    ZipEntry zipEntry;
    while ((zipEntry = zipInput.getNextEntry()) != null) {
        ...
```

#### 4. 符号软链接提取缓存 (SYMLINKS.txt)
为了跨平台在 Android 这种不完全支持标准 ZIP 软链接属性的运行时中建立符号链接：
- 压缩包中附带了一个 `SYMLINKS.txt`。
- 安装器一旦读取到 `SYMLINKS.txt`，就启动逐行解析。每一行的格式使用特殊符号分割：`oldPath←linkPath`。
- 例如：`bash←bin/sh` 代表 `/data/data/com.termux/files/usr/bin/sh` 应该是一个指向 `bash` 的符号链接。
- 解析出的路径关系被存储到 `symlinks` 缓存列表中：
```java
String[] parts = line.split("←");
String oldPath = parts[0];
String newPath = TERMUX_STAGING_PREFIX_DIR_PATH + "/" + parts[1];
symlinks.add(Pair.create(oldPath, newPath));
```

#### 5. 常规文件提取与执行权限 (chmod 0700) 设定
- 常规文件被依次写出到临时 staging 文件夹 `usr-staging` 中。
- **核心文件可执行权限分配**：
  在解压过程中，如果 Entry 路径属于 `bin/`、`libexec/`、或特许的 apt helper 可执行程序目录，安装器会使用 native 的 `Os.chmod` 工具函数强制将解压后的文件权限修改为 **`0700`** （所有者完全控制并具备执行权，防止安卓系统限制无法运行底层指令）：
```java
if (zipEntryName.startsWith("bin/") || zipEntryName.startsWith("libexec") ||
    zipEntryName.startsWith("lib/apt/apt-helper") || zipEntryName.startsWith("lib/apt/methods")) {
    Os.chmod(targetFile.getAbsolutePath(), 0700);
}
```

#### 6. 批量重建软链接
解压完所有常规文件后，开始遍历先前的 `symlinks` 缓存，使用 Android OS 层接口 `Os.symlink` 在对应 staging 位置创建对应的符号软链接：
```java
for (Pair<String, String> symlink : symlinks) {
    Os.symlink(symlink.first, symlink.second);
}
```

#### 7. 原子替换确保安全
解压和权限分配、软链接全部建立成功后，使用重命名操作直接将 staging 文件夹更名为正式目标：
```java
if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
    throw new RuntimeException("Moving termux prefix staging to prefix directory failed");
}
```
> [!NOTE]
> 使用 `renameTo` 进行目录重命名是一个原子操作。这确保了在发生意外断电或中途崩溃时，不会给系统留下一个半解压的、受损的 `$PREFIX` 目录，从而在下次打开时仍能重新触发干净的安装。

#### 8. 生成并写入默认环境变量文件
更名搬迁成功后，安装器将调用 `TermuxShellEnvironment.writeEnvironmentToFile(activity)`。
它会将诸如 `HOME`、`PREFIX`、`PATH`、`TMPDIR` 等基本环境属性写入持久的 `/data/data/com.termux/files/usr/etc/termux/termux.env` 文件。

#### 9. 配套存储软链接自动化创建 (setupStorageSymlinks)
除了部署基本系统外，为了让用户能够顺畅地与 Android 外部存储空间进行数据交互，Termux 还包含了一个配套的存储初始化流程：
- 当用户首次启动或是在终端中执行 `termux-setup-storage` 并授予外部存储读写权限时，系统会调用 `TermuxInstaller.setupStorageSymlinks(Context)` 方法。
- 该方法会自动在用户主目录 `~/storage`（即 `/data/data/com.termux/files/home/storage`）下创建指向系统标准共享目录的一整套符号软链接：
  - `shared` -> `/sdcard` (或 `/storage/emulated/0`)
  - `downloads` -> `/sdcard/Download`
  - `dcim` -> `/sdcard/DCIM`
  - `pictures` -> `/sdcard/Pictures`
  - `music` -> `/sdcard/Music`
  - `movies` -> `/sdcard/Movies`
  - `external-1` -> 指向外置 SD 卡的私有目录（若存在）
- 这些软链接极大地降低了用户在无 Root 权限的 Android 沙盒环境中访问手机照片、下载文件和音乐等外部资源的门槛。

至此，Bootstrap 的解压和部署彻底宣告成功。此时会撤销 "Installing..." 进度对话框，并触发主 Activity 的后续回调逻辑（包括启动默认的 Shell 进程）。
