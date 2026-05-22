package com.mermes.app.data.model

/**
 * 记忆条目实体
 */
data class MemoryEntry(
    val id: String,                     // 条目 ID
    val content: String,                // 记忆内容
    val category: String? = null,       // 分类
    val createdAt: Long,                // 创建时间
    val updatedAt: Long                 // 更新时间
)

/**
 * 用户画像
 */
data class UserProfile(
    val id: String,
    val content: String,                // 画像内容
    val updatedAt: Long
)
