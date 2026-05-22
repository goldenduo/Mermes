package com.mermes.model

interface ModelDiscoveryService {
    /**
     * 自动嗅探获取提供商支持的模型 ID 列表
     * @param provider 提供商类型，支持: "ollama", "openrouter", "deepseek", "openai" 等
     * @param baseUrl 提供商 API 基础地址
     * @param apiKey 可选的鉴权 Key
     * @return 可用模型 ID 的建议列表
     */
    suspend fun discoverModels(provider: String, baseUrl: String, apiKey: String? = null): List<String>
}
