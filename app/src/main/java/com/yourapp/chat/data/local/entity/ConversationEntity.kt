package com.yourapp.chat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore
import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Immutable
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val characterCardId: Long? = null,
    /** 是否注入角色卡世界书 */
    @ColumnInfo(defaultValue = "0")
    val useCardWorld: Boolean = false,
    /** 是否注入全局世界书 */
    @ColumnInfo(defaultValue = "0")
    val useGlobalWorld: Boolean = false,
    /** 是否展示思考内容 */
    @ColumnInfo(defaultValue = "1")
    val showThinking: Boolean = true,
    /** 最大输出 token，0 = 无限 */
    @ColumnInfo(defaultValue = "0")
    val maxOutputTokens: Int = 0,
    /** 最大上下文消息数，0 = 不限制 */
    @ColumnInfo(defaultValue = "0")
    val maxContextMessages: Int = 0,
    /** 温度（0.0-2.0），-1 = 不设置 */
    @ColumnInfo(defaultValue = "-1")
    val temperature: Double = -1.0,
    /** top-k，0 = 不设置 */
    @ColumnInfo(defaultValue = "0")
    val topK: Int = 0,
    /** top-p（0-1），-1 = 不设置 */
    @ColumnInfo(defaultValue = "-1")
    val topP: Double = -1.0,
    /** 用户开场白（可选） */
    @ColumnInfo(defaultValue = "''")
    val userGreeting: String = "",
    /** AI 开场白（可选） */
    @ColumnInfo(defaultValue = "''")
    val aiGreeting: String = "",
    /** 是否注入角色卡设定（system prompt） */
    @ColumnInfo(defaultValue = "1")
    val useCharacterCard: Boolean = true,
    /** 提示词注入轮次（0=每轮都注入，默认 25） */
    @ColumnInfo(defaultValue = "25")
    val injectionInterval: Int = 25,
    /** 思考力度：-1=自动，0=不思考，1-5=力度（5 最大） */
    @ColumnInfo(defaultValue = "-1")
    val thinkingLevel: Int = -1,
    /** 上下文压缩专用模型名（空 = 复用聊天模型） */
    @ColumnInfo(defaultValue = "''")
    val compressionModel: String = "",
    /** 识图专用模型名（空 = 未配置识图模型，图片附件不处理） */
    @ColumnInfo(defaultValue = "''")
    val visionModel: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** 该对话选中的世界书 ID 列表（JSON 存储），用于分对话控制注入 */
    @ColumnInfo(defaultValue = "'[]'")
    val selectedWorldBookIdsJson: String = "[]",
    /** 该对话上次使用的 API profile ID；null 表示尚未指定（新对话用全局默认 API） */
    @ColumnInfo(defaultValue = "NULL")
    val lastUsedProfileId: Long? = null,
    /** 是否置顶（列表按 pinned DESC, updatedAt DESC 排序） */
    @ColumnInfo(defaultValue = "0")
    val pinned: Boolean = false
) {
    @Ignore
    private val gson = Gson()

    /** 获取选中的世界书 ID 列表 */
    fun getSelectedWorldBookIds(): List<Long> {
        return try {
            gson.fromJson(selectedWorldBookIdsJson, object : TypeToken<List<Long>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 设置选中的世界书 ID 列表 */
    fun withSelectedWorldBookIds(ids: List<Long>): ConversationEntity = copy(
        selectedWorldBookIdsJson = gson.toJson(ids)
    )
}
