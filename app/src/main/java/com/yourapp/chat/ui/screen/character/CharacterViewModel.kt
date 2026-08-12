package com.yourapp.chat.ui.screen.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.yourapp.chat.ChatApplication
import com.yourapp.chat.data.local.entity.CharacterCardEntity
import com.yourapp.chat.data.repository.CharacterCardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CharacterUiState(
    val cards: List<CharacterCardEntity> = emptyList(),
    val importing: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

class CharacterViewModel(
    private val repository: CharacterCardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharacterUiState())
    val uiState: StateFlow<CharacterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllCards().collect { cards ->
                _uiState.update { it.copy(cards = cards) }
            }
        }
    }

    fun importCard(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(importing = true, error = null) }
            try {
                repository.importCard(uri)
                _uiState.update { it.copy(importing = false, info = "导入成功") }
            } catch (e: Exception) {
                _uiState.update { it.copy(importing = false, error = e.message) }
            }
        }
    }

    fun deleteCard(card: CharacterCardEntity) {
        viewModelScope.launch {
            repository.deleteCard(card)
        }
    }

    fun toggleEnabled(card: CharacterCardEntity) {
        viewModelScope.launch {
            repository.setEnabled(card, !card.isEnabled)
        }
    }

    /** 编辑角色卡：更新姓名/描述/系统提示/开场白 */
    fun updateCard(
        card: CharacterCardEntity,
        name: String,
        description: String?,
        systemPrompt: String?,
        firstMessage: String?
    ) {
        viewModelScope.launch {
            try {
                repository.updateCard(card, name, description, systemPrompt, firstMessage)
                _uiState.update { it.copy(info = "角色卡已更新") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** 创建自定义角色卡 */
    fun createCard(
        name: String,
        description: String?,
        systemPrompt: String?,
        firstMessage: String?
    ) {
        viewModelScope.launch {
            try {
                val card = CharacterCardEntity(
                    name = name.trim(),
                    description = description?.trim()?.ifBlank { null },
                    systemPrompt = systemPrompt?.trim()?.ifBlank { null },
                    firstMessage = firstMessage?.trim()?.ifBlank { null },
                    jsonData = null,
                    imagePath = null,
                    isEnabled = true
                )
                repository.insertCard(card)
                _uiState.update { it.copy(info = "角色已创建") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearInfo() {
        _uiState.update { it.copy(info = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = ChatApplication.instance
                return CharacterViewModel(app.characterCardRepository) as T
            }
        }
    }
}
