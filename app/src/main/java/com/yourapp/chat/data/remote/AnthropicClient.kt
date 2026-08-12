package com.yourapp.chat.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.yourapp.chat.data.remote.model.ChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Anthropic 原生 Messages API 的 SSE 流式对话客户端。
 * 端点 /messages（baseUrl 以 /v1 结尾），鉴权头 x-api-key + anthropic-version。
 * 事件类型：content_block_delta（delta.text）/ message_stop / error。
 */
class AnthropicClient(private val okHttpClient: OkHttpClient) {
    private val gson = Gson()
    private val streamingClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** @param request 复用 OpenAI 的 ChatRequest，内部会把 role=system 拆到顶层 system 字段 */
    fun chatStream(
        baseUrl: String,
        apiKey: String,
        request: ChatRequest,
        onThinking: (String) -> Unit = {}
    ): Flow<String> = flow {
        val payload = buildPayload(request)
        val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val fullUrl = "${baseUrl.trimEnd('/')}/messages"
        val httpRequest = Request.Builder()
            .url(fullUrl)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = withContext(Dispatchers.IO) {
            streamingClient.newCall(httpRequest).execute()
        }
        if (!response.isSuccessful) {
            val errBody = response.body?.string().orEmpty()
            throw IOException("Anthropic error: ${response.code} ${response.message} $errBody")
        }
        val source = response.body?.source() ?: throw IOException("Empty body")
        var emitted = false
        var accumulated = StringBuilder()
        var lastEmitTime = System.currentTimeMillis()
        try {
            try {
                while (true) {
                    val line = withContext(Dispatchers.IO) {
                        if (source.exhausted()) null else source.readUtf8Line()
                    } ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    if (!trimmed.startsWith("data:")) continue
                    val data = trimmed.removePrefix("data:").trim()
                    if (data.isEmpty() || data == "[DONE]") continue
                    val event = try {
                        gson.fromJson(data, JsonObject::class.java)
                    } catch (e: Exception) {
                        null
                    }
                    val type = event?.get("type")?.asString
                    when (type) {
                        "error" -> {
                            val msg = event.getAsJsonObject("error")?.get("message")?.asString ?: "Anthropic error"
                            throw IOException(msg)
                        }
                        "content_block_delta" -> {
                            val delta = event.getAsJsonObject("delta")
                            val t = delta?.get("type")?.asString
                            val text = when (t) {
                                "text_delta" -> delta.get("text")?.asString
                                "thinking_delta" -> delta.get("thinking")?.asString
                                else -> null
                            }
                            if (text != null) {
                                if (t == "thinking_delta") onThinking(text)
                                else {
                                    emitted = true
                                    accumulated.append(text)
                                    val now = System.currentTimeMillis()
                                    if (accumulated.length >= 60 || now - lastEmitTime >= 50) {
                                        emit(accumulated.toString())
                                        accumulated.clear()
                                        lastEmitTime = now
                                    }
                                }
                            }
                        }
                        "message_stop" -> break
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                throw IOException("接口响应超时（60 秒内未收到任何数据）。请检查网络与 Anthropic 配置。")
            }
        } finally {
            response.close()
        }
        if (accumulated.isNotEmpty()) emit(accumulated.toString())
        if (!emitted) {
            throw IOException("Anthropic 接口未返回任何正文（请检查模型名、Base URL 与 api-key）。")
        }
    }

    /** 非流式一次性补全（用于总结等），返回 content[0].text */
    suspend fun completeText(baseUrl: String, apiKey: String, request: ChatRequest): String {
        val root = buildPayload(request)
        root.addProperty("stream", false)
        val body = root.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val fullUrl = "${baseUrl.trimEnd('/')}/messages"
        val httpRequest = Request.Builder()
            .url(fullUrl)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        val response = withContext(Dispatchers.IO) {
            streamingClient.newCall(httpRequest).execute()
        }
        response.use {
            if (!it.isSuccessful) throw IOException("Anthropic error: ${it.code} ${it.message}")
            val text = it.body?.string().orEmpty()
            return gson.fromJson(text, JsonObject::class.java)
                ?.getAsJsonArray("content")?.firstOrNull()
                ?.asJsonObject?.get("text")?.asString ?: "(无总结)"
        }
    }

    private fun buildPayload(request: ChatRequest): JsonObject {
        val root = JsonObject()
        root.addProperty("model", request.model)
        root.addProperty("stream", true)
        // Anthropic 强制要求 max_tokens，缺失时给默认值
        root.addProperty("max_tokens", request.max_tokens?.takeIf { it > 0 } ?: 4096)
        request.temperature?.takeIf { it >= 0 }?.let { root.addProperty("temperature", it) }
        request.top_p?.takeIf { it >= 0 }?.let { root.addProperty("top_p", it) }
        // 提取 system 角色 → 顶层 system 字段；其余进 messages
        val system = request.messages.filter { it.role == "system" }.joinToString("\n") { it.content }
            .takeIf { it.isNotBlank() }
        system?.let { root.addProperty("system", it) }
        val msgs = com.google.gson.JsonArray()
        request.messages.filter { it.role != "system" }.forEach { m ->
            val obj = JsonObject()
            obj.addProperty("role", if (m.role == "assistant") "assistant" else "user")
            obj.addProperty("content", m.content)
            msgs.add(obj)
        }
        root.add("messages", msgs)
        return root
    }
}