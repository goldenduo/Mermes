package com.mermes.app.data.repository.impl

import com.mermes.app.data.local.dao.MemoryDao
import com.mermes.app.data.local.entity.MemoryEntity
import com.mermes.app.data.model.MemoryEntry
import com.mermes.app.data.model.UserProfile
import com.mermes.app.data.repository.MemoryRepository
import com.mermes.common.log.MermesLog
import java.util.UUID

class MemoryRepositoryImpl(
    private val memoryDao: MemoryDao
) : MemoryRepository {

    override suspend fun getMemories(): List<MemoryEntry> {
        return memoryDao.getAllMemoriesSync().map { it.toDomain() }
    }

    override suspend fun addMemory(content: String, category: String?): MemoryEntry? {
        return try {
            val memory = MemoryEntity(
                id = UUID.randomUUID().toString(),
                content = content,
                category = category,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            memoryDao.insertMemory(memory)
            memory.toDomain()
        } catch (e: Exception) {
            MermesLog.e("MemoryRepo", "Failed to add memory", e)
            null
        }
    }

    override suspend fun updateMemory(id: String, content: String): Boolean {
        return try {
            val existing = memoryDao.getMemoryById(id) ?: return false
            val updated = existing.copy(
                content = content,
                updatedAt = System.currentTimeMillis()
            )
            memoryDao.updateMemory(updated)
            true
        } catch (e: Exception) {
            MermesLog.e("MemoryRepo", "Failed to update memory", e)
            false
        }
    }

    override suspend fun deleteMemory(id: String): Boolean {
        return try {
            memoryDao.deleteMemoryById(id)
            true
        } catch (e: Exception) {
            MermesLog.e("MemoryRepo", "Failed to delete memory", e)
            false
        }
    }

    override suspend fun getProfile(): UserProfile? {
        // 从记忆中查找画像类型的条目
        val profileMemories = memoryDao.getMemoriesByCategory("profile")
        return profileMemories.firstOrNull()?.let {
            UserProfile(
                id = it.id,
                content = it.content,
                updatedAt = it.updatedAt
            )
        }
    }

    override suspend fun updateProfile(content: String): Boolean {
        return try {
            val existing = getProfile()
            if (existing != null) {
                updateMemory(existing.id, content)
            } else {
                addMemory(content, "profile") != null
            }
        } catch (e: Exception) {
            MermesLog.e("MemoryRepo", "Failed to update profile", e)
            false
        }
    }

    override suspend fun refineProfileWithAi(content: String): String? {
        // TODO: 调用 AI 进行画像提炼
        return content
    }

    private fun MemoryEntity.toDomain() = MemoryEntry(
        id = id,
        content = content,
        category = category,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
