package com.mermes.app.data.repository

import com.mermes.app.data.model.AiModel
import com.mermes.app.data.model.Provider

/**
 * 模型仓库接口
 */
interface ModelRepository {
    // 模型管理
    suspend fun getModels(): List<AiModel>
    suspend fun addModel(model: AiModel): Boolean
    suspend fun updateModel(model: AiModel): Boolean
    suspend fun deleteModel(id: String): Boolean
    suspend fun setDefaultModel(id: String): Boolean

    // 模型发现
    suspend fun discoverModels(provider: String, baseUrl: String, apiKey: String? = null): List<String>

    // 提供商管理
    suspend fun getProviders(): List<Provider>
    suspend fun updateProvider(provider: Provider): Boolean
}
