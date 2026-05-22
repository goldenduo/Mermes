package com.mermes.app.data.model

/**
 * 工具状态实体
 */
data class ToolState(
    val name: String,                   // 工具名称 (如 "file", "web", "terminal")
    val isEnabled: Boolean,             // 是否启用
    val iconResId: String,              // SVG 图标别名
    val isHighRisk: Boolean             // 是否为高风险工具
)

/**
 * MCP 服务器实体
 */
data class McpServer(
    val id: String,                     // 唯一标识
    val name: String,                   // MCP 服务名称
    val status: McpStatus,              // 运行状态
    val transportType: String,          // 通信机制 (如 "stdio", "http")
    val command: String?,               // 启动命令（仅 stdio 模式）
    val arguments: List<String>?,       // 运行参数（仅 stdio 模式）
    val serverUrl: String?              // 通讯 URL（仅 http 模式）
)

/**
 * MCP 运行状态
 */
enum class McpStatus {
    RUNNING,        // 正常运行中
    STOPPED,        // 已停止
    CRASHED,        // 异常退出
    INITIALIZING    // 正在初始化启动
}

/**
 * MCP 控制动作
 */
enum class McpAction {
    START,
    STOP,
    RESTART
}
