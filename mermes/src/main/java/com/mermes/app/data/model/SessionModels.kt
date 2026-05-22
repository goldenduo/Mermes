package com.mermes.app.data.model

/**
 * 会话实体
 */
data class Session(
    val id: String,                     // 会话 ID
    val source: String,                 // 来源
    val startedAt: Long,                // 开始时间戳
    val messageCount: Int,              // 消息总数
    val model: String?,                 // 模型名称
    val title: String?,                 // 标题描述
    val preview: String? = null         // 首句预览
)

/**
 * 消息实体
 */
data class Message(
    val id: String,                     // 消息 ID
    val sessionId: String,              // 所属会话 ID
    val role: MessageRole,              // 角色
    val content: String,                // 消息内容
    val timestamp: Long,                // 时间戳
    val model: String? = null,          // 使用的模型
    val tokenUsage: TokenUsage? = null, // Token 使用统计
    val toolCalls: List<ToolCall>? = null, // 工具调用
    val attachments: List<Attachment>? = null // 附件
)

/**
 * 消息角色
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

/**
 * Token 使用统计
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val costUsd: Double? = null         // 美元成本
)

/**
 * 工具调用
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    val result: String? = null,
    val status: ToolCallStatus = ToolCallStatus.PENDING
)

enum class ToolCallStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

/**
 * 附件
 */
data class Attachment(
    val id: String,
    val type: AttachmentType,
    val name: String,
    val path: String?,
    val base64Data: String? = null,
    val mimeType: String? = null
)

enum class AttachmentType {
    IMAGE,
    FILE,
    CAMERA
}

/**
 * 会话搜索结果
 */
data class SessionSearchResult(
    val sessionId: String,
    val title: String?,
    val startedAt: Long,
    val messageCount: Int,
    val snippet: String                  // 匹配片段
)
