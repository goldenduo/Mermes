# mermes 模块实现状态

## 项目概述

本次重构将 mermes 从传统的 Activity/Fragment 架构迁移到现代的 Jetpack Compose + MVVM 架构，实现了需求文档中的 15 个功能模块。

## 技术栈

- **UI 框架**: Jetpack Compose + Material Design 3
- **架构模式**: MVVM + Repository Pattern
- **导航**: Navigation Compose
- **状态管理**: StateFlow + collectAsState
- **网络**: OkHttp + Retrofit
- **SSH**: JSch (com.github.mwiede:jsch:0.2.18)
- **本地存储**: DataStore Preferences + Room Database
- **异步**: Kotlin Coroutines + Flow

## 模块实现状态

### 1. 启动屏与欢迎页 (Splash & Welcome) ✅
- **SplashScreen**: 环境检测、Bootstrap 安装进度、连接配置检查
- **WelcomeScreen**: 三种连接模式选择（本地/HTTP/SSH）
- **SplashViewModel**: 状态管理、重试机制

### 2. 智能对话 (Chat Terminal) ✅
- **ChatScreen**: 消息气泡、输入框、附件功能
- **ChatViewModel**: 消息发送、会话管理
- **MessageBubble**: 用户/助手/系统消息样式
- **TypingIndicator**: 打字指示器

### 3. 会话管理 (Sessions) ✅
- **SessionsScreen**: 会话列表、搜索、删除
- **SessionCard**: 会话预览、模型标识

### 4. 长期记忆 (Memory & Profile) ✅
- **MemoryScreen**: 双标签页（记忆/画像）
- **MemoryCard**: 记忆条目展示、删除
- **ProfileEditor**: 画像编辑、AI 提炼按钮

### 5. 任务看板 (Kanban) ✅
- **KanbanScreen**: 泳道标签、任务列表
- **TaskCard**: 任务详情、阻塞状态、负责人

### 6. 平台网关 (Gateway) ✅
- **GatewayScreen**: 手风琴面板、状态监控
- **PlatformCard**: 连接状态、配置表单

### 7. 插件技能 (Skills & Plugins) ✅
- **SkillsScreen**: 瀑布流卡片、安装进度
- **SkillCard**: 技能信息、安装状态

### 8. 灵魂设定 (Soul) ✅
- **SoulScreen**: Prompt 编辑器、防抖自动保存
- **ResetDialog**: 出厂重置确认

### 9. 模型库 (Models) ✅
- **ModelsScreen**: 模型网格、搜索
- **ModelCard**: 模型信息、默认设置、删除

### 10. 提供商配置 (Providers) ✅
- **ProvidersScreen**: 提供商列表
- **ProviderCard**: API Key 管理、显示/隐藏

### 11. 定时任务 (Schedules) ✅
- **SchedulesScreen**: 任务列表、新建按钮
- **ScheduleCard**: Cron 表达式、执行状态、错误显示

### 12. 工具与 MCP (Tools) ✅
- **ToolsScreen**: 系统工具网格、MCP 列表
- **ToolCard**: 工具状态、高风险警告
- **McpServerCard**: 服务器状态、协议信息

### 13. 全局设置 (Settings) ✅
- **SettingsScreen**: 设置分组、语言切换
- **SettingsItem**: 导航项
- **SettingsToggleItem**: 开关项

### 14. 办公协同 (Office - Claw3D) ✅
- **OfficeScreen**: WebView 挂载、快捷操作
- **SettingsPanel**: 端口配置、日志查看

### 15. 连接管理 (Connection) ✅
- **ConnectionRepositoryImpl**: SSH/HTTP/本地连接管理
- **ConnectionViewModel**: 连接状态、配置管理
- **TerminalCommandExecutor**: 命令执行器接口

## 数据层实现

### 数据模型 (data/model/)
- `ConnectionModels.kt`: 连接模式、SSH 配置、连接状态、SSH 测试结果
- `InitModels.kt`: 初始化状态、Bootstrap 结果
- `SessionModels.kt`: 会话、消息、Token 使用
- `MemoryModels.kt`: 记忆条目、用户画像
- `KanbanModels.kt`: 看板、任务、状态
- `GatewayModels.kt`: 网关平台配置
- `SkillModels.kt`: 技能信息
- `ModelModels.kt`: 模型、提供商
- `ScheduleModels.kt`: 定时任务
- `ToolModels.kt`: 工具状态、MCP 服务器

### 仓库接口 (data/repository/)
- `ConnectionRepository.kt`: 连接管理接口
- `SessionRepository.kt`: 会话管理接口
- `MemoryRepository.kt`: 记忆管理接口
- `KanbanRepository.kt`: 看板管理接口
- `GatewayRepository.kt`: 网关管理接口
- `SkillRepository.kt`: 技能管理接口
- `ModelRepository.kt`: 模型管理接口
- `ScheduleRepository.kt`: 定时任务接口
- `ToolRepository.kt`: 工具管理接口

### 远程数据源 (data/remote/)
- `TerminalCommandExecutor.kt`: 终端命令执行器
  - `LocalCommandExecutor`: 本地 PTY 执行器
  - `SshCommandExecutor`: SSH 远程执行器
  - `HttpCommandExecutor`: HTTP API 执行器

## Common 模块增强

### 日志系统 (common/log/)
- `MermesLog.kt`: 统一日志系统，支持发布模式脱敏

### I18N 翻译器 (common/i18n/)
- `MermesI18nTranslator.kt`: 错误语义翻译，支持中英文

### 崩溃处理 (common/crash/)
- `MermesCrashHandler.kt`: 全局崩溃处理器

## 导航结构

```
Splash
  └─> Welcome (选择连接模式)
       └─> Home (底部导航)
            ├─> Chat (智能对话)
            ├─> Kanban (任务看板)
            ├─> Memory (长期记忆)
            └─> Gateway (平台网关)

其他页面 (从 Home 或 Settings 进入):
- Sessions (历史会话)
- Skills (插件技能)
- Soul (灵魂设定)
- Models (模型库)
- Providers (提供商)
- Schedules (定时任务)
- Tools (工具与 MCP)
- Settings (全局设置)
- Office (办公协同)
- SshConfigs (SSH 配置)
```

## 已完成事项

1. ✅ **编译验证**: 修复所有编译错误，项目可正常编译
2. ✅ **SSH 连接实现**: 集成 JSch 库，支持密码和密钥认证
3. ✅ **数据持久化**: 实现 Room 数据库存储会话和记忆
4. ✅ **UI 细节完善**: 添加通用组件、优化主题
5. ✅ **SSH 测试连接失败原因提示**: 实现详细的 SSH 测试连接失败原因分类与显示
   - 新增 `SshTestFailureReason` 枚举：认证失败、网络不可达、连接超时、密钥解析失败、主机密钥验证失败、端口转发失败、未知错误
   - 新增 `SshTestResult` 密封类：携带成功/失败状态及详细错误信息
   - 更新 `SshCommandExecutor.testConnection()` 方法：捕获并分类 JSch 异常
   - 更新 `ConnectionRepositoryImpl.testSshConnection()` 方法：返回 `SshTestResult` 类型
   - 更新 `ConnectionViewModel` 和 UI 层：显示具体失败原因而非通用错误信息
   - 添加中英文字符串资源：7 种失败原因的本地化描述

## 待完成事项

1. **流式响应**: 实现 SSE 或 WebSocket 流式消息
2. **附件功能**: 实现图片拍摄、文件选择
3. **通知服务**: 实现 SSH 隧道和 Bootstrap 的前台服务
4. **国际化**: 完善 strings.xml 资源文件
5. **测试**: 添加单元测试和 UI 测试

## 文件结构

```
mermes/
├── build.gradle.kts
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml
    ├── java/com/mermes/app/
    │   ├── MermesApplication.kt
    │   ├── MainActivity.kt
    │   ├── data/
    │   │   ├── model/          # 数据模型
    │   │   ├── repository/     # 仓库接口
    │   │   │   └── impl/       # 仓库实现
    │   │   ├── local/          # 本地数据源 (Room)
    │   │   │   ├── dao/        # 数据访问对象
    │   │   │   └── entity/     # 数据库实体
    │   │   └── remote/         # 远程数据源 (SSH/HTTP)
    │   └── ui/
    │       ├── navigation/     # 导航
    │       ├── theme/          # 主题
    │       └── screens/        # 屏幕
    │           ├── splash/
    │           ├── chat/
    │           ├── sessions/
    │           ├── memory/
    │           ├── kanban/
    │           ├── gateway/
    │           ├── skills/
    │           ├── soul/
    │           ├── models/
    │           ├── providers/
    │           ├── schedules/
    │           ├── tools/
    │           ├── settings/
    │           └── office/
    └── res/
        ├── values/             # 中文资源
        ├── values-en/          # 英文资源
        └── drawable/           # 图片资源
```
