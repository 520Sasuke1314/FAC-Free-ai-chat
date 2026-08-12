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
    val thinking_enabled: Boolean? = null
)

data class Message(
    val role: String,
    val content: String
)
