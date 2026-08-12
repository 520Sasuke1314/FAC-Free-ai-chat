package com.yourapp.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.yourapp.chat.data.local.entity.SavedModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedModelDao {
    @Query("SELECT * FROM saved_models ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SavedModelEntity>>

    @Query("SELECT * FROM saved_models WHERE apiProfileId = :apiProfileId ORDER BY createdAt DESC")
    fun getByProfile(apiProfileId: Long): Flow<List<SavedModelEntity>>

    @Query("SELECT * FROM saved_models ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<SavedModelEntity>

    @Query("SELECT * FROM saved_models WHERE canText = 1 ORDER BY createdAt DESC")
    fun getTextModels(): Flow<List<SavedModelEntity>>

    @Query("SELECT * FROM saved_models WHERE canVision = 1 ORDER BY createdAt DESC")
    fun getVisionModels(): Flow<List<SavedModelEntity>>

    @Query("SELECT * FROM saved_models WHERE id = :id")
    suspend fun getById(id: Long): SavedModelEntity?

    @Insert
    suspend fun insert(model: SavedModelEntity): Long

    @Update
    suspend fun update(model: SavedModelEntity)

    @Delete
    suspend fun delete(model: SavedModelEntity)

    @Query("DELETE FROM saved_models WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_models WHERE apiProfileId = :apiProfileId")
    suspend fun deleteByProfile(apiProfileId: Long)
}
