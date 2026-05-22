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
