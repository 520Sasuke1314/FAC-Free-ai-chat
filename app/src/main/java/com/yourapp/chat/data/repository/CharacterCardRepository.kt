package com.yourapp.chat.data.repository

import android.content.Context
import android.net.Uri
import com.yourapp.chat.data.local.AppDatabase
import com.yourapp.chat.data.local.dao.CharacterCardDao
import com.yourapp.chat.data.local.entity.CharacterCardEntity
import com.yourapp.chat.data.local.entity.WorldEntryEntity
import com.yourapp.chat.domain.model.CharacterCardData
import com.yourapp.chat.domain.parser.CharacterCardParser
import kotlinx.coroutines.flow.Flow
import java.io.File

class CharacterCardRepository(
    private val context: Context,
    private val characterCardDao: CharacterCardDao,
    private val db: AppDatabase
) {
    fun getAllCards(): Flow<List<CharacterCardEntity>> = characterCardDao.getAllCards()

    suspend fun getCard(id: Long): CharacterCardEntity? = characterCardDao.getById(id)

    /** 直接插入一张角色卡（用于自定义创建角色） */
    suspend fun insertCard(card: CharacterCardEntity): Long = characterCardDao.insert(card)

    /**
     * 导入角色卡文件（PNG 或 JSON），返回解析出的数据。
     */
    suspend fun importCard(uri: Uri): CharacterCardEntity {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法打开所选文件")
        val bytes = input.use { it.readBytes() }
        if (bytes.isEmpty()) throw IllegalStateException("文件为空")

        val data: CharacterCardData = if (isPng(bytes)) {
            val tmp = File(context.cacheDir, "import_${System.currentTimeMillis()}.png")
            tmp.writeBytes(bytes)
            CharacterCardParser.parsePng(tmp)
                ?: throw IllegalStateException("PNG 中未找到角色卡数据（缺少 chara 块）")
        } else {
            val text = String(bytes, Charsets.UTF_8)
            if (!CharacterCardParser.isJson(text)) {
                throw IllegalStateException("无法识别文件格式（需要 PNG 角色卡或 JSON 角色卡）")
            }
            CharacterCardParser.parseJson(text)
        }

        // 保存图片副本（PNG 时）
        val imagePath: String? = if (isPng(bytes)) {
            val dir = File(context.filesDir, "cards").apply { mkdirs() }
            val dest = File(dir, "${data.name}_${System.currentTimeMillis()}.png")
            dest.writeBytes(bytes)
            dest.absolutePath
        } else null

        val entity = CharacterCardEntity(
            name = data.name,
            description = data.description,
            systemPrompt = buildSystemPrompt(data),
            firstMessage = data.firstMessage ?: data.greeting,
            jsonData = data.rawJson,
            imagePath = imagePath,
            isEnabled = true
        )
        val cardId = characterCardDao.insert(entity)

        // 世界书条目入库（与角色卡关联）
        val worldDao = db.worldEntryDao()
        CharacterCardParser.extractWorldEntriesAsData(data.rawJson).forEach { w ->
            worldDao.insert(
                WorldEntryEntity(
                    cardId = cardId,
                    keys = w.keys,
                    content = w.content,
                    enabled = w.enabled,
                    priority = w.priority,
                    comment = w.comment
                )
            )
        }

        return entity.copy(id = cardId)
    }

    private fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        return bytes.copyOfRange(0, 8).contentEquals(sig)
    }

    private fun buildSystemPrompt(data: CharacterCardData): String? {
        val parts = mutableListOf<String>()
        data.description?.let { parts.add("【角色设定】\n$it") }
        data.persona?.let { parts.add("【角色人设】\n$it") }
        data.scenario?.let { parts.add("【场景】\n$it") }
        data.world?.let { parts.add("【世界设定】\n$it") }
        data.exampleDialogue?.let { parts.add("【对话示例】\n$it") }
        data.systemPrompt?.let { parts.add("【系统提示】\n$it") }
        return if (parts.isEmpty()) null else parts.joinToString("\n\n")
    }

    suspend fun deleteCard(card: CharacterCardEntity) {
        card.imagePath?.let { path -> runCatching { File(path).delete() } }
        db.worldEntryDao().deleteByCardId(card.id)
        characterCardDao.delete(card)
    }

    suspend fun setEnabled(card: CharacterCardEntity, enabled: Boolean) {
        characterCardDao.update(card.copy(isEnabled = enabled))
    }

    /** 更新角色卡参数（名称/描述/系统提示/开场白/启用状态） */
    suspend fun updateCard(
        card: CharacterCardEntity,
        name: String,
        description: String?,
        systemPrompt: String?,
        firstMessage: String?
    ) {
        characterCardDao.update(
            card.copy(
                name = name.trim(),
                description = description?.trim()?.ifBlank { null },
                systemPrompt = systemPrompt?.trim()?.ifBlank { null },
                firstMessage = firstMessage?.trim()?.ifBlank { null }
            )
        )
    }

    suspend fun getEnabledCards(): List<CharacterCardEntity> = characterCardDao.getEnabledCards()
}
