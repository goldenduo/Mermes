package com.mermes.app.data.model

/**
 * 大模型实体
 */
data class AiModel(
    val id: String,                     // 模型 ID (如 "deepseek-chat")
    val name: String,                   // 显示名称
    val provider: String,               // 提供商标识
    val baseUrl: String?,               // 基础 URL
    val isDefault: Boolean = false,     // 是否为默认模型
    val maxTokens: Int? = null,         // 最大 Token 数
    val supportsVision: Boolean = false, // 是否支持视觉
    val supportsTools: Boolean = false   // 是否支持工具调用
)

/**
 * 提供商实体
 */
data class Provider(
    val id: String,                     // 提供商 ID
    val name: String,                   // 提供商名称
    val icon: String,                   // 图标标识
    val baseUrl: String,                // API 基础 URL
    val apiKey: String?,                // API Key
    val isConfigured: Boolean = false   // 是否已配置
)
