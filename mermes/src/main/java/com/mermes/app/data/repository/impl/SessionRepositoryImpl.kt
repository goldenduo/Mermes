package com.mermes.app.data.repository.impl

import com.google.gson.Gson
import com.mermes.app.data.local.dao.SessionDao
import com.mermes.app.data.local.dao.MessageDao
import com.mermes.app.data.local.entity.SessionEntity
import com.mermes.app.data.local.entity.MessageEntity
import com.mermes.app.data.model.Message
import com.mermes.app.data.model.MessageRole
import com.mermes.app.data.model.Session
import com.mermes.app.data.model.SessionSearchResult
import com.mermes.app.data.model.TokenUsage
import com.mermes.app.data.repository.SessionRepository
import com.mermes.common.log.MermesLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class SessionRepositoryImpl(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao
) : SessionRepository {
    private val gson = Gson()

    override suspend fun getSessions(limit: Int, offset: Int): List<Session> {
        return sessionDao.getSessions(limit, offset).map { it.toDomain() }
    }

    override suspend fun getSessionById(sessionId: String): Session? {
        return sessionDao.getSessionById(sessionId)?.toDomain()
    }

    override suspend fun getMessages(sessionId: String): List<Message> {
        return messageDao.getMessagesBySessionSync(sessionId).map { it.toDomain() }
    }

    override suspend fun sendMessage(sessionId: String, content: String, attachments: List<String>?): Message? {
        return try {
            val message = MessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = MessageRole.USER.name,
                content = content,
                timestamp = System.currentTimeMillis(),
                attachmentsJson = attachments?.let { gson.toJson(it) }
            )
            messageDao.insertMessage(message)

            // 更新会话统计
            val messages = messageDao.getMessagesBySessionSync(sessionId)
            sessionDao.updateSessionStats(sessionId, messages.size, System.currentTimeMillis())

            message.toDomain()
        } catch (e: Exception) {
            MermesLog.e("SessionRepo", "Failed to send message", e)
            null
        }
    }

    override suspend fun searchSessions(query: String, limit: Int): List<SessionSearchResult> {
        return sessionDao.searchSessions(query, limit).map { session ->
            SessionSearchResult(
                sessionId = session.id,
                title = session.title,
                startedAt = session.startedAt,
                messageCount = session.messageCount,
                snippet = "" // 可以通过消息搜索获取
            )
        }
    }

    override suspend fun deleteSession(sessionId: String): Boolean {
        return try {
            messageDao.deleteMessagesBySession(sessionId)
            sessionDao.deleteSessionById(sessionId)
            true
        } catch (e: Exception) {
            MermesLog.e("SessionRepo", "Failed to delete session", e)
            false
        }
    }

    override suspend fun createSession(): Session? {
        return try {
            val session = SessionEntity(
                id = UUID.randomUUID().toString(),
                source = "chat",
                startedAt = System.currentTimeMillis(),
                messageCount = 0,
                model = null,
                title = "新会话"
            )
            sessionDao.insertSession(session)
            session.toDomain()
        } catch (e: Exception) {
            MermesLog.e("SessionRepo", "Failed to create session", e)
            null
        }
    }

    override fun observeMessages(sessionId: String): Flow<Message> {
        return messageDao.getMessagesBySession(sessionId).map { messages ->
            messages.lastOrNull()?.toDomain() ?: Message(
                id = "",
                sessionId = sessionId,
                role = MessageRole.SYSTEM,
                content = "",
                timestamp = 0
            )
        }
    }

    private fun SessionEntity.toDomain() = Session(
        id = id,
        source = source,
        startedAt = startedAt,
        messageCount = messageCount,
        model = model,
        title = title
    )

    private fun MessageEntity.toDomain() = Message(
        id = id,
        sessionId = sessionId,
        role = MessageRole.valueOf(role),
        content = content,
        timestamp = timestamp,
        model = model,
        tokenUsage = tokenUsageJson?.let { gson.fromJson(it, TokenUsage::class.java) }
    )
}
