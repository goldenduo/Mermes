package com.mermes.app.data.repository

import com.mermes.app.data.model.Message
import com.mermes.app.data.model.Session
import com.mermes.app.data.model.SessionSearchResult
import kotlinx.coroutines.flow.Flow

/**
 * 会话管理仓库接口
 */
interface SessionRepository {
    // 会话列表
    suspend fun getSessions(limit: Int = 20, offset: Int = 0): List<Session>

    // 会话详情
    suspend fun getSessionById(sessionId: String): Session?

    // 消息列表
    suspend fun getMessages(sessionId: String): List<Message>

    // 发送消息
    suspend fun sendMessage(sessionId: String, content: String, attachments: List<String>? = null): Message?

    // 搜索会话
    suspend fun searchSessions(query: String, limit: Int = 50): List<SessionSearchResult>

    // 删除会话
    suspend fun deleteSession(sessionId: String): Boolean

    // 创建新会话
    suspend fun createSession(): Session?

    // 流式消息响应
    fun observeMessages(sessionId: String): Flow<Message>
}
