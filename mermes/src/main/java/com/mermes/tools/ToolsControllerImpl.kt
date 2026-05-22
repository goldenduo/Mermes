package com.mermes.tools

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mermes.common.log.MermesLog as Log
import com.mermes.utils.AgentRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ToolsControllerImpl(private val context: Context) : ToolsController {
    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences("mermes_tools_mcp", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "ToolsControllerImpl"
        private const val KEY_TOOLS = "system_tools_json"
        private const val KEY_MCP = "mcp_servers_json"
    }

    init {
        // Prepopulate default system tools if empty
        if (!prefs.contains(KEY_TOOLS)) {
            val defaultTools = listOf(
                ToolState("file", true, "ic_tool_file", true),
                ToolState("web", true, "ic_tool_web", false),
                ToolState("terminal", false, "ic_tool_terminal", true),
                ToolState("calculator", false, "ic_tool_calc", false)
            )
            prefs.edit().putString(KEY_TOOLS, gson.toJson(defaultTools)).apply()
        }

        // Prepopulate default MCP servers if empty
        if (!prefs.contains(KEY_MCP)) {
            val defaultMcp = listOf(
                McpServer(
                    id = "mcp-git",
                    name = "Git Helper Server",
                    status = McpStatus.RUNNING,
                    transportType = "stdio",
                    command = "npx",
                    arguments = listOf("-y", "@modelcontextprotocol/server-gitea"),
                    serverUrl = null
                ),
                McpServer(
                    id = "mcp-postgres",
                    name = "PostgreSQL Analyzer",
                    status = McpStatus.STOPPED,
                    transportType = "stdio",
                    command = "npx",
                    arguments = listOf("-y", "@modelcontextprotocol/server-postgres", "postgresql://localhost/mermes"),
                    serverUrl = null
                ),
                McpServer(
                    id = "mcp-weather",
                    name = "Local Weather API Gateway",
                    status = McpStatus.RUNNING,
                    transportType = "http",
                    command = null,
                    arguments = null,
                    serverUrl = "http://127.0.0.1:8080/mcp/weather"
                )
            )
            prefs.edit().putString(KEY_MCP, gson.toJson(defaultMcp)).apply()
        }
    }

    private fun getLocalTools(): MutableList<ToolState> {
        val json = prefs.getString(KEY_TOOLS, null) ?: return mutableListOf()
        val type = object : TypeToken<List<ToolState>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun saveLocalTools(tools: List<ToolState>) {
        prefs.edit().putString(KEY_TOOLS, gson.toJson(tools)).apply()
    }

    private fun getLocalMcp(): MutableList<McpServer> {
        val json = prefs.getString(KEY_MCP, null) ?: return mutableListOf()
        val type = object : TypeToken<List<McpServer>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun saveLocalMcp(mcp: List<McpServer>) {
        prefs.edit().putString(KEY_MCP, gson.toJson(mcp)).apply()
    }

    override suspend fun getSystemTools(): List<ToolState> = withContext(Dispatchers.IO) {
        val result = AgentRunner.executeLocalCommand(context, "hermes", arrayOf("tools", "list", "--json"))
        if (result.exitCode == 0) {
            try {
                val type = object : TypeToken<Map<String, List<ToolState>>>() {}.type
                val response: Map<String, List<ToolState>> = gson.fromJson(result.output.trim(), type)
                return@withContext response["tools"] ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse CLI tools output: ${result.output}", e)
            }
        }
        return@withContext getLocalTools()
    }

    override suspend fun toggleSystemTool(name: String, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val stateStr = if (enable) "on" else "off"
        val result = AgentRunner.executeLocalCommand(context, "hermes", arrayOf("tools", "toggle", name, "--state", stateStr))
        if (result.exitCode == 0) {
            return@withContext true
        }
        // Fallback local update
        val tools = getLocalTools()
        val index = tools.indexOfFirst { it.name == name }
        if (index >= 0) {
            tools[index] = tools[index].copy(isEnabled = enable)
            saveLocalTools(tools)
            return@withContext true
        }
        return@withContext false
    }

    override suspend fun getMcpServers(): List<McpServer> = withContext(Dispatchers.IO) {
        val result = AgentRunner.executeLocalCommand(context, "hermes", arrayOf("mcp", "status", "--json"))
        if (result.exitCode == 0) {
            try {
                val type = object : TypeToken<Map<String, List<McpServer>>>() {}.type
                val response: Map<String, List<McpServer>> = gson.fromJson(result.output.trim(), type)
                return@withContext response["mcp_servers"] ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse CLI mcp status output: ${result.output}", e)
            }
        }
        return@withContext getLocalMcp()
    }

    override suspend fun controlMcpServer(id: String, action: McpAction): Boolean = withContext(Dispatchers.IO) {
        val actionStr = action.name.lowercase()
        val result = AgentRunner.executeLocalCommand(context, "hermes", arrayOf("mcp", "control", id, "--action", actionStr))
        if (result.exitCode == 0) {
            return@withContext true
        }
        // Fallback local update
        val mcpList = getLocalMcp()
        val index = mcpList.indexOfFirst { it.id == id }
        if (index >= 0) {
            val newStatus = when (action) {
                McpAction.START -> McpStatus.RUNNING
                McpAction.STOP -> McpStatus.STOPPED
                McpAction.RESTART -> McpStatus.RUNNING
            }
            mcpList[index] = mcpList[index].copy(status = newStatus)
            saveLocalMcp(mcpList)
            return@withContext true
        }
        return@withContext false
    }
}
