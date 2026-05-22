package com.mermes.app.ui.screens.settings

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mermes.app.R
import com.mermes.app.data.model.AuthType
import com.mermes.app.data.model.SshConfig
import com.mermes.app.data.repository.impl.ConnectionRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FolderOpen
import java.io.File

class SshConfigEditViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConnectionRepositoryImpl(application)

    private val _config = MutableStateFlow<SshConfig?>(null)
    val config: StateFlow<SshConfig?> = _config.asStateFlow()

    private val _saveStatus = MutableStateFlow<Boolean?>(null)
    val saveStatus: StateFlow<Boolean?> = _saveStatus.asStateFlow()

    fun loadConfig(id: String?) {
        if (id.isNullOrEmpty() || id == "null") {
            _config.value = SshConfig(
                id = UUID.randomUUID().toString(),
                name = "",
                host = "",
                port = 22,
                username = "",
                authType = AuthType.PASSWORD,
                password = "",
                privateKeyPath = "",
                passphrase = "",
                isDefault = false,
                useEncryption = true,
                useTunnel = false,
                localPort = 11434,
                tunnelRemoteHost = "127.0.0.1",
                tunnelRemotePort = 11434
            )
        } else {
            viewModelScope.launch {
                _config.value = repository.getSshConfigById(id)
            }
        }
    }

    fun updateConfig(updated: SshConfig) {
        _config.value = updated
    }

    fun saveConfig(onSuccess: () -> Unit) {
        val current = _config.value ?: return
        viewModelScope.launch {
            val success = repository.saveSshConfig(current)
            _saveStatus.value = success
            if (success) {
                onSuccess()
            }
        }
    }

    fun resetSaveStatus() {
        _saveStatus.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshConfigEditScreen(
    configId: String?,
    onNavigateBack: () -> Unit,
    viewModel: SshConfigEditViewModel = viewModel()
) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()

    var showPassword by remember { mutableStateOf(false) }
    var showPassphrase by remember { mutableStateOf(false) }

    val privateKeyPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                val keysDir = File(context.filesDir, "keys")
                if (!keysDir.exists()) {
                    keysDir.mkdirs()
                }
                val destinationFile = File(keysDir, "id_rsa_${UUID.randomUUID()}")
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        destinationFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    val current = config
                    if (current != null) {
                        viewModel.updateConfig(current.copy(privateKeyPath = destinationFile.absolutePath))
                    }
                    Toast.makeText(context, "私钥已安全托管至应用私有目录", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "密钥拷贝失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    LaunchedEffect(configId) {
        viewModel.loadConfig(configId)
    }

    LaunchedEffect(saveStatus) {
        if (saveStatus == true) {
            Toast.makeText(context, context.getString(R.string.ssh_edit_save_success), Toast.LENGTH_SHORT).show()
            viewModel.resetSaveStatus()
        } else if (saveStatus == false) {
            Toast.makeText(context, context.getString(R.string.ssh_edit_save_failed), Toast.LENGTH_SHORT).show()
            viewModel.resetSaveStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (configId == null || configId == "null") {
                            stringResource(id = R.string.ssh_edit_title_new)
                        } else {
                            stringResource(id = R.string.ssh_edit_title_edit)
                        },
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
                actions = {
                    IconButton(
                        onClick = {
                            val current = config
                            if (current != null) {
                                if (current.name.isBlank() || current.host.isBlank() || current.username.isBlank()) {
                                    Toast.makeText(context, context.getString(R.string.ssh_edit_invalid_fields), Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.saveConfig(onSuccess = onNavigateBack)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(id = R.string.save)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            config?.let { currentConfig ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. 基础配置卡片
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "基本信息",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // 配置名称
                            OutlinedTextField(
                                value = currentConfig.name,
                                onValueChange = { viewModel.updateConfig(currentConfig.copy(name = it)) },
                                label = { Text(stringResource(id = R.string.ssh_edit_name)) },
                                placeholder = { Text(stringResource(id = R.string.ssh_edit_name_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 主机地址
                                OutlinedTextField(
                                    value = currentConfig.host,
                                    onValueChange = { viewModel.updateConfig(currentConfig.copy(host = it)) },
                                    label = { Text(stringResource(id = R.string.ssh_edit_host)) },
                                    placeholder = { Text(stringResource(id = R.string.ssh_edit_host_hint)) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                // SSH 端口
                                OutlinedTextField(
                                    value = currentConfig.port.toString(),
                                    onValueChange = {
                                        val portVal = it.toIntOrNull() ?: 22
                                        viewModel.updateConfig(currentConfig.copy(port = portVal))
                                    },
                                    label = { Text(stringResource(id = R.string.ssh_edit_port)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(90.dp),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            // 登录用户名
                            OutlinedTextField(
                                value = currentConfig.username,
                                onValueChange = { viewModel.updateConfig(currentConfig.copy(username = it)) },
                                label = { Text(stringResource(id = R.string.ssh_edit_username)) },
                                placeholder = { Text(stringResource(id = R.string.ssh_edit_username_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    // 2. 身份验证配置卡片
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.ssh_edit_auth_type),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // 验证方式单选 Tab Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(4.dp)
                            ) {
                                val isPassword = currentConfig.authType == AuthType.PASSWORD
                                Button(
                                    onClick = { viewModel.updateConfig(currentConfig.copy(authType = AuthType.PASSWORD)) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPassword) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        contentColor = if (isPassword) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text(text = stringResource(id = R.string.ssh_edit_auth_password), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { viewModel.updateConfig(currentConfig.copy(authType = AuthType.KEY)) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isPassword) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        contentColor = if (!isPassword) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text(text = stringResource(id = R.string.ssh_edit_auth_key), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (currentConfig.authType == AuthType.PASSWORD) {
                                // 登录密码
                                OutlinedTextField(
                                    value = currentConfig.password ?: "",
                                    onValueChange = { viewModel.updateConfig(currentConfig.copy(password = it)) },
                                    label = { Text(stringResource(id = R.string.ssh_edit_password)) },
                                    placeholder = { Text(stringResource(id = R.string.ssh_edit_password_hint)) },
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            } else {
                                // 密钥路径
                                OutlinedTextField(
                                    value = currentConfig.privateKeyPath ?: "",
                                    onValueChange = { viewModel.updateConfig(currentConfig.copy(privateKeyPath = it)) },
                                    label = { Text(stringResource(id = R.string.ssh_edit_private_key)) },
                                    placeholder = { Text(stringResource(id = R.string.ssh_edit_private_key_hint)) },
                                    trailingIcon = {
                                        IconButton(onClick = { privateKeyPickerLauncher.launch(arrayOf("*/*")) }) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = "选择密钥文件",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                // 私钥口令
                                OutlinedTextField(
                                    value = currentConfig.passphrase ?: "",
                                    onValueChange = { viewModel.updateConfig(currentConfig.copy(passphrase = it)) },
                                    label = { Text(stringResource(id = R.string.ssh_edit_passphrase)) },
                                    placeholder = { Text(stringResource(id = R.string.ssh_edit_passphrase_hint)) },
                                    visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                            Icon(
                                                imageVector = if (showPassphrase) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    // 3. 强安全加密控制卡片
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(id = R.string.ssh_secure_card_title),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(id = R.string.ssh_secure_encryption_enable),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = currentConfig.useEncryption,
                                    onCheckedChange = { viewModel.updateConfig(currentConfig.copy(useEncryption = it)) }
                                )
                            }

                            Text(
                                text = stringResource(id = R.string.ssh_secure_encryption_desc),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // 4. SSH 本地转发隧道卡片
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(id = R.string.ssh_tunnel_card_title),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(id = R.string.ssh_tunnel_enable),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = currentConfig.useTunnel,
                                    onCheckedChange = { viewModel.updateConfig(currentConfig.copy(useTunnel = it)) }
                                )
                            }

                            Text(
                                text = stringResource(id = R.string.ssh_tunnel_desc),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                lineHeight = 16.sp
                            )

                            AnimatedVisibility(
                                visible = currentConfig.useTunnel,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // 本地监听端口
                                    OutlinedTextField(
                                        value = currentConfig.localPort.toString(),
                                        onValueChange = {
                                            val p = it.toIntOrNull() ?: 11434
                                            viewModel.updateConfig(currentConfig.copy(localPort = p))
                                        },
                                        label = { Text(stringResource(id = R.string.ssh_tunnel_local_port)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // 远程转发主机
                                        OutlinedTextField(
                                            value = currentConfig.tunnelRemoteHost,
                                            onValueChange = { viewModel.updateConfig(currentConfig.copy(tunnelRemoteHost = it)) },
                                            label = { Text(stringResource(id = R.string.ssh_tunnel_remote_host)) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        )

                                        // 远程转发端口
                                        OutlinedTextField(
                                            value = currentConfig.tunnelRemotePort.toString(),
                                            onValueChange = {
                                                val p = it.toIntOrNull() ?: 11434
                                                viewModel.updateConfig(currentConfig.copy(tunnelRemotePort = p))
                                            },
                                            label = { Text(stringResource(id = R.string.ssh_tunnel_remote_port)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.width(100.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. 是否为默认
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = currentConfig.isDefault,
                            onCheckedChange = { viewModel.updateConfig(currentConfig.copy(isDefault = it)) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.ssh_edit_is_default),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
