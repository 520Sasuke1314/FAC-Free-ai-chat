package com.yourapp.chat.ui.screen.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourapp.chat.ChatApplication
import com.yourapp.chat.data.local.entity.ApiProfileEntity
import com.yourapp.chat.data.local.entity.SavedModelEntity
import com.yourapp.chat.data.remote.ApiPresets
import com.yourapp.chat.data.repository.ApiProfileRepository
import com.yourapp.chat.data.repository.SavedModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApiConfigUiState(
    val profiles: List<ApiProfileEntity> = emptyList(),
    /** 编辑中的配置（null = 未编辑） */
    val editing: ApiProfileEntity? = null,
    val showEditor: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    /** 官网免费登录对话框 */
    val showWebLogin: Boolean = false,
    val webLoginSending: Boolean = false,
    /** 当前子页：false = 添加 API，true = 保存的模型管理 */
    val showSavedModels: Boolean = false,
    /** 保存的模型（按配置分组） */
    val savedModels: List<SavedModelEntity> = emptyList(),
    /** 正在新增/编辑的保存模型（null = 未编辑） */
    val editingModel: SavedModelEntity? = null,
    val showModelEditor: Boolean = false,
    /** 新增模型时所属的 API 配置 ID */
    val modelProfileId: Long = 0
)

class ApiConfigViewModel(
    private val repository: ApiProfileRepository,
    private val savedModelRepository: SavedModelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiConfigUiState())
    val uiState: StateFlow<ApiConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAll().collect { profiles ->
                _uiState.update { it.copy(profiles = profiles) }
            }
        }
        viewModelScope.launch {
            savedModelRepository.getAll().collect { models ->
                _uiState.update { it.copy(savedModels = models) }
            }
        }
    }

    /** 切换到保存的模型管理子页 */
    fun openSavedModels() {
        _uiState.update { it.copy(showSavedModels = true) }
    }

    fun backToAddApi() {
        _uiState.update { it.copy(showSavedModels = false) }
    }

    /** 新增保存模型：指定所属配置 */
    fun openAddModel(profileId: Long) {
        _uiState.update {
            it.copy(
                modelProfileId = profileId,
                editingModel = SavedModelEntity(apiProfileId = profileId, model = ""),
                showModelEditor = true
            )
        }
    }

    /** 编辑保存模型 */
    fun openEditModel(model: SavedModelEntity) {
        _uiState.update { it.copy(modelProfileId = model.apiProfileId, editingModel = model, showModelEditor = true) }
    }

    fun dismissModelEditor() {
        _uiState.update { it.copy(showModelEditor = false, editingModel = null) }
    }

    /** 保存模型（新增或更新） */
    fun saveModel(model: SavedModelEntity) {
        if (model.model.isBlank()) {
            _uiState.update { it.copy(message = "模型名不能为空", isError = true) }
            return
        }
        viewModelScope.launch {
            if (model.id == 0L) {
                savedModelRepository.save(model)
            } else {
                savedModelRepository.update(model)
            }
            _uiState.update { it.copy(showModelEditor = false, editingModel = null, message = "已保存模型", isError = false) }
        }
    }

    fun deleteModel(model: SavedModelEntity) {
        viewModelScope.launch {
            savedModelRepository.delete(model)
        }
    }

    /** 新建：选择预设后自动填充 URL/模型 */
    fun openNew(preset: ApiPresets.Preset) {
        _uiState.update {
            it.copy(
                editing = ApiProfileEntity(
                    provider = preset.provider,
                    name = preset.name,
                    baseUrl = preset.baseUrl,
                    model = preset.model,
                    // Claude 预设默认用 Anthropic 原生协议
                    protocol = if (preset.provider == "claude") "anthropic" else "openai"
                ),
                showEditor = true
            )
        }
    }

    fun openEdit(profile: ApiProfileEntity) {
        _uiState.update { it.copy(editing = profile, showEditor = true) }
    }

    fun dismissEditor() {
        _uiState.update { it.copy(showEditor = false, editing = null) }
    }

    fun save(profile: ApiProfileEntity, makeDefault: Boolean) {
        viewModelScope.launch {
            try {
                val id = repository.save(profile, makeDefault)
                // 若该配置声明了模型，自动生成一个「文本」能力的保存模型（若尚无同名的）
                if (profile.model.isNotBlank() && profile.provider != "deepseek_web") {
                    val profileModels = savedModelRepository.getAllOnce().filter { it.apiProfileId == id }
                    val exists = profileModels.any { it.model == profile.model }
                    if (!exists) {
                        savedModelRepository.save(
                            com.yourapp.chat.data.local.entity.SavedModelEntity(
                                apiProfileId = id,
                                model = profile.model.trim(),
                                canText = true,
                                canVision = false
                            )
                        )
                    }
                }
                _uiState.update { it.copy(showEditor = false, editing = null, message = "已保存", isError = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message ?: "保存失败", isError = true) }
            }
        }
    }

    fun setDefault(profile: ApiProfileEntity) {
        viewModelScope.launch {
            repository.setDefault(profile.id)
            _uiState.update { it.copy(message = "已设为默认", isError = false) }
        }
    }

    fun delete(profile: ApiProfileEntity) {
        viewModelScope.launch {
            repository.delete(profile.id)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** 打开官网免费登录对话框 */
    fun openWebLogin() {
        _uiState.update { it.copy(showWebLogin = true, webLoginSending = false) }
    }

    fun dismissWebLogin() {
        _uiState.update { it.copy(showWebLogin = false, webLoginSending = false) }
    }

    /** 官网账号登录：成功则自动创建/更新 deepseek_web profile */
    fun webLogin(email: String, password: String) {
        if (_uiState.value.webLoginSending) return
        _uiState.update { it.copy(webLoginSending = true) }
        viewModelScope.launch {
            try {
                val app = ChatApplication.instance
                app.deepSeekWebRepository.login(email, password)
                val existing = repository.getAllOnce().firstOrNull { it.provider == "deepseek_web" }
                if (existing != null) {
                    repository.save(existing.copy(name = "DeepSeek 官网免费"), false)
                } else {
                    repository.save(
                        ApiProfileEntity(
                            provider = "deepseek_web",
                            name = "DeepSeek 官网免费",
                            baseUrl = "",
                            apiKey = "",
                            model = "",
                            isDefault = existing == null && repository.getAllOnce().isEmpty()
                        ),
                        false
                    )
                }
                _uiState.update {
                    it.copy(showWebLogin = false, webLoginSending = false, message = "官网登录成功，可在对话中选用「DeepSeek 官网免费」", isError = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(webLoginSending = false, message = "官网登录失败: ${e.message}", isError = true) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = ChatApplication.instance
                return ApiConfigViewModel(app.apiProfileRepository, app.savedModelRepository) as T
            }
        }
    }
}
