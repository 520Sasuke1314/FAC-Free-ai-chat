package com.yourapp.chat.ui.screen.official

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourapp.chat.ChatApplication
import com.yourapp.chat.data.local.entity.MessageEntity
import com.yourapp.chat.data.repository.DeepSeekWebRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OfficialUiState(
    val loggedIn: Boolean = false,
    val messages: List<MessageEntity> = emptyList(),
    val sending: Boolean = false,
    val inputText: String = "",
    val error: String? = null,
    val showRiskDialog: Boolean = false
)

class OfficialViewModel(
    private val repository: DeepSeekWebRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OfficialUiState())
    val uiState: StateFlow<OfficialUiState> = _uiState.asStateFlow()

    private var nextId = 1L

    init {
        _uiState.update {
            it.copy(loggedIn = repository.hasToken(), showRiskDialog = repository.hasToken().not())
        }
    }

    fun acceptRisk() {
        _uiState.update { it.copy(showRiskDialog = false) }
    }

    fun declineRisk() {
        // 不登录，保持退出状态
        _uiState.update { it.copy(showRiskDialog = false) }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun login(email: String, password: String) {
        if (_uiState.value.sending) return
        _uiState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            try {
                repository.login(email, password)
                _uiState.update { it.copy(sending = false, loggedIn = true, showRiskDialog = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(sending = false, error = e.message ?: "登录失败") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.update { it.copy(loggedIn = false, messages = emptyList(), error = null) }
        }
    }

    fun send() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.sending) return
        _uiState.update { it.copy(inputText = "", sending = true, error = null) }
        viewModelScope.launch {
            val userMsg = MessageEntity(
                id = nextId++,
                conversationId = 0,
                role = "user",
                content = text
            )
            val assistantMsg = MessageEntity(
                id = nextId++,
                conversationId = 0,
                role = "assistant",
                content = ""
            )
            _uiState.update { it.copy(messages = it.messages + userMsg + assistantMsg) }
            try {
                val sb = StringBuilder()
                repository.chatStream(conversationId = -1L, prompt = text).collect { token ->
                    sb.append(token)
                    _uiState.update { state ->
                        val updated = state.messages.map { m ->
                            if (m.id == assistantMsg.id) m.copy(content = sb.toString()) else m
                        }
                        state.copy(messages = updated)
                    }
                }
                _uiState.update { it.copy(sending = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(sending = false, error = e.message ?: "发送失败") }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = ChatApplication.instance
                return OfficialViewModel(app.deepSeekWebRepository) as T
            }
        }
    }
}
