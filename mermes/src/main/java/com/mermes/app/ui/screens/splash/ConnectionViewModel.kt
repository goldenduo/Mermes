package com.mermes.app.ui.screens.splash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mermes.app.data.model.AuthType
import com.mermes.app.data.model.ConnectionMode
import com.mermes.app.data.model.HttpConfig
import com.mermes.app.data.model.SshConfig
import com.mermes.app.data.model.SshConnectionState
import com.mermes.app.data.model.SshTestResult
import com.mermes.app.data.repository.impl.ConnectionRepositoryImpl
import com.mermes.common.log.MermesLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ConnectionRepositoryImpl(application)

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private val _sshConfigs = MutableStateFlow<List<SshConfig>>(emptyList())
    val sshConfigs: StateFlow<List<SshConfig>> = _sshConfigs.asStateFlow()

    init {
        loadSshConfigs()
        observeConnectionState()
    }

    private fun loadSshConfigs() {
        viewModelScope.launch {
            _sshConfigs.value = repository.getAllSshConfigs()
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
            }
        }
    }

    fun connectLocal() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConnecting = true)
            repository.connectLocal()
            _uiState.value = _uiState.value.copy(isConnecting = false)
        }
    }

    fun connectHttp(serverUrl: String, apiKey: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConnecting = true)
            val config = HttpConfig(
                serverUrl = serverUrl,
                apiKey = apiKey?.takeIf { it.isNotBlank() }
            )
            repository.connectHttp(config)
            _uiState.value = _uiState.value.copy(isConnecting = false)
        }
    }

    fun connectSsh(config: SshConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConnecting = true)
            repository.connectSsh(config)
            _uiState.value = _uiState.value.copy(isConnecting = false)
        }
    }

    fun testSshConnection(config: SshConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingConnection = true)
            val result = repository.testSshConnection(config)
            _uiState.value = _uiState.value.copy(
                isTestingConnection = false,
                testResult = result
            )
        }
    }

    fun saveSshConfig(config: SshConfig) {
        viewModelScope.launch {
            repository.saveSshConfig(config)
            loadSshConfigs()
        }
    }

    fun deleteSshConfig(id: String) {
        viewModelScope.launch {
            repository.deleteSshConfig(id)
            loadSshConfigs()
        }
    }

    fun setDefaultSshConfig(id: String) {
        viewModelScope.launch {
            repository.setDefaultSshConfig(id)
            loadSshConfigs()
        }
    }

    fun clearTestResult() {
        _uiState.value = _uiState.value.copy(testResult = null)
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
        }
    }
}

data class ConnectionUiState(
    val connectionState: SshConnectionState = SshConnectionState.Disconnected,
    val isConnecting: Boolean = false,
    val isTestingConnection: Boolean = false,
    val testResult: SshTestResult? = null
)
