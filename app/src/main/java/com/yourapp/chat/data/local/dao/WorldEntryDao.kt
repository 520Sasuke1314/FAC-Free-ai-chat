package com.yourapp.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.yourapp.chat.data.local.entity.WorldEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldEntryDao {
    /** 某角色卡下的世界书条目 */
    @Query("SELECT * FROM world_entries WHERE cardId = :cardId ORDER BY priority ASC, id ASC")
    fun getByCardId(cardId: Long): Flow<List<WorldEntryEntity>>

    /** 某世界书集合下的条目 */
    @Query("SELECT * FROM world_entries WHERE bookId = :bookId ORDER BY priority ASC, id ASC")
    fun getByBookId(bookId: Long): Flow<List<WorldEntryEntity>>

    /** 手动添加的全局条目（无集合、无角色卡） */
    @Query("SELECT * FROM world_entries WHERE cardId IS NULL AND bookId IS NULL ORDER BY priority ASC, id ASC")
    fun getManualGlobal(): Flow<List<WorldEntryEntity>>

    /** 全局世界书条目（含手动与集合，仅 enabled） */
    @Query("SELECT * FROM world_entries WHERE cardId IS NULL AND enabled = 1 ORDER BY priority ASC, id ASC")
    suspend fun getEnabledGlobal(): List<WorldEntryEntity>

    @Query("SELECT * FROM world_entries WHERE cardId IS NULL ORDER BY priority ASC, id ASC")
    fun getGlobalAll(): Flow<List<WorldEntryEntity>>

    @Query("SELECT * FROM world_entries WHERE cardId = :cardId AND enabled = 1 ORDER BY priority ASC, id ASC")
    suspend fun getEnabledByCardId(cardId: Long): List<WorldEntryEntity>

    @Insert
    suspend fun insert(entry: WorldEntryEntity): Long

    @Update
    suspend fun update(entry: WorldEntryEntity)

    @Delete
    suspend fun delete(entry: WorldEntryEntity)

    @Query("DELETE FROM world_entries WHERE cardId = :cardId")
    suspend fun deleteByCardId(cardId: Long)

    @Query("DELETE FROM world_entries WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: Long)
}
