package com.yourapp.chat.ui.screen.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourapp.chat.ChatApplication
import com.yourapp.chat.data.local.entity.SkillEntity
import com.yourapp.chat.data.repository.SkillRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SkillUiState(
    val skills: List<SkillEntity> = emptyList(),
    val loading: Boolean = false,
    val info: String? = null,
    val error: String? = null
)

class SkillViewModel(
    private val repo: SkillRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillUiState())
    val uiState: StateFlow<SkillUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getAll().collect { list ->
                _uiState.update { it.copy(skills = list) }
            }
        }
    }

    /** 从文本新建/保存技能（id == 0 表示新增） */
    fun save(id: Long, name: String, description: String, content: String, source: String) {
        viewModelScope.launch {
            val n = name.trim().ifBlank { content.trim().lineSequence().firstOrNull { it.isNotBlank() }?.take(30) ?: "未命名技能" }
            if (id == 0L) {
                repo.insert(SkillEntity(name = n, description = description.trim(), content = content.trim(), source = source))
                _uiState.update { it.copy(info = "技能已添加") }
            } else {
                val old = _uiState.value.skills.find { it.id == id }
                if (old != null) {
                    repo.update(old.copy(name = n, description = description.trim(), content = content.trim()))
                    _uiState.update { it.copy(info = "技能已更新") }
                }
            }
        }
    }

    fun delete(skill: SkillEntity) {
        viewModelScope.launch {
            repo.delete(skill)
        }
    }

    /** 从 GitHub 抓取 SKILL.md，返回解析后的 (名称, 正文)；失败抛异常由 UI 展示 */
    suspend fun fetchFromGithub(url: String): Pair<String, String> {
        val content = repo.fetchFromGithub(url)
        val name = content.trim().lineSequence()
            .firstOrNull { it.startsWith("#") }
            ?.removePrefix("#")
            ?.trim()
            ?.take(40)
            ?: url.substringAfterLast('/').ifBlank { "GitHub Skill" }
        return name to content
    }

    fun clearInfo() {
        _uiState.update { it.copy(info = null, error = null) }
    }

    fun setError(msg: String) {
        _uiState.update { it.copy(error = msg) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = ChatApplication.instance
                return SkillViewModel(app.skillRepository) as T
            }
        }
    }
}
