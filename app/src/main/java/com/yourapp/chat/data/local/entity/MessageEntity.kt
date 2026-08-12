package com.yourapp.chat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable

@Immutable
@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("conversationId"), Index("parentMessageId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,          // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val parentMessageId: Long? = null,
    val isActiveBranch: Boolean = true,
    /** 是否被用户收藏（消息收藏列表） */
    val isFavorite: Boolean = false,
    /** 收藏列表内是否置顶（右滑切换，排序置顶优先） */
    @ColumnInfo(defaultValue = "0")
    val pinned: Boolean = false,
    /** 附件元数据 JSON（用户消息展示缩略图/文件 chip 用）：[{"name","mime","isImage","dataUrl"?}] */
    val attachmentsJson: String? = null
)
