# webserver 模块接口

## 接口概述

本模块提供与 `download/hermes-web-ui` 相同的 RESTful API 接口。大部分接口需要 JWT 认证，公开接口（health、webhook、auth、tts）无需认证。

## 认证说明

### 认证方式

- **类型**: JWT (JSON Web Token)
- **传递方式**: HTTP Header `Authorization: Bearer <token>`
- **Token 获取**: 通过 `/api/auth/login` 接口登录获取

### 公开接口（无需认证）

- `GET /health` - 健康检查
- `POST /api/auth/login` - 用户登录
- `GET /api/auth/status` - 认证状态检查
- `POST /api/webhook` - Webhook 接收
- `POST /api/hermes/tts` - TTS 接口

### 受保护接口（需要认证）

除上述公开接口外，所有其他接口均需要有效的 JWT Token。

## 接口列表

### 1. 认证系统

#### POST /api/auth/login

- 路径：`/api/auth/login`
- 方法：`POST`
- 描述：用户登录获取 JWT Token

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

返回值：
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": "user_id",
    "username": "admin"
  }
}
```

#### GET /api/auth/status

- 路径：`/api/auth/status`
- 方法：`GET`
- 描述：检查认证状态

参数：无

返回值：
```json
{
  "authenticated": true,
  "user": {
    "id": "user_id",
    "username": "admin"
  }
}
```

#### GET /api/auth/me

- 路径：`/api/auth/me`
- 方法：`GET`
- 描述：获取当前用户信息
- 认证：需要

参数：无

返回值：
```json
{
  "id": "user_id",
  "username": "admin",
  "created_at": "2024-01-01T00:00:00Z"
}
```

#### POST /api/auth/change-password

- 路径：`/api/auth/change-password`
- 方法：`POST`
- 描述：修改密码
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| old_password | string | 是 | 旧密码 |
| new_password | string | 是 | 新密码 |

返回值：
```json
{
  "success": true
}
```

#### POST /api/auth/change-username

- 路径：`/api/auth/change-username`
- 方法：`POST`
- 描述：修改用户名
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| new_username | string | 是 | 新用户名 |
| password | string | 是 | 当前密码 |

返回值：
```json
{
  "success": true
}
```

#### GET /api/auth/locked-ips

- 路径：`/api/auth/locked-ips`
- 方法：`GET`
- 描述：获取锁定的 IP 列表
- 认证：需要

参数：无

返回值：
```json
{
  "locked_ips": [
    {
      "ip": "192.168.1.100",
      "locked_at": "2024-01-01T00:00:00Z",
      "reason": "多次登录失败"
    }
  ]
}
```

#### DELETE /api/auth/locked-ips

- 路径：`/api/auth/locked-ips`
- 方法：`DELETE`
- 解锁指定 IP
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ip | string | 是 | 要解锁的 IP 地址 |

返回值：
```json
{
  "success": true
}
```

### 2. 健康检查

#### GET /health

- 路径：`/health`
- 方法：`GET`
- 描述：服务健康检查端点

参数：无

返回值：
```json
{
  "status": "ok",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

### 3. 会话管理

#### GET /api/hermes/sessions

- 路径：`/api/hermes/sessions`
- 方法：`GET`
- 描述：获取会话列表
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| page | int | 否 | 页码，默认 1 |
| limit | int | 否 | 每页数量，默认 20 |
| search | string | 否 | 搜索关键词 |

返回值：
```json
{
  "sessions": [
    {
      "id": "session_id",
      "title": "会话标题",
      "created_at": "2024-01-01T00:00:00Z",
      "updated_at": "2024-01-01T00:00:00Z",
      "message_count": 10
    }
  ],
  "total": 100,
  "page": 1,
  "limit": 20
}
```

#### DELETE /api/hermes/sessions/{id}

- 路径：`/api/hermes/sessions/{id}`
- 方法：`DELETE`
- 描述：删除指定会话
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | string | 是 | 会话 ID |

返回值：
```json
{
  "success": true
}
```

### 4. 聊天运行

#### WebSocket /chat-run

- 路径：`/chat-run`
- 协议：WebSocket (Socket.IO)
- 描述：实时流式对话
- 认证：需要（通过查询参数传递 token）

连接参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| session_id | string | 是 | 会话 ID |
| message | string | 是 | 用户消息 |

消息格式：
```json
{
  "type": "message",
  "content": "消息内容",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

### 5. 定时任务管理

#### GET /api/hermes/jobs

- 路径：`/api/hermes/jobs`
- 方法：`GET`
- 描述：获取定时任务列表
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| status | string | 否 | 任务状态过滤 |

返回值：
```json
{
  "jobs": [
    {
      "id": "job_id",
      "name": "任务名称",
      "cron": "0 0 * * *",
      "status": "active",
      "last_run": "2024-01-01T00:00:00Z",
      "next_run": "2024-01-02T00:00:00Z"
    }
  ]
}
```

#### POST /api/hermes/jobs

- 路径：`/api/hermes/jobs`
- 方法：`POST`
- 描述：创建定时任务
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| name | string | 是 | 任务名称 |
| cron | string | 是 | Cron 表达式 |
| prompt | string | 是 | 任务提示词 |
| session_id | string | 否 | 关联会话 ID |

返回值：
```json
{
  "id": "job_id",
  "name": "任务名称",
  "cron": "0 0 * * *",
  "status": "active"
}
```

#### PATCH /api/hermes/jobs/{id}

- 路径：`/api/hermes/jobs/{id}`
- 方法：`PATCH`
- 描述：更新定时任务
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | string | 是 | 任务 ID |
| name | string | 否 | 任务名称 |
| cron | string | 否 | Cron 表达式 |
| status | string | 否 | 任务状态 |

返回值：
```json
{
  "success": true
}
```

#### DELETE /api/hermes/jobs/{id}

- 路径：`/api/hermes/jobs/{id}`
- 方法：`DELETE`
- 描述：删除定时任务
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | string | 是 | 任务 ID |

返回值：
```json
{
  "success": true}
```

#### POST /api/hermes/jobs/{id}/run

- 路径：`/api/hermes/jobs/{id}/run`
- 方法：`POST`
- 描述：手动触发定时任务
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | string | 是 | 任务 ID |

返回值：
```json
{
  "success": true,
  "message": "任务已触发"
}
```

### 6. 看板系统

#### GET /api/hermes/kanban/boards

- 路径：`/api/hermes/kanban/boards`
- 方法：`GET`
- 描述：获取看板列表
- 认证：需要

参数：无

返回值：
```json
{
  "boards": [
    {
      "id": "board_id",
      "name": "看板名称",
      "created_at": "2024-01-01T00:00:00Z"
    }
  ]
}
```

#### POST /api/hermes/kanban/boards

- 路径：`/api/hermes/kanban/boards`
- 方法：`POST`
- 描述：创建看板

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| name | string | 是 | 看板名称 |

返回值：
```json
{
  "id": "board_id",
  "name": "看板名称"
}
```

#### GET /api/hermes/kanban/boards/{boardId}/tasks

- 路径：`/api/hermes/kanban/boards/{boardId}/tasks`
- 方法：`GET`
- 描述：获取看板任务列表

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| boardId | string | 是 | 看板 ID |

返回值：
```json
{
  "tasks": [
    {
      "id": "task_id",
      "title": "任务标题",
      "description": "任务描述",
      "status": "todo",
      "assigned_to": "agent_id",
      "created_at": "2024-01-01T00:00:00Z"
    }
  ]
}
```

#### POST /api/hermes/kanban/boards/{boardId}/tasks

- 路径：`/api/hermes/kanban/boards/{boardId}/tasks`
- 方法：`POST`
- 描述：创建看板任务

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| boardId | string | 是 | 看板 ID |
| title | string | 是 | 任务标题 |
| description | string | 否 | 任务描述 |
| assigned_to | string | 否 | 分配给的 Agent ID |

返回值：
```json
{
  "id": "task_id",
  "title": "任务标题",
  "status": "todo"
}
```

### 7. 群聊系统

#### GET /api/hermes/group-chat/rooms

- 路径：`/api/hermes/group-chat/rooms`
- 方法：`GET`
- 描述：获取群聊房间列表
- 认证：需要

参数：无

返回值：
```json
{
  "rooms": [
    {
      "id": "room_id",
      "name": "房间名称",
      "agents": ["agent1", "agent2"],
      "created_at": "2024-01-01T00:00:00Z"
    }
  ]
}
```

#### POST /api/hermes/group-chat/rooms

- 路径：`/api/hermes/group-chat/rooms`
- 方法：`POST`
- 描述：创建群聊房间
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| name | string | 是 | 房间名称 |
| agents | array | 是 | Agent ID 列表 |

返回值：
```json
{
  "id": "room_id",
  "name": "房间名称",
  "agents": ["agent1", "agent2"]
}
```

### 8. 文件管理

#### GET /api/hermes/files

- 路径：`/api/hermes/files`
- 方法：`GET`
- 描述：浏览文件目录
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| path | string | 否 | 目录路径，默认为根目录 |

返回值：
```json
{
  "files": [
    {
      "name": "file.txt",
      "type": "file",
      "size": 1024,
      "modified": "2024-01-01T00:00:00Z"
    },
    {
      "name": "directory",
      "type": "directory"
    }
  ]
}
```

#### GET /api/hermes/download

- 路径：`/api/hermes/download`
- 方法：`GET`
- 描述：下载文件
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| path | string | 是 | 文件路径 |

返回值：文件流

#### POST /api/upload

- 路径：`/api/upload`
- 方法：`POST`
- 描述：上传文件
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| file | file | 是 | 文件内容 |
| path | string | 否 | 上传目录路径 |

返回值：
```json
{
  "success": true,
  "filename": "uploaded_file.txt",
  "size": 1024
}
```

### 9. 使用量分析

#### GET /api/hermes/usage

- 路径：`/api/hermes/usage`
- 方法：`GET`
- 描述：获取使用量统计
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| session_id | string | 否 | 会话 ID |
| start_date | string | 否 | 开始日期 |
| end_date | string | 否 | 结束日期 |

返回值：
```json
{
  "total_tokens": 100000,
  "total_cost": 0.5,
  "cache_hit_rate": 0.85,
  "models": [
    {
      "model": "gpt-4",
      "tokens": 50000,
      "cost": 0.3
    }
  ]
}
```

### 10. 终端系统

#### WebSocket /api/hermes/terminal

- 路径：`/api/hermes/terminal`
- 协议：WebSocket
- 描述：浏览器内终端访问
- 认证：需要（通过查询参数传递 token）

连接参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| cols | int | 否 | 终端列数，默认 80 |
| rows | int | 否 | 终端行数，默认 24 |

消息格式：
```json
{
  "type": "input",
  "data": "ls -la\n"
}
```

```json
{
  "type": "output",
  "data": "total 0\ndrwxr-xr-x  2 user  staff  64 Jan  1 00:00 .\n"
}
```

### 11. TTS (文本转语音)

#### POST /api/hermes/tts

- 路径：`/api/hermes/tts`
- 方法：`POST`
- 描述：文本转语音

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| text | string | 是 | 要转换的文本 |
| voice | string | 否 | 语音类型 |
| speed | float | 否 | 语速，默认 1.0 |

返回值：音频流 (audio/mpeg)

### 12. 技能管理

#### GET /api/hermes/skills

- 路径：`/api/hermes/skills`
- 方法：`GET`
- 描述：获取技能列表
- 认证：需要

参数：无

返回值：
```json
{
  "skills": [
    {
      "id": "skill_id",
      "name": "技能名称",
      "description": "技能描述",
      "enabled": true,
      "pinned": false
    }
  ]
}
```

#### PATCH /api/hermes/skills/{id}

- 路径：`/api/hermes/skills/{id}`
- 方法：`PATCH`
- 描述：更新技能状态
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| id | string | 是 | 技能 ID |
| enabled | boolean | 否 | 是否启用 |
| pinned | boolean | 否 | 是否固定 |

返回值：
```json
{
  "success": true
}
```

### 13. 模型管理

#### GET /api/hermes/models

- 路径：`/api/hermes/models`
- 方法：`GET`
- 描述：获取可用模型列表
- 认证：需要

参数：无

返回值：
```json
{
  "models": [
    {
      "id": "gpt-4",
      "name": "GPT-4",
      "provider": "openai",
      "context_window": 8192
    }
  ]
}
```

#### GET /api/hermes/models/config

- 路径：`/api/hermes/models/config`
- 方法：`GET`
- 描述：获取模型配置
- 认证：需要

参数：无

返回值：
```json
{
  "default_model": "gpt-4",
  "aliases": {
    "fast": "gpt-3.5-turbo",
    "smart": "gpt-4"
  }
}
```

#### PUT /api/hermes/models/config

- 路径：`/api/hermes/models/config`
- 方法：`PUT`
- 描述：更新模型配置
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| default_model | string | 否 | 默认模型 |
| aliases | object | 否 | 模型别名映射 |

返回值：
```json
{
  "success": true
}
```

### 14. 提供商管理

#### POST /api/hermes/config/providers/{poolKey}

- 路径：`/api/hermes/config/providers/{poolKey}`
- 方法：`POST`
- 描述：添加提供商配置
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| poolKey | string | 是 | 提供商池键 |
| config | object | 是 | 提供商配置 |

返回值：
```json
{
  "success": true
}
```

#### PUT /api/hermes/config/providers/{poolKey}

- 路径：`/api/hermes/config/providers/{poolKey}`
- 方法：`PUT`
- 描述：更新提供商配置
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| poolKey | string | 是 | 提供商池键 |
| config | object | 是 | 提供商配置 |

返回值：
```json
{
  "success": true
}
```

#### DELETE /api/hermes/config/providers/{poolKey}

- 路径：`/api/hermes/config/providers/{poolKey}`
- 方法：`DELETE`
- 描述：删除提供商配置
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| poolKey | string | 是 | 提供商池键 |

返回值：
```json
{
  "success": true
}
```

### 15. 记忆系统

#### GET /api/hermes/memory

- 路径：`/api/hermes/memory`
- 方法：`GET`
- 描述：获取记忆数据
- 认证：需要

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| key | string | 否 | 记忆键 |

返回值：
```json
{
  "memories": [
    {
      "key": "memory_key",
      "value": "memory_value",
      "updated_at": "2024-01-01T00:00:00Z"
    }
  ]
}
```

#### POST /api/hermes/memory

- 路径：`/api/hermes/memory`
- 方法：`POST`
- 描述：更新记忆数据

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| key | string | 是 | 记忆键 |
| value | string | 是 | 记忆值 |

返回值：
```json
{
  "success": true
}
```

### 15. 配置管理

#### GET /api/hermes/config

- 路径：`/api/hermes/config`
- 方法：`GET`
- 描述：获取配置信息

参数：无

返回值：
```json
{
  "config": {
    "port": 8648,
    "log_level": "info",
    "data_dir": "/path/to/data"
  }
}
```

#### PUT /api/hermes/config

- 路径：`/api/hermes/config`
- 方法：`PUT`
- 描述：更新配置信息

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| config | object | 是 | 配置对象 |

返回值：
```json
{
  "success": true
}
```

### 16. 日志系统

#### GET /api/hermes/logs

- 路径：`/api/hermes/logs`
- 方法：`GET`
- 描述：获取日志文件列表

参数：无

返回值：
```json
{
  "logs": [
    {
      "name": "app.log",
      "size": 1024000,
      "modified": "2024-01-01T00:00:00Z"
    }
  ]
}
```

#### GET /api/hermes/logs/{filename}

- 路径：`/api/hermes/logs/{filename}`
- 方法：`GET`
- 描述：获取日志内容

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| filename | string | 是 | 日志文件名 |
| lines | int | 否 | 获取行数，默认 100 |
| level | string | 否 | 日志级别过滤 |

返回值：
```json
{
  "logs": [
    {
      "timestamp": "2024-01-01T00:00:00Z",
      "level": "info",
      "message": "Application started"
    }
  ]
}
```

### 17. Webhook 处理

#### POST /api/webhook

- 路径：`/api/webhook`
- 方法：`POST`
- 描述：接收外部 Webhook

参数：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| payload | object | 是 | Webhook 负载 |

返回值：
```json
{
  "success": true,
  "message": "Webhook received"
}
```

### 18. 更新管理

#### GET /api/update/check

- 路径：`/api/update/check`
- 方法：`GET`
- 描述：检查更新

参数：无

返回值：
```json
{
  "current_version": "1.0.0",
  "latest_version": "1.1.0",
  "update_available": true
}
```

#### POST /api/update/execute

- 路径：`/api/update/execute`
- 方法：`POST`
- 描述：执行更新

参数：无

返回值：
```json
{
  "success": true,
  "message": "Update started"
}
```

### 19. 平台认证

#### GET /api/hermes/copilot-auth

- 路径：`/api/hermes/copilot-auth`
- 方法：`GET`
- 描述：GitHub Copilot OAuth 认证

参数：无

返回值：
```json
{
  "auth_url": "https://github.com/login/oauth/authorize?..."
}
```

#### GET /api/hermes/codex-auth

- 路径：`/api/hermes/codex-auth`
- 方法：`GET`
- 描述：OpenAI Codex OAuth 认证

参数：无

返回值：
```json
{
  "auth_url": "https://auth0.com/authorize?..."
}
```

#### GET /api/hermes/nous-auth

- 路径：`/api/hermes/nous-auth`
- 方法：`GET`
- 描述：Nous Research OAuth 认证

参数：无

返回值：
```json
{
  "auth_url": "https://nousresearch.com/oauth?..."
}
```

#### GET /api/hermes/xai-auth

- 路径：`/api/hermes/xai-auth`
- 方法：`GET`
- 描述：XAI OAuth 认证

参数：无

返回值：
```json
{
  "auth_url": "https://x.ai/oauth?..."
}
```

#### GET /api/hermes/weixin/qrcode

- 路径：`/api/hermes/weixin/qrcode`
- 方法：`GET`
- 描述：获取微信登录二维码

参数：无

返回值：
```json
{
  "qrcode_url": "https://api.weixin.qq.com/...",
  "session_id": "weixin_session_id"
}
```

### 20. 性能监控

#### GET /api/hermes/performance

- 路径：`/api/hermes/performance`
- 方法：`GET`
- 描述：获取性能指标

参数：无

返回值：
```json
{
  "cpu_usage": 0.25,
  "memory_usage": 0.60,
  "active_connections": 10,
  "request_rate": 100
}
```

## 错误响应格式

所有接口在出错时返回统一的错误格式：

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "错误描述",
    "details": {}
  }
}
```

常见错误码：
- `BAD_REQUEST` (400)：请求参数错误
- `NOT_FOUND` (404)：资源不存在
- `INTERNAL_ERROR` (500)：服务器内部错误

## 注意事项

1. **无需认证**：所有接口均不需要认证头或 Token
2. **CORS 支持**：支持跨域请求
3. **Content-Type**：请求和响应均使用 `application/json`
4. **WebSocket**：实时功能使用 WebSocket 协议
5. **文件上传**：使用 `multipart/form-data` 格式
6. **分页**：列表接口支持分页参数 `page` 和 `limit`