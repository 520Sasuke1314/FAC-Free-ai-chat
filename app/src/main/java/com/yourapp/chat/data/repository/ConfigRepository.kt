package com.yourapp.chat.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.yourapp.chat.data.local.dao.ApiConfigDao
import com.yourapp.chat.data.local.entity.ApiConfigEntity

class ConfigRepository(
    private val apiConfigDao: ApiConfigDao,
    private val prefs: SharedPreferences
) {
    suspend fun getConfig(): ApiConfigEntity? = apiConfigDao.getConfig()

    suspend fun saveConfig(baseUrl: String, apiKey: String, model: String) {
        apiConfigDao.upsert(ApiConfigEntity(id = 0, baseUrl = baseUrl, apiKey = apiKey, model = model))
    }

    /** 是否启用消息流式输出（默认开启） */
    fun isStreamingEnabled(): Boolean = prefs.getBoolean("streaming_enabled", true)

    fun setStreamingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("streaming_enabled", enabled).apply()
    }

    /** 流式刷新频率（毫秒）：UI 推送间隔。50/100/200/500 可选，默认 100ms（≈10fps） */
    fun getStreamRefreshMs(): Int = prefs.getInt("stream_refresh_ms", 100)

    fun setStreamRefreshMs(ms: Int) {
        prefs.edit().putInt("stream_refresh_ms", ms).apply()
    }

    /** 用户昵称（个人信息） */
    fun getNickname(): String = prefs.getString("nickname", "").orEmpty()

    fun setNickname(nickname: String) {
        prefs.edit().putString("nickname", nickname).apply()
    }

    /** 用户头像（emoji，如 🐳 / 😀；空 = 默认） */
    fun getAvatar(): String = prefs.getString("avatar", "🐳").orEmpty()

    fun setAvatar(avatar: String) {
        prefs.edit().putString("avatar", avatar).apply()
    }

    /** 用户自定义设定（人设/偏好，会随对话注入给 AI） */
    fun getPersona(): String = prefs.getString("persona", "").orEmpty()

    fun setPersona(persona: String) {
        prefs.edit().putString("persona", persona).apply()
    }

    /** 该对话是否使用过非官网 API（切换官网免费会丢记忆，用于禁用官网选项） */
    fun isConversationUsedApi(conversationId: Long): Boolean =
        prefs.getBoolean("used_api_$conversationId", false)

    fun markConversationUsedApi(conversationId: Long) {
        prefs.edit().putBoolean("used_api_$conversationId", true).apply()
    }

    companion object {
        fun create(context: Context, dao: ApiConfigDao): ConfigRepository =
            ConfigRepository(dao, context.getSharedPreferences("chat_app_settings", Context.MODE_PRIVATE))
    }
}
