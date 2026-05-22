package com.mermes.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val role: String,  // USER, ASSISTANT, SYSTEM, TOOL
    val content: String,
    val timestamp: Long,
    val model: String? = null,
    val tokenUsageJson: String? = null,  // JSON string
    val toolCallsJson: String? = null,   // JSON string
    val attachmentsJson: String? = null  // JSON string
)
