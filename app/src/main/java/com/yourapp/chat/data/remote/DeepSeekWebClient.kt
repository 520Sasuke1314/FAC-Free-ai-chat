package com.yourapp.chat.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * DeepSeek 官网（chat.deepseek.com）网页版 API 客户端。
 * 协议逆向参考：github.com/CJackHwang/ds2api
 * 注意：使用官网接口有封号风险，仅建议小号使用。
 */
class DeepSeekWebClient(private val okHttpClient: OkHttpClient) {

    private val gson = com.google.gson.Gson()
    private val jsonMedia = "application/json".toMediaTypeOrNull()
    // fragment 索引提取：response/fragments/-1 或 fragments/0 等形式
    private val FRAG_IDX_RE = Regex("(?:response/)?fragments/(-?\\d+)(?:/.*)?")
    // 流式读取专用 client：空闲读超时 60s，避免接口保持连接但不返回数据时
    // readUtf8Line() 永久阻塞导致 UI 一直显示省略号。
    private val streamingClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        const val BASE = "https://chat.deepseek.com/api/v0"
        const val LOGIN = "$BASE/users/login"
        const val CREATE_SESSION = "$BASE/chat_session/create"
        const val CREATE_POW = "$BASE/chat/create_pow_challenge"
        const val COMPLETION = "$BASE/chat/completion"
        const val CONTINUE = "$BASE/chat/continue"
        const val REGENERATE = "$BASE/chat/regenerate"

        val DEFAULT_HEADERS = mapOf(
            // 注意：不能设置 Host 头，OkHttp 禁止手动覆盖（会抛异常）
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "accept-charset" to "UTF-8",
            "User-Agent" to "DeepSeek/2.0.3 Android/35",
            "x-client-platform" to "android",
            "x-client-version" to "2.0.3",
            "x-client-locale" to "zh_CN"
        )
    }

    /** 账号密码登录，返回 token */
    suspend fun login(email: String, password: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("device_id", "deepseek_to_api")
            .put("os", "android")
            .toString()
        val resp = post(LOGIN, body, null)
        val json = JSONObject(resp)
        if (json.optInt("code") != 0) throw IOException("登录失败: ${json.optString("msg")}")
        val data = json.optJSONObject("data") ?: throw IOException("登录失败: 响应格式错误")
        if (data.optInt("biz_code") != 0) throw IOException("登录失败: ${data.optString("biz_msg")}")
        val bizData = data.optJSONObject("biz_data") ?: throw IOException("登录失败: 无数据")
        val user = bizData.optJSONObject("user") ?: throw IOException("登录失败: 无用户")
        val token = user.optString("token")
        if (token.isBlank()) throw IOException("登录失败: 未返回 token")
        token
    }

    /** 创建会话，返回 session_id */
    suspend fun createSession(token: String): String = withContext(Dispatchers.IO) {
        val resp = post(CREATE_SESSION, "{\"agent\":\"chat\"}", token)
        val json = JSONObject(resp)
        checkBiz(json, "创建会话失败")
        val bizData = json.getJSONObject("data").optJSONObject("biz_data")
        val id = bizData?.optString("id")
        if (!id.isNullOrBlank()) return@withContext id
        val chatSession = bizData?.optJSONObject("chat_session")
        val sid = chatSession?.optString("id")
        if (!sid.isNullOrBlank()) return@withContext sid
        throw IOException("创建会话失败: 未返回会话 ID")
    }

    /** 获取 PoW 挑战并求解，返回 x-ds-pow-response 头值 */
    suspend fun solvePow(token: String, targetPath: String = "/api/v0/chat/completion"): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("target_path", targetPath).toString()
        val resp = post(CREATE_POW, body, token)
        val json = JSONObject(resp)
        checkBiz(json, "获取 PoW 失败")
        val bizData = json.getJSONObject("data").optJSONObject("biz_data")
        val challenge = bizData?.optJSONObject("challenge")
            ?: throw IOException("获取 PoW 失败: 无 challenge")
        val algorithm = challenge.optString("algorithm")
        if (algorithm != "DeepSeekHashV1") throw IOException("不支持的 PoW 算法: $algorithm")
        val challengeStr = challenge.optString("challenge")
        val salt = challenge.optString("salt")
        val expireAt = challenge.optLong("expire_at", 1680000000)
        val difficulty = challenge.optLong("difficulty", 144000)
        val signature = challenge.optString("signature")
        val target = challenge.optString("target_path")

        val answer = DeepSeekPow.solve(challengeStr, salt, expireAt, difficulty)
        val headerJson = JSONObject()
            .put("algorithm", algorithm)
            .put("challenge", challengeStr)
            .put("salt", salt)
            .put("answer", answer)
            .put("signature", signature)
            .put("target_path", target)
            .toString()
        android.util.Base64.encodeToString(headerJson.toByteArray(), android.util.Base64.NO_WRAP)
    }

    /**
     * 流式对话。返回的 Flow 逐段发出正文文本。
     * @param parentMessageId 上一条回复的 message_id（用于同一会话内连贯上下文），null = 新话题
     * @param searchProvider 搜索引擎（bing/sogou/360/baidu/google/duckduckgo 等，官网可能忽略）
     * @param onMessageId 回调返回本次回复的 message_id（供下一条消息作 parent）
     * @param onFinished 结束回调，参数为是否风控
     */
    suspend fun chatStream(
        token: String,
        sessionId: String,
        prompt: String,
        thinkingEnabled: Boolean = true,
        searchEnabled: Boolean = false,
        searchProvider: String? = null,
        parentMessageId: Long? = null,
        pow: String? = null,
        onMessageId: (Long?) -> Unit = {},
        onRequestMessageId: (Long?) -> Unit = {},
        onThinking: (String) -> Unit = {},
        onFinished: (contentFilter: Boolean) -> Unit = {}
    ): Flow<String> = flow {
        val solvedPow = pow ?: solvePow(token)
        logDiag("官网chatStream: sessionId=$sessionId parentId=$parentMessageId 思考=$thinkingEnabled 联网=$searchEnabled")
        val body = JSONObject()
            .put("chat_session_id", sessionId)
            .put("model_type", "default")
            .put("parent_message_id", parentMessageId ?: JSONObject.NULL)
            .put("prompt", prompt)
            .put("ref_file_ids", org.json.JSONArray())
            .put("thinking_enabled", thinkingEnabled)
            .put("search_enabled", searchEnabled)
        // 官网不识别 search_provider 字段，盲目添加会导致请求被拒（400）。
        // 搜索引擎仅通过 search_enabled 开关控制，provider 保留给未来协议支持。
        val bodyStr = body.toString()

        // 网络请求在 IO 线程执行，SSE 解析与 emit 留在 flow 上下文（与 collect 同一线程）。
        val req = buildRequest(COMPLETION, bodyStr, token, solvedPow)
        val response = withContext(Dispatchers.IO) {
            streamingClient.newCall(req).execute()
        }
        logDiag("官网响应码=${response.code} ${response.message}")
        if (!response.isSuccessful) {
            throw IOException("API error: ${response.code} ${response.message}")
        }
        emitAll(parseWebStream(response, thinkingEnabled, onMessageId, onRequestMessageId, onThinking, onFinished))
    }

    /**
     * 解析 DeepSeek 官网 SSE 流（completion / regenerate 共用同一协议）。
     * 网络请求与读行在 IO 线程执行，解析与 emit 留在 flow 上下文（与 collect 同一线程），
     * 从机制上保证 emit 与 collect 上下文一致，彻底避免跨线程 emit 违规。
     */
    private fun parseWebStream(
        response: okhttp3.Response,
        thinkingEnabled: Boolean,
        onMessageId: (Long?) -> Unit,
        onRequestMessageId: (Long?) -> Unit,
        onThinking: (String) -> Unit,
        onFinished: (contentFilter: Boolean) -> Unit
    ): Flow<String> = flow {
        var finished = false
        var lastMessageId: Long? = null
        var lastRequestId: Long? = null
        val source = response.body?.source() ?: throw IOException("Empty body")
        // —— 解析状态（参考 ds2api internal/sse/parser.go 的状态机）——
        val fragments = ArrayList<JsonObject>()         // 片段对象列表（保序）
        var currentType = if (thinkingEnabled) "thinking" else "text" // 当前默认内容类型
        val thinkingBuf = StringBuilder()               // 用于探测 </think> 切换点
        val thinkClose = Regex("(?i)</\\s*think\\s*>")

        fun resolveIdx(raw: Int): Int = if (raw < 0) fragments.size + raw else raw

        fun fragType(f: JsonObject?): String =
            f?.takeIf { it.has("type") && it.get("type").isJsonPrimitive }?.get("type")?.asString?.uppercase() ?: ""

        /** 输出一段内容：THINK 进思考回调，其余进正文；发现 </think> 后强制切正文 */
        suspend fun emitContent(text: String, type: String) {
            if (text.isEmpty()) return
            if (type == "THINK" || type == "THINKING") {
                onThinking(text)
                thinkingBuf.append(text)
                if (currentType != "text" && thinkClose.containsMatchIn(thinkingBuf)) currentType = "text"
            } else {
                emit(text)
            }
        }

        /** 追加 fragment 列表（APPEND 追加 / SET 全量替换），按其类型更新 currentType、输出内联内容 */
        suspend fun handleFragmentAppend(arr: JsonArray) {
            for (e in arr) {
                if (e !is JsonObject) continue
                fragments.add(e)
                val t = fragType(e)
                val c = if (e.has("content") && e.get("content").isJsonPrimitive) e.get("content").asString else ""
                when (t) {
                    "THINK", "THINKING" -> {
                        currentType = "thinking"
                        emitContent(c, "THINK")
                    }
                    "RESPONSE" -> {
                        currentType = "text"
                        emitContent(c, "text")
                    }
                    else -> emitContent(c, "text")
                }
            }
        }

        /**
         * 路径补丁分派（参考 ds2api ParseSSEChunkForContentDetailed）。
         * 关键：content 增量路径索引为「相对末尾」（-1 = 最后一个 fragment），
         * 且内容按「当前类型」而非片段类型路由，避免上游思考结束后仍从 fragments 路径下发正文。
         */
        suspend fun applyFragmentOp(p: String, value: JsonElement) {
            // 1) fragments 列表（APPEND 追加 / SET 全量替换，内容输出逻辑一致）
            if ((p == "response/fragments" || p == "fragments") && value.isJsonArray) {
                handleFragmentAppend(value.asJsonArray)
                return
            }
            // 2) 显式内容路径：response/content → 正文；response/thinking_content → 思考
            if (p == "response/content" || p == "response/thinking_content") {
                val isThinking = p == "response/thinking_content"
                if (isThinking) currentType = "thinking" else currentType = "text"
                val text = when {
                    value.isJsonPrimitive -> value.asString
                    value.isJsonObject -> {
                        val obj = value.asJsonObject
                        if (obj.has("text") && obj.get("text").isJsonPrimitive) obj.get("text").asString
                        else if (obj.has("content") && obj.get("content").isJsonPrimitive) obj.get("content").asString
                        else ""
                    }
                    else -> ""
                }
                emitContent(text, if (isThinking) "THINK" else "text")
                return
            }
            // 3) 片段内容增量：fragments/<idx>/content（负数 = 相对末尾；o 字段可能缺失，不做 op 限制）
            if (value.isJsonPrimitive && p.contains("fragments/") && p.endsWith("/content")) {
                val rel = fragmentIndexFromPath(p) ?: return
                val abs = resolveIdx(rel)
                val t = fragType(fragments.getOrNull(abs))
                emitContent(value.asString, if (t == "THINK" || t == "THINKING") "THINK" else currentType)
                return
            }
            // 4) 单个片段新建/替换：fragments/<idx> SET（对象），内联 content 直接输出
            if (value.isJsonObject && p.contains("fragments/") && !p.endsWith("/content") && !p.endsWith("/status") && !p.endsWith("/thinking_content")) {
                val rel = fragmentIndexFromPath(p) ?: return
                val abs = resolveIdx(rel)
                val obj = value.asJsonObject
                val t = fragType(obj)
                if (t == "THINK" || t == "THINKING") currentType = "thinking"
                if (t == "RESPONSE") currentType = "text"
                if (abs in fragments.indices) {
                    fragments[abs] = obj
                } else if (abs == fragments.size) {
                    fragments.add(obj)
                }
                if (obj.has("content") && obj.get("content").isJsonPrimitive) {
                    emitContent(obj.get("content").asString, if (t == "THINK" || t == "THINKING") "THINK" else "text")
                }
                return
            }
        }

        /** 处理一组相对 response 的 patch 子操作（顶层 v 数组 / BATCH） */
        suspend fun processSubPatches(arr: JsonArray) {
            for (e in arr) {
                if (e !is JsonObject) continue
                if (!e.has("p") || !e.get("p").isJsonPrimitive) continue
                if (!e.has("v")) continue
                val subP = e.get("p").asString
                val subV = e.get("v")
                applyFragmentOp(subP, subV)
                if (isFinished(subP, subV)) finished = true
            }
        }

        try {
            try {
                while (!finished) {
                    val line = withContext(Dispatchers.IO) {
                        if (source.exhausted()) null else source.readUtf8Line()
                    } ?: break
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("data:")) continue
                    val data = trimmed.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val chunk = try {
                        JsonParser.parseString(data).asJsonObject
                    } catch (e: Exception) {
                        continue
                    }
                    // 提取 message_id（会话连贯用）与 request_message_id（用户消息官方 id）
                    extractMessageId(chunk)?.let { lastMessageId = it }
                    extractRequestMessageId(chunk)?.let { lastRequestId = it }

                    when {
                        // 顶层 v 数组（终态/批量收口，无 o 字段）：{"v":[{"p":...}]}
                        !chunk.has("p") && chunk.has("v") && chunk.get("v").isJsonArray ->
                            processSubPatches(chunk.getAsJsonArray("v"))
                        // 路径补丁：{"p":"response/...","o":"APPEND/SET/BATCH","v":...}
                        chunk.has("p") && chunk.get("p").isJsonPrimitive && chunk.has("v") -> {
                            val p = chunk.get("p").asString
                            val o = if (chunk.has("o") && chunk.get("o").isJsonPrimitive) chunk.get("o").asString else "SET"
                            val v = chunk.get("v")
                            if (o == "BATCH" && v.isJsonArray) {
                                processSubPatches(v.asJsonArray)
                            } else {
                                applyFragmentOp(p, v)
                                if (isFinished(p, v)) finished = true
                            }
                        }
                    }
                    // 初始 envelope：{"v":{"response":{"fragments":[...]}}}（首个片段内联下发）
                    val initialFrags = extractInitialFragments(chunk)
                    if (initialFrags != null) handleFragmentAppend(initialFrags)
                    // 无路径 v：字符串按 currentType 路由；对象取 text/content 字段；数组已在上面处理
                    if (chunk.has("v") && !chunk.has("p")) {
                        val v = chunk.get("v")
                        when {
                            v.isJsonPrimitive -> emitContent(v.asString, currentType)
                            v.isJsonObject -> {
                                val obj = v.asJsonObject
                                val text = if (obj.has("text") && obj.get("text").isJsonPrimitive) obj.get("text").asString
                                else if (obj.has("content") && obj.get("content").isJsonPrimitive) obj.get("content").asString
                                else null
                                if (!text.isNullOrEmpty()) {
                                    emitContent(text, currentType)
                                } else if (obj.has("response") && obj.get("response").isJsonObject) {
                                    val resp = obj.getAsJsonObject("response")
                                    if (resp.has("fragments") && resp.get("fragments").isJsonArray) {
                                        handleFragmentAppend(resp.getAsJsonArray("fragments"))
                                    }
                                }
                            }
                        }
                    }
                    // 兼容字段：thinking / thinking_content / delta.reasoning_content
                    extractLegacyThinking(chunk)?.let { onThinking(it) }
                }
            } catch (e: java.net.SocketTimeoutException) {
                throw IOException("官网响应超时（60 秒内未收到任何数据），请检查网络后重试。")
            }
        } finally {
            response.close()
        }
        onMessageId(lastMessageId)
        onRequestMessageId(lastRequestId)
        onFinished(false)
    }

    /**
     * 官网重新生成（POST /api/v0/chat/regenerate）。
     * 用 child_message_id 定位被重新生成的那条 AI 回复：服务器复用原用户消息、直接替换该回复，
     * 不会像 completion 通道那样把用户消息再发一遍。
     * @param childMessageId 被重生成那条 AI 回复的官方 message_id
     */
    suspend fun regenerateStream(
        token: String,
        sessionId: String,
        childMessageId: Long,
        thinkingEnabled: Boolean = true,
        searchEnabled: Boolean = false,
        onMessageId: (Long?) -> Unit = {},
        onRequestMessageId: (Long?) -> Unit = {},
        onThinking: (String) -> Unit = {},
        onFinished: (contentFilter: Boolean) -> Unit = {}
    ): Flow<String> = flow {
        val solvedPow = solvePow(token)
        logDiag("官网regenerate: sessionId=$sessionId childId=$childMessageId 思考=$thinkingEnabled 联网=$searchEnabled")
        val body = JSONObject()
            .put("chat_session_id", sessionId)
            .put("child_message_id", childMessageId)
            .put("thinking_enabled", thinkingEnabled)
            .put("search_enabled", searchEnabled)
            .put("user_options", JSONObject.NULL)
        val req = buildRequest(REGENERATE, body.toString(), token, solvedPow)
        val response = withContext(Dispatchers.IO) {
            streamingClient.newCall(req).execute()
        }
        logDiag("官网regenerate响应码=${response.code} ${response.message}")
        if (!response.isSuccessful) {
            throw IOException("API error: ${response.code} ${response.message}")
        }
        emitAll(parseWebStream(response, thinkingEnabled, onMessageId, onRequestMessageId, onThinking, onFinished))
    }

    /** 从 SSE chunk 提取回复 message_id（供 parent_message_id 链接） */
    private fun extractMessageId(chunk: JsonObject): Long? {
        // 顶层 {"response_message_id": 123}
        if (chunk.has("response_message_id") && chunk.get("response_message_id").isJsonPrimitive) {
            return runCatching { chunk.get("response_message_id").asLong }.getOrNull()
        }
        // {"v":{"response":{"message_id":123}}}
        if (chunk.has("v") && chunk.get("v").isJsonObject) {
            val v = chunk.getAsJsonObject("v")
            if (v.has("response") && v.get("response").isJsonObject) {
                val resp = v.getAsJsonObject("response")
                if (resp.has("message_id") && resp.get("message_id").isJsonPrimitive) {
                    return runCatching { resp.get("message_id").asLong }.getOrNull()
                }
            }
        }
        return null
    }

    /** 提取初始快照内联片段：{"v":{"response":{"fragments":[...]}}}（首个 chunk 附带完整消息骨架） */
    private fun extractInitialFragments(chunk: JsonObject): JsonArray? {
        if (chunk.has("v") && chunk.get("v").isJsonObject) {
            val v = chunk.getAsJsonObject("v")
            if (v.has("response") && v.get("response").isJsonObject) {
                val resp = v.getAsJsonObject("response")
                if (resp.has("fragments") && resp.get("fragments").isJsonArray) {
                    return resp.getAsJsonArray("fragments")
                }
            }
        }
        return null
    }

    /** 从 ready 事件提取用户消息官方 id：{"request_message_id": 123}（重生成/编辑时作为正确父消息） */
    private fun extractRequestMessageId(chunk: JsonObject): Long? {
        if (chunk.has("request_message_id") && chunk.get("request_message_id").isJsonPrimitive) {
            return runCatching { chunk.get("request_message_id").asLong }.getOrNull()
        }
        return null
    }

    /** 从 path 中提取 fragment 索引（支持 response/fragments/-1、fragments/0 等形式） */
    private fun fragmentIndexFromPath(p: String): Int? {
        return FRAG_IDX_RE.find(p)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * 判断 path+value 是否为整条流结束。
     * 只认 response/status 与 status 两个精确路径（以及顶层裸 "FINISHED"）。
     * 注意：绝不能按「contains status」判断——response/fragments/-1/status=FINISHED
     * 只是思考片段自身的结束状态，误判会把整条流提前终止，导致正文永远收不到（一直三个点）。
     */
    private fun isFinished(p: String, v: JsonElement): Boolean {
        if (!v.isJsonPrimitive) return false
        val s = v.asString
        if (p != "response/status" && p != "status" && p != "") return false
        return s == "FINISHED" || s == "CONTENT_FILTER"
    }

    /** 兼容非 fragments 通道的思考字段（其他协议的 delta 等） */
    private fun extractLegacyThinking(chunk: JsonObject): String? {
        if (chunk.has("thinking") && chunk.get("thinking").isJsonPrimitive) {
            return chunk.get("thinking").asString
        }
        if (chunk.has("thinking_content") && chunk.get("thinking_content").isJsonPrimitive) {
            return chunk.get("thinking_content").asString
        }
        if (chunk.has("v") && chunk.get("v").isJsonObject) {
            val v = chunk.getAsJsonObject("v")
            if (v.has("delta") && v.get("delta").isJsonObject) {
                val delta = v.getAsJsonObject("delta")
                if (delta.has("reasoning_content") && delta.get("reasoning_content").isJsonPrimitive) {
                    return delta.get("reasoning_content").asString
                }
            }
        }
        return null
    }

    private fun post(url: String, body: String, token: String?): String {
        val req = buildRequest(url, body, token, null)
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}: ${resp.message}")
            }
            return resp.body?.string() ?: throw IOException("Empty response")
        }
    }

    private fun get(url: String, token: String?): String {
        val builder = Request.Builder().url(url).get()
        for ((k, v) in DEFAULT_HEADERS) builder.header(k, v)
        token?.let { builder.header("authorization", "Bearer $it") }
        okHttpClient.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}: ${resp.message}")
            }
            return resp.body?.string() ?: throw IOException("Empty response")
        }
    }

    /** 官网消息（含 message_id / parent_id，用于接续上下文） */
    data class WebMessage(
        val id: Long?,
        val parentId: Long?,
        val role: String,
        val content: String
    )

    /**
     * 拉取会话历史消息（官网网页版历史接口）。
     * 正确接口为 GET /chat/history_messages?chat_session_id=xxx；
     * 旧代码用的 POST /chat/history 不存在，会导致同步永远拿不到数据。
     */
    suspend fun fetchHistory(token: String, sessionId: String): List<WebMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE/chat/history_messages?chat_session_id=" +
                java.net.URLEncoder.encode(sessionId, "UTF-8")
            parseHistory(get(url, token))
        }.getOrDefault(emptyList())
    }

    private fun parseHistory(jsonText: String): List<WebMessage> {
        val out = ArrayList<WebMessage>()
        val root = JSONObject(jsonText)
        val data = root.optJSONObject("data")
        val bizData = data?.optJSONObject("biz_data")
        // 官网 history_messages 的数组键是 chat_messages（fragments 拆内容、role 为大写）；
        // 兼容旧结构 chat_history / messages / history
        val arr = bizData?.optJSONArray("chat_messages")
            ?: data?.optJSONArray("chat_messages")
            ?: bizData?.optJSONArray("messages")
            ?: bizData?.optJSONArray("chat_history")
            ?: bizData?.optJSONArray("history")
            ?: data?.optJSONArray("messages")
            ?: data?.optJSONArray("chat_history")
            ?: data?.optJSONArray("history")
            ?: root.optJSONArray("chat_messages")
            ?: root.optJSONArray("messages")
            ?: root.optJSONArray("chat_history")
        if (arr == null) return out

        // 解析出带 id / parent_id 的消息树节点；没有 id 的按原顺序直接输出
        data class Node(val id: Long, val parentId: Long, val role: String, val content: String, val ts: Long)
        val nodes = ArrayList<Node>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val rawRole = item.optString("role").ifBlank {
                if (item.optString("type") == "user") "USER" else "ASSISTANT"
            }
            val role = rawRole.trim().lowercase().let {
                when (it) { "user" -> "user"; "system" -> "system"; else -> "assistant" }
            }
            var content = item.optString("content").ifBlank { item.optString("text") }
            if (content.isBlank()) {
                // 助手正文在 fragments 里：type=REQUEST/RESPONSE/TEXT 拼接，跳过 THINK/TIP
                item.optJSONArray("fragments")?.let { frags ->
                    val sb = StringBuilder()
                    for (j in 0 until frags.length()) {
                        val f = frags.optJSONObject(j) ?: continue
                        val ft = f.optString("type")
                        if (ft == "THINK" || ft == "TIP" || ft == "thinking") continue
                        sb.append(f.optString("content"))
                    }
                    if (sb.isNotEmpty()) content = sb.toString()
                }
            }
            if (content.isBlank()) continue
            val id = item.optString("message_id").toLongOrNull()
                ?: item.optLong("message_id", item.optLong("id", 0L))
            val parentId = item.optString("parent_id").toLongOrNull()
                ?: item.optLong("parent_id", -1L)
            val ts = item.optLong("inserted_at", item.optLong("created_at", 0L))
            if (id == 0L || parentId < 0) {
                out.add(WebMessage(id.takeIf { it != 0L }, parentId.takeIf { it >= 0 }, role, content))
                continue
            }
            nodes.add(Node(id, parentId, role, content, ts))
        }
        if (nodes.isEmpty()) return out

        // 优先按官网「当前路径」回溯（编辑/分支只保留当前看到的版本）
        val byId = nodes.associateBy { it.id }
        val currentId = (bizData ?: data)
            ?.optJSONObject("chat_session")
            ?.optLong("current_message_id", -1L)
        val ordered = ArrayList<Node>()
        if (currentId != null && byId.containsKey(currentId)) {
            val seen = HashSet<Long>()
            var cur: Node? = byId[currentId]
            while (cur != null && seen.add(cur.id)) {
                ordered.add(cur)
                cur = byId[cur.parentId]
            }
            ordered.reverse()
        } else {
            // 回退：从根出发，每个分支点取最新 child
            val ids = nodes.map { it.id }.toHashSet()
            val roots = nodes.filter { it.parentId !in ids }.sortedBy { it.ts }
            val children = HashMap<Long, MutableList<Node>>()
            for (n in nodes) if (n.parentId in ids) children.getOrPut(n.parentId) { ArrayList() }.add(n)
            for (kids in children.values) kids.sortBy { it.ts }
            val visited = HashSet<Long>()
            for (r in roots) {
                var cur: Node? = r
                while (cur != null && visited.add(cur.id)) {
                    ordered.add(cur)
                    cur = children[cur.id]?.lastOrNull()
                }
            }
        }
        for (n in ordered) out.add(WebMessage(n.id, n.parentId, n.role, n.content))
        return out
    }

    /**
     * 在官网删除单条消息（best-effort）。
     * 官网消息级删除端点未公开确认，采用 chat/message/delete 尝试；
     * 删除成功后返回 true，接口不兼容/失败返回 false（不抛异常）。
     */
    suspend fun deleteMessage(token: String, sessionId: String, messageId: Long): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("chat_session_id", sessionId)
                    .put("message_id", messageId)
                    .toString()
                val resp = post("$BASE/chat/message/delete", body, token)
                val json = JSONObject(resp)
                json.optInt("code", -1) == 0
            }.getOrDefault(false)
        }

    /**
     * 在官网重命名会话标题（best-effort）。用于官网对话标题与本地一致。
     */
    suspend fun renameSession(token: String, sessionId: String, title: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("chat_session_id", sessionId)
                    .put("title", title)
                    .toString()
                val resp = post("$BASE/chat_session/save", body, token)
                val json = JSONObject(resp)
                json.optInt("code", -1) == 0
            }.getOrDefault(false)
        }

    /** 官网网页端的会话信息（列表页用） */
    data class SessionInfo(
        val id: String,
        val title: String,
        val updatedAt: Double
    )

    /**
     * 拉取官网会话列表（网页版 / ds2api 同款接口）。
     * GET /chat_session/fetch_page?lte_cursor.pinned=false
     * 返回按服务端顺序排列的会话，含 id / title / updated_at。
     */
    suspend fun fetchSessions(token: String): List<SessionInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE/chat_session/fetch_page?lte_cursor.pinned=false"
            val resp = get(url, token)
            val root = JSONObject(resp)
            val bizData = root.optJSONObject("data")?.optJSONObject("biz_data")
            val arr = bizData?.optJSONArray("chat_sessions") ?: return@runCatching emptyList()
            val list = ArrayList<SessionInfo>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isNotBlank()) {
                    list.add(
                        SessionInfo(
                            id = id,
                            title = item.optString("title"),
                            updatedAt = item.optDouble("updated_at", 0.0)
                        )
                    )
                }
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun buildRequest(url: String, body: String, token: String?, pow: String?): Request {
        val builder = Request.Builder().url(url).post(body.toRequestBody(jsonMedia))
        for ((k, v) in DEFAULT_HEADERS) builder.header(k, v)
        token?.let { builder.header("authorization", "Bearer $it") }
        pow?.let { builder.header("x-ds-pow-response", it) }
        return builder.build()
    }

    /** 诊断日志：写入崩溃日志文件 */
    private fun logDiag(text: String) {
        try {
            val app = com.yourapp.chat.ChatApplication.instance
            com.yourapp.chat.util.CrashLog.append(app, "[DeepSeekWebClient] $text")
        } catch (_: Exception) {
        }
    }

    private fun checkBiz(json: JSONObject, errPrefix: String) {
        if (json.optInt("code") != 0) throw IOException("$errPrefix: ${json.optString("msg")}")
        val data = json.optJSONObject("data")
        if (data != null && data.optInt("biz_code") != 0) {
            throw IOException("$errPrefix: ${data.optString("biz_msg")}")
        }
    }
}
