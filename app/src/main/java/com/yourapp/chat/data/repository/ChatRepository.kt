package com.yourapp.chat.data.repository

import android.util.Log
import com.yourapp.chat.data.local.AppDatabase
import com.yourapp.chat.data.local.entity.ApiProfileEntity
import com.yourapp.chat.data.local.entity.CharacterCardEntity
import com.yourapp.chat.data.local.entity.ConversationEntity
import com.yourapp.chat.data.local.entity.MessageEntity
import com.yourapp.chat.data.remote.ApiService
import com.yourapp.chat.data.remote.DeepSeekWebClient
import com.yourapp.chat.data.remote.AnthropicClient
import com.yourapp.chat.data.remote.SseClient
import com.yourapp.chat.data.remote.PhoneWebSearch
import com.yourapp.chat.data.remote.WebSearchResult
import com.yourapp.chat.data.remote.model.ChatRequest
import com.yourapp.chat.data.remote.model.Message
import com.yourapp.chat.data.remote.model.ToolCall
import com.yourapp.chat.data.remote.model.ToolCallFunction
import com.yourapp.chat.data.remote.model.ToolDef
import com.yourapp.chat.data.remote.model.ToolFunctionDef
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class ChatRepository(
    private val db: AppDatabase,
    private val apiService: ApiService,
    private val sseClient: SseClient,
    private val anthropicClient: AnthropicClient,
    private val configRepo: ConfigRepository,
    private val worldEntryRepo: WorldEntryRepository? = null,
    private val apiProfileRepo: ApiProfileRepository? = null,
    private val deepSeekWebRepo: DeepSeekWebRepository? = null
) {

    companion object {
        /** 注入频率：每 N 句对话注入一次世界信息（减少 token 占用，同时保证设定新鲜度） */
        const val INJECTION_INTERVAL = 25
        private const val TAG = "ChatRepo"
    }

    /**
     * 深度思考（新版协议）对象 {"type":"enabled"/"disabled"}：
     * - DeepSeek 官方 API / DeepSeek 兼容中转站识别该字段并生效（旧字段 thinking_enabled
     *   已被官方忽略，只发旧字段导致「关了还在思考」）；
     * - 不识别该字段的接口（OpenAI/Claude 等）会返回 400，由 SseClient 自动回退
     *   重试（剥离 thinking 字段）并记入黑名单，功能不受影响；
     * - Claude 通道由 AnthropicClient 自行组包，本字段不会发出去。
     */
    private fun buildThinkingJson(enabled: Boolean): JsonObject? {
        return JsonObject().apply { addProperty("type", if (enabled) "enabled" else "disabled") }
    }

    /** 是否注入轮次：第 1 轮必然注入，之后每 N 轮注入一次（interval=0 表示每轮都注入） */
    private fun isInjectionTurn(userCountIncludingCurrent: Int, interval: Int): Boolean {
        if (interval <= 0) return true
        return userCountIncludingCurrent <= 0 || (userCountIncludingCurrent - 1) % interval == 0
    }

    /** 按 profile 协议分派到 OpenAI 兼容或 Anthropic 原生流式客户端 */
    private suspend fun chatStream(cfg: ApiProfileEntity, request: ChatRequest, onThinking: (String) -> Unit = {}, onToolCall: (ToolCall) -> Unit = {}) =
        if (cfg.protocol == "anthropic" && cfg.provider != "deepseek_web") {
            anthropicClient.chatStream(cfg.baseUrl, cfg.apiKey, request, onThinking)
        } else {
            sseClient.chatStream(cfg.baseUrl, cfg.apiKey, request, onThinking, onToolCall)
        }

    /**
     * 按「最大上下文消息数」截断历史消息。
     * 系统消息（角色卡/世界书/早期总结）始终保留在开头，其余消息只保留最近 maxContext 条。
     * 0 或负数表示不限制。
     */
    private fun applyMaxContext(messages: List<MessageEntity>, maxContext: Int): List<MessageEntity> {
        if (maxContext <= 0) return messages
        val sys = messages.filter { it.role == "system" }
        val nonSys = messages.filter { it.role != "system" }.takeLast(maxContext)
        return sys + nonSys
    }

    fun getActiveMessages(conversationId: Long): Flow<List<MessageEntity>> =
        db.messageDao().getActiveMessagesForConversation(conversationId)

    fun getConversations(): Flow<List<ConversationEntity>> =
        db.conversationDao().getAllConversations()

    fun getFavoriteMessages(): Flow<List<MessageEntity>> =
        db.messageDao().getFavoriteMessages()

suspend fun toggleFavorite(messageId: Long, favorite: Boolean) {
    db.messageDao().setFavorite(messageId, favorite)
  }
  suspend fun toggleMessagePinned(messageId: Long, pinned: Boolean) {
    db.messageDao().setPinned(messageId, pinned)
  }

    suspend fun deleteFavoriteMessages(ids: List<Long>) {
        if (ids.isEmpty()) return
        db.messageDao().deleteMessagesByIds(ids)
    }

    fun getConversationsWithLast(query: String): Flow<List<com.yourapp.chat.data.local.dao.ConversationWithLast>> =
        db.conversationDao().getConversationsWithLast(query)

    suspend fun getConversation(id: Long): ConversationEntity? = db.conversationDao().getById(id)

    suspend fun renameConversation(id: Long, title: String) {
        db.conversationDao().rename(id, title)
    }

    /** 置顶/取消置顶对话（右滑列表项切换） */
    suspend fun setConversationPinned(id: Long, pinned: Boolean) {
        db.conversationDao().setPinned(id, pinned)
    }

    /** 该对话是否已发送过用户消息（官网同步的前置检查：没聊过第一句话不允许同步，避免串流） */
    suspend fun hasUserMessage(conversationId: Long): Boolean =
        db.messageDao().getAllMessagesForConversation(conversationId).any { it.role == "user" }

    suspend fun createConversation(title: String, characterCardId: Long?): Long {
        val id = db.conversationDao().insert(
            ConversationEntity(title = title, characterCardId = characterCardId)
        )
        // 新对话 = 官网新会话：重置该对话的官网 session，让官网也开新窗口
        deepSeekWebRepo?.resetSession(id)
        // 应用角色卡：将 system prompt 与开场白插入
        applyCharacterCardToConversation(id, characterCardId)
        return id
    }

    suspend fun deleteConversation(conversation: ConversationEntity) {
        db.conversationDao().delete(conversation)
    }

    suspend fun applyCharacterCardToConversation(conversationId: Long, cardId: Long?) {
        val conversation = db.conversationDao().getById(conversationId) ?: return
        val card: CharacterCardEntity? = cardId?.let { db.characterCardDao().getById(it) }
        db.conversationDao().update(conversation.copy(characterCardId = cardId))

        // 移除旧的 system 消息（角色卡相关），以及旧的开场白（assistant 且 parentMessageId=null 且 content 非空）
        val existing = db.messageDao().getAllMessagesForConversation(conversationId)
        val oldSystemIds = existing.filter { it.role == "system" }.map { it.id }
        // 旧的开场白：assistant 消息且 parentMessageId=null（通常是角色卡的 firstMessage）
        val oldFirstMessageIds = existing.filter { it.role == "assistant" && it.parentMessageId == null && it.content.isNotBlank() }.map { it.id }
        val toDelete = (oldSystemIds + oldFirstMessageIds).distinct()
        if (toDelete.isNotEmpty()) {
            db.messageDao().deleteMessages(conversationId, toDelete)
        }

        // 只有启用角色卡注入时才插入 system prompt 和开场白
        if (conversation.useCharacterCard) {
            card?.let {
                it.systemPrompt?.let { sys ->
                    db.messageDao().insert(
                        MessageEntity(
                            conversationId = conversationId,
                            role = "system",
                            content = "你正在扮演角色「${it.name}」。\n$sys",
                            parentMessageId = null
                        )
                    )
                }
                it.firstMessage?.let { first ->
                    db.messageDao().insert(
                        MessageEntity(
                            conversationId = conversationId,
                            role = "assistant",
                            content = first,
                            parentMessageId = null
                        )
                    )
                }
            }
        }
    }

    /**
     * 流式发送用户消息，收流式 token。
     * @param profile 指定使用的 API 配置；null 时用默认配置
     * @param thinkingEnabled 深度思考（仅官网免费通道）
     * @param searchEnabled 联网搜索（仅官网免费通道）
     * @param searchProvider 搜索引擎（仅官网免费通道）
     * @param onInjectionNotice 注入提示回调（世界书/角色卡注入时触发）
     */
    suspend fun sendMessageStream(
        conversationId: Long,
        userContent: String,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit,
        onContent: (String) -> Unit = {},
        onThinking: (String) -> Unit = {},
        onInjectionNotice: (String) -> Unit = {},
        profile: ApiProfileEntity? = null,
        thinkingEnabled: Boolean = true,
        searchEnabled: Boolean = false,
        searchProvider: String? = null,
        customEngineUrl: String? = null,
        webSearchPrompt: String? = null,
        webSourcesJson: String? = null,
        imageDataUrls: List<String> = emptyList(),
        visionContext: String? = null,
        attachmentText: String? = null,
        attachmentsJson: String? = null
    ) {
        val cfg = profile ?: apiProfileRepo?.getDefault()
            ?: configRepo.getConfig()?.let { ApiProfileEntity(provider = "custom", name = "旧配置", baseUrl = it.baseUrl, apiKey = it.apiKey, model = it.model) }
            ?: throw IllegalStateException("请先配置 API")
        // 记录该对话使用过非官网 API（之后切换官网免费会丢记忆，届时禁用官网选项）
        if (cfg.provider != "deepseek_web") configRepo.markConversationUsedApi(conversationId)
        val history = db.messageDao().getAllMessagesForConversation(conversationId)
            .filter { it.isActiveBranch }
            .sortedBy { it.timestamp }
        val last = history.lastOrNull()

        // 插入 user 消息（附件元数据一并存储，气泡内展示缩略图/文件 chip）
        val userMsgId = db.messageDao().insert(
            MessageEntity(
                conversationId = conversationId,
                role = "user",
                content = userContent,
                parentMessageId = last?.id,
                attachmentsJson = attachmentsJson
            )
        )

        // 世界书注入前先取对话（用于「最大上下文消息数」截断）
        val conversation = getConversation(conversationId)
        // 构造请求消息（包含刚插入的 user 消息），按最大上下文消息数截断历史
        val limitedHistory = applyMaxContext(history, conversation?.maxContextMessages ?: 0)
        val requestMessages = limitedHistory.filter { it.role != "system" || it.content.isNotBlank() }
            .map { Message(it.role, it.content) } + Message("user", userContent)

        // 世界书注入：按对话开关加载 角色卡世界书 + 全局世界书，仅注入轮次才附加（每 25 句一次），
        // 始终只作为请求中的 system 消息发送给 AI，不写入数据库，故对用户不可见。
        val turnIndex = history.count { it.role == "user" } + 1
        val matchedWorld = if (isInjectionTurn(turnIndex, conversation?.injectionInterval ?: 25)) {
            buildMatchedWorldInfo(conversation, userContent)
        } else emptyList()
        val persona = configRepo.getPersona().trim()
        
        // 注入提示
        if (matchedWorld.isNotEmpty()) {
            onInjectionNotice("已注入世界书信息（${matchedWorld.size} 条）")
        }
        if (persona.isNotEmpty()) {
            onInjectionNotice("已注入用户设定")
        }
        // 角色卡注入提示
        if (conversation?.useCharacterCard == true && conversation?.characterCardId != null) {
            val card = conversation?.characterCardId?.let { db.characterCardDao().getById(it) }
            card?.let { onInjectionNotice("已注入角色卡：${it.name}") }
        }
        
        val finalMessages = if (matchedWorld.isNotEmpty() || persona.isNotEmpty() || !visionContext.isNullOrBlank() || !attachmentText.isNullOrBlank()) {
            val worldBlock = matchedWorld.joinToString("\n\n").take(6000)
            val personaBlock = if (persona.isNotEmpty()) {
                "【用户设定】这是与你对话的用户的自定义设定，请始终遵守：\n$persona"
            } else ""
            val visionBlock = if (!visionContext.isNullOrBlank()) {
                "以下是用户本次所附图片的内容描述，请结合图片内容回答问题：\n$visionContext"
            } else ""
            val attachmentBlock = if (!attachmentText.isNullOrBlank()) {
                "以下是用户本次上传的文件内容，请结合内容回答（与当前对话无关时可忽略）：\n$attachmentText"
            } else ""
            val block = listOfNotNull(
                worldBlock.takeIf { it.isNotEmpty() }?.let { "以下是当前场景的世界信息，请融入对话：\n$it" },
                personaBlock.takeIf { it.isNotEmpty() },
                visionBlock.takeIf { it.isNotEmpty() },
                attachmentBlock.takeIf { it.isNotEmpty() }
            ).joinToString("\n\n")
            requestMessages + Message(role = "system", content = block)
        } else requestMessages

        // 插入 assistant 占位消息
        var assistantId = db.messageDao().insert(
            MessageEntity(
                conversationId = conversationId,
                role = "assistant",
                content = "",
                parentMessageId = userMsgId
            )
        )

        val sb = StringBuilder()
        try {
            // 官网免费通道
            if (cfg.provider == "deepseek_web") {
                val webRepo = deepSeekWebRepo ?: throw IllegalStateException("官网通道未初始化")
                if (!webRepo.hasToken()) throw IllegalStateException("未登录 DeepSeek 官网账号")
                // 官网 completion 的 prompt 是单条消息：把角色卡设定 + 命中的世界书信息 + 附件内容作为前缀注入，
                // 让模型在回答前先读到角色与场景设定（这是角色卡/世界书生效的关键）。
                streamWebReply(
                    conversationId = conversationId,
                    prompt = buildWebPrompt(conversation, userContent, matchedWorld, attachmentText),
                    assistantId = assistantId,
                    assistantParentId = userMsgId,
                    parentOverride = null,
                    thinkingEnabled = thinkingEnabled,
                    searchEnabled = searchEnabled,
                    searchProvider = searchProvider,
                    images = imageDataUrls,
                    sb = sb,
                    onToken = onToken,
                    onContent = onContent,
                    onThinking = onThinking
                )
            } else {
                val convSettings = getConversation(conversationId)
                // 自建 API 联网搜索：把手机端搜索到的网页结果作为 system 消息注入请求，
                // 否则模型看不到搜索结果，联网搜索等于没生效
                val requestMessagesWithWeb = if (!webSearchPrompt.isNullOrBlank()) {
                    finalMessages + Message(role = "system", content = webSearchPrompt)
                } else finalMessages
                val request = ChatRequest(
                    model = cfg.model,
                    messages = requestMessagesWithWeb,
                    stream = true,
                    temperature = convSettings?.temperature?.takeIf { it >= 0 },
                    max_tokens = convSettings?.maxOutputTokens?.takeIf { it > 0 },
                    top_p = convSettings?.topP?.takeIf { it >= 0 },
                    top_k = convSettings?.topK?.takeIf { it > 0 },
                    images = imageDataUrls,
                    // 关闭深度思考时显式下发 thinking_enabled=false；
                    // 开启时不发送该字段，避免不兼容的第三方接口报错
                    thinking_enabled = if (!thinkingEnabled) false else null,
                    // 新版 thinking 协议对象（旧字段会被 DeepSeek 官方 API 忽略，双字段齐发）
                    thinking = buildThinkingJson(thinkingEnabled),
                    // 联网搜索：让模型自主决定是否搜索（OpenAI 兼容 function calling）。
                    // 若接口不支持 tools，SseClient 的 onToolCall 不会触发，回退为关键词注入（见上 webSearchPrompt）。
                    tools = if (searchEnabled && cfg.protocol != "anthropic" && cfg.provider != "deepseek_web") buildSearchTool() else null
                )
                Log.d(TAG, ">>> 请求模型=${cfg.model} protocol=${cfg.protocol} provider=${cfg.provider} searchEnabled=$searchEnabled tools=${if (request.tools != null) "SET(${request.tools.size})" else "null"}流式走 streamSseReply")
                streamSseReply(
                    conversationId = conversationId,
                    cfg = cfg,
                    request = request,
                    assistantId = assistantId,
                    assistantParentId = userMsgId,
                    sb = sb,
                    webSourcesJson = webSourcesJson,
                    searchProvider = searchProvider,
                    customEngineUrl = customEngineUrl,
                    searchEnabled = searchEnabled,
                    onToken = onToken,
                    onContent = onContent,
                    onThinking = onThinking
                )
            }
            db.conversationDao().update(
                getConversation(conversationId)!!.copy(updatedAt = System.currentTimeMillis())
            )
            onComplete()
        } catch (e: Exception) {
            // 失败时删除空的 assistant 消息
            if (sb.isEmpty()) {
                db.messageDao().deleteMessages(conversationId, listOf(assistantId))
            }
            onError(e)
        }
    }

    /**
     * 官网 completion 的单条 prompt：角色卡设定 + 世界信息 + 附件内容 + 用户正文。
     * 让模型在回答前先读到角色与场景设定、本次上传的文件内容。
     */
    private suspend fun buildWebPrompt(
        conversation: ConversationEntity?,
        userContent: String,
        matchedWorld: List<String>,
        attachmentText: String? = null
    ): String {
        val card = conversation?.characterCardId?.let { db.characterCardDao().getById(it) }
        val worldPrefix = matchedWorld.joinToString("\n\n").take(6000)
        val persona = configRepo.getPersona().trim()
        return buildString {
            // 只有启用角色卡注入时才加入角色设定
            if (conversation?.useCharacterCard == true) {
                card?.systemPrompt?.takeIf { it.isNotBlank() }?.let { sys ->
                    append("【角色设定】你正在扮演角色「${card.name}」。\n$sys\n\n")
                }
            }
            if (worldPrefix.isNotEmpty()) {
                append("【世界信息】\n$worldPrefix\n\n")
            }
            if (persona.isNotEmpty()) {
                append("【用户设定】这是与你对话的用户的自定义设定，请始终遵守：\n$persona\n\n")
            }
            val attachText = attachmentText?.trim()
            if (!attachText.isNullOrEmpty()) {
                append("【上传文件内容】用户本次上传了以下文件，请结合内容回答（与当前对话无关时可忽略）：\n$attachText\n\n")
            }
            append(userContent)
        }
    }

    /**
     * 官网流式回复写入：节流写库（默认每 120ms），结束前必定落盘。
     * 若用户关闭「消息流式输出」，则流式期间不写库，只在结束时写一次（等全部生成完才显示）。
     */
    private suspend fun streamWebReply(
        conversationId: Long,
        prompt: String,
        assistantId: Long,
        assistantParentId: Long?,
        parentOverride: Long?,
        thinkingEnabled: Boolean,
        searchEnabled: Boolean,
        searchProvider: String?,
        images: List<String> = emptyList(),
        sb: StringBuilder,
        onToken: (String) -> Unit,
        onContent: (String) -> Unit,
        onThinking: (String) -> Unit = {},
        regenerateChildMessageId: Long? = null
    ) {
        val webRepo = deepSeekWebRepo ?: throw IllegalStateException("官网通道未初始化")
        if (!webRepo.hasToken()) throw IllegalStateException("未登录 DeepSeek 官网账号")
        val streaming = configRepo.isStreamingEnabled()
        // 流式刷新频率（毫秒）：正文与思考链推送共用同一节流间隔（设置页可调）
        val pushInterval = configRepo.getStreamRefreshMs().toLong().coerceIn(10L, 2000L)
        var thinkingText = StringBuilder()
        // 流式期间不写库：完整内容经 onContent 回调节流推送（内存态驱动 UI），
        // 避免每次写库触发 Room 全列表刷新导致的界面卡顿。仅在结束时落盘一次。
        val originalTimestamp = db.messageDao().getById(assistantId)?.timestamp
        var lastUiPush = 0L
        var lastThinkPush = 0L
        try {
            val webFlow = if (regenerateChildMessageId != null) {
                // 官网重新生成：专用 /regenerate 端点，服务器复用原用户消息、直接替换该回复
                webRepo.regenerateStream(
                    conversationId = conversationId,
                    childMessageId = regenerateChildMessageId,
                    thinkingEnabled = thinkingEnabled,
                    searchEnabled = searchEnabled,
                    onThinking = { t ->
                        thinkingText.append(t)
                        // 思考链按刷新频率节流推送（思考逐段增长，UI 单独展示）
                        val now = System.currentTimeMillis()
                        if (now - lastThinkPush >= pushInterval) {
                            onThinking(thinkingText.toString())
                            lastThinkPush = now
                        }
                    }
                )
            } else {
                webRepo.chatStream(
                    conversationId = conversationId,
                    prompt = prompt,
                    thinkingEnabled = thinkingEnabled,
                    searchEnabled = searchEnabled,
                    searchProvider = searchProvider,
                    parentMessageIdOverride = parentOverride,
                    imageDataUrls = images,
                    onThinking = { t ->
                        thinkingText.append(t)
                        // 思考链按刷新频率节流推送（思考逐段增长，UI 单独展示）
                        val now = System.currentTimeMillis()
                        if (now - lastThinkPush >= pushInterval) {
                            onThinking(thinkingText.toString())
                            lastThinkPush = now
                        }
                    }
                )
            }
            webFlow.collect { token ->
                sb.append(token)
                onToken(token)
                if (streaming) {
                    val now = System.currentTimeMillis()
                    // 节流推送正文（间隔由设置页「刷新频率」控制），避免每 token 触发 UI 重组导致 CPU 飙升
                    if (now - lastUiPush >= pushInterval) {
                        onContent(sb.toString())
                        lastUiPush = now
                    }
                }
            }
        } catch (e: Exception) {
            // 流式异常时：已收到的内容必须落盘，否则数据库里只留下空占位消息，
            // 界面会永远显示"..."直到用户手动同步（streamingContent 是内存态，退出即丢）。
            if (sb.isNotEmpty()) {
                val partial = if (thinkingText.isNotEmpty()) "[思考]${thinkingText}[/思考]\n\n$sb" else sb.toString()
                onContent(partial)
                if (thinkingText.isNotEmpty()) onThinking(thinkingText.toString())
                db.messageDao().update(
                    MessageEntity(
                        id = assistantId,
                        conversationId = conversationId,
                        role = "assistant",
                        content = partial,
                        timestamp = originalTimestamp ?: System.currentTimeMillis(),
                        parentMessageId = assistantParentId
                    )
                )
            }
            throw e
        }
        val finalContent = if (thinkingText.isNotEmpty()) "[思考]${thinkingText}[/思考]\n\n$sb" else sb.toString()
        if (sb.isEmpty() && thinkingText.isEmpty()) {
            throw java.io.IOException("官网本次未返回任何内容（parent_message_id 可能无效）。请稍后重试或改用标准 API。")
        }
        if (thinkingText.isNotEmpty()) onThinking(thinkingText.toString())
        onContent(finalContent)
        db.messageDao().update(
            MessageEntity(
                id = assistantId,
                conversationId = conversationId,
                role = "assistant",
                content = finalContent,
                timestamp = originalTimestamp ?: System.currentTimeMillis(),
                parentMessageId = assistantParentId
            )
        )
    }

    /**
     * SSE 流式回复写入：节流写库（默认每 120ms），结束前必定落盘。
     * 若用户关闭「消息流式输出」，则流式期间不写库，只在结束时写一次。
     */
    private suspend fun streamSseReply(
        conversationId: Long,
        cfg: ApiProfileEntity,
        request: ChatRequest,
        assistantId: Long,
        assistantParentId: Long?,
        sb: StringBuilder,
        webSourcesJson: String? = null,
        searchProvider: String? = null,
        customEngineUrl: String? = null,
        searchEnabled: Boolean = false,
        onToken: (String) -> Unit,
        onContent: (String) -> Unit,
        onThinking: (String) -> Unit = {}
    ) {
        val streaming = configRepo.isStreamingEnabled()
        // 流式刷新频率（毫秒）：正文与思考链推送共用同一节流间隔（设置页可调）
        val pushInterval = configRepo.getStreamRefreshMs().toLong().coerceIn(10L, 2000L)
        // 流式期间不写库：完整内容经 onContent 回调节流推送（内存态驱动 UI），
        // 避免每次写库触发 Room 全列表刷新导致的界面卡顿。仅在结束时落盘一次。
        val originalTimestamp = db.messageDao().getById(assistantId)?.timestamp
        var lastUiPush = 0L
        var lastThinkPush = 0L
        var thinkingText = StringBuilder()
        // 工具搜索命中的结果，用于写入消息 sources（"浏览/访问的网页"）；需在 try 之外以便异常分支复用
        val toolSources = ArrayList<WebSearchResult>()
        try {
            // —— 联网搜索（函数调用）循环 ——
            // 参考 RikkaHub：把 search_web 工具交给模型，让它自主决定是否搜索；
            // 模型发出工具调用 → 我们执行搜索 → 把结果作为 tool 消息回填 → 再次请求生成最终答案。
            // 兼容两种工具调用形式：
            //  1) 原生 JSON delta.tool_calls（OpenAI 兼容，经 onToolCall 回调）
            //  2) 文本式 <tool_call>search_web\n<arg_key>query</arg_key>\n<arg_value>...</arg_value></tool_call>（部分中转站/模型把工具调用以纯文本流式吐出）
            var currentRequest = request
            var pendingSearch: String? = null            // 待执行的搜索词（文本式工具调用解析所得）
            var toolResult: Pair<ToolCall, String>? = null   // (原生工具调用, 搜索结果提示)
            var round = 0
            while (true) {
                var capturedToolCall: ToolCall? = null
                // 记录本轮是否收到过文本式工具调用
                var textToolSeen = false
                chatStream(cfg, currentRequest, onThinking = { t ->
                    thinkingText.append(t)
                    val now = System.currentTimeMillis()
                    if (now - lastThinkPush >= pushInterval) {
                        onThinking(thinkingText.toString())
                        lastThinkPush = now
                    }
                }, onToolCall = { tc ->
                    if (capturedToolCall == null && tc.function.name == "search_web") {
                        capturedToolCall = tc
                        Log.d(TAG, "检测到原生工具调用 search_web: ${tc.function.arguments}")
                    }
                }).collect { token ->
                    sb.append(token)
                    // 文本式工具调用检测：出现 search_web 标记即命中（query 在 <arg_value> 中，从原始文本解析）
                    if (round == 0 && !textToolSeen) {
                        if (token.contains("<tool_call>", ignoreCase = true)) {
                            textToolSeen = true
                            Log.d(TAG, "检测到文本式工具调用标记，token 开头: ${token.take(120)}")
                        }
                    }
                    onToken(token)
                    if (streaming) {
                        val now = System.currentTimeMillis()
                        if (now - lastUiPush >= pushInterval) {
                            onContent(sb.toString())
                            lastUiPush = now
                        }
                    }
                }
                // 1) 原生工具调用优先
                var tc = capturedToolCall
                var query: String? = null
                if (tc != null) {
                    query = extractSearchQuery(tc.function.arguments)
                }
                // 2) 否则尝试文本式工具调用（从本轮原始文本解析 <arg_value>）
                if (tc == null && textToolSeen) {
                    val q = parseTextToolQuery(sb.toString())
                    if (q != null) {
                        tc = ToolCall(id = "search_web_${System.currentTimeMillis()}", function = ToolCallFunction(name = "search_web", arguments = "{\"query\":\"$q\"}"))
                        query = q
                        Log.d(TAG, "文本式工具调用解析到 query='$q'")
                    }
                }
                if (tc == null || query.isNullOrBlank() || round >= 2) {
                    Log.d(TAG, "工具循环退出: tc=${tc != null}, query='$query', textToolSeen=$textToolSeen, round=$round")
                    break
                }
                // 执行联网搜索
                Log.d(TAG, "开始执行联网搜索: query='$query'")
                val (results, errMsg) = try {
                    PhoneWebSearch.search(query, limit = 5, engine = searchProvider ?: "bing", customEngineUrl = customEngineUrl) to null
                } catch (e: Exception) {
                    emptyList<WebSearchResult>() to (e.message ?: "搜索请求失败")
                }
                Log.d(TAG, "搜索完成: 结果数=${results.size}, errMsg='$errMsg'")
                val resultPrompt = if (results.isNotEmpty()) {
                    toolSources.addAll(results)
                    PhoneWebSearch.buildPrompt(query, results)
                } else {
                    "【联网搜索】没有找到与「$query」相关的结果${errMsg?.let { "（$it）" } ?: ""}。请基于已有知识回答，或请用户补充信息。"
                }
                toolResult = tc to resultPrompt
                // 追加 assistant(带 tool_calls) + tool(结果) 后再次请求，让模型基于结果作答
                val assistantToolMsg = Message(
                    role = "assistant",
                    content = null,
                    tool_calls = listOf(tc)
                )
                val toolMsg = Message(
                    role = "tool",
                    content = resultPrompt,
                    tool_call_id = tc.id,
                    name = tc.function.name
                )
                currentRequest = currentRequest.copy(
                    messages = currentRequest.messages + assistantToolMsg + toolMsg,
                    tools = null
                )
                round++
            }
        val cleanSb = sb.toString().replace(Regex("<tool_call>(.*?)</tool_call>", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
        val finalContent = if (thinkingText.isNotEmpty()) "[思考]${thinkingText}[/思考]\n\n$cleanSb" else cleanSb
        if (thinkingText.isNotEmpty()) onThinking(thinkingText.toString())
        onContent(finalContent)
        // 工具搜索命中的结果优先作为"访问的网页"来源；否则沿用传入的 webSourcesJson
        val effectiveSources = if (toolSources.isNotEmpty()) PhoneWebSearch.buildSourcesJson(toolSources) else webSourcesJson
        db.messageDao().update(
            MessageEntity(
                id = assistantId,
                conversationId = conversationId,
                role = "assistant",
                content = finalContent,
                timestamp = originalTimestamp ?: System.currentTimeMillis(),
                parentMessageId = assistantParentId,
                attachmentsJson = effectiveSources
            )
        )
    } catch (e: Exception) {
        // 流式异常时：已收到的内容必须落盘，否则数据库里只留下空占位消息，
        // 界面会永远显示"..."直到用户手动同步（streamingContent 是内存态，退出即丢）。
        if (sb.isNotEmpty()) {
            val partial = if (thinkingText.isNotEmpty()) "[思考]${thinkingText}[/思考]\n\n$sb" else sb.toString()
            onContent(partial)
            if (thinkingText.isNotEmpty()) onThinking(thinkingText.toString())
            db.messageDao().update(
                MessageEntity(
                    id = assistantId,
                    conversationId = conversationId,
                    role = "assistant",
                    content = partial,
                    timestamp = originalTimestamp ?: System.currentTimeMillis(),
                    parentMessageId = assistantParentId,
                    attachmentsJson = if (toolSources.isNotEmpty()) PhoneWebSearch.buildSourcesJson(toolSources) else webSourcesJson
                )
            )
        }
        throw e
    }
}

    /** 从 search_web 工具的 JSON arguments 里提取 query 字段 */
    private fun extractSearchQuery(arguments: String?): String {
        if (arguments.isNullOrBlank()) return ""
        return try {
            val obj = org.json.JSONObject(arguments)
            obj.optString("query").trim()
        } catch (e: Exception) {
            // 部分实现直接返回裸字符串 query
            arguments.trim().removeSurrounding("\"").trim()
        }
    }

    /**
     * 从累积的文本式工具调用内容里解析出 query 词。
     * 文本式工具调用形如：
     *   <tool_call>search_web\n<arg_key>query</arg_key>\n<arg_value>抖音鸽子神</arg_value></tool_call>
     */
    private fun parseTextToolQuery(accumulated: String): String? {
        val t = accumulated.trim()
        if (t.isBlank()) return null
        // 优先取 <arg_value> 值
        Regex("<arg_value>(.*?)</arg_value>", RegexOption.DOT_MATCHES_ALL)
            .find(t)?.let { m ->
                val v = m.groupValues[1].trim()
                if (v.isNotBlank()) return v
            }
        // 兜底：去掉标签后的纯文本
        val stripped = t.replace(Regex("<[^>]+>"), "").trim()
        return stripped.ifBlank { null }
    }

    /** 构造 search_web 工具定义（OpenAI 兼容 JSON Schema） */
    private fun buildSearchTool(): List<ToolDef> {
        val params = com.google.gson.JsonObject()
        params.addProperty("type", "object")
        val props = com.google.gson.JsonObject()
        val q = com.google.gson.JsonObject()
        q.addProperty("type", "string")
        q.addProperty("description", "要搜索的关键词或问题，应尽量简短精炼")
        props.add("query", q)
        params.add("properties", props)
        val required = com.google.gson.JsonArray().apply { add("query") }
        params.add("required", required)
        return listOf(
            ToolDef(
                function = ToolFunctionDef(
                    name = "search_web",
                    description = "当你需要获取最新、实时、或外部网络信息（如新闻、天气、价格、人物/事件、百科知识、比赛比分等）来回答用户问题时，调用此工具进行联网搜索。搜索结果会作为资料供你引用。",
                    parameters = params
                )
            )
        )
    }

/**
 * SSE 流式回复写入：节流写库（默认每 120ms），结束前必定落盘。
 * 若用户关闭「消息流式输出」，则流式期间不写库，只在结束时写一次。
 */
    suspend fun regenerateMessage(
        conversationId: Long,
        messageId: Long,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit,
        onContent: (String) -> Unit = {},
        onThinking: (String) -> Unit = {},
        onInjectionNotice: (String) -> Unit = {},
        profile: ApiProfileEntity? = null,
        thinkingEnabled: Boolean = true,
        searchEnabled: Boolean = false,
        searchProvider: String? = null
    ) {
        val cfg = profile ?: apiProfileRepo?.getDefault()
            ?: configRepo.getConfig()?.let { ApiProfileEntity(provider = "custom", name = "旧配置", baseUrl = it.baseUrl, apiKey = it.apiKey, model = it.model) }
            ?: throw IllegalStateException("请先配置 API")
        // 记录该对话使用过非官网 API（之后切换官网免费会丢记忆，届时禁用官网选项）
        if (cfg.provider != "deepseek_web") configRepo.markConversationUsedApi(conversationId)
        val all = db.messageDao().getAllMessagesForConversation(conversationId)
            .filter { it.isActiveBranch }
            .sortedBy { it.timestamp }
        val target = all.find { it.id == messageId } ?: return
        val userMsg = all.filter { it.role == "user" && it.timestamp <= target.timestamp }.lastOrNull()
        val userContent = userMsg?.content ?: ""
        val conversation = getConversation(conversationId)
        val turnIndex = all.count { it.role == "user" && it.timestamp <= (userMsg?.timestamp ?: target.timestamp) }
        val matchedWorld = if (isInjectionTurn(turnIndex, conversation?.injectionInterval ?: 25)) {
            buildMatchedWorldInfo(conversation, userContent)
        } else emptyList()

        // 本地删除该 AI 消息及其后续，插入占位 assistant
        val toDelete = all.filter { it.timestamp >= target.timestamp }.map { it.id }
        if (toDelete.isNotEmpty()) db.messageDao().deleteMessages(conversationId, toDelete)
        var assistantId = db.messageDao().insert(
            MessageEntity(
                conversationId = conversationId,
                role = "assistant",
                content = "",
                parentMessageId = userMsg?.id
            )
        )
        val sb = StringBuilder()
        
        // 注入提示
        if (matchedWorld.isNotEmpty()) {
            onInjectionNotice("已注入世界书信息（${matchedWorld.size} 条）")
        }
        val persona = configRepo.getPersona().trim()
        // 角色卡注入提示
        if (conversation?.useCharacterCard == true && conversation?.characterCardId != null) {
            val card = conversation?.characterCardId?.let { db.characterCardDao().getById(it) }
            card?.let { onInjectionNotice("已注入角色卡：${it.name}") }
        }
        
        try {
            if (cfg.provider == "deepseek_web") {
                // 官网重生成走专用端点 /api/v0/chat/regenerate：用「被重生成回复自身的官网 message_id」
                // （child_message_id）定位，服务器复用原用户消息、直接替换该回复——
                // 之前用 completion + parent 指向回复 id 的写法，官网仍会把用户消息重发一遍
                // （表现为"重新生成后用户又发了一次一样的消息"）。
                // 解析链：
                // 1) 重新生成最后一条回复 → 直接复用本地记录的官网回复 id（最可靠，0 次网络请求）；
                // 2) 其他回复 → 按正文在官网历史里定位该回复自身（剥离思考块/来源标注后匹配）。
                // 两条路都失败时回退到 completion 接在对话末尾（旧逻辑），避免直接失败。
                val isLastAssistant = target.id == all.filter { it.role == "assistant" }.lastOrNull()?.id
                val childId = deepSeekWebRepo?.let { repo ->
                    (if (isLastAssistant) repo.lastAssistantReplyId(conversationId) else null)
                        ?: repo.findMessageIdByContent(conversationId, target.content, role = "assistant")
                }
                if (childId != null) {
                    streamWebReply(
                        conversationId = conversationId,
                        prompt = buildWebPrompt(conversation, userContent, matchedWorld),
                        assistantId = assistantId,
                        assistantParentId = userMsg?.id,
                        parentOverride = null,
                        thinkingEnabled = thinkingEnabled,
                        searchEnabled = searchEnabled,
                        searchProvider = searchProvider,
                        sb = sb,
                        onToken = onToken,
                        onContent = onContent,
                        onThinking = onThinking,
                        regenerateChildMessageId = childId
                    )
                } else {
                    streamWebReply(
                        conversationId = conversationId,
                        prompt = buildWebPrompt(conversation, userContent, matchedWorld),
                        assistantId = assistantId,
                        assistantParentId = userMsg?.id,
                        parentOverride = null,
                        thinkingEnabled = thinkingEnabled,
                        searchEnabled = searchEnabled,
                        searchProvider = searchProvider,
                        sb = sb,
                        onToken = onToken,
                        onContent = onContent,
                        onThinking = onThinking
                    )
                }
            } else {
                val convSettings = getConversation(conversationId)
                val requestMessages = if (userMsg != null) {
                    applyMaxContext(
                        all.filter { it.role != "system" || it.content.isNotBlank() }
                            .filter { it.timestamp <= userMsg.timestamp },
                        convSettings?.maxContextMessages ?: 0
                    ).map { Message(it.role, it.content) }
                } else {
                    // 无前置用户消息（如角色卡开场白）：用 system（角色设定）作为上下文
                    all.filter { it.role == "system" && it.content.isNotBlank() }
                        .map { Message(it.role, it.content) }
                }
                val finalMessages = if (matchedWorld.isNotEmpty()) {
                    requestMessages + Message(
                        role = "system",
                        content = "以下是当前场景的世界信息，请融入对话：\n${matchedWorld.joinToString("\n\n").take(6000)}"
                    )
                } else requestMessages
                val request = ChatRequest(
                    model = cfg.model,
                    messages = finalMessages,
                    stream = true,
                    temperature = convSettings?.temperature?.takeIf { it >= 0 },
                    max_tokens = convSettings?.maxOutputTokens?.takeIf { it > 0 },
                    top_p = convSettings?.topP?.takeIf { it >= 0 },
                    top_k = convSettings?.topK?.takeIf { it > 0 },
                    // 关闭深度思考时显式下发 thinking_enabled=false
                    thinking_enabled = if (!thinkingEnabled) false else null,
                    // 新版 thinking 协议对象（旧字段会被 DeepSeek 官方 API 忽略，双字段齐发）
                    thinking = buildThinkingJson(thinkingEnabled)
                )
                streamSseReply(
                    conversationId = conversationId,
                    cfg = cfg,
                    request = request,
                    assistantId = assistantId,
                    assistantParentId = userMsg?.id,
                    sb = sb,
                    onToken = onToken,
                    onContent = onContent,
                    onThinking = onThinking
                )
            }
            db.conversationDao().update(
                getConversation(conversationId)!!.copy(updatedAt = System.currentTimeMillis())
            )
            onComplete()
        } catch (e: Exception) {
            if (sb.isEmpty()) {
                db.messageDao().deleteMessages(conversationId, listOf(assistantId))
            }
            onError(e)
        }
    }

    /**
     * 编辑：长按用户消息 → 修改文本，AI 按编辑后的文本重新回答。
     * 官网路径：编辑后的文本作 prompt，parent 用编辑消息之前那条消息的官网 id（首条则 null）。
     * 本地：删除该用户消息及后续，插入编辑后的用户消息 + 占位 assistant。
     */
    suspend fun editUserMessage(
        conversationId: Long,
        messageId: Long,
        newContent: String,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit,
        onContent: (String) -> Unit = {},
        onThinking: (String) -> Unit = {},
        onInjectionNotice: (String) -> Unit = {},
        profile: ApiProfileEntity? = null,
        thinkingEnabled: Boolean = true,
        searchEnabled: Boolean = false,
        searchProvider: String? = null
    ) {
        if (newContent.isBlank()) return
        val cfg = profile ?: apiProfileRepo?.getDefault()
            ?: configRepo.getConfig()?.let { ApiProfileEntity(provider = "custom", name = "旧配置", baseUrl = it.baseUrl, apiKey = it.apiKey, model = it.model) }
            ?: throw IllegalStateException("请先配置 API")
        // 记录该对话使用过非官网 API（之后切换官网免费会丢记忆，届时禁用官网选项）
        if (cfg.provider != "deepseek_web") configRepo.markConversationUsedApi(conversationId)
        val all = db.messageDao().getAllMessagesForConversation(conversationId)
            .filter { it.isActiveBranch }
            .sortedBy { it.timestamp }
        val target = all.find { it.id == messageId } ?: return
        val conversation = getConversation(conversationId)
        val turnIndex = all.count { it.role == "user" && it.timestamp < target.timestamp } + 1
        val matchedWorld = if (isInjectionTurn(turnIndex, conversation?.injectionInterval ?: 25)) {
            buildMatchedWorldInfo(conversation, newContent)
        } else emptyList()

        // 本地删除该用户消息及后续
        val toDelete = all.filter { it.timestamp >= target.timestamp }.map { it.id }
        if (toDelete.isNotEmpty()) db.messageDao().deleteMessages(conversationId, toDelete)
        // 插入编辑后的用户消息（parent 沿用目标消息的 parent）
        val newUserMsgId = db.messageDao().insert(
            MessageEntity(
                conversationId = conversationId,
                role = "user",
                content = newContent,
                parentMessageId = target.parentMessageId
            )
        )
        var assistantId = db.messageDao().insert(
            MessageEntity(
                conversationId = conversationId,
                role = "assistant",
                content = "",
                parentMessageId = newUserMsgId
            )
        )
        val sb = StringBuilder()
        val persona = configRepo.getPersona().trim()
        
        // 注入提示
        if (matchedWorld.isNotEmpty()) {
            onInjectionNotice("已注入世界书信息（${matchedWorld.size} 条）")
        }
        if (persona.isNotEmpty()) {
            onInjectionNotice("已注入用户设定")
        }
        // 角色卡注入提示
        if (conversation?.useCharacterCard == true && conversation?.characterCardId != null) {
            val card = conversation?.characterCardId?.let { db.characterCardDao().getById(it) }
            card?.let { onInjectionNotice("已注入角色卡：${it.name}") }
        }
        
        try {
            if (cfg.provider == "deepseek_web") {
                // 官网：parent = 编辑消息之前那条消息的官网 id（按 content 匹配；首条则 null）
                val prevMsg = all.filter { it.timestamp < target.timestamp }.lastOrNull()
                val parentOverride = if (prevMsg != null) {
                    deepSeekWebRepo?.findMessageIdByContent(conversationId, prevMsg.content, role = null)
                } else null
                streamWebReply(
                    conversationId = conversationId,
                    prompt = buildWebPrompt(conversation, newContent, matchedWorld),
                    assistantId = assistantId,
                    assistantParentId = newUserMsgId,
                    parentOverride = parentOverride,
                    thinkingEnabled = thinkingEnabled,
                    searchEnabled = searchEnabled,
                    searchProvider = searchProvider,
                    sb = sb,
                    onToken = onToken,
                    onContent = onContent,
                    onThinking = onThinking
                )
            } else {
                val convSettings = getConversation(conversationId)
                val requestMessages = applyMaxContext(
                    all.filter { it.role != "system" || it.content.isNotBlank() }
                        .filter { it.timestamp < target.timestamp },
                    convSettings?.maxContextMessages ?: 0
                ).map { Message(it.role, it.content) } + Message("user", newContent)
                val finalMessages = if (matchedWorld.isNotEmpty()) {
                    requestMessages + Message(
                        role = "system",
                        content = "以下是当前场景的世界信息，请融入对话：\n${matchedWorld.joinToString("\n\n").take(6000)}"
                    )
                } else requestMessages
                val request = ChatRequest(
                    model = cfg.model,
                    messages = finalMessages,
                    stream = true,
                    temperature = convSettings?.temperature?.takeIf { it >= 0 },
                    max_tokens = convSettings?.maxOutputTokens?.takeIf { it > 0 },
                    top_p = convSettings?.topP?.takeIf { it >= 0 },
                    top_k = convSettings?.topK?.takeIf { it > 0 },
                    // 关闭深度思考时显式下发 thinking_enabled=false
                    thinking_enabled = if (!thinkingEnabled) false else null,
                    // 新版 thinking 协议对象（旧字段会被 DeepSeek 官方 API 忽略，双字段齐发）
                    thinking = buildThinkingJson(thinkingEnabled)
                )
                streamSseReply(
                    conversationId = conversationId,
                    cfg = cfg,
                    request = request,
                    assistantId = assistantId,
                    assistantParentId = newUserMsgId,
                    sb = sb,
                    onToken = onToken,
                    onContent = onContent,
                    onThinking = onThinking
                )
            }
            db.conversationDao().update(
                getConversation(conversationId)!!.copy(updatedAt = System.currentTimeMillis())
            )
            onComplete()
        } catch (e: Exception) {
            if (sb.isEmpty()) {
                db.messageDao().deleteMessages(conversationId, listOf(assistantId))
            }
            onError(e)
        }
    }

    /**
     * 本地编辑一条 assistant（AI）消息的内容并保存。
     * 与 editUserMessage 不同：只改动该条 AI 回复本身的文本，不触发重新生成，
     * 之后的对话会以编辑后的 AI 内容作为记忆/上下文。
     */
    suspend fun editAssistantMessage(
        conversationId: Long,
        messageId: Long,
        newThinking: String?,
        newContent: String
    ) {
        if (newContent.isBlank()) return
        val target = db.messageDao().getById(messageId) ?: return
        if (target.role != "assistant") return
        val finalContent = when {
            newThinking.isNullOrBlank() -> newContent
            else -> "[思考]${newThinking}[/思考]\n\n${newContent}"
        }
        db.messageDao().update(target.copy(content = finalContent))
        db.conversationDao().update(
            getConversation(conversationId)?.copy(updatedAt = System.currentTimeMillis()) ?: return
        )
    }

    /**
     * 中止生成时，把已流式的内容（含思考）写入该对话最后一条 assistant 消息，
     * 避免"中止后内容丢失"或留下空白占位。
     */
    suspend fun persistPartialAssistantContent(conversationId: Long, thinking: String?, content: String?) {
        val assistant = db.messageDao().getAllMessagesForConversation(conversationId)
            .filter { it.isActiveBranch && it.role == "assistant" }
            .maxByOrNull { it.timestamp } ?: return
        val finalContent = when {
            thinking.isNullOrBlank() -> content.orEmpty()
            else -> "[思考]${thinking}[/思考]\n\n${content.orEmpty()}"
        }
        db.messageDao().update(assistant.copy(content = finalContent))
    }

    /**
     * 回溯：从某条消息开始开新分支。将比该消息新的活跃消息全部标记为非活跃。
     */
    suspend fun branchAtMessage(conversationId: Long, messageId: Long) {
        val all = db.messageDao().getAllMessagesForConversation(conversationId)
            .sortedBy { it.timestamp }
        val target = all.find { it.id == messageId } ?: return
        val laterIds = all.filter { it.timestamp > target.timestamp && it.isActiveBranch }.map { it.id }
        if (laterIds.isNotEmpty()) {
            db.messageDao().deactivateMessages(conversationId, laterIds)
        }
    }

    /**
     * 改写：删除某条消息及其后所有消息，可选传入新内容替代。
     * 官网通道时 best-effort 同步删除官网对应消息（按 content 匹配）。
     * @return 官网删除是否成功（非官网通道返回 null）
     */
    suspend fun rewriteMessage(
        conversationId: Long,
        messageId: Long,
        newContent: String? = null,
        syncOfficial: Boolean = true
    ): Boolean? {
        val all = db.messageDao().getAllMessagesForConversation(conversationId)
            .sortedBy { it.timestamp }
        val target = all.find { it.id == messageId } ?: return null
        val toDelete = all.filter { it.timestamp >= target.timestamp }.map { it.id }
        db.messageDao().deleteMessages(conversationId, toDelete)
        var officialDeleted: Boolean? = null
        if (syncOfficial) {
            val webRepo = deepSeekWebRepo
            if (webRepo != null && webRepo.hasToken()) {
                val officialId = webRepo.findMessageIdByContent(conversationId, target.content, role = null)
                if (officialId != null) {
                    officialDeleted = webRepo.deleteMessageOnOfficial(conversationId, officialId)
                } else {
                    officialDeleted = false
                }
            }
        }
        if (!newContent.isNullOrBlank()) {
            db.messageDao().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = target.role,
                    content = newContent,
                    parentMessageId = target.parentMessageId
                )
            )
        }
        return officialDeleted
    }

    /**
     * 用官网同步的消息替换本对话的普通消息（保留 system 提示：角色卡/早期总结）。
     * 使 App 内显示与官网一致，并让下一条发送正确接在同步消息之后。
     */
    suspend fun replaceConversationWithSync(
        conversationId: Long,
        webMessages: List<DeepSeekWebClient.WebMessage>
    ) {
        if (webMessages.isEmpty()) return
        val all = db.messageDao().getAllMessagesForConversation(conversationId)
        // 保留 system 消息，删除其它（user/assistant）
        val nonSystemIds = all.filter { it.role != "system" }.map { it.id }
        if (nonSystemIds.isNotEmpty()) {
            db.messageDao().deleteMessages(conversationId, nonSystemIds)
        }
        // 时间戳从现有最大值后递增，保证 system 在最前、同步消息按序排列
        var ts = all.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()
        var parent: Long? = all.filter { it.role == "system" }.maxByOrNull { it.timestamp }?.id
        for (m in webMessages) {
            parent = db.messageDao().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = m.role,
                    content = m.content,
                    timestamp = ++ts,
                    parentMessageId = parent
                )
            )
        }
    }

    /**
     * 压缩上下文：将较早的对话总结成一段，插入在保留消息之前，并删除被压缩的消息。
     * @param keepCount 保留最近 N 条消息（默认 32），更早的将被压缩
     */
    suspend fun compressContext(conversationId: Long, profile: ApiProfileEntity? = null, keepCount: Int = 32) {
        val cfg = profile ?: apiProfileRepo?.getDefault()
            ?: configRepo.getConfig()?.let { ApiProfileEntity(provider = "custom", name = "旧配置", baseUrl = it.baseUrl, apiKey = it.apiKey, model = it.model) }
            ?: throw IllegalStateException("请先配置 API")
        if (cfg.provider == "deepseek_web") {
            throw IllegalStateException("官网免费通道不支持压缩上下文，请切换到其他 API")
        }
        val active = db.messageDao().getAllMessagesForConversation(conversationId)
            .filter { it.isActiveBranch && it.role != "system" }
            .sortedBy { it.timestamp }

        // 保留最近 keepCount 条，压缩更早的
        val keep = if (active.size > keepCount) active.takeLast(keepCount) else return
        val toCompress = active.dropLast(keepCount)
        if (toCompress.size < 2) return

        // 压缩专用模型：对话设置了 compressionModel 则用它，否则复用聊天模型
        val compressModel = db.conversationDao().getById(conversationId)?.compressionModel
            ?.takeIf { it.isNotBlank() } ?: cfg.model

        val summary = summarize(cfg, compressModel, toCompress)
        val compressIds = toCompress.map { it.id }

        // 删除被压缩的消息（不是仅标记非活跃）
        db.messageDao().deleteMessages(conversationId, compressIds)

        // 总结作为 system 消息插入，时间戳设为保留消息最早时间戳之前，保证排序在保留消息之前
        val insertTime = keep.first().timestamp - 1
        db.messageDao().insert(
            MessageEntity(
                conversationId = conversationId,
                role = "system",
                content = "[对话早期总结（${toCompress.size} 条）]\n$summary",
                parentMessageId = null,
                timestamp = insertTime
            )
        )
    }

    /**
     * 删除单条消息（及其后续回复链，保持分支一致性）。
     * 仅删除本地数据库记录，不触及官网同步。
     */
    suspend fun deleteMessage(conversationId: Long, messageId: Long) {
        val all = db.messageDao().getAllMessagesForConversation(conversationId)
            .filter { it.isActiveBranch }
            .sortedBy { it.timestamp }
        val target = all.find { it.id == messageId } ?: return
        // 删除该消息及其所有后续（同一分支）
        val toDelete = all.filter { it.timestamp >= target.timestamp && it.isActiveBranch }.map { it.id }
        if (toDelete.isNotEmpty()) db.messageDao().deleteMessages(conversationId, toDelete)
    }

    private suspend fun summarize(profile: ApiProfileEntity, model: String, messages: List<MessageEntity>): String {
        val prompt = "请用中文简洁地总结以下对话的要点（角色、主题、已确定的事实、当前进展），保留关键细节，200字以内：\n\n" +
                messages.joinToString("\n") { "${if (it.role == "user") "用户" else "助手"}: ${it.content}" }
        val request = ChatRequest(
            model = model,
            messages = listOf(
                Message("system", "你是一个对话总结助手。"),
                Message("user", prompt)
            ),
            stream = false
        )
        if (profile.protocol == "anthropic") {
            return anthropicClient.completeText(profile.baseUrl, profile.apiKey, request)
        }
        val url = "${profile.baseUrl.trimEnd('/')}/chat/completions"
        val response = apiService.chatCompletion(url, request)
        if (!response.isSuccessful) {
            throw IllegalStateException("总结失败: HTTP ${response.code()}")
        }
        return response.body()?.choices?.firstOrNull()?.message?.content ?: "(无总结)"
    }

    /**
     * 收集命中的世界书内容：选中的特定世界书（selectedWorldBookIds）+ 角色卡世界书（useCardWorld）+ 全局世界书（useGlobalWorld），
     * 按 priority 升序，返回格式化文本块。
     */
    private suspend fun buildMatchedWorldInfo(
        conversation: ConversationEntity?,
        userContent: String
    ): List<String> {
        if (conversation == null) return emptyList()
        val repo = worldEntryRepo ?: WorldEntryRepository(db.worldEntryDao(), db.worldBookDao())
        val blocks = ArrayList<String>()

        // 1. 选中的特定世界书（分对话配置，优先级最高）
        val selectedIds = conversation.getSelectedWorldBookIds()
        if (selectedIds.isNotEmpty()) {
            selectedIds.forEach { bookId ->
                val entries = repo.getEntriesByBook(bookId).first()
                entries.filter { it.enabled }.forEach { entry ->
                    val keys = entry.keys.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val label = keys.firstOrNull() ?: "设定"
                    blocks.add("【世界信息：$label】\n${entry.content}")
                }
            }
        }

        // 2. 角色卡世界书：始终注入所有启用条目（不依赖关键词）
        if (conversation.useCardWorld) {
            conversation.characterCardId?.let { cardId ->
                repo.getEnabledByCardId(cardId).forEach { entry ->
                    val keys = entry.keys.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val label = keys.firstOrNull() ?: "设定"
                    blocks.add("【世界信息：$label】\n${entry.content}")
                }
            }
        }

        // 3. 全局世界书：始终注入所有启用条目
        if (conversation.useGlobalWorld) {
            repo.getEnabledGlobal().forEach { entry ->
                val keys = entry.keys.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val label = keys.firstOrNull() ?: "设定"
                blocks.add("【世界信息：$label】\n${entry.content}")
            }
        }
        return blocks
    }

    /** 切换对话的 角色卡世界书/全局世界书 注入开关 */
    suspend fun setWorldToggles(conversationId: Long, useCardWorld: Boolean, useGlobalWorld: Boolean) {
        val conv = db.conversationDao().getById(conversationId) ?: return
        db.conversationDao().update(conv.copy(useCardWorld = useCardWorld, useGlobalWorld = useGlobalWorld))
    }

    /** 记录该对话上次使用的 API profile（下次进入时恢复） */
    suspend fun setLastUsedProfile(conversationId: Long, profileId: Long?) {
        db.conversationDao().updateLastUsedProfile(conversationId, profileId, System.currentTimeMillis())
    }

    /** 设置对话选中的世界书 ID 列表（分对话配置） */
    suspend fun setSelectedWorldBooks(conversationId: Long, bookIds: List<Long>) {
        val conv = db.conversationDao().getById(conversationId) ?: return
        db.conversationDao().update(conv.withSelectedWorldBookIds(bookIds))
    }

    /** 更新对话参数设置 */
    suspend fun updateConversationSettings(
        conversationId: Long,
        showThinking: Boolean,
        maxOutputTokens: Int,
        maxContextMessages: Int,
        temperature: Double,
        topK: Int,
        topP: Double,
        userGreeting: String,
        aiGreeting: String
    ) {
        val conv = db.conversationDao().getById(conversationId) ?: return
        db.conversationDao().update(
            conv.copy(
                showThinking = showThinking,
                maxOutputTokens = maxOutputTokens,
                maxContextMessages = maxContextMessages,
                temperature = temperature,
                topK = topK,
                topP = topP,
                userGreeting = userGreeting,
                aiGreeting = aiGreeting
            )
        )
    }

    /**
     * 用指定的识图模型（保存的识图能力模型）描述一张图片，返回文字描述。
     * 仅支持 OpenAI 兼容协议；失败抛异常。
     */
    suspend fun describeImage(
        profile: ApiProfileEntity,
        visionModelName: String,
        imageDataUrl: String
    ): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val baseUrl = profile.baseUrl.trimEnd('/')
        val body = com.google.gson.JsonObject().apply {
            addProperty("model", visionModelName)
            val contentArr = com.google.gson.JsonArray()
            val textPart = com.google.gson.JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", "请详细描述这张图片的内容，包括主体、场景、文字和视觉细节。")
            }
            contentArr.add(textPart)
            val imgPart = com.google.gson.JsonObject().apply {
                addProperty("type", "image_url")
                val u = com.google.gson.JsonObject()
                u.addProperty("url", imageDataUrl)
                add("image_url", u)
            }
            contentArr.add(imgPart)
            val msg = com.google.gson.JsonObject().apply {
                addProperty("role", "user")
                add("content", contentArr)
            }
            val msgs = com.google.gson.JsonArray()
            msgs.add(msg)
            add("messages", msgs)
            addProperty("stream", false)
        }
        val json = body.toString()
        val okhttp = com.yourapp.chat.ChatApplication.instance.okHttpClient
        val req = okhttp3.Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer ${profile.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
        okhttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("识图失败 HTTP ${resp.code}: ${resp.body?.string().orEmpty().take(200)}")
            }
            val text = resp.body?.string() ?: ""
            val parsed = com.google.gson.JsonParser.parseString(text).asJsonObject
            val choices = parsed.getAsJsonArray("choices")
            if (choices.isEmpty()) throw IllegalStateException("识图接口无返回")
            choices[0].asJsonObject
                .getAsJsonObject("message")
                ?.get("content")
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("识图模型未返回描述")
        }
    }
}