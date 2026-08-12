package com.yourapp.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 世界书集合：一次导入的一个世界书文件对应一条记录，
 * 其下挂多个世界书条目（world_entries.bookId）。
 */
@Entity(tableName = "world_books")
data class WorldBookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
