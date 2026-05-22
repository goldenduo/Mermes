package com.mermes.app.data.repository.impl

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mermes.app.data.model.AuthType
import com.mermes.app.data.model.ConnectionConfig
import com.mermes.app.data.model.ConnectionMode
import com.mermes.app.data.model.HttpConfig
import com.mermes.app.data.model.SshConfig
import com.mermes.app.data.model.SshConnectionState
import com.mermes.app.data.remote.LocalCommandExecutor
import com.mermes.app.data.remote.SshCommandExecutor
import com.mermes.app.data.remote.HttpCommandExecutor
import com.mermes.app.data.remote.TerminalCommandExecutor
import com.mermes.app.data.repository.ConnectionRepository
import com.mermes.common.log.MermesLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.connectionDataStore: DataStore<Preferences> by preferencesDataStore(name = "connection_prefs")

/**
 * 连接管理仓库实现
 */
class ConnectionRepositoryImpl(
    private val context: Context
) : ConnectionRepository {

    private val gson = Gson()
    private val _connectionState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    private val _currentMode = MutableStateFlow<ConnectionMode?>(null)
    private var currentExecutor: TerminalCommandExecutor? = null

    companion object {
        private val SSH_CONFIGS_KEY = stringPreferencesKey("ssh_configs")
        private val CURRENT_MODE_KEY = stringPreferencesKey("current_mode")
        private val HTTP_CONFIG_KEY = stringPreferencesKey("http_config")
    }

    override val connectionState: Flow<SshConnectionState> = _connectionState.asStateFlow()
    override val currentMode: Flow<ConnectionMode?> = _currentMode.asStateFlow()

    override suspend fun getAllSshConfigs(): List<SshConfig> {
        return try {
            val prefs = context.connectionDataStore.data.first()
            val json = prefs[SSH_CONFIGS_KEY] ?: return emptyList()
            val type = object : TypeToken<List<SshConfig>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            MermesLog.e("ConnectionRepo", "Failed to get SSH configs", e)
            emptyList()
        }
    }

    override suspend fun getSshConfigById(id: String): SshConfig? {
        return getAllSshConfigs().find { it.id == id }
    }

    override suspend fun saveSshConfig(config: SshConfig): Boolean {
        return try {
            val configs = getAllSshConfigs().toMutableList()
            val index = configs.indexOfFirst { it.id == config.id }
            if (index >= 0) {
                configs[index] = config
            } else {
                configs.add(config)
            }
            saveSshConfigs(configs)
            true
        } catch (e: Exception) {
            MermesLog.e("ConnectionRepo", "Failed to save SSH config", e)
            false
        }
    }

    override suspend fun deleteSshConfig(id: String): Boolean {
        return try {
            val configs = getAllSshConfigs().filter { it.id != id }
            saveSshConfigs(configs)
            true
        } catch (e: Exception) {
            MermesLog.e("ConnectionRepo", "Failed to delete SSH config", e)
            false
        }
    }

    override suspend fun setDefaultSshConfig(id: String): Boolean {
        return try {
            val configs = getAllSshConfigs().map {
                it.copy(isDefault = it.id == id)
            }
            saveSshConfigs(configs)
            true
        } catch (e: Exception) {
            MermesLog.e("ConnectionRepo", "Failed to set default SSH config", e)
            false
        }
    }

    override suspend fun connectLocal() {
        try {
            _connectionState.value = SshConnectionState.Connecting
            _currentMode.value = ConnectionMode.LOCAL
            currentExecutor = LocalCommandExecutor()

            // 测试本地连接
            val result = currentExecutor?.execute("echo 'connected'")
            if (result != null) {
                _connectionState.value = SshConnectionState.Connected("local")
                MermesLog.i("ConnectionRepo", "Local connection established")
            } else {
                _connectionState.value = SshConnectionState.Error("Failed to establish local connection")
            }
        } catch (e: Exception) {
            _connectionState.value = SshConnectionState.Error("Local connection failed", e)
            MermesLog.e("ConnectionRepo", "Local connection failed", e)
        }
    }

    override suspend fun connectHttp(config: HttpConfig) {
        try {
            _connectionState.value = SshConnectionState.Connecting
            _currentMode.value = ConnectionMode.HTTP

            // 保存 HTTP 配置
            context.connectionDataStore.edit { prefs ->
                prefs[HTTP_CONFIG_KEY] = gson.toJson(config)
            }

            currentExecutor = HttpCommandExecutor(config.serverUrl, config.apiKey)

            // 测试 HTTP 连接
            val result = currentExecutor?.execute("echo 'connected'")
            if (result != null) {
                _connectionState.value = SshConnectionState.Connected("http")
                MermesLog.i("ConnectionRepo", "HTTP connection established to ${config.serverUrl}")
            } else {
                _connectionState.value = SshConnectionState.Error("Failed to connect to ${config.serverUrl}")
            }
        } catch (e: Exception) {
            _connectionState.value = SshConnectionState.Error("HTTP connection failed", e)
            MermesLog.e("ConnectionRepo", "HTTP connection failed", e)
        }
    }

    override suspend fun connectSsh(config: SshConfig) {
        try {
            _connectionState.value = SshConnectionState.Connecting
            _currentMode.value = ConnectionMode.SSH

            currentExecutor = SshCommandExecutor(
                host = config.host,
                port = config.port,
                username = config.username,
                password = config.password,
                privateKeyPath = config.privateKeyPath,
                passphrase = config.passphrase
            )

            // 测试 SSH 连接
            val result = currentExecutor?.execute("echo 'connected'")
            if (result != null) {
                _connectionState.value = SshConnectionState.Connected(config.id)

                // 更新最后连接时间
                val updatedConfig = config.copy(lastConnectedAt = System.currentTimeMillis())
                saveSshConfig(updatedConfig)

                MermesLog.i("ConnectionRepo", "SSH connection established to ${config.host}")
            } else {
                _connectionState.value = SshConnectionState.Error("Failed to connect to ${config.host}")
            }
        } catch (e: Exception) {
            _connectionState.value = SshConnectionState.Error("SSH connection failed", e)
            MermesLog.e("ConnectionRepo", "SSH connection failed", e)
        }
    }

    override suspend fun disconnect() {
        currentExecutor?.close()
        currentExecutor = null
        _connectionState.value = SshConnectionState.Disconnected
        _currentMode.value = null
        MermesLog.i("ConnectionRepo", "Disconnected")
    }

    override suspend fun testSshConnection(config: SshConfig): SshConnectionState {
        return try {
            val executor = SshCommandExecutor(
                host = config.host,
                port = config.port,
                username = config.username,
                password = config.password,
                privateKeyPath = config.privateKeyPath,
                passphrase = config.passphrase
            )

            val result = executor.execute("echo 'test'")
            executor.close()

            if (result != null) {
                SshConnectionState.Connected(config.id)
            } else {
                SshConnectionState.Error("Connection test failed")
            }
        } catch (e: Exception) {
            SshConnectionState.Error("Connection test failed", e)
        }
    }

    override suspend fun getCurrentConfig(): ConnectionConfig? {
        val mode = _currentMode.value ?: return null
        return when (mode) {
            ConnectionMode.LOCAL -> ConnectionConfig(mode = ConnectionMode.LOCAL)
            ConnectionMode.HTTP -> {
                val prefs = context.connectionDataStore.data.first()
                val json = prefs[HTTP_CONFIG_KEY] ?: return null
                val config = gson.fromJson(json, HttpConfig::class.java)
                ConnectionConfig(mode = ConnectionMode.HTTP, httpConfig = config)
            }
            ConnectionMode.SSH -> {
                // 返回默认 SSH 配置
                val defaultConfig = getAllSshConfigs().find { it.isDefault }
                ConnectionConfig(mode = ConnectionMode.SSH, sshConfig = defaultConfig)
            }
        }
    }

    fun getExecutor(): TerminalCommandExecutor? = currentExecutor

    private suspend fun saveSshConfigs(configs: List<SshConfig>) {
        context.connectionDataStore.edit { prefs ->
            prefs[SSH_CONFIGS_KEY] = gson.toJson(configs)
        }
    }
}
