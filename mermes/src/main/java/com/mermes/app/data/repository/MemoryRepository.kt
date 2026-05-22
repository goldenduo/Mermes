package com.mermes.app.data.repository

import com.mermes.app.data.model.MemoryEntry
import com.mermes.app.data.model.UserProfile

/**
 * 长期记忆仓库接口
 */
interface MemoryRepository {
    // 记忆条目
    suspend fun getMemories(): List<MemoryEntry>
    suspend fun addMemory(content: String, category: String? = null): MemoryEntry?
    suspend fun updateMemory(id: String, content: String): Boolean
    suspend fun deleteMemory(id: String): Boolean

    // 用户画像
    suspend fun getProfile(): UserProfile?
    suspend fun updateProfile(content: String): Boolean
    suspend fun refineProfileWithAi(content: String): String?
}
