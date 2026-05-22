package com.mermes.app.data.repository

import com.mermes.app.data.model.GatewayPlatform

/**
 * 网关仓库接口
 */
interface GatewayRepository {
    // 获取所有平台
    suspend fun getPlatforms(): List<GatewayPlatform>

    // 获取平台详情
    suspend fun getPlatformById(id: String): GatewayPlatform?

    // 更新平台配置
    suspend fun updatePlatformConfig(id: String, config: Map<String, String>): Boolean

    // 切换连接状态
    suspend fun toggleConnection(id: String, connect: Boolean): Boolean
}
