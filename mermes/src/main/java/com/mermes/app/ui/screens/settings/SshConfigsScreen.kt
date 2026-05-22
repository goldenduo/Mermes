package com.mermes.app.ui.screens.settings

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mermes.app.R
import com.mermes.app.data.model.AuthType
import com.mermes.app.data.model.SshConfig
import com.mermes.app.data.model.SshConnectionState
import com.mermes.app.data.model.SshTestFailureReason
import com.mermes.app.data.model.SshTestResult
import com.mermes.app.data.repository.impl.ConnectionRepositoryImpl
import com.mermes.common.log.MermesLog
import androidx.compose.ui.platform.LocalConfiguration
import com.mermes.common.i18n.MermesI18nTranslator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SshConfigsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConnectionRepositoryImpl(application)

    private val _configs = MutableStateFlow<List<SshConfig>>(emptyList())
    val configs: StateFlow<List<SshConfig>> = _configs.asStateFlow()

    private val _uiState = MutableStateFlow<SshConfigsUiState>(SshConfigsUiState.Idle)
    val uiState: StateFlow<SshConfigsUiState> = _uiState.asStateFlow()

    // 追踪每个 Config 专属的测通状态
    private val _testStates = MutableStateFlow<Map<String, SshTestResult?>>(emptyMap())
    val testStates: StateFlow<Map<String, SshTestResult?>> = _testStates.asStateFlow()

    // 测试连接结果事件流
    private val _testEvents = MutableSharedFlow<Pair<String, SshTestResult>>(replay = 0)
    val testEvents: SharedFlow<Pair<String, SshTestResult>> = _testEvents.asSharedFlow()

    fun loadConfigs() {
        viewModelScope.launch {
            _configs.value = repository.getAllSshConfigs()
        }
    }

    fun deleteConfig(id: String) {
        viewModelScope.launch {
            val success = repository.deleteSshConfig(id)
            if (success) {
                loadConfigs()
            }
        }
    }

    fun setDefaultConfig(id: String) {
        viewModelScope.launch {
            val success = repository.setDefaultSshConfig(id)
            if (success) {
                loadConfigs()
            }
        }
    }

    fun testConnection(config: SshConfig) {
        viewModelScope.launch {
            // 使用 null 表示正在测试中
            _testStates.value = _testStates.value + (config.id to null)
            val result = repository.testSshConnection(config)
            _testStates.value = _testStates.value + (config.id to result)
            _testEvents.emit(config.id to result)
        }
    }

    fun clearTestState(id: String) {
        _testStates.value = _testStates.value - id
    }
}

sealed class SshConfigsUiState {
    object Idle : SshConfigsUiState()
    object Loading : SshConfigsUiState()
    data class Error(val message: String) : SshConfigsUiState()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SshConfigsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String?) -> Unit,
    viewModel: SshConfigsViewModel = viewModel()
) {
    val context = LocalContext.current
    val configs by viewModel.configs.collectAsState()
    val testStates by viewModel.testStates.collectAsState()

    val translator = remember { MermesI18nTranslator() }
    val locale = LocalConfiguration.current.locales[0]

    var showDeleteConfirmDialog by remember { mutableStateOf<SshConfig?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadConfigs()
    }

    LaunchedEffect(Unit) {
        viewModel.testEvents.collect { (_, result) ->
            when (result) {
                is SshTestResult.Success -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.ssh_configs_test_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is SshTestResult.Failure -> {
                    val reasonText = getFailureReasonText(context, result.reason)
                    val detailMsg = if (result.detail != null) {
                        "$reasonText\n${result.detail}"
                    } else {
                        reasonText
                    }
                    Toast.makeText(context, detailMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.ssh_configs_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEdit(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.ssh_configs_add)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (configs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.ssh_configs_empty),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(configs, key = { it.id }) { config ->
                        val testState = testStates[config.id]

                        SshConfigCard(
                            config = config,
                            testState = testState,
                            onCardClick = { onNavigateToEdit(config.id) },
                            onTestConnect = { viewModel.testConnection(config) },
                            onSetDefault = {
                                viewModel.setDefaultConfig(config.id)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.success),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDelete = { showDeleteConfirmDialog = config }
                        )
                    }
                }
            }
        }

        // 删除确认对话框
        showDeleteConfirmDialog?.let { config ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = null },
                title = { Text(text = stringResource(id = R.string.confirm)) },
                text = {
                    Text(
                        text = stringResource(
                            id = R.string.ssh_configs_delete_confirm,
                            config.name
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteConfig(config.id)
                            showDeleteConfirmDialog = null
                            Toast.makeText(
                                context,
                                context.getString(R.string.ssh_configs_delete_success),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Text(
                            text = stringResource(id = R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = null }) {
                        Text(text = stringResource(id = R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun SshConfigCard(
    config: SshConfig,
    testState: SshTestResult?,
    onCardClick: () -> Unit,
    onTestConnect: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (config.isDefault) 1.02f else 1.0f,
        animationSpec = tween(durationMillis = 300),
        label = "scale"
    )

    val cardBorderBrush = if (config.isDefault) {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.colorScheme.outlineVariant
            )
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (config.isDefault) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        border = CardDefaults.outlinedCardBorder(enabled = true).copy(
            brush = cardBorderBrush,
            width = if (config.isDefault) 2.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header: Name & Default Label & Delete Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (config.useTunnel) Icons.Default.Link else Icons.Default.Terminal,
                    contentDescription = null,
                    tint = if (config.isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (config.isDefault) {
                        Text(
                            text = stringResource(id = R.string.welcome_ssh_mode),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Delete action icon
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.delete),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body: Connection Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SSH Address",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${config.host}:${config.port}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "User / Auth",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${config.username} (${if (config.authType == AuthType.PASSWORD) "Pwd" else "Key"})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Tunnel Details
            if (config.useTunnel) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tunnel: L$${config.localPort} -> $${config.tunnelRemoteHost}:$${config.tunnelRemotePort}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actions & Test State Line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Test Connection Action Row
                val cardContext = LocalContext.current
                val cardLocale = LocalConfiguration.current.locales[0]
                val cardTranslator = remember { MermesI18nTranslator() }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = {
                        if (testState is SshTestResult.Failure) {
                            val reasonText = getFailureReasonText(cardContext, testState.reason)
                            val detailMsg = if (testState.detail != null) {
                                "$reasonText\n${testState.detail}"
                            } else {
                                reasonText
                            }
                            Toast.makeText(cardContext, detailMsg, Toast.LENGTH_LONG).show()
                        }
                        onTestConnect()
                    })
                ) {
                    when (testState) {
                        null -> {
                            // 正在测试中 (null 表示测试进行中)
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.ssh_configs_test_connecting),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        is SshTestResult.Success -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.ssh_configs_status_connected),
                                fontSize = 13.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        is SshTestResult.Failure -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.ssh_configs_status_failed),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Default Settings Button (Only show if not currently default)
                if (!config.isDefault) {
                    Button(
                        onClick = onSetDefault,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.ssh_configs_set_default),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun getFailureReasonText(context: android.content.Context, reason: SshTestFailureReason): String {
    return when (reason) {
        SshTestFailureReason.AUTH_FAILED -> context.getString(R.string.ssh_failure_auth_failed)
        SshTestFailureReason.NETWORK_UNREACHABLE -> context.getString(R.string.ssh_failure_network_unreachable)
        SshTestFailureReason.CONNECTION_TIMEOUT -> context.getString(R.string.ssh_failure_connection_timeout)
        SshTestFailureReason.KEY_PARSE_FAILED -> context.getString(R.string.ssh_failure_key_parse_failed)
        SshTestFailureReason.HOST_KEY_CHANGED -> context.getString(R.string.ssh_failure_host_key_changed)
        SshTestFailureReason.PORT_FORWARD_FAILED -> context.getString(R.string.ssh_failure_port_forward_failed)
        SshTestFailureReason.UNKNOWN -> context.getString(R.string.ssh_failure_unknown)
    }
}
