# Mermes 模块需求

## 功能概述

`mermes` 模块是 Mermes 项目的主入口 App 应用程序模块。

根据全新设计要求，**精简所有原本用于大模型对话、长期记忆、任务看板、灵魂设定、多平台网关管理等多余复杂功能，将应用重心聚焦于极简、高效的本地服务加载与高保真 WebView 主控制台挂载。**

重新设计后的核心业务交互流程如下：
1. **环境自检与安装**：在应用启动时的 Splash 欢迎引导阶段，自动解包部署本地基础 Linux 环境（`bootstrap`）并安装全部预置的 Debian 依赖包（如 `python3`, `openssl` 等）。
2. **初始化自定义运行脚本**：解包安装完成后，将内置在 App `assets` 目录下的初始化运行脚本（如 `init_script.sh`）复制到本地环境（`HOME` 目录），并添加执行权限。
3. **调用核心接口执行脚本**：调用 `core` 模块提供的不通过终端、代码直调的命令执行接口（`ShellCommandExecutor`），在后台静默拉起执行该初始化脚本。该脚本会在本地运行一个启动在 **`20265` 端口** 的本地 Web 服务网站（如基于 `python3` 等开启的后台应用）。
4. **全屏 WebView 挂载与网页交互**：脚本运行成功启动本地 Web 服务后，App 平滑过渡并进入一个全屏的高性能原生 `WebView` 交互界面。该 `WebView` 加载本地网站 `http://127.0.0.1:20265`，并全面支持正常的网页操作功能（支持 JavaScript、Cookie、历史回退、全屏交互等）。

---

## 模块规格

- **模块类型**: Android 应用程序 (可打包为独立 APK)
- **应用包名**: `com.mermes` (applicationId)
- **命名空间**: `com.mermes.app`
- **最低支持 SDK**: API 24 (Android 7.0)
- **核心架构**: MVVM (轻量化) + Navigation Compose + 原生高性能 WebView

---

## 核心功能点

### 1. 强韧性启动三阶段初始化 (Splash & Setup Screen)
- **自适应免装检查**：启动时检测 bootstrap 和 debs 是否全部已安装。若已全部完成，直接跳过解包以秒开。
- **前台安装通知服务**：若未安装，进入 Setup 解包页。为了应对后台被系统杀掉，使用 Android 前台服务 (Foreground Service) 在状态栏展示实时安装进度条。
- **容灾机制**：任何步骤安装失败，通过中英文语义转换器输出友好提示，并提供一键重试按钮。

### 2. 初始化脚本部署与静默拉起 [NEW]
- **脚本复制部署**：从 App 编译包的 `assets/init_script.sh` 读取预置脚本，以流写入本地环境的 `$HOME/init_script.sh`。通过设置可执行权限（`chmod 700`）确保脚本能够被正常拉起。
- **后台代码级直调**：调用 `core` 模块的 `ShellCommandExecutor` 执行命令：
  ```bash
  bash /data/data/com.mermes/files/home/init_script.sh
  ```
  不使用 PTY 窗口进行交互，完全在后台默默执行。
- **服务自愈与保活**：如果 Web 服务进程发生意外挂起或崩溃，App 应维持进程检测并在需要时触发脚本重启，保障高可用。

### 3. 高性能 Web 主控制台界面 (WebView Control Screen) [NEW]
- **全屏原生挂载**：在脚本拉起完成后，界面渐入切换到全屏原生 `WebView`。
- **基础加载源**：加载 `http://127.0.0.1:20265`。
- **全要素网页能力支持**：
  - 开启 `JavaScriptEnabled = true`，支持现代前端框架（React、Vue 等）。
  - 支持 `DomStorageEnabled = true` 与 `DatabaseEnabled = true` 以启用本地 LocalStorage 存储。
  - 支持双指手势缩放、Cookie 保存。
  - 挂载 `WebChromeClient` 和 `WebViewClient`，接管网页重定向，在加载本地请求时提供最顺滑的本地渲染。
  - 支持硬件返回键，网页可以自由返回上一级（`webView.goBack()`）。
- **外部浏览器联动**：右上角提供快捷按钮，允许用户随时将当前网页一键复制或在系统自带外部浏览器中打开。

---

## 非功能性需求与美学
- **全量双语国际化 (Bilingual)**：启动初始化进度、WebView 错误重试等所有提示均提供中英文全语言翻译。
- **暗黑/明亮主题自适应**：支持跟随系统主题颜色做高保真极光暗色/亮色无卡顿无重启热切换。
