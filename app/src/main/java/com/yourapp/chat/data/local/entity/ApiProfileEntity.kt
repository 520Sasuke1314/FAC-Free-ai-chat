package com.yourapp.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable

/**
 * API 配置（可保存多个）。
 * provider 为预设标识（deepseek/openai/claude/grok/kimi/glm/gemini/mimo/minimax/deepseek_web/custom）。
 * deepseek_web 表示 DeepSeek 官网免费通道（无需 baseUrl/apiKey）。
 */
@Immutable
@Entity(tableName = "api_profiles")
data class ApiProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val model: String = "",
    /** 图标域名（用于网上获取 favicon） */
    val iconDomain: String? = null,
    /** 请求协议：openai = OpenAI 兼容 /chat/completions；anthropic = Anthropic 原生 /v1/messages */
    val protocol: String = "openai",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
