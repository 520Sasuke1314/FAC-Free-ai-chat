package com.yourapp.chat.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourapp.chat.ChatApplication
import com.yourapp.chat.data.local.entity.ApiProfileEntity
import com.yourapp.chat.data.local.entity.CharacterCardEntity
import com.yourapp.chat.data.local.entity.ConversationEntity
import com.yourapp.chat.data.local.entity.MessageEntity
import com.yourapp.chat.data.local.entity.WorldBookEntity
import com.yourapp.chat.data.local.entity.WorldEntryEntity
import com.yourapp.chat.data.repository.ApiProfileRepository
import com.yourapp.chat.data.repository.ChatRepository
import com.yourapp.chat.data.repository.CharacterCardRepository
import com.yourapp.chat.data.repository.ConfigRepository
import com.yourapp.chat.data.repository.WorldEntryRepository
import com.yourapp.chat.data.local.AppDatabase
import com.yourapp.chat.data.remote.PhoneWebSearch
import com.yourapp.chat.data.remote.ShizukuHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversation: ConversationEntity? = null,
    val messages: List<MessageEntity> = emptyList(),
    val isSending: Boolean = false,
    val inputText: String = "",
    /** 待发送附件：图片 dataURL / 文本内容 */
    val attachments: List<Attachment> = emptyList(),
    /** 识图描述（仅本地显示，不发给聊天模型） */
    val visionDescription: String? = null,
    val error: String? = null,
    val info: String? = null,
    val availableCards: List<CharacterCardEntity> = emptyList(),
    val cardWorldEntries: List<WorldEntryEntity> = emptyList(),
    val worldBooks: List<WorldBookEntity> = emptyList(),
    val entriesByBook: Map<Long, List<WorldEntryEntity>> = emptyMap(),
    val apiProfiles: List<ApiProfileEntity> = emptyList(),
    val selectedProfileId: Long? = null,
    val thinkingEnabled: Boolean = true,
    /** 思考力度：-1=自动，0=不思考，1-5=力度（5 最大） */
    val thinkingLevel: Int = -1,
    val searchEnabled: Boolean = false,
    val searchProvider: String = "bing",
    val searchEngineUrl: String = "https://www.bing.com/search?q={query}",
    val showSearchPicker: Boolean = false,
    val showCardPicker: Boolean = false,
    val showWorldPicker: Boolean = false,
    val showApiPicker: Boolean = false,
    val showModelPicker: Boolean = false,
    val showCompressionModelPicker: Boolean = false,
    val showVisionModelPicker: Boolean = false,
    val showSettings: Boolean = false,
    val showBranchPicker: Boolean = false,
    val showRewritePicker: Boolean = false,
    val branchSelectedMessageId: Long? = null,
    val rewriteSelectedMessageId: Long? = null,
    /** 保存的文本能力模型（聊天/压缩可选） */
    val savedTextModels: List<com.yourapp.chat.data.local.entity.SavedModelEntity> = emptyList(),
    /** 保存的识图能力模型 */
    val savedVisionModels: List<com.yourapp.chat.data.local.entity.SavedModelEntity> = emptyList(),
    /** 识图专用模型名（空 = 未配置） */
    val visionModel: String = "",
    val compressKeepCount: Int = 32,
    /** 上下文压缩专用模型名（空 = 复用聊天模型） */
    val compressionModel: String = "",
    val injectionInterval: Int = 25,
    val streamingEnabled: Boolean = true,
    /** 正在流式生成中的 assistant 消息完整内容（内存态，驱动 UI 实时刷新） */
    val streamingContent: String? = null,
    /** 正在流式生成中的思考链内容（内存态，思考逐段实时刷新，与正文分开展示） */
    val streamingThinking: String? = null,
    /** 该对话是否使用过非官网 API（为 true 时禁止切换到官网免费，避免丢失记忆） */
    val conversationUsedApi: Boolean = false,
    /** 当前对话选中的世界书 ID 列表 */
    val selectedWorldBookIds: List<Long> = emptyList(),
    /** 世界书/角色卡注入提示 */
    val injectionNotice: String? = null,
    /** 官网同步版本号：每次成功同步 +1，UI 据此在同步完成后回到列表底部 */
    val syncVersion: Int = 0,
    /** 处理状态（如识图中、OCR中），用于在输入区显示进度 */
    val processingStatus: String? = null,
    /** 自建 API 联网搜索：是否正在浏览网页 */
    val webBrowsing: Boolean = false,
    /** 待触发 Shizuku 授权（联网搜索需要该权限，UI 监听到 true 时弹出授权窗口） */
    val shizukuGrantRequested: Boolean = false
)

/** 待发送附件：图片存 dataURL，文本直接存内容 */
data class Attachment(
    val name: String,
    val mimeType: String,
    val isImage: Boolean,
    val content: String
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val characterCardRepository: CharacterCardRepository,
    private val worldEntryRepository: WorldEntryRepository,
    private val apiProfileRepository: ApiProfileRepository,
    private val savedModelRepository: com.yourapp.chat.data.repository.SavedModelRepository,
    private val configRepository: ConfigRepository,
    private val db: AppDatabase,
    private val conversationId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** 当前正在执行的生成任务（发送/重新生成/编辑），用于"中止"时取消 */
    private var sendJob: Job? = null

    init {
        viewModelScope.launch {
            val conv = chatRepository.getConversation(conversationId)
            _uiState.update {
                it.copy(
                    conversation = conv,
                    streamingEnabled = configRepository.isStreamingEnabled(),
                    conversationUsedApi = configRepository.isConversationUsedApi(conversationId),
                    thinkingLevel = conv?.thinkingLevel ?: -1,
                    thinkingEnabled = (conv?.thinkingLevel ?: -1) != 0,
                    injectionInterval = conv?.injectionInterval ?: 25,
                    compressionModel = conv?.compressionModel ?: "",
                    visionModel = conv?.visionModel ?: "",
                    selectedWorldBookIds = conv?.getSelectedWorldBookIds() ?: emptyList()
                )
            }
            conv?.characterCardId?.let { loadCardWorldEntriesOnce(it) }
        }
        viewModelScope.launch {
            chatRepository.getActiveMessages(conversationId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
        viewModelScope.launch {
            characterCardRepository.getAllCards().collect { cards ->
                _uiState.update { it.copy(availableCards = cards) }
            }
        }
        viewModelScope.launch {
            worldEntryRepository.getBooks().collect { books ->
                _uiState.update { it.copy(worldBooks = books) }
            }
        }
        viewModelScope.launch {
            worldEntryRepository.getManualGlobal().collect { entries ->
                _uiState.update { it.copy(entriesByBook = it.entriesByBook + (-1L to entries)) }
            }
        }
        viewModelScope.launch {
            apiProfileRepository.getAll().collect { profiles ->
                val current = _uiState.value.selectedProfileId
                val conv = _uiState.value.conversation
                // 优先恢复该对话上次使用的 API；否则用全局默认 API（只对未指定的新对话生效）
                val selected = when {
                    current != null && profiles.any { it.id == current } -> current
                    conv?.lastUsedProfileId != null && profiles.any { it.id == conv.lastUsedProfileId } ->
                        conv.lastUsedProfileId
                    else -> profiles.firstOrNull { it.isDefault }?.id ?: profiles.firstOrNull()?.id
                }
                _uiState.update { it.copy(apiProfiles = profiles, selectedProfileId = selected) }
            }
        }
        viewModelScope.launch {
            savedModelRepository.getTextModels().collect { list ->
                _uiState.update { it.copy(savedTextModels = list) }
            }
        }
        viewModelScope.launch {
            savedModelRepository.getVisionModels().collect { list ->
                _uiState.update { it.copy(savedVisionModels = list) }
            }
        }
    }

    /** 加载某世界书集合的条目（选择器展开时） */
    fun loadBookEntries(bookId: Long) {
        viewModelScope.launch {
            worldEntryRepository.getEntriesByBook(bookId).collect { entries ->
                _uiState.update { it.copy(entriesByBook = it.entriesByBook + (bookId to entries)) }
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun addAttachment(attachment: Attachment) {
        _uiState.update { it.copy(attachments = it.attachments + attachment) }
    }

    fun removeAttachment(name: String) {
        _uiState.update { it.copy(attachments = it.attachments.filterNot { a -> a.name == name }) }
    }

    fun sendMessage() {
        val state0 = _uiState.value
        val text = state0.inputText.trim()
        val attachments = state0.attachments
        if (text.isEmpty() && attachments.isEmpty() || state0.isSending) return
        val profile = state0.apiProfiles.firstOrNull { it.id == state0.selectedProfileId }
        if (profile == null) {
            _uiState.update { it.copy(error = "请先在「API 配置」中添加 API") }
            return
        }
        _uiState.update { it.copy(inputText = "", isSending = true, error = null, streamingContent = null, streamingThinking = null, injectionNotice = null) }
        // 思考力度：非官网用滑块（0=不思考；自动/1-5=思考，自动交给模型决定力度）；官网免费用开关
        val thinking = resolveThinking()
        val search = _uiState.value.searchEnabled
        val provider = _uiState.value.searchProvider
        val isWebChannel = profile.provider == "deepseek_web"
        // 自建 API 联网搜索需要 Shizuku 授权：未授权则弹出授权窗口并中止本次发送
        if (search && !isWebChannel && !ShizukuHelper.isGranted()) {
            _uiState.update {
                it.copy(isSending = false, shizukuGrantRequested = true, error = null, info = "联网搜索需要 Shizuku 授权，请在弹出的窗口中点击「允许」后再发送")
            }
            return
        }
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            try {
                // 自建 API 联网搜索：用手机网络浏览网页，注入结果给 AI
                var webSearchPrompt: String? = null
                var webSourcesJson: String? = null
                if (search && !isWebChannel) {
                    _uiState.update { it.copy(webBrowsing = true) }
                    try {
                        val results = PhoneWebSearch.search(text, limit = 5)
                        if (results.isEmpty()) {
                            _uiState.update { it.copy(info = "没有搜索到相关网页，将直接回答") }
                        } else {
                            webSearchPrompt = PhoneWebSearch.buildPrompt(results)
                            webSourcesJson = PhoneWebSearch.buildSourcesJson(results)
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(info = "联网搜索失败：${e.message}") }
                    } finally {
                        _uiState.update { it.copy(webBrowsing = false) }
                    }
                }
                val isWebChannel = profile.provider == "deepseek_web"
                // 官网免费通道不支持图片附件：剔除图片并提示，正文与文本附件照常发送
                val sendableAttachments = if (isWebChannel) attachments.filter { !it.isImage } else attachments
                val textAttachments = sendableAttachments.filter { !it.isImage }
                val imageAttachments = sendableAttachments.filter { it.isImage }
                if (isWebChannel && attachments.any { it.isImage }) {
                    _uiState.update { it.copy(info = "官网免费通道不支持图片附件，已忽略图片") }
                }
                // 文本附件内容不再拼进消息正文（否则用户消息会显示一大段文件文字）：
                // 改为经 system 注入提供给 AI，正文只保留用户输入的文字。
                // 单文件截断 2 万字符、总注入上限 8 万字符，防止超大文件撑爆官网 prompt 长度限制。
                val attachmentText = textAttachments.joinToString("\n\n") {
                    "【${it.name}】\n${it.content.take(20000)}"
                }.take(80000).takeIf { it.isNotBlank() }
                // 附件元数据：存进用户消息（图片存 dataURL 用于气泡缩略图，文本文件只存名字/类型）
                val attachedJson = org.json.JSONArray().apply {
                    attachments.forEach { a ->
                        val o = org.json.JSONObject()
                            .put("name", a.name)
                            .put("mime", a.mimeType)
                            .put("isImage", a.isImage)
                        if (a.isImage) o.put("dataUrl", a.content)
                        put(o)
                    }
                }.toString().takeIf { it.isNotBlank() }
                var finalText = text
                // 图片附件：若配置了识图模型，先让识图模型描述，描述仅在本地显示给用户，不发给聊天模型；否则原生多模态发送
                var imageDataUrls: List<String> = emptyList()
                var visionDescription = ""
                if (imageAttachments.isNotEmpty()) {
                    val visionModelName = _uiState.value.visionModel
                    if (visionModelName.isNotBlank() && profile.provider != "deepseek_web") {
                        try {
                            _uiState.update { it.copy(processingStatus = "正在识别图片…") }
                            val descriptions = imageAttachments.map { a ->
                                chatRepository.describeImage(profile, visionModelName, a.content)
                            }
                            visionDescription = descriptions.joinToString("\n\n")
                        } catch (e: Exception) {
                            // 识图失败：回退为原生多模态发送，避免整条消息失败（rikkahub 思路）
                            imageDataUrls = imageAttachments.map { it.content }
                        } finally {
                            _uiState.update { it.copy(processingStatus = null) }
                        }
                    } else {
                        imageDataUrls = imageAttachments.map { it.content }
                    }
                }
                chatRepository.sendMessageStream(
                    conversationId = conversationId,
                    userContent = finalText,
                    onToken = { },
                    onComplete = { },
                    onError = { e -> _uiState.update { it.copy(error = e.message, isSending = false, streamingContent = null, streamingThinking = null) } },
                    onContent = { content -> _uiState.update { it.copy(streamingContent = content) } },
                    onThinking = { t -> _uiState.update { it.copy(streamingThinking = t) } },
                    onInjectionNotice = { notice -> _uiState.update { it.copy(injectionNotice = notice) } },
                    profile = profile,
                    thinkingEnabled = thinking,
                    searchEnabled = search,
                    searchProvider = provider,
                    webSearchPrompt = webSearchPrompt,
                    webSourcesJson = webSourcesJson,
                    imageDataUrls = imageDataUrls,
                    visionContext = visionDescription.takeIf { it.isNotBlank() },
                    attachmentText = attachmentText,
                    attachmentsJson = attachedJson
                )
                _uiState.update {
                    it.copy(
                        isSending = false,
                        attachments = emptyList(),
                        injectionNotice = null,
                        processingStatus = null,
                        visionDescription = null,
                        // 本次使用了非官网 API → 立刻锁定官网免费切换
                        conversationUsedApi = it.conversationUsedApi || profile.provider != "deepseek_web"
                    )
                }
                persistLastUsedProfile()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户点击"中止"取消生成：保留已流式内容，写入数据库占位
                persistPartialToDb()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isSending = false, streamingContent = null, streamingThinking = null, attachments = emptyList(), processingStatus = null, visionDescription = null) }
            }
        }
    }

    /** 中止当前生成：取消网络流并保留已生成内容 */
    fun stopSending() {
        sendJob?.cancel()
        sendJob = null
        _uiState.update { it.copy(isSending = false, streamingContent = null, streamingThinking = null) }
    }

    /** 中止时把已流式内容写入数据库（保留思考与正文） */
    private suspend fun persistPartialToDb() {
        try {
            chatRepository.persistPartialAssistantContent(
                conversationId,
                _uiState.value.streamingThinking,
                _uiState.value.streamingContent
            )
        } catch (_: Exception) {
        } finally {
            _uiState.update { it.copy(isSending = false, streamingContent = null, streamingThinking = null) }
        }
    }

    /** 回溯：从该消息处开新分支 */
    fun branchAt(messageId: Long) {
        viewModelScope.launch {
            chatRepository.branchAtMessage(conversationId, messageId)
            _uiState.update { it.copy(info = "已回溯到该消息，从此处开始新分支") }
        }
    }

    /** 改写：删除该消息及其后续（官网通道时同步删除官网消息） */
    fun rewrite(messageId: Long) {
        viewModelScope.launch {
            val officialDeleted = chatRepository.rewriteMessage(conversationId, messageId)
            _uiState.update {
                it.copy(info = if (officialDeleted == false) {
                    "已删除该消息及其后续（官网未匹配到对应消息，未同步删除）"
                } else {
                    "已删除该消息及其后续"
                })
            }
        }
    }

    /** 删除单条消息（用户或 AI） */
    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            chatRepository.deleteMessage(conversationId, messageId)
            _uiState.update { it.copy(info = "已删除消息") }
        }
    }

    /** 重新生成：长按 AI 消息 → 官网重新生成该回复 */
    fun regenerate(messageId: Long) {
        val profile = _uiState.value.apiProfiles.firstOrNull { it.id == _uiState.value.selectedProfileId }
        if (profile == null) {
            _uiState.update { it.copy(error = "请先在「API 配置」中添加 API") }
            return
        }
        _uiState.update { it.copy(isSending = true, error = null, streamingContent = null, streamingThinking = null, injectionNotice = null) }
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            try {
                chatRepository.regenerateMessage(
                    conversationId = conversationId,
                    messageId = messageId,
                    onToken = { },
                    onComplete = { },
                    onError = { e -> _uiState.update { it.copy(error = e.message, isSending = false, streamingContent = null, streamingThinking = null) } },
                    onContent = { content -> _uiState.update { it.copy(streamingContent = content) } },
                    onThinking = { t -> _uiState.update { it.copy(streamingThinking = t) } },
                    onInjectionNotice = { notice -> _uiState.update { it.copy(injectionNotice = notice) } },
                    profile = profile,
                    thinkingEnabled = resolveThinking(),
                    searchEnabled = _uiState.value.searchEnabled,
                    searchProvider = _uiState.value.searchProvider
                )
                _uiState.update {
                    it.copy(
                        isSending = false,
                        info = "已重新生成",
                        injectionNotice = null,
                        conversationUsedApi = it.conversationUsedApi || profile.provider != "deepseek_web"
                    )
                }
                persistLastUsedProfile()
            } catch (e: kotlinx.coroutines.CancellationException) {
                persistPartialToDb()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isSending = false, streamingContent = null, streamingThinking = null) }
            }
        }
    }

    /** 编辑：长按用户消息 → 修改文本，AI 按编辑后的文本重新回答 */
    fun editUserMessage(messageId: Long, newContent: String) {
        val profile = _uiState.value.apiProfiles.firstOrNull { it.id == _uiState.value.selectedProfileId }
        if (profile == null) {
            _uiState.update { it.copy(error = "请先在「API 配置」中添加 API") }
            return
        }
        _uiState.update { it.copy(isSending = true, error = null, streamingContent = null, streamingThinking = null, injectionNotice = null) }
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            try {
                chatRepository.editUserMessage(
                    conversationId = conversationId,
                    messageId = messageId,
                    newContent = newContent,
                    onToken = { },
                    onComplete = { },
                    onError = { e -> _uiState.update { it.copy(error = e.message, isSending = false, streamingContent = null, streamingThinking = null) } },
                    onContent = { content -> _uiState.update { it.copy(streamingContent = content) } },
                    onThinking = { t -> _uiState.update { it.copy(streamingThinking = t) } },
                    onInjectionNotice = { notice -> _uiState.update { it.copy(injectionNotice = notice) } },
                    profile = profile,
                    thinkingEnabled = resolveThinking(),
                    searchEnabled = _uiState.value.searchEnabled,
                    searchProvider = _uiState.value.searchProvider
                )
                _uiState.update {
                    it.copy(
                        isSending = false,
                        info = "已编辑并重新生成",
                        injectionNotice = null,
                        conversationUsedApi = it.conversationUsedApi || profile.provider != "deepseek_web"
                    )
                }
                persistLastUsedProfile()
            } catch (e: kotlinx.coroutines.CancellationException) {
                persistPartialToDb()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isSending = false, streamingContent = null, streamingThinking = null) }
            }
        }
    }

    /** 压缩上下文 */
    fun compressContext() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            try {
                val profile = _uiState.value.apiProfiles.firstOrNull { it.id == _uiState.value.selectedProfileId }
                chatRepository.compressContext(conversationId, profile, _uiState.value.compressKeepCount)
                _uiState.update { it.copy(isSending = false, info = "上下文已压缩") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, error = e.message) }
            }
        }
    }

    fun toggleCardPicker() {
        _uiState.update { it.copy(showCardPicker = !it.showCardPicker, showWorldPicker = false, showApiPicker = false, showSearchPicker = false, showSettings = false) }
    }

    fun toggleWorldPicker() {
        _uiState.update { it.copy(showWorldPicker = !it.showWorldPicker, showCardPicker = false, showApiPicker = false, showSearchPicker = false, showSettings = false) }
    }

    fun toggleApiPicker() {
        _uiState.update { it.copy(showApiPicker = !it.showApiPicker, showCardPicker = false, showWorldPicker = false, showSearchPicker = false, showSettings = false) }
    }

    fun selectApi(profileId: Long) {
        // 该对话已使用过非官网 API：禁止切换到官网免费（会丢失对话记忆）。
        // 直接读取持久化标志（而非内存缓存），确保"先官网后 API"也能被正确拦截。
        val target = _uiState.value.apiProfiles.firstOrNull { it.id == profileId }
        if (target?.provider == "deepseek_web" && configRepository.isConversationUsedApi(conversationId)) {
            _uiState.update {
                it.copy(
                    showApiPicker = false,
                    info = "本对话已使用过 API，切换到官网免费会丢失对话记忆，已禁止切换"
                )
            }
            return
        }
        _uiState.update { it.copy(selectedProfileId = profileId, showApiPicker = false) }
        // 记住该对话上次使用的 API，下次进入时恢复
        viewModelScope.launch {
            chatRepository.setLastUsedProfile(conversationId, profileId)
            refreshConversation()
        }
    }

    /** 切换模型选择器（对话底部 API 栏长按触发） */
    fun toggleModelPicker() {
        _uiState.update { it.copy(showModelPicker = !it.showModelPicker) }
    }

    /** 切换上下文压缩模型选择器 */
    fun toggleCompressionModelPicker() {
        _uiState.update { it.copy(showCompressionModelPicker = !it.showCompressionModelPicker) }
    }

    /** 切换识图模型选择器 */
    fun toggleVisionModelPicker() {
        _uiState.update { it.copy(showVisionModelPicker = !it.showVisionModelPicker) }
    }

    /**
     * 切换聊天模型。
     * 模型按「保存的模型」绑定各自的 API 配置：选中的模型属于哪个配置，
     * 就切到哪个配置（并记住为该对话的上次使用 API）。
     * 关键词：从官网免费选中 API 模型后，对话会切到该 API 配置，
     * 从而解锁思考力度 / 压缩模型 / 识图模型 / 生成参数等设置项（isWeb 随之变化）。
     */
    fun selectModel(model: String) {
        if (model.isBlank()) return
        viewModelScope.launch {
            try {
                val s = _uiState.value
                // 1) 优先按「保存的模型」找拥有该模型的配置；找不到则留在当前配置（手动输入模型名场景）
                val owner = s.savedTextModels.firstOrNull { it.model == model }?.apiProfileId
                    ?.let { pid -> s.apiProfiles.firstOrNull { it.id == pid } }
                val target = owner ?: s.apiProfiles.firstOrNull { it.id == s.selectedProfileId } ?: return@launch
                // 2) 选中的是官网免费但本对话已用过 API：禁止切回（与 selectApi 同规则，避免丢记忆）
                if (target.provider == "deepseek_web" && configRepository.isConversationUsedApi(conversationId)) {
                    _uiState.update {
                        it.copy(
                            showModelPicker = false,
                            info = "本对话已使用过 API，切回官网免费会丢失对话记忆，已禁止切换"
                        )
                    }
                    return@launch
                }
                // 3) 保存模型到该配置、切换选中配置并记住
                if (target.id != s.selectedProfileId) {
                    chatRepository.setLastUsedProfile(conversationId, target.id)
                }
                apiProfileRepository.save(target.copy(model = model), false)
                _uiState.update {
                    it.copy(
                        selectedProfileId = target.id,
                        showModelPicker = false,
                        info = "已切换模型：$model"
                    )
                }
                refreshConversation()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "切换模型失败: ${e.message}") }
            }
        }
    }

    fun setThinking(enabled: Boolean) {
        // 深度思考总开关：关闭 → 力度 0（禁止思考）；开启 → 若当前力度为 0 则回默认自动
        if (enabled) {
            if (_uiState.value.thinkingLevel == 0) setThinkingLevel(-1) else {
                _uiState.update { it.copy(thinkingEnabled = true) }
            }
        } else {
            setThinkingLevel(0)
        }
    }

    /** 解析本次请求是否启用思考：深度思考总开关关闭则一律禁止；开启后按力度（0=不思考，-1 自动/1-3 指定力度） */
    private fun resolveThinking(): Boolean {
        return _uiState.value.thinkingLevel != 0
    }    /** 保存上下文压缩专用模型（空 = 复用聊天模型）并持久化 */
    fun setCompressionModel(model: String) {
        val m = model.trim()
        _uiState.update { it.copy(compressionModel = m) }
        viewModelScope.launch {
            val conv = chatRepository.getConversation(conversationId) ?: return@launch
            db.conversationDao().update(conv.copy(compressionModel = m))
            refreshConversation()
            _uiState.update { it.copy(showCompressionModelPicker = false) }
        }
    }

    /** 设置识图专用模型（空 = 未配置） */
    fun setVisionModel(model: String) {
        val m = model.trim()
        _uiState.update { it.copy(visionModel = m) }
        viewModelScope.launch {
            val conv = chatRepository.getConversation(conversationId) ?: return@launch
            db.conversationDao().update(conv.copy(visionModel = m))
            refreshConversation()
            _uiState.update { it.copy(showVisionModelPicker = false) }
        }
    }

    /** 设置思考力度（-1 自动 / 0 不思考 / 1-3 力度）并持久化到当前对话 */
    fun setThinkingLevel(level: Int) {
        val l = level.coerceIn(-1, 5)
        _uiState.update { it.copy(thinkingLevel = l, thinkingEnabled = l != 0) }
        viewModelScope.launch {
            val conv = chatRepository.getConversation(conversationId) ?: return@launch
            db.conversationDao().update(conv.copy(thinkingLevel = l))
            refreshConversation()
        }
    }

    fun setSearch(enabled: Boolean) {
        _uiState.update { it.copy(searchEnabled = enabled) }
        // 打开自建 API 联网搜索时立即申请 Shizuku 权限（未授予则弹窗）
        if (enabled) {
            val isWeb = _uiState.value.apiProfiles.firstOrNull { it.id == _uiState.value.selectedProfileId }?.provider == "deepseek_web"
            if (!isWeb && !ShizukuHelper.isGranted()) {
                _uiState.update { it.copy(shizukuGrantRequested = true, info = "联网搜索需要 Shizuku 授权，请在弹出的窗口中点击「允许」") }
            }
        }
    }

    /** 仅供 UI 触发 Shizuku 授权窗口 */
    fun requestShizuku() {
        _uiState.update { it.copy(shizukuGrantRequested = true) }
    }

    /** Shizuku 授权结果回调（UI 调用） */
    fun onShizukuResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                shizukuGrantRequested = false,
                info = if (granted) "已获得 Shizuku 授权，联网搜索可用" else "未获得 Shizuku 授权，联网搜索不可用"
            )
        }
    }

    /** 是否已具备联网搜索条件（官网通道总是可用；自建 API 需 Shizuku 已授权） */
    fun canSearchNow(): Boolean {
        if (_uiState.value.searchEnabled) {
            val isWeb = _uiState.value.apiProfiles.firstOrNull { it.id == _uiState.value.selectedProfileId }?.provider == "deepseek_web"
            return isWeb || ShizukuHelper.isGranted()
        }
        return false
    }

    fun setStreaming(enabled: Boolean) {
        configRepository.setStreamingEnabled(enabled)
        _uiState.update { it.copy(streamingEnabled = enabled) }
    }

    fun toggleSearchPicker() {
        // 注意：不能重置 showSettings=false，否则从设置页打开搜索引擎选择器时会退回到聊天页。
        _uiState.update { it.copy(showSearchPicker = !it.showSearchPicker, showCardPicker = false, showWorldPicker = false, showApiPicker = false) }
    }

    fun toggleSettings() {
        _uiState.update { it.copy(showSettings = !it.showSettings, showCardPicker = false, showWorldPicker = false, showApiPicker = false, showSearchPicker = false) }
    }

    fun toggleBranchPicker() {
        val userMessages = _uiState.value.messages.filter { it.role == "user" }
        val selectedId = userMessages.lastOrNull()?.id
        _uiState.update { it.copy(
            showBranchPicker = !it.showBranchPicker,
            branchSelectedMessageId = selectedId
        ) }
    }

    fun toggleRewritePicker() {
        val allMessages = _uiState.value.messages.filter { it.role != "system" }
        val selectedId = allMessages.lastOrNull()?.id
        _uiState.update { it.copy(
            showRewritePicker = !it.showRewritePicker,
            rewriteSelectedMessageId = selectedId
        ) }
    }

    /** 离开对话页时复位所有整页覆盖状态（设置页/选择器），避免下次进入仍停留 */
    fun resetOverlay() {
        _uiState.update {
            it.copy(
                showSettings = false,
                showCardPicker = false,
                showWorldPicker = false,
                showApiPicker = false,
                showModelPicker = false,
                showCompressionModelPicker = false,
                showVisionModelPicker = false,
                showSearchPicker = false,
                showBranchPicker = false,
                showRewritePicker = false
            )
        }
    }

    fun selectSearchProvider(provider: String, engineUrl: String) {
        _uiState.update { it.copy(searchProvider = provider, searchEngineUrl = engineUrl, showSearchPicker = false) }
    }

    /** 从官网拉取当前会话消息，写入本地对话（反向同步），并切换到官网会话 */
    fun syncOfficialMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            try {
                val app = ChatApplication.instance
                // 官网模式：没聊过第一句话（本地无用户消息）的对话不允许同步，
                // 避免把其他对话/官网历史串到新开的对话里
                if (!chatRepository.hasUserMessage(conversationId)) {
                    _uiState.update { it.copy(isSending = false, info = "请先发送第一条消息，再同步官网记录") }
                    return@launch
                }
                val msgs = app.deepSeekWebRepository.syncOfficialSession(conversationId, _uiState.value.conversation?.title)
                if (msgs.isEmpty()) {
                    _uiState.update { it.copy(isSending = false, info = "官网没有可同步的消息（或接口暂不兼容）") }
                    return@launch
                }
                // 持久化到本地数据库（保留 system 提示），DB flow 会自动刷新界面；
                // 同时 syncOfficialSession 已把官网 session/parent 切到该会话，
                // 之后发出的下一条消息会正确接在官网对话末尾。
                chatRepository.replaceConversationWithSync(conversationId, msgs)
                refreshConversation()
                _uiState.update { it.copy(isSending = false, info = "已同步 ${msgs.size} 条官网消息", syncVersion = it.syncVersion + 1) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, error = "同步失败: ${e.message}") }
            }
        }
    }

    /** 切换某条世界书的启用状态（对话内选择器右侧按钮） */
    fun toggleWorldEntry(entry: WorldEntryEntity) {
        viewModelScope.launch {
            worldEntryRepository.update(entry.copy(enabled = !entry.enabled))
        }
    }

    /** 切换本对话的 角色卡世界书注入 开关 */
    fun setUseCardWorld(enabled: Boolean) {
        viewModelScope.launch {
            chatRepository.setWorldToggles(conversationId, enabled, _uiState.value.conversation?.useGlobalWorld ?: true)
            refreshConversation()
        }
    }

    /** 切换本对话的 全局世界书注入 开关 */
    fun setUseGlobalWorld(enabled: Boolean) {
        viewModelScope.launch {
            chatRepository.setWorldToggles(conversationId, _uiState.value.conversation?.useCardWorld ?: true, enabled)
            refreshConversation()
        }
    }

    /** 切换本对话的 角色卡设定注入 开关 */
    fun setUseCharacterCard(enabled: Boolean) {
        viewModelScope.launch {
            val conv = chatRepository.getConversation(conversationId) ?: return@launch
            chatRepository.updateConversationSettings(
                conversationId,
                conv.showThinking,
                conv.maxOutputTokens,
                conv.maxContextMessages,
                conv.temperature,
                conv.topK,
                conv.topP,
                conv.userGreeting,
                conv.aiGreeting
            )
            // 直接更新 useCharacterCard 字段
            db.conversationDao().update(conv.copy(useCharacterCard = enabled))
            refreshConversation()
        }
    }

    private suspend fun refreshConversation() {
        _uiState.update { it.copy(conversation = chatRepository.getConversation(conversationId)) }
    }

    /** 记录本次实际使用的 API 为该对话的上次使用 API（下次进入时恢复） */
    private suspend fun persistLastUsedProfile() {
        chatRepository.setLastUsedProfile(conversationId, _uiState.value.selectedProfileId)
    }

    /** 切换某个世界书在当前对话中的选中状态 */
    fun toggleSelectedWorldBook(bookId: Long) {
        val current = _uiState.value.selectedWorldBookIds
        val updated = if (current.contains(bookId)) current - bookId else current + bookId
        _uiState.update { it.copy(selectedWorldBookIds = updated) }
        viewModelScope.launch {
            chatRepository.setSelectedWorldBooks(conversationId, updated)
        }
    }

    fun setCompressKeepCount(count: Int) {
        _uiState.update { it.copy(compressKeepCount = count) }
    }

    fun setInjectionInterval(interval: Int) {
        val v = interval.coerceIn(0, 100)
        _uiState.update { it.copy(injectionInterval = v) }
        viewModelScope.launch {
            val conv = chatRepository.getConversation(conversationId) ?: return@launch
            db.conversationDao().update(conv.copy(injectionInterval = v))
            refreshConversation()
        }
    }

    /** 更新对话参数设置 */
    fun updateConversationSettings(
        showThinking: Boolean,
        maxOutputTokens: Int,
        maxContextMessages: Int,
        temperature: Double,
        topK: Int,
        topP: Double,
        userGreeting: String,
        aiGreeting: String
    ) {
        viewModelScope.launch {
            val conv = chatRepository.getConversation(conversationId) ?: return@launch
            chatRepository.updateConversationSettings(
                conversationId,
                showThinking,
                maxOutputTokens,
                maxContextMessages,
                temperature,
                topK,
                topP,
                userGreeting,
                aiGreeting
            )
            refreshConversation()
            _uiState.update { it.copy(info = "设置已保存") }
        }
    }

    fun applyCard(cardId: Long) {
        viewModelScope.launch {
            try {
                chatRepository.applyCharacterCardToConversation(conversationId, cardId)
                refreshConversation()
                _uiState.update { it.copy(showCardPicker = false, info = "已应用角色卡") }
                loadCardWorldEntriesOnce(cardId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /** 一次性加载某角色卡的世界书条目（选择器展示） */
    private suspend fun loadCardWorldEntriesOnce(cardId: Long) {
        val entries = worldEntryRepository.getByCardId(cardId).first()
        _uiState.update { it.copy(cardWorldEntries = entries) }
    }

    fun clearInfo() {
        _uiState.update { it.copy(info = null) }
    }

    /** 收藏/取消收藏某条消息 */
    fun toggleFavorite(messageId: Long, favorite: Boolean) {
        viewModelScope.launch {
            chatRepository.toggleFavorite(messageId, favorite)
            _uiState.update {
                it.copy(info = if (favorite) "已收藏" else "已取消收藏")
            }
        }
    }

    companion object {
        fun factory(conversationId: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = ChatApplication.instance
                return ChatViewModel(
                    app.chatRepository,
                    app.characterCardRepository,
                    app.worldEntryRepository,
                    app.apiProfileRepository,
                    app.savedModelRepository,
                    app.configRepository,
                    app.database,
                    conversationId
                ) as T
            }
        }
    }
}
