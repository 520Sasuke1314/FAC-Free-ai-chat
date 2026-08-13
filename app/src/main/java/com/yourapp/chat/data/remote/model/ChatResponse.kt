package com.yourapp.chat.data.remote.model

data class ChatResponse(
    val id: String?,
    val `object`: String?,
    val created: Long?,
    val model: String?,
    val choices: List<Choice>?
)

data class Choice(
    val index: Int?,
    val delta: Delta?,
    val message: Message?,
    val finish_reason: String?
)

data class Delta(
    val role: String?,
    val content: String?,
    val reasoning_content: String? = null,
    val tool_calls: List<ToolCall>? = null
)
