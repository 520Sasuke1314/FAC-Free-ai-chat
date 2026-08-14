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
import com.yourapp.chat.data.remote.model.ChatRequest
import com.yourapp.chat.data.remote.model.Message
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
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
    private suspend fun chatStream(cfg: ApiProfileEntity, request: ChatRequest, onThinking: (String) -> Unit = {}) =
        if (cfg.protocol == "anthropic" && cfg.provider != "deepseek_web") {
            anthropicClient.chatStream(cfg.baseUrl, cfg.apiKey, request, onThinking)
        } else {
            sseClient.chatStream(cfg.baseUrl, cfg.apiKey, request, onThinking)
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
        val requestBase = limitedHistory.filter { it.role != "system" || it.content.isNotBlank() }
            .map { Message(it.role, it.content) }
        // 识图结果直接植入本条用户消息（带前缀），使模型把描述当作该消息本身的内容，而非独立的 system 提示。
        val embeddedUserContent = if (!visionContext.isNullOrBlank()) {
            "$userContent\n\n这是一张用户发来的图片，里面所包含的内容：\n$visionContext"
        } else userContent
        val requestMessages = requestBase + Message("user", embeddedUserContent)

        // 世界书注入：按对话开关加载 角色卡世界书 + 全局世界书，仅注入轮次才附加（每 25 句一次），
        // 始终只作为请求中的 system 消息发送给 AI，不写入数据库，故对用户不可见。
        val turnIndex = history.count { it.role == "user" } + 1
        val shouldInject = isInjectionTurn(turnIndex, conversation?.injectionInterval ?: 25)
        val matchedWorld = if (shouldInject) {
            buildMatchedWorldInfo(conversation, userContent)
        } else emptyList()
        val persona = configRepo.getPersona().trim()
        
        val finalMessages = if (shouldInject || !attachmentText.isNullOrBlank()) {
            val worldBlock = matchedWorld.joinToString("\n\n").take(6000)
            // 只有在注入轮次时才加入用户设定（节省缓存 token）
            val personaBlock = if (shouldInject && persona.isNotEmpty()) {
                "【用户设定】这是与你对话的用户的自定义设定，请始终遵守：\n$persona"
            } else ""
            val attachmentBlock = if (!attachmentText.isNullOrBlank()) {
                "以下是用户本次上传的文件内容，请结合内容回答（与当前对话无关时可忽略）：\n$attachmentText"
            } else ""
            val block = listOfNotNull(
                worldBlock.takeIf { it.isNotEmpty() }?.let { "以下是当前场景的世界信息，请融入对话：\n$it" },
                personaBlock.takeIf { it.isNotEmpty() },
                attachmentBlock.takeIf { it.isNotEmpty() }
            ).joinToString("\n\n")
            if (block.isBlank()) requestMessages
            else requestMessages + Message(role = "system", content = block)
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
                    prompt = buildWebPrompt(conversation, userContent, matchedWorld, attachmentText, shouldInject),
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
                val request = ChatRequest(
                    model = cfg.model,
                    messages = finalMessages,
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
                    thinking = buildThinkingJson(thinkingEnabled)
                )
                streamSseReply(
                    conversationId = conversationId,
                    cfg = cfg,
                    request = request,
                    assistantId = assistantId,
                    assistantParentId = userMsgId,
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
        attachmentText: String? = null,
        isInjectionTurn: Boolean = false
    ): String {
        val card = conversation?.characterCardId?.let { db.characterCardDao().getById(it) }
        val worldPrefix = matchedWorld.joinToString("\n\n").take(6000)
        val persona = configRepo.getPersona().trim()
        return buildString {
            // 只有在注入轮次时才加入角色卡设定和用户设定（节省缓存 token）
            if (isInjectionTurn) {
                if (conversation?.useCharacterCard == true) {
                    card?.systemPrompt?.takeIf { it.isNotBlank() }?.let { sys ->
                        append("【角色设定】你正在扮演角色「${card.name}」。\n$sys\n\n")
                    }
                }
                if (persona.isNotEmpty()) {
                    append("【用户设定】这是与你对话的用户的自定义设定，请始终遵守：\n$persona\n\n")
                }
            }
            if (worldPrefix.isNotEmpty()) {
                append("【世界信息】\n$worldPrefix\n\n")
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
        editMessageId: Long? = null,
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
            } else if (editMessageId != null) {
                // 官网编辑消息：专用 /api/v0/chat/edit_message 端点，用官方 message_id 原地替换该用户消息
                webRepo.editMessageStream(
                    conversationId = conversationId,
                    messageId = editMessageId,
                    prompt = prompt,
                    thinkingEnabled = thinkingEnabled,
                    searchEnabled = searchEnabled,
                    onThinking = { t ->
                        thinkingText.append(t)
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
        try {
            chatStream(cfg, request, onThinking = { t ->
                thinkingText.append(t)
                val now = System.currentTimeMillis()
                if (now - lastThinkPush >= pushInterval) {
                    onThinking(thinkingText.toString())
                    lastThinkPush = now
                }
            }).collect { token ->
                sb.append(token)
                onToken(token)
                if (streaming) {
                    val now = System.currentTimeMillis()
                    if (now - lastUiPush >= pushInterval) {
                        onContent(sb.toString())
                        lastUiPush = now
                    }
                }
            }
        val finalContent = if (thinkingText.isNotEmpty()) "[思考]${thinkingText}[/思考]\n\n$sb" else sb.toString()
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
        val shouldInject = isInjectionTurn(turnIndex, conversation?.injectionInterval ?: 25)
        val matchedWorld = if (shouldInject) {
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
                        prompt = buildWebPrompt(conversation, userContent, matchedWorld, null, shouldInject),
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
                        prompt = buildWebPrompt(conversation, userContent, matchedWorld, null, shouldInject),
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
     * 官网路径：先删除官网原消息及其后续（best-effort），再用编辑内容替换该位置。
     * 本地：删除该用户消息及后续，插入编辑后的用户消息 + 占位 assistant。
     * @return 官网原消息分支是否已删除（非官网通道返回 false）
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
    ): Boolean {
        if (newContent.isBlank()) return false
        val cfg = profile ?: apiProfileRepo?.getDefault()
            ?: configRepo.getConfig()?.let { ApiProfileEntity(provider = "custom", name = "旧配置", baseUrl = it.baseUrl, apiKey = it.apiKey, model = it.model) }
            ?: throw IllegalStateException("请先配置 API")
        // 记录该对话使用过非官网 API（之后切换官网免费会丢记忆，届时禁用官网选项）
        if (cfg.provider != "deepseek_web") configRepo.markConversationUsedApi(conversationId)
        val all = db.messageDao().getAllMessagesForConversation(conversationId)
            .filter { it.isActiveBranch }
            .sortedBy { it.timestamp }
        val target = all.find { it.id == messageId } ?: return false
        val conversation = getConversation(conversationId)
        val turnIndex = all.count { it.role == "user" && it.timestamp < target.timestamp } + 1
        val shouldInject = isInjectionTurn(turnIndex, conversation?.injectionInterval ?: 25)
        val matchedWorld = if (shouldInject) {
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
        // 官网原消息分支是否已删除（best-effort，编辑同步替换用）
        var officialReplaced = false
        
        try {
            if (cfg.provider == "deepseek_web") {
                val webRepo = deepSeekWebRepo
                // 官网编辑走专用端点 /api/v0/chat/edit_message：用官方 message_id 定位被编辑的用户消息，
                // 服务端原地替换该消息并重新生成回复（completion + parent/current_message_id 只会追加发出）。
                val targetOfficialId = webRepo?.findMessageIdByContent(conversationId, target.content, role = "user")
                officialReplaced = targetOfficialId != null
                // 找不到被编辑消息的官网 id 时回退到 completion 追加（旧行为），避免直接失败
                val parentOverride = if (targetOfficialId == null) {
                    all.filter { it.timestamp < target.timestamp }.lastOrNull()?.let { prevMsg ->
                        webRepo?.findMessageIdByContent(conversationId, prevMsg.content, role = null)
                    }
                } else null
                streamWebReply(
                    conversationId = conversationId,
                    prompt = buildWebPrompt(conversation, newContent, matchedWorld, null, shouldInject),
                    assistantId = assistantId,
                    assistantParentId = newUserMsgId,
                    parentOverride = parentOverride,
                    editMessageId = targetOfficialId,
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
            return officialReplaced
        } catch (e: Exception) {
            if (sb.isEmpty()) {
                db.messageDao().deleteMessages(conversationId, listOf(assistantId))
            }
            onError(e)
        }
        return officialReplaced
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

        // 压缩模型可能属于其它 API 配置：必须用该模型所属配置的 baseUrl/apiKey 调总结接口，
        // 否则用错了密钥/端点会报 401。找不到归属配置时回退到聊天配置。
        val summaryCfg = if (compressModel == cfg.model) {
            cfg
        } else {
            db.savedModelDao().getAllOnce().firstOrNull { it.model == compressModel }
                ?.apiProfileId?.let { pid -> db.apiProfileDao().getById(pid) }
                ?: cfg
        }

        val summary = summarize(summaryCfg, compressModel, toCompress)
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
        val locale = if (messages.any { it.content.contains(Regex("[\\u4e00-\\u9fa5]")) }) "中文" else "English"
        val conversationText = messages.joinToString("\n\n") {
            "${if (it.role == "user") "用户" else "助手"}: ${it.content}"
        }
        val prompt = "You are a conversation compression assistant. Compress the following conversation into a concise summary.\n\n" +
            "Requirements:\n" +
            "1. Preserve key facts, decisions, and important context that would be needed to continue the conversation\n" +
            "2. Keep the summary in the same language as the original conversation\n" +
            "3. Target approximately 4000 tokens\n" +
            "4. Output the summary directly without any explanations or meta-commentary\n" +
            "5. Format the summary as context information that can be used to continue the conversation\n" +
            "6. Use $locale language\n" +
            "7. Start the output with a clear indicator that this is a summary (e.g., \"[Summary of previous conversation]\" or equivalent in the target language)\n\n" +
            "{additional_context}\n\n" +
            "<conversation>\n$conversationText\n</conversation>"
        val request = ChatRequest(
            model = model,
            messages = listOf(
                Message("system", "你是对话压缩助手，输出严格遵循用户要求的格式。"),
                Message("user", prompt)
            ),
            stream = false,
            max_tokens = 4000
        )
        if (profile.protocol == "anthropic") {
            return anthropicClient.completeText(profile.baseUrl, profile.apiKey, request)
        }
        val url = "${profile.baseUrl.trimEnd('/')}/chat/completions"
        // 用带 Authorization 的原始 okhttp 请求（Retrofit 的 ApiService 不带任何请求头，
        // 直接调用会因缺少 Bearer 凭证而 401）。同步 execute() 是阻塞调用，
        // 必须在 IO 线程执行，否则会卡住主线程导致"点了没反应"。
        return withContext(Dispatchers.IO) {
            val json = com.google.gson.Gson().toJson(request)
            val okhttp = com.yourapp.chat.ChatApplication.instance.okHttpClient
            val req = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${profile.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
            okhttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("总结失败 HTTP ${resp.code}: ${resp.body?.string().orEmpty().take(200)}")
                }
                val text = resp.body?.string() ?: ""
                val parsed = com.google.gson.JsonParser.parseString(text).asJsonObject
                val choices = parsed.getAsJsonArray("choices")
                if (choices.isEmpty()) throw IllegalStateException("总结接口无返回")
                choices[0].asJsonObject
                    .getAsJsonObject("message")
                    ?.get("content")
                    ?.asString
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "(无总结)"
            }
        }
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
     * @param label 该图片的名称/序号，写进提示词让模型明确"正在看哪张"，避免多图/连续识图时
     * 视觉模型或缓存通道按上一张图/上一次提示词作答。
     */
    suspend fun describeImage(
        profile: ApiProfileEntity,
        visionModelName: String,
        imageDataUrl: String,
        label: String = "你上传的图片"
    ): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        // 识图模型可能属于其它 API 配置：必须用该模型所属配置的 baseUrl/apiKey 调识图接口，
        // 否则用错密钥/端点会 401/404（与压缩模型同款处理）。找不到归属配置时回退到聊天配置。
        val visionCfg = db.savedModelDao().getAllOnce().firstOrNull { it.model == visionModelName }
            ?.apiProfileId?.let { pid -> db.apiProfileDao().getById(pid) }
            ?: profile
        val baseUrl = visionCfg.baseUrl.trimEnd('/')
        val ocrPrompt = "这是用户最新发送的一张真实图片【$label】。请只针对这张图片（忽略任何之前的图片或之前的描述），\n\n" +
            "Extract all visible text from the image and also describe any non-text elements (icons, shapes, arrows, objects, symbols, or emojis).\n\n" +
            "For each element, specify:\n" +
            "- The exact text (for text) or a short description (for non-text).\n" +
            "- For document-type content, please use markdown and latex format.\n" +
            "- If there are objects like buildings or characters, try to identify who they are.\n" +
            "- Its approximate position in the image (e.g., 'top left', 'center right', 'bottom middle').\n" +
            "- Its spatial relationship to nearby elements (e.g., 'above', 'below', 'next to', 'on the left of').\n\n" +
            "Keep the original reading order and layout structure as much as possible.\n" +
            "Do not interpret or translate—only transcribe and describe what is visually present."
        val body = com.google.gson.JsonObject().apply {
            addProperty("model", visionModelName)
            val contentArr = com.google.gson.JsonArray()
            val textPart = com.google.gson.JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", ocrPrompt)
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
            .addHeader("Authorization", "Bearer ${visionCfg.apiKey}")
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