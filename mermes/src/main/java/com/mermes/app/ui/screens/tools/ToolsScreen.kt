package com.mermes.app.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mermes.app.data.model.McpServer
import com.mermes.app.data.model.McpStatus
import com.mermes.app.data.model.ToolState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateBack: () -> Unit
) {
    var tools by remember { mutableStateOf(getMockTools()) }
    var mcpServers by remember { mutableStateOf(getMockMcpServers()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "工具与 MCP",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 系统工具部分
            item {
                Text(
                    text = "系统工具",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tools) { tool ->
                        ToolCard(
                            tool = tool,
                            onToggle = { enabled ->
                                tools = tools.map {
                                    if (it.name == tool.name) it.copy(isEnabled = enabled) else it
                                }
                            }
                        )
                    }
                }
            }

            // MCP 服务器部分
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "MCP 服务器",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            items(mcpServers) { server ->
                McpServerCard(server = server)
            }
        }
    }
}

@Composable
private fun ToolCard(
    tool: ToolState,
    onToggle: (Boolean) -> Unit
) {
    val icon = when (tool.name) {
        "file" -> Icons.Default.Folder
        "web" -> Icons.Default.Language
        "terminal" -> Icons.Default.Terminal
        "code" -> Icons.Default.Code
        else -> Icons.Default.Code
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!tool.isEnabled) },
        colors = CardDefaults.cardColors(
            containerColor = if (tool.isEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (tool.isEnabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tool.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (tool.isEnabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (tool.isHighRisk) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "高风险",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun McpServerCard(server: McpServer) {
    val statusColor = when (server.status) {
        McpStatus.RUNNING -> MaterialTheme.colorScheme.primary
        McpStatus.STOPPED -> MaterialTheme.colorScheme.error
        McpStatus.CRASHED -> MaterialTheme.colorScheme.error
        McpStatus.INITIALIZING -> MaterialTheme.colorScheme.tertiary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = server.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${server.transportType} | ${server.status.name}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun getMockTools(): List<ToolState> {
    return listOf(
        ToolState("file", true, "file", true),
        ToolState("web", true, "web", false),
        ToolState("terminal", false, "terminal", true),
        ToolState("code", true, "code", false)
    )
}

private fun getMockMcpServers(): List<McpServer> {
    return listOf(
        McpServer(
            id = "mcp-git",
            name = "Git Helper",
            status = McpStatus.RUNNING,
            transportType = "stdio",
            command = "npx",
            arguments = listOf("-y", "@modelcontextprotocol/server-gitea"),
            serverUrl = null
        ),
        McpServer(
            id = "mcp-web",
            name = "Web Scraper",
            status = McpStatus.STOPPED,
            transportType = "http",
            command = null,
            arguments = null,
            serverUrl = "http://localhost:8080"
        )
    )
}
