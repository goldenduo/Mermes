package com.mermes.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val category: String?,
    val createdAt: Long,
    val updatedAt: Long
)
