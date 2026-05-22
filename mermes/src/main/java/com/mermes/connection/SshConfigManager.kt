package com.mermes.connection

import android.content.Context

interface SshConfigManager {
    // 获取所有已保存的 SSH 配置
    suspend fun getAllConfigs(context: Context): List<SshConfig>

    // 根据 ID 获取配置
    suspend fun getConfigById(context: Context, id: String): SshConfig?

    // 保存配置 (新增或更新)
    suspend fun saveConfig(context: Context, config: SshConfig): Boolean

    // 删除配置
    suspend fun deleteConfig(context: Context, id: String): Boolean

    // 设置默认配置
    suspend fun setDefault(context: Context, id: String): Boolean

    // 测试 SSH 连接
    suspend fun testConnection(config: SshConfig): SshConnectionState
}
