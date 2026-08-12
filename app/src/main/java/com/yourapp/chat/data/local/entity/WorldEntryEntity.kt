package com.yourapp.chat.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "world_entries",
    indices = [Index("cardId"), Index("bookId")]
)
data class WorldEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 所属角色卡；null 表示全局 */
    val cardId: Long? = null,
    /** 所属世界书集合；null 表示手动添加的全局条目 */
    val bookId: Long? = null,
    /** 触发关键词，逗号分隔 */
    val keys: String,
    val content: String,
    val enabled: Boolean = true,
    /** 数字越小优先级越高 */
    val priority: Int = 100,
    val comment: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
