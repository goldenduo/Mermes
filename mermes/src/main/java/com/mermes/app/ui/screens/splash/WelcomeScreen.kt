package com.mermes.app.ui.screens.splash

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mermes.app.R
import com.mermes.app.data.model.SshConfig
import com.mermes.app.data.model.SshConnectionState
import com.mermes.common.i18n.MermesI18nTranslator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onConnected: () -> Unit,
    onNavigateToSshConfigs: () -> Unit,
    viewModel: ConnectionViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf<String?>(null) }
    val uiState by viewModel.uiState.collectAsState()
    val sshConfigs by viewModel.sshConfigs.collectAsState()

    // HTTP 临时输入状态
    var serverUrl by remember { mutableStateOf("http://127.0.0.1:11434") }
    var apiKey by remember { mutableStateOf("") }

    // SSH 选中的配置
    var selectedSshConfig by remember { mutableStateOf<SshConfig?>(null) }

    val translator = remember { MermesI18nTranslator() }
    val locale = Locale.getDefault()

    // 默认选中
    LaunchedEffect(sshConfigs) {
        if (selectedSshConfig == null && sshConfigs.isNotEmpty()) {
            selectedSshConfig = sshConfigs.find { it.isDefault } ?: sshConfigs.first()
        }
    }

    // 监听连接状态
    LaunchedEffect(uiState.connectionState) {
        when (val state = uiState.connectionState) {
            is SshConnectionState.Connected -> {
                onConnected()
            }
            is SshConnectionState.Error -> {
                val friendlyErr = translator.translate(state.message, locale)
                Toast.makeText(context, friendlyErr, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    // 监听连接测试结果
    LaunchedEffect(uiState.testResult) {
        uiState.testResult?.let { result ->
            when (result) {
                is SshConnectionState.Connected -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.ssh_configs_test_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is SshConnectionState.Error -> {
                    val friendlyErr = translator.translate(result.message, locale)
                    val formatStr = context.getString(R.string.ssh_configs_test_failed, friendlyErr)
                    Toast.makeText(context, formatStr, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
            viewModel.clearTestResult()
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(id = R.string.welcome_title),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = stringResource(id = R.string.welcome_subtitle),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 本地 Termux 模式卡片
                ConnectionModeCard(
                    title = stringResource(id = R.string.welcome_local_mode),
                    description = stringResource(id = R.string.welcome_local_desc),
                    icon = Icons.Default.Computer,
                    isSelected = selectedMode == "local",
                    onClick = { selectedMode = "local" }
                )

                // HTTP 模式卡片
                ConnectionModeCard(
                    title = stringResource(id = R.string.welcome_http_mode),
                    description = stringResource(id = R.string.welcome_http_desc),
                    icon = Icons.Default.Cloud,
                    isSelected = selectedMode == "http",
                    onClick = { selectedMode = "http" }
                )

                // HTTP 面板展示
                AnimatedVisibility(visible = selectedMode == "http") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = serverUrl,
                                onValueChange = { serverUrl = it },
                                label = { Text("Server URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                label = { Text("API Key (Optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // SSH 模式卡片
                ConnectionModeCard(
                    title = stringResource(id = R.string.welcome_ssh_mode),
                    description = stringResource(id = R.string.welcome_ssh_desc),
                    icon = Icons.Default.Key,
                    isSelected = selectedMode == "ssh",
                    onClick = { selectedMode = "ssh" }
                )

                // SSH 动态面板展示
                AnimatedVisibility(visible = selectedMode == "ssh") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (sshConfigs.isEmpty()) {
                                Text(
                                    text = stringResource(id = R.string.ssh_configs_empty),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = onNavigateToSshConfigs,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(id = R.string.ssh_configs_add))
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "选择 SSH 凭证:",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    TextButton(onClick = onNavigateToSshConfigs) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "配置管理", fontSize = 12.sp)
                                    }
                                }

                                // 凭证卡片流
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    sshConfigs.forEach { config ->
                                        val isSelected = selectedSshConfig?.id == config.id
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(CardDefaults.shape)
                                                .clickable { selectedSshConfig = config },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                } else {
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                                }
                                            ),
                                            elevation = CardDefaults.cardElevation(1.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = config.name,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        if (config.isDefault) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Icon(
                                                                imageVector = Icons.Default.CheckCircle,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "${config.username}@${config.host}:${config.port}",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                if (isSelected) {
                                                    IconButton(
                                                        onClick = { viewModel.testSshConnection(config) },
                                                        enabled = !uiState.isTestingConnection
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.NetworkCheck,
                                                            contentDescription = "测试连接",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 连接按钮
                Button(
                    onClick = {
                        when (selectedMode) {
                            "local" -> viewModel.connectLocal()
                            "http" -> {
                                if (serverUrl.isBlank()) {
                                    Toast.makeText(context, "请输入有效的 Server URL", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.connectHttp(serverUrl, apiKey)
                                }
                            }
                            "ssh" -> {
                                val config = selectedSshConfig
                                if (config == null) {
                                    Toast.makeText(context, "请选择或新建一个 SSH 配置", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.connectSsh(config)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = selectedMode != null && !uiState.isConnecting &&
                            (selectedMode != "ssh" || selectedSshConfig != null)
                ) {
                    if (uiState.isConnecting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(id = R.string.welcome_connect),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 测试连接中的 Loading 状态
            if (uiState.isTestingConnection) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(
                                text = stringResource(id = R.string.ssh_configs_test_connecting),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionModeCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                },
                modifier = Modifier.size(32.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }
        }
    }
}
