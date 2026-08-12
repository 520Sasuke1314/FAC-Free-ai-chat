package com.yourapp.chat.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.compose.runtime.Immutable
import com.yourapp.chat.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

/** 会话 + 最后一条消息摘要/时间 */
@Immutable
data class ConversationWithLast(
    @Embedded val conversation: ConversationEntity,
    @ColumnInfo(name = "lastMessage") val lastMessage: String? = null,
    @ColumnInfo(name = "lastTime") val lastTime: Long? = null
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    /**
     * 会话列表（带最后一条消息摘要/时间），可按标题或消息内容搜索。
     * query 为空串时返回全部。
     * lastMessage 用 substr 截断到 200 字符：完整正文（动辄几十 KB）直接塞进列表
     * 会让 Text 布局在滚动时反复巨量测字，导致只有十几二十帧。
     */
    @Query(
        "SELECT c.*, " +
                "(SELECT substr(content, 1, 200) FROM messages m WHERE m.conversationId = c.id AND m.isActiveBranch = 1 " +
                " ORDER BY m.timestamp DESC LIMIT 1) AS lastMessage, " +
                "(SELECT timestamp FROM messages m WHERE m.conversationId = c.id AND m.isActiveBranch = 1 " +
                " ORDER BY m.timestamp DESC LIMIT 1) AS lastTime " +
                "FROM conversations c " +
                "WHERE :query = '' " +
                "   OR c.title LIKE '%' || :query || '%' " +
                "   OR c.id IN (SELECT DISTINCT conversationId FROM messages WHERE content LIKE '%' || :query || '%') " +
                "ORDER BY c.pinned DESC, c.updatedAt DESC"
    )
    fun getConversationsWithLast(query: String): Flow<List<ConversationWithLast>>

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Delete
    suspend fun delete(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE conversations SET lastUsedProfileId = :profileId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLastUsedProfile(id: Long, profileId: Long?, updatedAt: Long)
}
