package com.yourapp.chat.data.remote

/**
 * 主流 AI 的 API 预设模板。
 * 图标已下载到本地 drawable（网上获取），URL 均以 /v1 结尾，实际请求统一追加 /chat/completions。
 */
object ApiPresets {

    data class Preset(
        val provider: String,
        val name: String,
        val baseUrl: String,
        val model: String,
        /** 本地图标资源 id（R.drawable.xxx），0 = 无 */
        val iconRes: Int,
        val color: Long,
        val label: String,
        /** 是否需 API Key */
        val needKey: Boolean = true
    )

    val DEEPSEEK_WEB = Preset(
        provider = "deepseek_web",
        name = "DeepSeek 官网免费",
        baseUrl = "",
        model = "",
        iconRes = com.yourapp.chat.R.drawable.ic_ai_deepseek,
        color = 0xFF4D6BFE,
        label = "免",
        needKey = false
    )

    val ALL = listOf(
        Preset("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", com.yourapp.chat.R.drawable.ic_ai_deepseek, 0xFF4D6BFE, "DS"),
        Preset("openai", "ChatGPT (OpenAI)", "https://api.openai.com/v1", "gpt-4o-mini", com.yourapp.chat.R.drawable.ic_ai_openai, 0xFF10A37F, "GPT"),
        Preset("claude", "Claude (Anthropic)", "https://api.anthropic.com/v1", "claude-sonnet-4-20250514", com.yourapp.chat.R.drawable.ic_ai_claude, 0xFFD97757, "CL"),
        Preset("grok", "Grok (xAI)", "https://api.x.ai/v1", "grok-3", com.yourapp.chat.R.drawable.ic_ai_grok, 0xFF111111, "GX"),
        Preset("kimi", "Kimi (Moonshot)", "https://api.moonshot.cn/v1", "moonshot-v1-8k", com.yourapp.chat.R.drawable.ic_ai_kimi, 0xFF5A67F2, "KM"),
        Preset("glm", "GLM (智谱)", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", com.yourapp.chat.R.drawable.ic_ai_glm, 0xFF3B82F6, "GLM"),
        Preset("gemini", "Gemini (Google)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash", com.yourapp.chat.R.drawable.ic_ai_gemini, 0xFF4285F4, "GM"),
        Preset("mimo", "Mimo", "https://api.minimax.chat/v1", "MiniMax-M1", com.yourapp.chat.R.drawable.ic_ai_mimo, 0xFF8B5CF6, "MO"),
        Preset("minimax", "MiniMax", "https://api.minimax.io/v1", "MiniMax-Text-01", com.yourapp.chat.R.drawable.ic_ai_minimax, 0xFF6D28D9, "MX"),
        // 通用 OpenAI 兼容接口（自定义）
        Preset("openai_compat", "OpenAI 兼容接口", "", "", 0, 0xFF607D8B, "OC")
    )

    fun byProvider(provider: String): Preset? = ALL.find { it.provider == provider }
}
