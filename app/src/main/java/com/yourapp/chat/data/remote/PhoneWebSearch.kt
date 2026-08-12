package com.yourapp.chat.data.remote

import com.yourapp.chat.ChatApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder

/** 搜索到的网页条目 */
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

/**
 * 联网搜索：获得 Shizuku 授权后，直接通过手机网络在 Bing 搜索网页并解析结果。
 * 解析出的结果会注入给 AI 作上下文，同时记录访问的网页供用户查看。
 */
object PhoneWebSearch {

    private const val BING_URL = "https://www.bing.com/search?q=%s&count=%d&setlang=zh-cn"
    private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * 在 Bing 中搜索 [query]，返回最多 [limit] 条结果。
     * 失败时抛异常，由调用方提示。
     */
    suspend fun search(query: String, limit: Int = 5): List<WebSearchResult> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = String.format(BING_URL, encoded, limit)
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .get()
                .build()
            val html = ChatApplication.instance.okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("搜索失败 HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
            parseResults(html, limit)
        }

    /** 把搜索结果拼成供 AI 阅读的上下文块 */
    fun buildPrompt(results: List<WebSearchResult>): String {
        if (results.isEmpty()) return ""
        val sb = StringBuilder("【联网搜索结果】以下是你刚刚浏览网页得到的资料，请结合它们回答用户问题（可适量引用，但不要编造未给出的内容）：\n")
        results.forEachIndexed { i, r ->
            sb.append("\n")
                .append(i + 1).append(". ").append(r.title).append("\n")
                .append("   来源：").append(r.url).append("\n")
            if (r.snippet.isNotBlank()) sb.append("   摘要：").append(r.snippet).append("\n")
        }
        return sb.toString()
    }

    /** 把搜索结果序列化为 JSON（存入 AI 消息，供「查看访问的网页」按钮使用） */
    fun buildSourcesJson(results: List<WebSearchResult>): String {
        val arr = org.json.JSONArray()
        results.forEach { r ->
            val o = org.json.JSONObject()
                .put("title", r.title)
                .put("url", r.url)
            arr.put(o)
        }
        return arr.toString()
    }

    /** 解析 Bing 搜索结果页中的 b_algo 结果块 */
    internal fun parseResults(html: String, limit: Int): List<WebSearchResult> {
        val results = ArrayList<WebSearchResult>()
        val blockRe = Regex("""<li class="b_algo".*?</li>""", setOf(RegexOption.DOT_MATCHES_ALL))
        for (m in blockRe.findAll(html)) {
            if (results.size >= limit) break
            val block = m.value
            val title = Regex("""<h2>.*?<a[^>]*>(.*?)</a>.*?</h2>""", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(block)?.groupValues?.getOrNull(1)
                ?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""
            val url = Regex("""<h2[^>]*>.*?<a[^>]*href="([^"]+)"[^>]*>""", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(block)?.groupValues?.getOrNull(1) ?: ""
            val snippet = Regex("""<p[^>]*>(.*?)</p>""", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(block)?.groupValues?.getOrNull(1)
                ?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""
            if (title.isNotBlank() && url.isNotBlank()) {
                results.add(WebSearchResult(title, url, snippet))
            }
        }
        return results
    }
}