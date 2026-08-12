package com.yourapp.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.yourapp.chat.data.local.entity.SkillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SkillEntity>>

    @Insert
    suspend fun insert(skill: SkillEntity): Long

    @Update
    suspend fun update(skill: SkillEntity)

    @Delete
    suspend fun delete(skill: SkillEntity)
}
