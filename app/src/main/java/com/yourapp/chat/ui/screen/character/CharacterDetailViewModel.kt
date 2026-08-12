package com.yourapp.chat.ui.screen.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourapp.chat.ChatApplication
import com.yourapp.chat.data.local.entity.CharacterCardEntity
import com.yourapp.chat.data.local.entity.WorldEntryEntity
import com.yourapp.chat.data.repository.CharacterCardRepository
import com.yourapp.chat.data.repository.WorldEntryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CharacterDetailUiState(
    val card: CharacterCardEntity? = null,
    val loaded: Boolean = false,
    val worldEntries: List<WorldEntryEntity> = emptyList(),
    val showEdit: Boolean = false,
    val message: String? = null
)

class CharacterDetailViewModel(
    private val cardId: Long,
    private val cardRepository: CharacterCardRepository,
    private val worldEntryRepository: WorldEntryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharacterDetailUiState())
    val uiState: StateFlow<CharacterDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val card = cardRepository.getCard(cardId)
            _uiState.update { it.copy(card = card, loaded = true) }
        }
        viewModelScope.launch {
            worldEntryRepository.getByCardId(cardId).collect { entries ->
                _uiState.update { it.copy(worldEntries = entries) }
            }
        }
    }

    fun toggleEntry(entry: WorldEntryEntity) {
        viewModelScope.launch {
            worldEntryRepository.update(entry.copy(enabled = !entry.enabled))
        }
    }

    fun deleteEntry(entry: WorldEntryEntity) {
        viewModelScope.launch {
            worldEntryRepository.delete(entry)
        }
    }

    fun toggleEdit() {
        _uiState.update { it.copy(showEdit = !it.showEdit) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** 保存角色卡参数编辑 */
    fun saveCard(name: String, description: String, systemPrompt: String, firstMessage: String) {
        val card = _uiState.value.card ?: return
        viewModelScope.launch {
            try {
                cardRepository.updateCard(card, name, description, systemPrompt, firstMessage)
                _uiState.update {
                    it.copy(
                        showEdit = false,
                        message = "已保存",
                        card = it.card?.copy(
                            name = name.trim(),
                            description = description.trim().ifBlank { null },
                            systemPrompt = systemPrompt.trim().ifBlank { null },
                            firstMessage = firstMessage.trim().ifBlank { null }
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "保存失败: ${e.message}") }
            }
        }
    }

    companion object {
        fun factory(cardId: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = ChatApplication.instance
                return CharacterDetailViewModel(
                    cardId,
                    app.characterCardRepository,
                    app.worldEntryRepository
                ) as T
            }
        }
    }
}
