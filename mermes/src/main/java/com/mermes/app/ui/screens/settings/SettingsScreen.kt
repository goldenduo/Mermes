package com.mermes.app.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mermes.app.R
import com.mermes.app.data.model.GatewayPlatform
import com.mermes.app.ui.screens.splash.ConnectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToSshConfigs: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToWelcome: () -> Unit,
    viewModel: ConnectionViewModel = viewModel()
) {
    var isZhLanguage by remember { mutableStateOf(true) }
    var isLogSanitized by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.settings_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 语言设置
            SettingsSection(title = stringResource(id = R.string.settings_general)) {
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = stringResource(id = R.string.settings_language),
                    subtitle = if (isZhLanguage) "中文" else "English",
                    onClick = { isZhLanguage = !isZhLanguage }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 连接设置
            SettingsSection(title = stringResource(id = R.string.settings_connection)) {
                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = stringResource(id = R.string.settings_ssh_config),
                    subtitle = stringResource(id = R.string.settings_ssh_config_desc),
                    onClick = onNavigateToSshConfigs
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Cloud,
                    title = stringResource(id = R.string.settings_providers),
                    subtitle = stringResource(id = R.string.settings_providers_desc),
                    onClick = onNavigateToProviders
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.ModelTraining,
                    title = stringResource(id = R.string.settings_models),
                    subtitle = stringResource(id = R.string.settings_models_desc),
                    onClick = onNavigateToModels
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 平台网关配置
            SettingsSection(title = stringResource(id = R.string.gateway_title)) {
                GatewaySection()
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 安全设置
            SettingsSection(title = stringResource(id = R.string.settings_security)) {
                SettingsToggleItem(
                    icon = Icons.Default.Security,
                    title = stringResource(id = R.string.settings_log_sanitize),
                    subtitle = stringResource(id = R.string.settings_log_sanitize_desc),
                    isChecked = isLogSanitized,
                    onToggle = { isLogSanitized = it }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 断开连接大按钮
            Button(
                onClick = {
                    viewModel.disconnect()
                    onNavigateToWelcome()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 6.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = stringResource(id = R.string.settings_disconnect)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.settings_disconnect_desc),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GatewaySection() {
    var platforms by remember { mutableStateOf(getMockPlatforms()) }

    Column {
        platforms.forEach { platform ->
            PlatformCard(
                platform = platform,
                onToggle = { connect ->
                    platforms = platforms.map {
                        if (it.id == platform.id) it.copy(isConnected = connect) else it
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // 头部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 8.dp),
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (platform.isConnected) stringResource(id = R.string.gateway_connected) else stringResource(id = R.string.gateway_disconnected),
                    fontSize = 11.sp,
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
                    contentDescription = if (isExpanded) "收起" else "展开",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 展开的配置表单
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
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

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onToggle
        )
    }
}
