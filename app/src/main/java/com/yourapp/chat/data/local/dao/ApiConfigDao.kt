package com.yourapp.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yourapp.chat.data.local.entity.ApiConfigEntity

@Dao
interface ApiConfigDao {
    @Query("SELECT * FROM api_config WHERE id = 0 LIMIT 1")
    suspend fun getConfig(): ApiConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ApiConfigEntity)
}
