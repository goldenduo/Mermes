# mermes 纯原生 Android 客户端需求与设计规格书

## 1. 功能概述
`mermes` 模块是 Mermes 项目的官方 **100% 纯原生 Android GUI 客户端应用**。该模块定位为 **Android 平台原生 Hermes 智能看板与主控制台**，完全采用 Android 原生技术栈（Kotlin + Jetpack Compose / Material Design 3 原生组件体系）重构并实现了与 PC 桌面端 [HermesDesktop](https://github.com/fathah/hermes-desktop) 对标的核心管控与智能交互功能。

App 在启动时通过原生进程和服务管理进行环境初始化，依赖并调用底层的 `core` 模块（集成 Termux 本地环境、高效 SSH 隧道套接字与后台 Python 守护进程）与 `common` 模块（原生崩溃接管与脱敏日志管道），提供了一个集大模型对话、长期记忆编辑、灵魂设定、任务看板、平台网关管理、本地/远程 SSH 连接切换于一体的高流畅度、低延迟的原生 GUI 交互客户端。除协同办公等特定挂载页使用高性能原生 `WebView` 外，其余所有交互界面均使用 Android 原生组件渲染，确保最极致的性能与电池寿命。

---

## 2. 模块规格
- **模块类型**: 纯原生 Android 应用程序 (Pure Native Android Application, 可打包为独立 APK)
- **物理路径 / 承载模块**: `mermes/` (应用入口主模块)
- **应用包名 (applicationId)**: `com.mermes`
- **命名空间 (namespace)**: `com.mermes.app`
- **最低支持 SDK 版本 (minSdk)**: API 24 (Android 7.0)
- **目标 SDK 版本 (targetSdk)**: API 35 (Android 15)
- **核心开发语言**: Kotlin 2.0+
- **异步与线程管控**: Kotlin Coroutines (协程) + Kotlin Flow (数据流)
- **UI 渲染引擎**: Jetpack Compose 与 Material Design 3 组件体系（100% 纯原生绘制）
- **架构模式**: MVVM (Model-View-ViewModel) + Repository Pattern
- **导航框架**: Navigation Compose
- **状态管理**: StateFlow + collectAsState
- **依赖注入**: 手动注入（后续可迁移至 Hilt）
- **网络层**: OkHttp + Retrofit
- **本地存储**: DataStore Preferences
- **图片加载**: Coil Compose

---

## 3. 移动端原生交互与界面设计规格

针对 Android 平台的系统特性（触摸手势、软键盘避让、原生文件选择器、生命周期管理），我们为 100% 纯原生 Android 客户端设计了以下 15 个功能模块的交互与布局规格：

```
+---------------------------------------------------------------+
|                        移动端 (Mermes App)                         |
| +---------------------------------------------------------------+ |
| | [AppBar]        Mermes 智能核心控制台 (Bilingual Toggle)         | |
| +---------------------------------------------------------------+ |
| | +-------------------+ +-------------------+                   | |
| | |  💬 智能对话      | |  📋 任务看板      |                   | |
| | |  - Flowing Bubbles| |  - ViewPager2 Tab |                   | |
| | |  - Multi-media    | |  - Drag & Assign  |                   | |
| | +-------------------+ +-------------------+                   | |
| | +-------------------+ +-------------------+                   | |
| | |  🧠 长期记忆      | |  🌐 平台网关      |                   | |
| | |  - Swipe Card     | |  - Accordion Form |                   | |
| | |  - BottomSheet    | |  - Particle Effect|                   | |
| | +-------------------+ +-------------------+                   | |
| |                                                               | |
| |                 [Bottom Navigation Bar]                       | |
| |          [Chat]   [Kanban]   [Memory]   [Gateway]             | |
| +---------------------------------------------------------------+ |
+---------------------------------------------------------------+
```

### 3.1 启动屏、欢迎页与三模式连接 (Splash & Welcome Page)
* **界面定位与原生布局**：
  - 应用启动的入口与初次环境嗅探验证界面。展示沉浸式高档极光暗色系 Splash 面板，若环境未就绪平滑渐入 Wizard 风格的欢迎与三模式连接引导页。
* **原生交互与系统集成**：
  - **三连接模式选择**：提供“本地 Termux 模式”（一键触发本地环境解包并拉起 Termux 核心容器进程）、“远程 HTTP 模式”（输入 Server URL 与可选 API Key，触发设备连接探测与 Spinner 动画）以及“远程 SSH 隧道模式”。
  - **SSH 凭证管理器与加密保护**：支持多套 SSH 凭证卡片，内置 SSH 私钥文件安全导入功能（调用 Android 原生 `DocumentProvider` 允许安全加载外部 `.pem` 或 `.key` 文件）。为了保护用户敏感数据，系统提供**用户名与密码/私钥密码的加密存储**，写入 DataStore 时自动采用 AES-128 对称加密。
  - **SSH 隧道配置与自动建立**：每个 SSH 配置均支持可选的”端口转发隧道”参数（本地映射端口与远程映射端口）。连接成功后，底层的 `SshCommandExecutor` 自动静默建立 SSH 本地端口转发隧道（Local Port Forwarding）以实现安全数据打通，并在 Executor 关闭时自动释放连接与端口映射。
  - **测试连接失败原因提示**：点击”测试连接”按钮后，如果连接失败，系统必须在 UI 上显示具体的失败原因，而非仅显示”连接失败”。失败原因包括但不限于：
    - 认证失败（密码错误、密钥无效、用户名错误）
    - 网络不可达（主机地址错误、端口未开放、防火墙拦截）
    - 连接超时（网络延迟过高、服务器无响应）
    - 密钥解析失败（密钥格式错误、口令不匹配）
    - 主机密钥验证失败（首次连接或密钥变更）

### 3.2 依赖解包与大模型服务商引导配置 (Install & Setup Page)
* **界面定位与原生布局**：
  - 系统环境解压安装与 Provider 初始配置引导。采用纯原生 Material 3 组件绘制磨砂玻璃质感的线性与圆环双进度条，下方辅以半折叠式的高亮 Shell 安装日志抽屉；Setup 页面采用高档极光暗色系的卡片网格。
* **原生交互与系统集成**：
  - **后台解包服务 (Foreground Service & WorkManager)**：由于 Android 系统的后台冻结策略，移动端解包采用原生 **前台服务 (Foreground Service)** 运行，并在系统状态栏绑定常驻的实时进度通知；如果应用被意外强杀，利用 **WorkManager** 实现断点续传与自愈解包，极大增强了系统初始化的强健性。
  - **本地局域网嗅探**：在配置本地 Local Presets（如 Ollama, Llama.cpp 等）时，支持一键在局域网内嗅探可用端口（如 `11434` / `8080`），探测通过后一键填充，免去手动输入 IP 的麻烦。

### 3.3 智能对话主控台 (Chat Terminal Page)
* **界面定位与原生布局**：
  - 交互对话核心主区。沉浸式全屏聊天主控制台。顶部 Toolbar 常驻“网络隧道心跳呼吸灯”和“中英文 Bilingual 即时一键热切换按钮”。
* **原生交互与系统集成**：
  - **工具流式微进度组件 (Micro-progress Tool Log)**：大模型在调用 Tool 时，聊天气泡上方会显示动态步进加载器（如 `🔍 正在检索互联网...` -> `💻 正在运行沙箱代码...`），支持点击一键拉起半屏/折叠日志终端查看详细 stdout/stderr 运行日志。
  - **多合一附件轮盘**：右下角提供 FAB “+” 按钮，点击展开 Glassmorphism 浮动快捷轮盘，支持调用 Android Camera 拍照、选取多张系统图片（Base64 转码）或利用系统 DocumentProvider 直接导入本地文本文件（封装入 Markdown 的 `<file>` 块进行上下文注入）。
  - **成本指标浮窗**：顶部可滑动拉下 Token 统计浮板，直观展示实时美元消耗。

### 3.4 历史会话管理器 (Sessions Page)
* **界面定位与原生布局**：
  - 会话历史搜索与切换。在底部导航“历史” Tab 展现。顶部包含常驻的毛玻璃模糊搜索框，以 RecyclerView 载入历史会话卡片流。
* **原生交互与系统集成**：
  - **侧滑手势删除 (Swipe to delete)**：支持手势左滑卡片，滑出带红色废纸篓图标的底色，轻触即可完成历史会话的异步安全抹除。
  - **回溯加载**：卡片显示会话首句大意预览、所用模型标志小图标与最后更新时间戳。点击卡片平滑切换到 Chat 界面并无卡顿恢复该历史会话。

### 3.5 长期记忆与画像管理 (Memory & Profile Page)
* **界面定位与原生布局**：
  - Agent 知识库查看与用户画像个性化编辑。分为“长期记忆”与“用户画像”左右滑动双 Tab 原生面板。
* **原生交互与系统集成**：
  - **条目化卡片编辑**：长期记忆以卡片流（RecyclerView）呈现，右滑立即删除，轻触卡片弹出优雅的 BottomSheet 局部编辑器，编辑完自动在后台协程中重组 `§` 分割段，安全持久化。
  - **AI 提炼画像**：用户画像支持富文本输入，并在软键盘上方挂载“AI 自动提炼与润色”原生悬浮控制条，一键对画像进行结构化整理和更新。

### 3.6 任务看板管理器 (Kanban Page)
* **界面定位与原生布局**：
  - 任务流追踪与自动化流水线分发。采用 **ViewPager2 + TabLayout (横向左右滑动分页)** 结构，一屏聚焦单泳道，下方带泳道索引小原点指示器。
* **原生交互与系统集成**：
  - **卡片交互**：任务卡片展示负责人头像缩略图和当前阻塞标记。长按任务卡片调起底部半屏操作轮盘，用户点击“指派给...”或“转移至...”，后台静默发送 shell 命令并无缝触发 RecyclerView 局部的增量刷新。
  - **Dispatch 波纹微动效**：顶部保留精美的 Floating Action Button (FAB) 用以一键触发流水线 `dispatch` 调度，并在运行阶段播放渐变闪烁波纹微动效。

### 3.7 平台网关与集成控制 (Gateway & Platform Page)
* **界面定位与原生布局**：
  - 第三方通讯渠道集成控制。采用折叠手风琴面板（Expandable Accordion List），每个三方通信平台独立成栏。
* **原生交互与系统集成**：
  - **呼吸状态监控**：面板头部集成了精细的红绿呼吸状态指示灯（Connected/Disconnected）和高颜值 Switch 开关。
  - **原生表单优化**：点击展开折叠项后，露出针对移动端软键盘输入进行优化的配置表单（如 API Key 快速粘贴、Channel ID 验证等）。
  - **粒子动效**：Switch 状态切换保存后，App 在前台显示“网关重新部署”炫酷粒子动效，后台静默包装并调用重启脚本，做到无缝感知。

### 3.8 插件技能商店 (Skills & Plugins Page)
* **界面定位与原生布局**：
  - 系统扩展技能库管理。采用类似 App Store 风格的精美瀑布流（Staggered Grid）卡片。
* **原生交互与系统集成**：
  - **详情半屏抽屉**：扫描本机的 skills 目录提取 `SKILL.md` 的 YAML 头，点击插件调起详情 BottomSheet（支持原生渲染 Markdown 说明文档）。
  - **进度环装卸**：点击“安装”时，卡片上的按钮平滑过渡为环形进度指示器（Progress Ring），并在后台协程中静默执行 `hermes skills install` 依赖安装，避免阻塞前台 UI 渲染。

### 3.9 灵魂设定编辑器 (Soul Page)
* **界面定位与原生布局**：
  - Agent 个性设定编辑器。提供单屏沉浸式的 Prompt 大文本编辑域，背景隐约透露优雅的深灰色极光光晕。
* **原生交互与系统集成**：
  - **防抖静默自动保存**：编写中采用 **500ms 无感防抖自动保存 (Debounced Auto-save)**：当检测到用户停止输入超过 500 毫秒，静默向后台执行写入，同时在顶部 Toolbar 右侧播放“已安全同步”的微缩文字渐隐动画。
  - **一键出厂重置**：提供“重置灵魂”动作按钮，点击弹出 BottomSheet 对话框进行二次防误触确认，确认后将灵魂恢复为系统出厂初始性格设定。

### 3.10 模型库管理界面 (Models Page)
* **界面定位与原生布局**：
  - 自定义可用大模型卡片库。采用两列网格（Grid）卡片流。点击右下角悬浮“+”按钮调起全屏式的“新增模型”配置页。
* **原生交互与系统集成**：
  - **智能模型发现 (Model Discovery)**：在 Model ID 输入框旁挂载一个刷新按钮。用户填入 Base URL 与秘钥后，点击该按钮，前台显示高档磨砂 Spinner，后台向提供商接口请求可用模型列表。探测成功后，直接以可滑动的半屏 BottomSheet 建议列表形式让用户轻触选择，避免拼写错误。
  - 模型卡片支持模糊检索和侧滑删除（带安全气泡确认）。

### 3.11 提供商全局秘钥配置 (Providers Page)
* **界面定位与原生布局**：
  - 商业 LLM 提供商全局秘钥配置。垂直滚动的卡片列表，背景以渐变暗色凸显。
* **原生交互与系统集成**：
  - **输入优化**：提供商卡片（如 DeepSeek, OpenRouter 等）包含专属高分辨率 Brand LOGO 缩略图。输入框具有一键粘贴和“显示/隐藏 API Key”的小眼睛图标 Toggle。
  - **平滑同步**：保存配置后，会在底部显示 Toast 提示，并平滑动画同步到智能对话与模型管理库中。

### 3.12 办公协同空间 (Office Page - Claw3D)
* **界面定位与原生布局**：
  - Claw3D 集成工作流挂载。使用 Android 高性能的系统底层 **WebView 容器组件** 代替 Electron 的 `<webview>`，挂载 Termux 本地或 SSH 远程映射出的 Claw3D 端口页面。
* **原生交互与系统集成**：
  - **原生快捷操作**：顶部导航包含“Web 重新加载 (Reload)”与“外部浏览器打开 (External Open)”快捷图标。
  - **状态日志抽屉**：顶部工具栏右侧提供“服务设置 (Settings)”拉帘，可拉下修改 Port 端口、WebSocket 地址以及一键查看 Claw3D 的后台实时运行日志（Logs）文本抽屉。状态控制一键 Toggle 触发异步进程 `claw3dStartAll` 或 `claw3dStopAll`。

### 3.13 自动化定时任务调度 (Schedules Page)
* **界面定位与原生布局**：
  - 定时 CronJob 管理。RecyclerView 纵向列出所有的自动化 CronJob 任务卡片。点击右上角“+”按钮导航至“新建定时任务”专属页面。
* **原生交互与系统集成**：
  - **可视化频率构建器 (Visual Schedule Builder)**：提供“单选药丸组 (Frequency Pills)”选择频率类别（分钟/小时/每天/每周/自定义），并在下方根据选择动态渲染对应的 Android 系统原生 TimePicker 选择时间或星期，让用户轻松定制调度命令。
  - **富媒体外部投递**：投递目的地下拉菜单对 16 个通知平台做了高分辨率图标配图（如 Telegram, Discord, Feishu, WeCom 等），支持在输入框旁一键关联本机的插件技能。
  - **故障排查诊断**：卡片直接显示下一次/上一次运行时间、投递目的地，若上一次任务执行出错，展示红色 Alert 叹号，点击后弹出 BottomSheet 查看详细错误堆栈（Error Log）。提供卡片快捷动作条：一键暂停（Pause）、一键立即跑一次（Zap）以及侧滑二次确认删除。

### 3.14 系统集成工具与 MCP 控制台 (Tools Page)
* **界面定位与原生布局**：
  - 大模型自带运行工具及 MCP 状态监管。采用两列网格控制面板（Grid Control Panel）。上部分显示 System Toolsets 网格卡片，下部分显示 MCP Servers 状态列表。
* **原生交互与系统集成**：
  - **3D悬浮高亮反馈**：卡片具备高度交互感，自带专属的 SVG 彩色图标（开启后显示 HSL 蓝色辉光背景，关闭后置灰暗色）。点击卡片直接 Toggle 该系统工具（如 `file`, `web`），并播放平滑的 3D 悬浮开关过渡动效。
  - **沙箱安全警示**：在启用 `file` (文件读写) 或 `terminal` (沙箱命令行) 时，App 会自动弹出一个精美的 Material Design 3 安全警示框，提示用户工具权限边界，保障用户数据安全。
  - MCP Servers 列表以紧凑形式展现其运行协议、地址 and 命令行参数。

### 3.15 全局设置页面 (Settings Page)
* **界面定位与原生布局**：
  - 全局系统设置。标准 Android Preference 风格的滚动的设置列表。
* **原生交互与系统集成**：
  - **中英文 UI 热热切换 (Bilingual Hot Swap)**：用户在 Settings 中选择“中文 (简体)”或“English”后，应用免重启立即无缝刷新所有 Activity/Fragment 文本。
  - **发布级别日志脱敏开关 (Safe Decoupled Log Toggle)**：开启后，底层 `common` 模块自动激活脱敏规则，隐去敏感目录和数据结构实现，确保对外导出的应用崩溃日志或控制台日志绝对安全合规。

---

## 4. 交互流程与异常容错规范

### 4.1 强韧性启动三阶段逻辑
1. **第一阶段：解包校验与自动解压 (MermesBootstrap)** — 启动时检查 `MermesBootstrap.isBootstrapInstalled(context)`，如果未安装则调用 `installBootstrap` 执行完整环境解包并配置环境变量。如果在该阶段失败，自动执行至多 3 次带有指数退避的自愈重试，最后通过 `MermesI18nTranslator` 对错误进行本地化翻译并抛给 UI。
2. **第二阶段：预制依赖检测与增量部署 (DebInstaller)** — 环境就绪后，利用 `DebInstaller.isAllPresetInstalled(context)` 判定预制 Debian 依赖包（如 Python3、SQLite 等）是否全部正确安装。如果已全部安装，**自动跳过安装以实现闪开且避免重复写入**。
3. **第三阶段：增量包拓扑安装与容错** — 若有部分或全部包未安装，利用 `DependencyResolver` 的 `resolvePackageOrder` 进行拓扑排序解决包循环依赖，并执行增量 `DebInstaller.installPresetPackages(...)` 进行静默轮询部署。个别包安装失败时精确捕获原因，不挂起整体初始化流转，保障终端最大程度可用。

### 4.2 网络与隧道保活自愈规范
- **原生后台保活 (Foreground Service & WakeLock)**：由于 Android 系统的后台省电与墓碑机制，SSH 隧道与网关探针的后台执行容易被系统杀掉。App 在建立连接后注册一个轻量级的前台服务，在必要时持有短暂的 **WakeLock (唤醒锁)**，确保在锁屏状态下网络请求与本地套接字端口映射依然维持活性。
- **心跳机制**：App 后台原生协程在 Foreground Service 中每 30 秒执行一次 `/health` 或状态探针，监控链路状态。
- **静默重连**：当 SSH 连接超时或 socket 断开时，前台顶部 Toolbar 展现淡黄色的“重连中...”呼吸字样，后台自动执行端口重开并保活连接，对用户体验做到零打扰。

---

## 5. 设计美学与多语言支持 (I18N & Aesthetics)

### 5.1 纯原生设计美学与系统融合 (Premium Native Visuals)
- **Material Design 3 (MD3) 规范**：完全遵循 Google 的 Material Design 3 设计规范。支持 Android 12+ 的 **Dynamic Color (动态色彩/壁纸取色)** 功能，使应用的极光暗色主题与用户的系统主题和谐统一。
- **沉浸式系统栏 (Edge-to-Edge)**：利用 Android 原生的 `WindowInsets` 适配，将界面元素延伸至状态栏与底部手势导航栏下方，带来最纯粹的原生沉浸感。
- **磨砂玻璃微动效 (Glassmorphism)**：在 Android 12+ (API 31+) 设备上，利用原生 `RenderEffect` 实现高斯模糊；在低版本设备上采用优雅降级方案（高档半透明描边与层级阴影的 CardView），并搭配 Android 系统的 `Transition` 动画体系，呈现极具高级感的流畅转场微动效。

### 5.2 全量双语国际化 (Bilingual Architecture)
- **Android 标准 I18N 资源包**：所有 UI 界面文本完全外包，严格配置在 Android 原生的 `res/values/strings.xml` 与 `res/values-en/strings.xml` 资源文件夹中。
- **免重启热切换 (Bilingual Hot Swap)**：通过 Android 的 `AppCompatDelegate.setApplicationLocales()` 或 Jetpack Compose 的 `CompositionLocal` 状态流，实现用户切换语言时前台 UI **免重启即时刷新渲染**，彻底摆脱传统的销毁重建 Activity 的粗暴卡顿感。
- **原生错误语义包装**：底层 Termux 或远程 SSH 抛出的英文/原生命令行报错（如 `Permission denied` / `Timeout`），通过底层的 `common` 翻译包装器（I18N Parser）进行多语言解析转换，以友好的原生 Dialog 或 Snackbar 形式向用户呈现。
