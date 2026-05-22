package com.mermes.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val source: String,
    val startedAt: Long,
    val messageCount: Int,
    val model: String?,
    val title: String?,
    val lastMessageAt: Long = System.currentTimeMillis()
)
