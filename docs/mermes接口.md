# mermes 接口与脚本契约

本篇文档定义了 `mermes` 模块与本地/远程终端 Agent 的核心通信接口、注入 Python 脚本契约、命令行控制映射以及环境初始化错误处理的数据结构。

---

## 0. 连接模式与 SSH 配置数据结构

### 0.1 连接模式枚举 (`ConnectionMode.kt`)
```kotlin
package com.mermes.connection

enum class ConnectionMode {
    LOCAL,      // 本地 PTY 模式
    SSH         // 远程 SSH 模式
}
```

### 0.2 SSH 连接配置实体 (`SshConfig.kt`)
```kotlin
package com.mermes.connection

data class SshConfig(
    val id: String,                     // 唯一标识 (UUID)
    val name: String,                   // 配置名称 (用户自定义)
    val host: String,                   // 主机地址
    val port: Int = 22,                 // SSH 端口
    val username: String,               // 登录用户名
    val authType: AuthType,             // 认证方式
    val password: String? = null,       // 密码认证时的密码
    val privateKeyPath: String? = null, // 密钥认证时的私钥路径
    val passphrase: String? = null,     // 私钥密码 (如有)
    val tunnelLocalPort: Int? = null,   // 隧道本地映射端口 (如 8080)
    val tunnelRemotePort: Int? = null,  // 隧道远程目标端口 (如 8080)
    val isDefault: Boolean = false,     // 是否为默认连接
    val lastConnectedAt: Long = 0       // 最后连接时间戳
)

enum class AuthType {
    PASSWORD,   // 密码认证
    KEY         // 密钥认证
}
```

### 0.3 SSH 连接状态 (`SshConnectionState.kt`)
```kotlin
package com.mermes.connection

sealed class SshConnectionState {
    object Disconnected : SshConnectionState()
    object Connecting : SshConnectionState()
    data class Connected(val session: Any) : SshConnectionState()
    data class Error(val message: String, val exception: Throwable? = null) : SshConnectionState()
}
```

### 0.4 SSH 配置管理接口 (`SshConfigManager.kt`)
```kotlin
package com.mermes.connection

import android.content.Context

interface SshConfigManager {
    // 获取所有已保存的 SSH 配置
    suspend fun getAllConfigs(context: Context): List<SshConfig>

    // 根据 ID 获取配置
    suspend fun getConfigById(context: Context, id: String): SshConfig?

    // 保存配置 (新增或更新)
    suspend fun saveConfig(context: Context, config: SshConfig): Boolean

    // 删除配置
    suspend fun deleteConfig(context: Context, id: String): Boolean

    // 设置默认配置
    suspend fun setDefault(context: Context, id: String): Boolean

    // 测试 SSH 连接
    suspend fun testConnection(config: SshConfig): SshConnectionState
}
```

### 0.5 密码安全存储加解密契约 (`MermesCrypto.kt`)

为了保证 SSH 配置中敏感的密码 (`password`) 和私钥密码 (`passphrase`) 在本地持久化时不泄漏，底层 `common` 模块提供 `MermesCrypto` 静态加密辅助，在写入 preferencesDataStore 前执行加密，在读取时解密。

```kotlin
package com.mermes.common.security

object MermesCrypto {
    /**
     * 加密明文，若传入 null 或空则返回 null
     */
    fun encrypt(plainText: String?): String?

    /**
     * 解密密文，若传入 null 或空则返回 null
     */
    fun decrypt(cipherText: String?): String?
}
```

---

## 1. 启动初始化重试机制数据结构

在初始化阶段，系统暴露重试监听 API，以便在 UI 级实时展示进度与重试详细参数。

### 1.1 初始化进度与状态实体 (`InitStatus.kt`)
```kotlin
package com.mermes.init

sealed class InitStatus {
    object Idle : InitStatus()
    
    data class InstallingBootstrap(
        val progress: Int,
        val retryCount: Int,
        val maxRetries: Int,
        val lastError: String? = null
    ) : InitStatus()
    
    data class InstallingDebs(
        val currentPackage: String,
        val currentIndex: Int,
        val totalCount: Int,
        val retryCount: Int,
        val maxRetries: Int,
        val lastError: String? = null
    ) : InitStatus()
    
    object Success : InitStatus()
    
    data class Failed(
        val stage: Stage,
        val error: String
    ) : InitStatus() {
        enum class Stage { BOOTSTRAP, DEBS }
    }
}
```

---

## 2. SQLite 历史会话查询注入脚本契约

在 SSH 远程或本地模式下，我们需要在终端执行 Python 并向 `stdin` 注入以下 SQLite 查询脚本，抓取 stdout 的 JSON 结果。

### 2.1 会话列表分页抓取脚本 (Python)
* **注入命令**：`python3 -`
* **输入内容 (`stdin`)**：
```python
import sqlite3, json, os

def query_sessions(limit=20, offset=0):
    db_path = os.path.expanduser("~/.hermes/state.db")
    if not os.path.exists(db_path):
        return []
    
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    try:
        # 字段说明: 会话ID, 来源, 开始时间, 消息总数, 模型名称, 标题描述
        cursor.execute("""
            SELECT id, source, started_at, message_count, model, title 
            FROM sessions 
            ORDER BY started_at DESC 
            LIMIT ? OFFSET ?
        """, (limit, offset))
        rows = cursor.fetchall()
        sessions = []
        for r in rows:
            sessions.append({
                "id": r[0],
                "source": r[1],
                "started_at": r[2],
                "message_count": r[3],
                "model": r[4],
                "title": r[5]
            })
        return sessions
    except Exception as e:
        return {"error": str(e)}
    finally:
        conn.close()

print(json.dumps(query_sessions(limit={{LIMIT}}, offset={{OFFSET}})))
```

### 2.2 全文消息模糊检索脚本 (Python)
* **注入命令**：`python3 -`
* **输入内容 (`stdin`)**：
```python
import sqlite3, json, os

def search_messages(query, limit=50):
    db_path = os.path.expanduser("~/.hermes/state.db")
    if not os.path.exists(db_path):
        return []
        
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    try:
        cursor.execute("""
            SELECT DISTINCT s.id, s.title, s.started_at, s.message_count, m.content
            FROM sessions s 
            JOIN messages m ON m.session_id = s.id 
            WHERE m.content LIKE ? 
            ORDER BY s.started_at DESC 
            LIMIT ?
        """, (f"%{query}%", limit))
        rows = cursor.fetchall()
        results = []
        for r in rows:
            results.append({
                "session_id": r[0],
                "title": r[1],
                "started_at": r[2],
                "message_count": r[3],
                "snippet": r[4][:120] # 裁剪气泡片段
            })
        return results
    except Exception as e:
        return {"error": str(e)}
    finally:
        conn.close()

print(json.dumps(search_messages(query={{QUERY}}, limit={{LIMIT}})))
```

---

## 3. 看板 CLI 指令映射规范

`mermes` 的 Kanban 图形化界面应调用 core 中的会话流，静默发射并解析以下终端命令的标准 JSON 输出。

### 3.1 指令字典

| 功能 | 终端运行命令 | 返回 JSON 结构 |
| :--- | :--- | :--- |
| **拉取看板任务** | `hermes kanban list --json` | `{"kanbans": [{"slug": "s", "title": "t", "tasks": [...]}]}` |
| **创建任务** | `hermes kanban create-task --title "<title>" --description "<desc>"` | `{"success": true, "task_id": "T-123"}` |
| **指派任务** | `hermes kanban assign <id> <assignee>` | `{"success": true}` |
| **挂起阻碍** | `hermes kanban block <id> --reason "<reason>"` | `{"success": true}` |
| **归档/完成** | `hermes kanban complete <id> --result "<res>"` | `{"success": true}` |
| **唤醒自动化链** | `hermes kanban dispatch` | `{"status": "dispatched", "logs": [...]}` |

---

## 4. 插件与技能库扫描指令规范

### 4.1 本地已安装技能探查 (Python 注入)
由于 Android 文件 API 在 Termux 虚拟环境下可能会受限，采用 Python 递归检索技能并读取元数据是最稳定的跨平台机制。
* **注入命令**：`python3 -`
* **输入内容 (`stdin`)**：
```python
import os, json, re

def scan_skills():
    skills_dir = os.path.expanduser("~/.hermes/skills")
    if not os.path.exists(skills_dir):
        return []
        
    skills = []
    for folder in os.listdir(skills_dir):
        path = os.path.join(skills_dir, folder)
        if os.path.isdir(path):
            readme = os.path.join(path, "SKILL.md")
            desc = "No description provided."
            if os.path.exists(readme):
                try:
                    with open(readme, 'r', encoding='utf-8') as f:
                        content = f.read(2048)
                        # 正则匹配 markdown frontmatter 中的 description
                        match = re.search(r'description:\s*(.*)', content, re.IGNORECASE)
                        if match:
                            desc = match.group(1).strip().strip('"').strip("'")
                except:
                    pass
            skills.append({
                "name": folder,
                "description": desc,
                "path": path
            })
    return skills

print(json.dumps(scan_skills()))
```

---

## 5. 日志打印接入规范

在 `mermes` 模块的所有源文件中，**坚决禁止使用原生的 `android.util.Log`**。
所有的源文件必须在顶部引用我们的 `:common` 模块别名，以自动获得发布期的动态数据脱敏和异常栈隐蔽保密能力：

```kotlin
import com.mermes.common.log.MermesLog as Log

// 正常打印（发布期将自动忽略或脱敏敏感数据，隐蔽物理源码行数）
Log.i("MermesInit", "Starting bootstrap environment deployment")
Log.e("MermesInit", "Fatal error occurred during unzip", exception)
```

---

## 6. 错误语义多语言翻译契约 (I18nTranslator)

为支撑界面对标 3.15 Settings 与 5.2 节的免重启 Bilingual 交互，底层 `common` 模块提供 `MermesI18nTranslator` 错误包装器，对 SSH 连接、Termux 运行或 Python 脚本抛出的原生英文报错提供精准的语义解析并翻译成普通用户易懂的本地化语言。

### 6.1 翻译器契约接口 (`MermesI18nTranslator.kt`)
```kotlin
package com.mermes.common.i18n

import java.util.Locale

interface I18nTranslator {
    /**
     * 将底层命令或网络的原始错误文本翻译为指定语言的友好描述
     * @param rawError 底层抛出的异常描述或 Shell stderr 原始输出
     * @param locale 目标语言（目前支持 "zh" 和 "en"）
     * @return 翻译后的本地化错误提示
     */
    fun translate(rawError: String, locale: Locale): String
}
```

### 6.2 核心错误词条映射映射表
在 `MermesI18nTranslator` 的具体实现中，预置以下高频错误映射规则以支持双语秒级渲染：

| 原始英文错误特征 (`rawError` contains) | 中文翻译输出 (`locale = zh`) | 英文友好翻译输出 (`locale = en`) |
| :--- | :--- | :--- |
| `Permission denied` | 权限不足，请确认文件读写或执行权限。 | Permission denied. Please verify read/write or execution permissions. |
| `Connection refused` | 连接被拒绝，请确认目标服务或 SSH 端口已开启。 | Connection refused. Ensure target service or SSH port is active. |
| `No route to host` | 路由不可达，请检查设备网络或 VPN 连通状态。 | No route to host. Check device internet or VPN connectivity. |
| `Connection timed out` | 连接超时，可能网络丢包或远程服务器无响应。 | Connection timed out. Network is unstable or server is unresponsive. |
| `Address already in use` | 端口已被占用，请尝试在设置中更换服务映射端口。 | Address already in use. Please select a different port in Settings. |
| `No space left on device` | 设备存储空间不足，Termux 解包失败。 | Disk space exhausted. Termux installation failed. |
| `dpkg: error processing package` | Debian 依赖包损坏或版本冲突，正在自动尝试回退。 | Dependency package error or collision. Attempting automatic fallback. |
| `Invalid private key` | SSH 私钥文件解析失败，请确认密钥格式正确。 | Failed to parse SSH private key. Verify the key format. |

---

## 7. 可视化定时任务调度 (`Schedules`) CLI 契约

可视化定时任务界面通过执行 `hermes schedule` 系列指令完成底层 Cron 任务的定义、列举、开关与手动单次触发。

### 7.1 定时任务核心实体数据结构 (`ScheduleJob.kt`)
```kotlin
package com.mermes.schedule

data class ScheduleJob(
    val id: String,                  // 任务唯一识别 Slug / UUID
    val cronExpression: String,      // Cron 表达式 (如 "0 9 * * 1-5")
    val platform: String,            // 投递目标平台名称 (如 "Telegram", "Feishu")
    val prompt: String,              // 投递触发提示词或生成的系统 Prompt
    val isActive: Boolean,           // 调度是否激活
    val lastRunTime: Long,           // 上一次执行成功时间戳
    val nextRunTime: Long,           // 预估下一次执行时间戳
    val lastError: String? = null    // 上一次运行错误堆栈摘要 (如有)
)
```

### 7.2 指令字典与返回协议

| 功能 | 终端运行命令 | 返回 JSON 结构 |
| :--- | :--- | :--- |
| **获取定时任务列表** | `hermes schedule list --json` | `{"schedules": [{"id": "job_1", "cronExpression": "...", "platform": "...", "prompt": "...", "isActive": true, "lastRunTime": 1716301200000, "nextRunTime": 1716387600000, "lastError": null}]}` |
| **切换启用状态** | `hermes schedule toggle <id> --state <active\|paused>` | `{"success": true, "id": "job_1", "state": "active"}` |
| **立即单次执行一次** | `hermes schedule trigger <id>` | `{"success": true, "executedAt": 1716301200000, "logs": "..."}` |
| **创建新任务** | `hermes schedule create --cron "<expr>" --platform "<platform>" --prompt "<prompt>"` | `{"success": true, "id": "job_new"}` |
| **删除定时任务** | `hermes schedule delete <id>` | `{"success": true}` |

### 7.3 定时任务管理器接口 (`SchedulesManager.kt`)
```kotlin
package com.mermes.schedule

interface SchedulesManager {
    // 获取所有的定时任务
    suspend fun getSchedules(): List<ScheduleJob>

    // 启用或停用定时任务
    suspend fun toggleSchedule(id: String, active: Boolean): Boolean

    // 立即单次触发一次
    suspend fun triggerSchedule(id: String): Boolean

    // 创建定时任务
    suspend fun createSchedule(cronExpression: String, platform: String, prompt: String): ScheduleJob?

    // 删除定时任务
    suspend fun deleteSchedule(id: String): Boolean
}
```

---

## 8. 智能模型在线发现 (`ModelDiscovery`) 契约

模型库管理界面允许用户输入 Base URL 和 API Key，自动并异步嗅探当前大模型提供商（Provider）上实时支持的模型 ID，免去手动键入并保证拼写准确。

### 8.1 发现请求与响应契约接口 (`ModelDiscoveryService.kt`)
```kotlin
package com.mermes.model

interface ModelDiscoveryService {
    /**
     * 自动嗅探获取提供商支持的模型 ID 列表
     * @param provider 提供商类型，支持: "ollama", "openrouter", "deepseek", "openai" 等
     * @param baseUrl 提供商 API 基础地址
     * @param apiKey 可选的鉴权 Key
     * @return 可用模型 ID 的建议列表
     */
    suspend fun discoverModels(provider: String, baseUrl: String, apiKey: String? = null): List<String>
}
```

### 8.2 提供商服务端接口应答解析协议
在底层获取模型列表时，系统将自适应解析各厂商标准的 JSON 应答：

#### A. Ollama (`/api/tags`)
* **应答样例**：
```json
{
  "models": [
    {
      "name": "llama3:8b",
      "model": "llama3:8b"
    },
    {
      "name": "qwen2.5:7b",
      "model": "qwen2.5:7b"
    }
  ]
}
```
* **映射映射器 (Kotlin)**：解析 `models[*].name` 填充到 UI 气泡底板列表中。

#### B. OpenAI / DeepSeek / OpenRouter (`/v1/models`)
* **应答样例**：
```json
{
  "data": [
    {
      "id": "deepseek-chat",
      "object": "model"
    },
    {
      "id": "deepseek-coder",
      "object": "model"
    }
  ]
}
```
* **映射映射器 (Kotlin)**：解析 `data[*].id` 填充到 BottomSheet 选择列表中。

---

## 9. 工具与 MCP 控制状态管理器 (`ToolsController`) 契约

工具面板负责系统核心 Toolsets（如 `file` 读写, `web` 浏览器检索等）的开关以及 MCP 外部插件服务进程的状态监视。

### 9.1 工具配置状态实体 (`ToolState.kt`)
```kotlin
package com.mermes.tools

data class ToolState(
    val name: String,                // 工具名称 (如 "file", "web", "terminal")
    val isEnabled: Boolean,          // 是否启用
    val iconResId: String,           // SVG 图标别名
    val isHighRisk: Boolean          // 是否为高风险工具（开启前需触发沙箱警告弹窗）
)
```

### 9.2 MCP 服务器进程参数实体 (`McpServer.kt`)
```kotlin
package com.mermes.tools

data class McpServer(
    val id: String,                  // 唯一标识 (Slug)
    val name: String,                // MCP 服务名称 (用户友好)
    val status: McpStatus,           // MCP 运行状态
    val transportType: String,       // 通信机制 (如 "stdio", "http")
    val command: String?,            // 启动命令（仅 stdio 模式）
    val arguments: List<String>?,    // 运行参数（仅 stdio 模式）
    val serverUrl: String?           // 通讯 URL（仅 http 模式）
)

enum class McpStatus {
    RUNNING,     // 正常运行中
    STOPPED,     // 已停止
    CRASHED,     // 异常退出
    INITIALIZING // 正在初始化启动
}
```

### 9.3 工具与 MCP 命令行接口映射契约

| 功能 | 终端运行命令 | 返回 JSON 结构 |
| :--- | :--- | :--- |
| **列举系统工具状态** | `hermes tools list --json` | `{"tools": [{"name": "file", "isEnabled": true, "isHighRisk": true}, {"name": "web", "isEnabled": false, "isHighRisk": false}]}` |
| **切换工具状态** | `hermes tools toggle <name> --state <on\|off>` | `{"success": true, "name": "file", "isEnabled": true}` |
| **列举 MCP 状态** | `hermes mcp status --json` | `{"mcp_servers": [{"id": "mcp-git", "name": "Git Helper", "status": "RUNNING", "transportType": "stdio", "command": "npx", "arguments": ["-y", "@modelcontextprotocol/server-gitea"]}]}` |
| **控制 MCP 服务器进程** | `hermes mcp control <id> --action <start\|stop\|restart>` | `{"success": true, "id": "mcp-git", "newStatus": "RUNNING"}` |

### 9.4 控制器契约接口 (`ToolsController.kt`)
```kotlin
package com.mermes.tools

interface ToolsController {
    // 获取所有的系统默认工具状态
    suspend fun getSystemTools(): List<ToolState>

    // 切换特定系统工具的状态 (高危工具需在业务层额外显示 MD3 警告弹窗)
    suspend fun toggleSystemTool(name: String, enable: Boolean): Boolean

    // 获取所有的 MCP Servers 列表
    suspend fun getMcpServers(): List<McpServer>

    // 对特定的 MCP Server 发射运行控制信号
    suspend fun controlMcpServer(id: String, action: McpAction): Boolean
}

enum class McpAction {
    START, STOP, RESTART
}
```
