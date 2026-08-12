package com.yourapp.chat.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * API 配置测试与模型列表获取。
 * 调用 {baseUrl}/models 列出可用模型（OpenAI 兼容标准端点）。
 */
object ApiTester {

    /** 测试连接并返回模型列表（失败抛异常） */
    suspend fun testAndListModels(
        client: OkHttpClient,
        baseUrl: String,
        apiKey: String
    ): List<String> = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/models"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty().take(300)
                throw IllegalStateException("连接失败 HTTP ${resp.code}: $body")
            }
            val text = resp.body?.string() ?: throw IllegalStateException("空响应")
            parseModels(text)
        }
    }

    private fun parseModels(jsonText: String): List<String> {
        val obj = JSONObject(jsonText)
        val data: JSONArray = obj.optJSONArray("data") ?: return emptyList()
        val models = ArrayList<String>()
        for (i in 0 until data.length()) {
            val m = data.optJSONObject(i) ?: continue
            val id = m.optString("id")
            if (id.isNotBlank()) models.add(id)
        }
        return models
    }
}
