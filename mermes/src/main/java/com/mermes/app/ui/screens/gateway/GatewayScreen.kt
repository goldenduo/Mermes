package com.mermes.app.ui.screens.gateway

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mermes.app.data.model.GatewayPlatform

import androidx.compose.material.icons.filled.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewayScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    var platforms by remember { mutableStateOf(getMockPlatforms()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "平台网关",
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
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "全局设置"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            items(platforms) { platform ->
                PlatformCard(
                    platform = platform,
                    onToggle = { connect ->
                        platforms = platforms.map {
                            if (it.id == platform.id) it.copy(isConnected = connect) else it
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PlatformCard(
    platform: GatewayPlatform,
    onToggle: (Boolean) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val statusColor by animateColorAsState(
        targetValue = if (platform.isConnected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        animationSpec = tween(durationMillis = 500)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // 头部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态指示灯
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = platform.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (platform.isConnected) "已连接" else "已断开",
                        fontSize = 12.sp,
                        color = statusColor
                    )
                }

                Switch(
                    checked = platform.isConnected,
                    onCheckedChange = onToggle
                )

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "收起" else "展开"
                    )
                }
            }

            // 展开的配置表单
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    platform.config.forEach { (key, value) ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { /* TODO: 更新配置 */ },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            label = { Text(key) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { /* TODO: 保存配置 */ }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "保存配置",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun getMockPlatforms(): List<GatewayPlatform> {
    return listOf(
        GatewayPlatform(
            id = "telegram",
            name = "Telegram",
            icon = "telegram",
            isConnected = true,
            config = mapOf(
                "Bot Token" to "123456:ABC-DEF",
                "Chat ID" to "-100123456789"
            )
        ),
        GatewayPlatform(
            id = "discord",
            name = "Discord",
            icon = "discord",
            isConnected = false,
            config = mapOf(
                "Webhook URL" to "https://discord.com/api/webhooks/...",
                "Channel ID" to "123456789"
            )
        ),
        GatewayPlatform(
            id = "feishu",
            name = "飞书",
            icon = "feishu",
            isConnected = false,
            config = mapOf(
                "App ID" to "",
                "App Secret" to ""
            )
        ),
        GatewayPlatform(
            id = "wecom",
            name = "企业微信",
            icon = "wecom",
            isConnected = false,
            config = mapOf(
                "Corp ID" to "",
                "Agent ID" to "",
                "Secret" to ""
            )
        )
    )
}
