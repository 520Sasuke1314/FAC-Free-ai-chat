package com.yourapp.chat.data.remote.model

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = true,
    val temperature: Double? = null,
    val max_tokens: Int? = null,
    val top_p: Double? = null,
    val top_k: Int? = null,
    /** 附加到当前用户消息的图片（data URL），用于无识图模型时的原生多模态发送 */
    val images: List<String> = emptyList(),
    /** 深度思考开关（DeepSeek/兼容代理）。null = 不发送（交给服务端默认）；
     * false = 显式关闭思考。Gson 序列化会省略 null，避免不认识的第三方接口报错 */
    val thinking_enabled: Boolean? = null,
    /** 深度思考（新版协议）：DeepSeek 官方 API 的 {"type":"enabled"/"disabled"} 对象。
     * Gson 对 JsonElement 字段原样序列化；null = 不发送 */
    val thinking: com.google.gson.JsonObject? = null,
    /** 工具定义（OpenAI 兼容 function calling，如 search_web）。null = 不发送 */
    val tools: List<ToolDef>? = null
)

/** OpenAI 兼容的工具定义（function calling） */
data class ToolDef(
    val type: String = "function",
    val function: ToolFunctionDef
)

data class ToolFunctionDef(
    val name: String,
    val description: String,
    /** JSON Schema 对象，Gson 原样序列化；null = 省略 */
    val parameters: com.google.gson.JsonObject? = null
)

data class Message(
    val role: String,
    val content: String? = null,
    /** 助手发起的工具调用（拼接时用） */
    val tool_calls: List<ToolCall>? = null,
    /** 工具结果消息的回填 id（role=tool 时） */
    val tool_call_id: String? = null,
    /** 工具结果消息的工具名（role=tool 时） */
    val name: String? = null
)

data class ToolCall(
    val id: String?,
    val type: String? = null,
    val function: ToolCallFunction
)

data class ToolCallFunction(
    val name: String?,
    val arguments: String? = null
)
