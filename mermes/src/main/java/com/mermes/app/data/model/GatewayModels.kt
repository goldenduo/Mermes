package com.mermes.app.data.model

/**
 * 网关平台配置
 */
data class GatewayPlatform(
    val id: String,                     // 平台 ID
    val name: String,                   // 平台名称 (如 "Telegram", "Discord")
    val icon: String,                   // 图标标识
    val isConnected: Boolean,           // 连接状态
    val config: Map<String, String>     // 配置参数
)
