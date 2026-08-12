package com.yourapp.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.yourapp.chat.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND isActiveBranch = 1 ORDER BY timestamp ASC")
    fun getActiveMessagesForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND parentMessageId = :parentId")
    suspend fun getChildMessages(conversationId: Long, parentId: Long): List<MessageEntity>

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("UPDATE messages SET isActiveBranch = 0 WHERE conversationId = :conversationId AND id IN (:ids)")
    suspend fun deactivateMessages(conversationId: Long, ids: List<Long>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id IN (:ids)")
    suspend fun deleteMessages(conversationId: Long, ids: List<Long>)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getAllMessagesForConversation(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("SELECT MAX(timestamp) FROM messages WHERE conversationId = :conversationId")
    suspend fun getLastTimestamp(conversationId: Long): Long?

    @Query("UPDATE messages SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE messages SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("SELECT * FROM messages WHERE isFavorite = 1 ORDER BY pinned DESC, timestamp DESC")
    fun getFavoriteMessages(): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessagesByIds(ids: List<Long>)
}
