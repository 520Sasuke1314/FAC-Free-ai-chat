package com.yourapp.chat.ui.screen.conversationlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourapp.chat.ChatApplication
import com.yourapp.chat.data.local.dao.ConversationWithLast
import com.yourapp.chat.data.local.entity.ApiProfileEntity
import com.yourapp.chat.data.local.entity.ConversationEntity
import com.yourapp.chat.data.repository.ApiProfileRepository
import com.yourapp.chat.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationListUiState(
    val conversations: List<ConversationWithLast> = emptyList(),
    val apiProfiles: List<ApiProfileEntity> = emptyList(),
    val selectedProfileId: Long? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationListViewModel(
    private val chatRepository: ChatRepository,
    private val apiProfileRepository: ApiProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            searchQuery.flatMapLatest { chatRepository.getConversationsWithLast(it) }.collect { list ->
                _uiState.update { it.copy(conversations = list) }
            }
        }
        viewModelScope.launch {
            apiProfileRepository.getAll().collect { profiles ->
                val current = _uiState.value.selectedProfileId
                val selected = if (current != null && profiles.any { it.id == current }) {
                    current
                } else {
                    profiles.firstOrNull { it.isDefault }?.id ?: profiles.firstOrNull()?.id
                }
                _uiState.update { it.copy(apiProfiles = profiles, selectedProfileId = selected) }
            }
        }
    }

    fun setSearchQuery(q: String) {
        searchQuery.value = q
    }

    fun renameConversation(id: Long, title: String) {
        viewModelScope.launch {
            chatRepository.renameConversation(id, title)
        }
    }

    fun createConversation(title: String): Long? {
        var id: Long? = null
        viewModelScope.launch {
            id = chatRepository.createConversation(title, null)
        }
        return null
    }

    fun createConversationAndOpen(title: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = chatRepository.createConversation(title, null)
            onCreated(id)
        }
    }

    fun deleteConversation(conversation: ConversationEntity) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversation)
        }
    }

    fun togglePin(conversation: ConversationEntity) {
        viewModelScope.launch {
            chatRepository.setConversationPinned(conversation.id, !conversation.pinned)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = ChatApplication.instance
                return ConversationListViewModel(app.chatRepository, app.apiProfileRepository) as T
            }
        }
    }
}
