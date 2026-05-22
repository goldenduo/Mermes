package com.mermes.app.data.repository

import com.mermes.app.data.model.McpAction
import com.mermes.app.data.model.McpServer
import com.mermes.app.data.model.ToolState

/**
 * 工具仓库接口
 */
interface ToolRepository {
    // 系统工具
    suspend fun getSystemTools(): List<ToolState>
    suspend fun toggleSystemTool(name: String, enable: Boolean): Boolean

    // MCP 服务器
    suspend fun getMcpServers(): List<McpServer>
    suspend fun controlMcpServer(id: String, action: McpAction): Boolean
}
