package com.yourapp.chat.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable

/**
 * 保存的模型（按能力分类）。
 * 每个模型绑定一个 API 配置（apiProfileId），并声明能力：
 * canText = 文本处理（聊天 / 上下文压缩），canVision = 识图（把图片转成文本描述）。
 */
@Immutable
@Entity(
    tableName = "saved_models",
    foreignKeys = [ForeignKey(
        entity = ApiProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["apiProfileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("apiProfileId")]
)
data class SavedModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 所属 API 配置 ID */
    val apiProfileId: Long,
    /** 模型名 */
    val model: String,
    /** 是否具备文本能力 */
    val canText: Boolean = true,
    /** 是否具备识图能力 */
    val canVision: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
