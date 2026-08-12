package com.yourapp.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "character_cards")
data class CharacterCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String?,
    val systemPrompt: String?,
    val firstMessage: String?,
    val jsonData: String?,
    val imagePath: String?,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
