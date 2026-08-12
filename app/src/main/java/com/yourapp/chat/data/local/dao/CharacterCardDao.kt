package com.yourapp.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.yourapp.chat.data.local.entity.CharacterCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterCardDao {
    @Query("SELECT * FROM character_cards ORDER BY createdAt DESC")
    fun getAllCards(): Flow<List<CharacterCardEntity>>

    @Query("SELECT * FROM character_cards WHERE id = :id")
    suspend fun getById(id: Long): CharacterCardEntity?

    @Query("SELECT * FROM character_cards WHERE isEnabled = 1")
    suspend fun getEnabledCards(): List<CharacterCardEntity>

    @Insert
    suspend fun insert(card: CharacterCardEntity): Long

    @Update
    suspend fun update(card: CharacterCardEntity)

    @Delete
    suspend fun delete(card: CharacterCardEntity)
}
