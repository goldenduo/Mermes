package com.mermes.app.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mermes.app.data.model.Message
import com.mermes.app.data.model.MessageRole
import com.mermes.app.data.model.Session
import com.mermes.app.data.model.ToolCall
import com.mermes.app.data.model.ToolCallStatus
import com.mermes.common.log.MermesLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private var currentSessionId: String? = null

    fun loadSession(sessionId: String?) {
        currentSessionId = sessionId
        if (sessionId != null) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)
                // TODO: 从仓库加载会话消息
                // val messages = sessionRepository.getMessages(sessionId)
                // _messages.value = messages
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        viewModelScope.launch {
            val userMessage = Message(
                id = generateId(),
                sessionId = currentSessionId ?: "",
                role = MessageRole.USER,
                content = content,
                timestamp = System.currentTimeMillis()
            )

            _messages.value = _messages.value + userMessage
            _uiState.value = _uiState.value.copy(isSending = true)

            try {
                // TODO: 发送到后端并获取响应
                // 模拟助手响应
                val assistantMessage = Message(
                    id = generateId(),
                    sessionId = currentSessionId ?: "",
                    role = MessageRole.ASSISTANT,
                    content = "收到您的消息: $content\n\n这是模拟响应，实际实现需要连接到后端服务。",
                    timestamp = System.currentTimeMillis()
                )
                _messages.value = _messages.value + assistantMessage
            } catch (e: Exception) {
                MermesLog.e("ChatVM", "Failed to send message", e)
                val errorMessage = Message(
                    id = generateId(),
                    sessionId = currentSessionId ?: "",
                    role = MessageRole.SYSTEM,
                    content = "发送失败: ${e.message}",
                    timestamp = System.currentTimeMillis()
                )
                _messages.value = _messages.value + errorMessage
            } finally {
                _uiState.value = _uiState.value.copy(isSending = false)
            }
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    private fun generateId(): String {
        return System.currentTimeMillis().toString() + "_" + (Math.random() * 1000).toInt()
    }
}

data class ChatUiState(
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val currentSession: Session? = null,
    val error: String? = null
)
