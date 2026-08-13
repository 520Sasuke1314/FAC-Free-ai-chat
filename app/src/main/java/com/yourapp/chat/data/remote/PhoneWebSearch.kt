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
    // 解析器按桌面版 HTML 结构编写，故用桌面 UA，避免手机版布局导致 b_algo 匹配不到
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    /** 常用搜索引擎的 URL 模板（{query} 会被替换为编码后的查询词）。未命中则回退 Bing。 */
    private val ENGINE_TEMPLATES = mapOf(
        "google" to "https://www.google.com/search?q={query}&num={limit}&hl=zh-CN",
        "bing" to "https://www.bing.com/search?q={query}&count={limit}&setlang=zh-cn",
        "duckduckgo" to "https://html.duckduckgo.com/html/?q={query}",
        "sogou" to "https://www.sogou.com/web?query={query}",
        "360" to "https://www.so.com/s?q={query}",
        "baidu" to "https://www.baidu.com/s?wd={query}"
    )

    /**
     * 在所选搜索引擎（[engine]，默认 bing）中搜索 [query]，返回最多 [limit] 条结果。
     * [customEngineUrl] 为「自定义搜索引擎」时优先使用该模板。
     * 失败时抛异常，由调用方提示。
     */
    suspend fun search(query: String, limit: Int = 5, engine: String = "bing", customEngineUrl: String? = null): List<WebSearchResult> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = buildString {
                if (!customEngineUrl.isNullOrBlank() && customEngineUrl.contains("{query}")) {
                    append(customEngineUrl.replace("{query}", encoded).replace("{limit}", limit.toString()))
                } else {
                    append(
                        ENGINE_TEMPLATES[engine]?.replace("{query}", encoded)?.replace("{limit}", limit.toString())
                            ?: String.format(BING_URL, encoded, limit)
                    )
                }
            }
            val req = Request.Builder()
                .url(url)
                // 参考 RikkaHub 的 BingSearchService 请求头：完整浏览器头 + Referer + cookie，
                // 否则 Bing 会将其视为爬虫/机器人而返回空结果。
                // 注意：不要手动设置 Accept-Encoding —— OkHttp 会因此禁用自动 gzip 解压，
                // 导致拿到的是压缩字节、正则匹配不到 b_algo 而永远空结果（Jsoup 无此限制）。
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Accept-Charset", "utf-8")
                .header("Connection", "keep-alive")
                .header("Referer", "https://www.bing.com/")
                .header("Cookie", "SRCHHPGUSR=ULSR=1")
                .get()
                .build()
            val html = ChatApplication.instance.okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("搜索失败 HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
            // 参考 RikkaHub：无结果即视为失败抛出（而非静默返回空），让上层能明确提示"搜索未找到结果"
            parseResults(html, limit).also {
                if (it.isEmpty()) throw IllegalStateException("搜索失败：未找到相关结果")
            }
        }

    /**
     * 把搜索结果拼成供 AI 阅读的上下文块。
     * 参考 RikkaHub：注入"今天日期"并指示模型用 [citation,域名](编号) 标注引用来源，
     * 使模型更自然地据实引用而非凭记忆编造。
     */
    fun buildPrompt(query: String, results: List<WebSearchResult>): String {
        if (results.isEmpty()) return ""
        val today = java.time.LocalDate.now().toString()
        val sb = StringBuilder()
        sb.append("【联网搜索结果】你刚才在网络上检索了「$query」。今天是 $today。\n")
            .append("下方是检索到的网页资料，请优先据此回答。引用时在句末用 [citation,域名](编号) 标注来源，多来源可并列；未引用的编号可不标注。\n")
        results.forEachIndexed { i, r ->
            val domain = r.url.substringAfter("://").substringBefore("/")
            sb.append("\n").append(i + 1).append(". ").append(r.title).append("  [citation,").append(domain).append("](").append(i + 1).append(")\n")
            sb.append("   来源：").append(r.url).append("\n")
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

    /** 解析 Bing 搜索结果页中的结果块，兼容多种布局（b_algo / li / tile 等） */
    internal fun parseResults(html: String, limit: Int): List<WebSearchResult> {
        val results = ArrayList<WebSearchResult>()
        // 优先按 b_algo 块解析；兼容桌面新版也覆盖部分 <li> 结构
        val blockPatterns = listOf(
            Regex("""<li class="b_algo".*?</li>""", setOf(RegexOption.DOT_MATCHES_ALL)),
            Regex("""<li class="b_algo".*?</li>\s*""", setOf(RegexOption.DOT_MATCHES_ALL))
        )
        val blocks = ArrayList<String>()
        blockPatterns.forEach { re ->
            if (blocks.isEmpty()) re.findAll(html).forEach { blocks.add(it.value) }
        }
        // 兜底：直接抓所有带 href 的 <h2> 链接块
        if (blocks.isEmpty()) {
            Regex("""<h2[^>]*>.*?<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>.*?</h2>""", setOf(RegexOption.DOT_MATCHES_ALL))
                .findAll(html).forEach { blocks.add(it.value) }
        }
        for (block in blocks) {
            if (results.size >= limit) break
            val title = Regex("""<h2>.*?<a[^>]*>(.*?)</a>.*?</h2>""", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(block)?.groupValues?.getOrNull(1)
                ?.let { stripTags(it) }?.trim() ?: ""
            val url = Regex("""<h2[^>]*>.*?<a[^>]*href="([^"]+)"[^>]*>""", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(block)?.groupValues?.getOrNull(1) ?: ""
            val snippet = Regex("""<p[^>]*>(.*?)</p>""", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(block)?.groupValues?.getOrNull(1)
                ?.let { stripTags(it) }?.trim()
                ?: Regex("""class="b_caption"[^>]*>.*?<p[^>]*>(.*?)</p>""", setOf(RegexOption.DOT_MATCHES_ALL))
                    .find(block)?.groupValues?.getOrNull(1)?.let { stripTags(it) }?.trim()
                ?: ""
            // 过滤 Bing 站内导航/免责等非结果链接
            if (title.isNotBlank() && url.isNotBlank() && !url.startsWith("https://www.bing.com/search?")) {
                results.add(WebSearchResult(title, url, snippet))
            }
        }
        return results
    }

    /** 去掉 HTML 标签并解码常见实体 */
    private fun stripTags(html: String): String =
        html.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
            .trim()
}