package com.yourapp.chat.data.repository

import com.yourapp.chat.data.local.dao.SkillDao
import com.yourapp.chat.data.local.entity.SkillEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class SkillRepository(
    private val skillDao: SkillDao,
    private val okHttpClient: OkHttpClient
) {
    fun getAll(): Flow<List<SkillEntity>> = skillDao.getAll()

    suspend fun insert(skill: SkillEntity): Long = skillDao.insert(skill)

    suspend fun update(skill: SkillEntity) = skillDao.update(skill)

    suspend fun delete(skill: SkillEntity) = skillDao.delete(skill)

    /**
     * 从 GitHub 链接抓取 SKILL.md 正文。
     * 支持：
     *  - github.com/{owner}/{repo}           -> raw.githubusercontent.com/{owner}/{repo}/HEAD/SKILL.md
     *  - github.com/{owner}/{repo}/blob/...  -> 对应 raw 地址
     *  - raw.githubusercontent.com/...       直接使用
     */
    suspend fun fetchFromGithub(input: String): String = withContext(Dispatchers.IO) {
        val url = normalizeGithubUrl(input)
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "ChatApp/1.0")
            .get()
            .build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: $url")
            }
            resp.body?.string() ?: throw IllegalStateException("响应为空")
        }
    }

    private fun normalizeGithubUrl(input: String): String {
        val url = input.trim()
        if (url.isBlank()) throw IllegalStateException("请输入 GitHub 链接")
        if (url.contains("raw.githubusercontent.com") || url.contains("/raw/")) return url
        val m = Regex("github\\.com/([^/\\s?#]+)/([^/\\s?#]+)").find(url)
        if (m != null) {
            val owner = m.groupValues[1]
            val repo = m.groupValues[2].trimEnd('/')
            // blob 路径转 raw：/owner/repo/blob/branch/path
            val blob = Regex("github\\.com/[^/\\s?#]+/[^/\\s?#]+/blob/([^\\s?#]+)").find(url)
            if (blob != null) {
                return "https://raw.githubusercontent.com/$owner/$repo/${blob.groupValues[1]}"
            }
            // 仓库根目录 -> 尝试 SKILL.md
            return "https://raw.githubusercontent.com/$owner/$repo/HEAD/SKILL.md"
        }
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        throw IllegalStateException("无法识别的链接")
    }
}
