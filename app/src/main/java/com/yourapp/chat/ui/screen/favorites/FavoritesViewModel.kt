package com.yourapp.chat.ui.screen.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourapp.chat.ChatApplication
import com.yourapp.chat.data.local.entity.MessageEntity
import com.yourapp.chat.data.repository.ChatRepository
import com.yourapp.chat.data.repository.ConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoriteItem(
    val message: MessageEntity,
    val conversationTitle: String
)

data class FavoritesUiState(
    val items: List<FavoriteItem> = emptyList(),
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val info: String? = null
)

class FavoritesViewModel(
    private val chatRepository: ChatRepository,
    private val configRepository: ConfigRepository
) : ViewModel() {

    private var lastPinToggleAt = 0L
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                chatRepository.getFavoriteMessages().distinctUntilChanged(),
                chatRepository.getConversations().distinctUntilChanged()
            ) { messages, conversations ->
                val titles = conversations.associate { it.id to it.title }
                messages.map { m -> FavoriteItem(m, titles[m.conversationId] ?: "对话") }
            }.collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
    }

    fun setSelectionMode(enabled: Boolean) {
        _uiState.update { it.copy(isSelectionMode = enabled, selectedIds = if (enabled) it.selectedIds else emptySet()) }
    }

    fun toggleSelect(messageId: Long) {
        _uiState.update {
            val sel = if (it.selectedIds.contains(messageId)) it.selectedIds - messageId else it.selectedIds + messageId
            it.copy(selectedIds = sel)
        }
    }

    fun selectAll() {
        _uiState.update {
            it.copy(selectedIds = it.items.map { f -> f.message.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), isSelectionMode = false) }
    }

    /** 右滑置顶 / 取消置顶收藏（带防抖：快速连滑时忽略冷却期内的重复请求，避免置顶结果被覆盖） */
    fun togglePinFavorite(messageId: Long, pinned: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastPinToggleAt < PIN_COOLDOWN_MS) return
        lastPinToggleAt = now
        viewModelScope.launch {
            chatRepository.toggleMessagePinned(messageId, pinned)
            _uiState.update {
                it.copy(info = if (pinned) "已置顶该收藏" else "已取消置顶")
            }
        }
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            chatRepository.deleteFavoriteMessages(ids)
            _uiState.update {
                it.copy(
                    info = "已删除 ${ids.size} 条收藏",
                    selectedIds = emptySet(),
                    isSelectionMode = false
                )
            }
        }
    }

    fun clearInfo() {
        _uiState.update { it.copy(info = null) }
    }

    companion object {
        /** 置顶/取消置顶防抖间隔（毫秒），与收藏行手势配合防连滑 */
        private const val PIN_COOLDOWN_MS = 700L

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = ChatApplication.instance
                return FavoritesViewModel(app.chatRepository, app.configRepository) as T
            }
        }
    }
}
