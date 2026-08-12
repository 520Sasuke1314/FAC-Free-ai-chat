package com.yourapp.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_config")
data class ApiConfigEntity(
    @PrimaryKey val id: Int = 0,
    val baseUrl: String,
    val apiKey: String,
    val model: String
)
