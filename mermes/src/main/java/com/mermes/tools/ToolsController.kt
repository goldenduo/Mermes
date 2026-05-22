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
