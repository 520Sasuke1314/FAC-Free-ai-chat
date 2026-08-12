package com.yourapp.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.yourapp.chat.data.local.entity.ApiProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiProfileDao {
    @Query("SELECT * FROM api_profiles ORDER BY isDefault DESC, id ASC")
    fun getAll(): Flow<List<ApiProfileEntity>>

    @Query("SELECT * FROM api_profiles ORDER BY isDefault DESC, id ASC")
    suspend fun getAllOnce(): List<ApiProfileEntity>

    @Query("SELECT * FROM api_profiles WHERE id = :id")
    suspend fun getById(id: Long): ApiProfileEntity?

    @Query("SELECT * FROM api_profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): ApiProfileEntity?

    @Insert
    suspend fun insert(profile: ApiProfileEntity): Long

    @Update
    suspend fun update(profile: ApiProfileEntity)

    @Query("UPDATE api_profiles SET isDefault = 0")
    suspend fun clearDefault()

    @Query("DELETE FROM api_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
