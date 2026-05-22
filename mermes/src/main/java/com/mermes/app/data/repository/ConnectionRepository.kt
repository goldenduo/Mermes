package com.mermes.app.data.repository

import com.mermes.app.data.model.AuthType
import com.mermes.app.data.model.ConnectionConfig
import com.mermes.app.data.model.ConnectionMode
import com.mermes.app.data.model.HttpConfig
import com.mermes.app.data.model.SshConfig
import com.mermes.app.data.model.SshConnectionState
import com.mermes.app.data.model.SshTestResult
import kotlinx.coroutines.flow.Flow

/**
 * 连接管理仓库接口
 */
interface ConnectionRepository {
    // 当前连接状态
    val connectionState: Flow<SshConnectionState>

    // 当前连接模式
    val currentMode: Flow<ConnectionMode?>

    // SSH 配置管理
    suspend fun getAllSshConfigs(): List<SshConfig>
    suspend fun getSshConfigById(id: String): SshConfig?
    suspend fun saveSshConfig(config: SshConfig): Boolean
    suspend fun deleteSshConfig(id: String): Boolean
    suspend fun setDefaultSshConfig(id: String): Boolean

    // 连接操作
    suspend fun connectLocal()
    suspend fun connectHttp(config: HttpConfig)
    suspend fun connectSsh(config: SshConfig)
    suspend fun disconnect()
    suspend fun testSshConnection(config: SshConfig): SshTestResult

    // 获取当前连接配置
    suspend fun getCurrentConfig(): ConnectionConfig?
}
