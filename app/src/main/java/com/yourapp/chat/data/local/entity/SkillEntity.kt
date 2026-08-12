package com.yourapp.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 技能（Skills）：自定义 / 导入 SKILL.md / 从 GitHub 拉取。
 * content 为技能正文（Markdown），source 记录来源（custom / file / github:<url>）。
 */
@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val content: String,
    val source: String = "custom",
    val createdAt: Long = System.currentTimeMillis()
)
