package com.yourapp.chat.data.repository

import android.content.Context
import com.yourapp.chat.data.remote.DeepSeekWebClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * DeepSeek 官网（网页版）对话仓库。
 * 直接使用官网账号 token 调用网页版接口，与官网网页端共享同一会话（会话连贯同步）。
 * 警告：使用官网接口可能违反 DeepSeek 服务条款，有封号风险，建议使用小号。
 *
 * 会话隔离：官网 session_id / parent_message_id 按 conversationId 分别存储
 * （key 带 `_<conversationId>` 后缀），避免多个本地对话共用一套官网会话导致串流；
 * 读取时回退到旧版全局 key，实现平滑迁移。
 */
class DeepSeekWebRepository(
    private val context: Context,
    private val client: DeepSeekWebClient
) {

    private val prefs by lazy {
        context.getSharedPreferences("deepseek_web", Context.MODE_PRIVATE)
    }

    fun hasToken(): Boolean = !token().isNullOrBlank()

    fun token(): String? = prefs.getString("token", null)

    private fun sessionKey(cid: Long) = "session_id_$cid"
    private fun parentKey(cid: Long) = "parent_message_id_$cid"

    private fun sessionId(cid: Long): String? =
        prefs.getString(sessionKey(cid), null) ?: prefs.getString("session_id", null)

    private fun parentMessageId(cid: Long): Long? =
        prefs.getLong(parentKey(cid), -1L).takeIf { it > 0 }
            ?: prefs.getLong("parent_message_id", -1L).takeIf { it > 0 }

    suspend fun login(email: String, password: String): String {
        val token = client.login(email, password)
        prefs.edit().putString("token", token).apply()
        return token
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    /**
     * 流式对话：复用该对话对应的官网会话，用 parent_message_id 链接上下文，
     * 与官网网页端共享同一段对话记录。
     * @param conversationId 本地对话 id，官网会话状态按它隔离存储
     * @param parentMessageIdOverride 非空时强制使用该官方 message_id 作父消息
     * （重生成/编辑时用于把新回复接到官网对话的正确位置）；
     * null = 使用该对话保存的 parent_message_id。
     */
    fun chatStream(
        conversationId: Long,
        prompt: String,
        thinkingEnabled: Boolean = true,
        searchEnabled: Boolean = false,
        searchProvider: String? = null,
        parentMessageIdOverride: Long? = null,
        imageDataUrls: List<String> = emptyList(),
        onThinking: (String) -> Unit = {}
    ): Flow<String> = flow {
        val tk = token() ?: throw IllegalStateException("未登录 DeepSeek 官网账号")
        var sid = sessionId(conversationId)
        if (sid.isNullOrBlank()) {
            sid = client.createSession(tk)
            prefs.edit().putString(sessionKey(conversationId), sid).apply()
        }
        // 图片：先上传到官网文件接口拿 ref_file_id，再随对话请求下发
        val refFileIds = if (imageDataUrls.isEmpty()) emptyList() else {
            imageDataUrls.map { url -> client.uploadFile(tk, url) }
        }
        val parent = parentMessageIdOverride ?: parentMessageId(conversationId)
        emitAll(
            client.chatStream(
                token = tk,
                sessionId = sid,
                prompt = prompt,
                thinkingEnabled = thinkingEnabled,
                searchEnabled = searchEnabled,
                searchProvider = searchProvider,
                parentMessageId = parent,
                refFileIds = refFileIds,
                onMessageId = { id ->
                    if (id != null) {
                        prefs.edit().putLong(parentKey(conversationId), id).apply()
                    }
                },
                onRequestMessageId = { id ->
                    if (id != null) {
                        prefs.edit().putLong("last_user_message_id_$conversationId", id).apply()
                    }
                },
                onThinking = onThinking
            )
        )
    }

    /** 该对话最近一条用户消息的官方 message_id（重生成最后一条回复时作为父消息，最可靠） */
    fun lastUserMessageId(cid: Long): Long? =
        prefs.getLong("last_user_message_id_$cid", -1L).takeIf { it > 0 }
            ?: prefs.getLong("last_user_message_id", -1L).takeIf { it > 0 }

    /**
     * 官网重新生成：走专用端点 /api/v0/chat/regenerate，用 child_message_id 定位被重生成的回复。
     * 服务器复用原用户消息、直接替换该回复，不会像 completion 通道那样把用户消息重发一遍。
     * @param childMessageId 被重生成那条 AI 回复的官方 message_id
     */
    fun regenerateStream(
        conversationId: Long,
        childMessageId: Long,
        thinkingEnabled: Boolean = true,
        searchEnabled: Boolean = false,
        onThinking: (String) -> Unit = {}
    ): Flow<String> = flow {
        val tk = token() ?: throw IllegalStateException("未登录 DeepSeek 官网账号")
        var sid = sessionId(conversationId)
        if (sid.isNullOrBlank()) {
            sid = client.createSession(tk)
            prefs.edit().putString(sessionKey(conversationId), sid).apply()
        }
        emitAll(
            client.regenerateStream(
                token = tk,
                sessionId = sid,
                childMessageId = childMessageId,
                thinkingEnabled = thinkingEnabled,
                searchEnabled = searchEnabled,
                onMessageId = { id ->
                    if (id != null) {
                        prefs.edit().putLong(parentKey(conversationId), id).apply()
                    }
                },
                onRequestMessageId = { id ->
                    if (id != null) {
                        prefs.edit().putLong("last_user_message_id_$conversationId", id).apply()
                    }
                },
                onThinking = onThinking
            )
        )
    }

    /**
     * 官网编辑消息：走专用端点 /api/v0/chat/edit_message，用官方 message_id 定位被编辑的用户消息，
     * 服务端原地替换该消息并重新生成回复（这才是官网"修改输入"的正确机制，
     * completion + parent/current_message_id 只会把新内容追加到会话末尾）。
     * @param messageId 被编辑用户消息的官方 message_id
     */
    fun editMessageStream(
        conversationId: Long,
        messageId: Long,
        prompt: String,
        thinkingEnabled: Boolean = true,
        searchEnabled: Boolean = false,
        onThinking: (String) -> Unit = {}
    ): Flow<String> = flow {
        val tk = token() ?: throw IllegalStateException("未登录 DeepSeek 官网账号")
        var sid = sessionId(conversationId)
        if (sid.isNullOrBlank()) {
            sid = client.createSession(tk)
            prefs.edit().putString(sessionKey(conversationId), sid).apply()
        }
        emitAll(
            client.editMessageStream(
                token = tk,
                sessionId = sid,
                messageId = messageId,
                prompt = prompt,
                thinkingEnabled = thinkingEnabled,
                searchEnabled = searchEnabled,
                onMessageId = { id ->
                    if (id != null) {
                        prefs.edit().putLong(parentKey(conversationId), id).apply()
                    }
                },
                onRequestMessageId = { id ->
                    if (id != null) {
                        prefs.edit().putLong("last_user_message_id_$conversationId", id).apply()
                    }
                },
                onThinking = onThinking
            )
        )
    }

    /** 该对话最近一条 AI 回复的官方 message_id（chatStream 每次成功后由 onMessageId 记录）。 */
    fun lastAssistantReplyId(cid: Long): Long? = parentMessageId(cid)

    /** 重置某对话的官网会话（新对话 = 官网新会话）；同时清理旧版全局 key 完成迁移 */
    fun resetSession(conversationId: Long) {
        prefs.edit()
            .remove(sessionKey(conversationId))
            .remove(parentKey(conversationId))
            .remove("session_id")
            .remove("parent_message_id")
            .apply()
    }

    /**
     * 官网历史正文归一化：剥离思考块（半角/全角）、【reference:N】来源标注、折叠空白。
     * 本地消息与官网消息的正文在这三处常有差异，先归一化再比对才能命中。
     */
    private fun normalizeWebContent(s: String): String = s
        .replace(Regex("(\\[思考\\]|【思考】)([\\s\\S]*?)(\\[/思考\\]|【/思考】)"), "")
        .replace(Regex("【reference:\\d+】|\\[reference:\\d+\\]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * 在官网该对话对应的会话历史里按正文匹配消息，返回其 message_id（用于重生成/编辑/删除时定位官网消息）。
     * 先精确匹配，匹配不到再宽松匹配（官网用户消息可能带注入前缀，正文结尾才是原始输入）。
     * 匹配不到返回 null。
     * @param conversationId 本地对话 id，决定查哪个官网会话
     * @param role 限定匹配的消息角色；null = 匹配任意角色
     */
    suspend fun findMessageIdByContent(conversationId: Long, content: String, role: String? = "user"): Long? {
        if (content.isBlank()) return null
        val tk = token() ?: return null
        val sid = sessionId(conversationId) ?: return null
        return runCatching {
            val needle = normalizeWebContent(content)
            if (needle.isBlank()) return@runCatching null
            val history = client.fetchHistory(tk, sid).filter { role == null || it.role == role }
            // 取「最后一次」匹配（用户消息在官网上带注入前缀，原文在结尾；避免命中更早的重复消息）
            history.lastOrNull { normalizeWebContent(it.content) == needle }?.id
                ?: history.lastOrNull { normalizeWebContent(it.content).contains(needle) }?.id
        }.getOrNull()
    }

    /**
     * 返回某条官网消息的父消息 id（编辑/改写时新消息应接在父消息之下）。
     * 直接从官网历史里取 parent_id，比按正文匹配"上一条消息"更可靠。
     */
    suspend fun findParentIdOf(conversationId: Long, messageId: Long): Long? {
        if (messageId <= 0) return null
        val tk = token() ?: return null
        val sid = sessionId(conversationId) ?: return null
        return runCatching {
            client.fetchHistory(tk, sid).firstOrNull { it.id == messageId }?.parentId
        }.getOrNull()
    }

    /**
     * 按旧回复的正文在官网找到对应消息，返回其 parent_id（重生成时新回复应接在旧回复的父消息下，
     * 即用户消息的官方 id）。官网回复正文不含注入前缀，按精确匹配即可。
     */
    suspend fun findParentIdForReply(conversationId: Long, replyBody: String): Long? {
        if (replyBody.isBlank()) return null
        val tk = token() ?: return null
        val sid = sessionId(conversationId) ?: return null
        return runCatching {
            val needle = replyBody.trim()
            client.fetchHistory(tk, sid).firstOrNull { it.role == "assistant" && it.content.trim() == needle }?.parentId
        }.getOrNull()
    }

    /**
     * 编辑同步替换：把官网某条消息及其全部后续回复删除（best-effort）。
     * 编辑用户消息前调用，随后用该消息的「官网父消息 id」作 parent 重发编辑内容，
     * 官网界面表现为"修改输入"——原消息消失、编辑后的内容替换在原位置，
     * 而不是"新内容追加发出、原消息没变"。
     * @param targetId 被编辑消息的官方 message_id（由 findMessageIdByContent 解析）
     * @return 是否至少成功删除了目标消息
     */
    suspend fun deleteMessageBranchOnOfficial(conversationId: Long, targetId: Long): Boolean {
        val tk = token() ?: return false
        val sid = sessionId(conversationId) ?: return false
        if (targetId <= 0) return false
        return runCatching {
            val history = client.fetchHistory(tk, sid)
            // 按 parentId 建树，BFS 收集目标消息及其所有后代（不删祖先，新消息要接在其后）
            val children = history.groupBy { it.parentId }
            val toDelete = LinkedHashSet<Long>()
            val queue = ArrayDeque<Long>()
            toDelete.add(targetId)
            queue.add(targetId)
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                children[id]?.forEach { node ->
                    node.id?.let { childId ->
                        if (toDelete.add(childId)) queue.add(childId)
                    }
                }
            }
            var ok = false
            toDelete.forEach { id -> ok = client.deleteMessage(tk, sid, id) || ok }
            ok
        }.getOrDefault(false)
    }

    /**
     * 在官网删除单条消息（best-effort）。官网消息级删除端点未公开确认，
     * 若接口不兼容返回 false（调用方应保留本地删除并提示用户）。
     */
    suspend fun deleteMessageOnOfficial(conversationId: Long, messageId: Long): Boolean {
        if (messageId <= 0) return false
        val tk = token() ?: return false
        val sid = sessionId(conversationId) ?: return false
        return runCatching { client.deleteMessage(tk, sid, messageId) }.getOrDefault(false)
    }

    /**
     * 从官网拉取消息记录（反向同步）并把该对话的官网会话切换到该会话。
     * 策略：先拉官网会话列表，找到与该本地对话最匹配的会话（按 title/时间启发式匹配）；
     * 如果找不到匹配的，则创建新会话（不串流）。
     * 关键：同步后把该对话的 session_id / parent_message_id 切到该会话，
     * 使该对话之后发出的下一条消息正确接在官网对话末尾。
     * 返回带 message_id 的消息列表（空 = 无记录或接口不兼容）。
     */
    suspend fun syncOfficialSession(conversationId: Long, localTitle: String? = null): List<DeepSeekWebClient.WebMessage> {
        val tk = token() ?: return emptyList()
        val sessions = client.fetchSessions(tk)
        
        // 尝试按标题匹配；如果没有标题或没匹配到，回退到按时间最近的会话
        // 但必须确保该会话还没被其他本地对话占用
        val usedSessions = getUsedSessionIds()
        val availableSessions = sessions.filter { it.id !in usedSessions || it.id == sessionId(conversationId) }
        
        var targetSession: String? = if (localTitle != null && localTitle.isNotBlank()) {
            availableSessions.firstOrNull { it.title.trim().lowercase() == localTitle.trim().lowercase() }?.id
        } else null
        if (targetSession == null) {
            targetSession = availableSessions.maxByOrNull { it.updatedAt }?.id
        }
        if (targetSession == null) {
            targetSession = sessionId(conversationId)  // 回退到当前对话已关联的会话
        }
        if (targetSession == null) {
            targetSession = client.createSession(tk).also { sid ->
                prefs.edit().putString(sessionKey(conversationId), sid).apply()
            }
        }
        
        val msgs = client.fetchHistory(tk, targetSession)
        if (msgs.isNotEmpty()) {
            val lastId = msgs.lastOrNull()?.id
            val edit = prefs.edit().putString(sessionKey(conversationId), targetSession)
            if (lastId != null) edit.putLong(parentKey(conversationId), lastId)
            edit.apply()
        }
        return msgs
    }
    
    /**
     * 获取已被其他本地对话占用的官网 sessionId 集合
     */
    private fun getUsedSessionIds(): Set<String> {
        val allKeys = prefs.all.keys
        return allKeys
            .filter { it.startsWith("session_id_") }
            .mapNotNull { prefs.getString(it, "")?.takeIf { it.isNotBlank() } }
            .toSet()
    }
}
