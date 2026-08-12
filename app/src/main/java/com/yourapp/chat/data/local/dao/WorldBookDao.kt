package com.yourapp.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.yourapp.chat.data.local.entity.WorldBookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldBookDao {
    @Query("SELECT * FROM world_books ORDER BY createdAt DESC")
    fun getAll(): Flow<List<WorldBookEntity>>

    @Query("SELECT * FROM world_books WHERE id = :id")
    suspend fun getById(id: Long): WorldBookEntity?

    @Insert
    suspend fun insert(book: WorldBookEntity): Long

    @Update
    suspend fun update(book: WorldBookEntity)

    @Delete
    suspend fun delete(book: WorldBookEntity)
}
