package com.yourapp.chat.domain.parser

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yourapp.chat.domain.model.CharacterCardData
import com.yourapp.chat.domain.model.WorldBookEntry
import java.io.File

object CharacterCardParser {

    /**
     * 解析 PNG 角色卡：读取 tEXt 块中关键字为 "chara" 的 base64 JSON。
     */
    fun parsePng(file: File): CharacterCardData? {
        val charaJson = extractCharaFromPng(file) ?: return null
        val decoded = try {
            String(android.util.Base64.decode(charaJson, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            // 某些角色卡 tEXt 值未做 base64，直接当 JSON 文本
            charaJson
        }
        return try {
            parseJson(decoded)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractCharaFromPng(file: File): String? {
        val bytes = file.readBytes()
        var offset = 8 // PNG signature
        while (offset + 12 <= bytes.size) {
            val length = ((bytes[offset].toInt() and 0xff) shl 24) or
                    ((bytes[offset + 1].toInt() and 0xff) shl 16) or
                    ((bytes[offset + 2].toInt() and 0xff) shl 8) or
                    (bytes[offset + 3].toInt() and 0xff)
            if (offset + 8 + length > bytes.size) break
            val type = String(bytes, offset + 4, 4)
            if (type == "tEXt") {
                val data = bytes.copyOfRange(offset + 8, offset + 8 + length)
                val text = String(data, Charsets.ISO_8859_1)
                val nullIndex = text.indexOf('\u0000')
                if (nullIndex != -1) {
                    val keyword = text.substring(0, nullIndex)
                    val value = text.substring(nullIndex + 1)
                    if (keyword == "chara") {
                        return value
                    }
                }
            }
            offset += 12 + length
        }
        return null
    }

    /**
     * 解析 JSON 角色卡，兼容 SillyTavern / 愚乐书 顶层字段与嵌套 data 字段。
     */
    fun parseJson(jsonString: String): CharacterCardData {
        val root = try {
            JsonParser.parseString(jsonString).asJsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("不是有效的 JSON 文件")
        }
        val top = extractFields(root)
        val inner: JsonObject? = if (root.has("data") && root.get("data").isJsonObject) {
            root.getAsJsonObject("data")
        } else null
        val innerFields = inner?.let { extractFields(it) } ?: emptyFields

        val name = top.name ?: innerFields.name ?: "Unknown"
        val description = top.description ?: innerFields.description
        val system = top.systemPrompt ?: innerFields.systemPrompt
        val first = top.firstMessage ?: innerFields.firstMessage

        return CharacterCardData(
            name = name,
            description = description,
            systemPrompt = system,
            firstMessage = first,
            rawJson = jsonString,
            greeting = top.greeting ?: innerFields.greeting,
            scenario = top.scenario ?: innerFields.scenario,
            persona = top.persona ?: innerFields.persona,
            world = top.world ?: innerFields.world,
            exampleDialogue = top.exampleDialogue ?: innerFields.exampleDialogue,
            worldEntries = extractWorldEntries(root, inner)
        )
    }

    /** 解析世界书条目（兼容 SillyTavern character_book 与愚乐书 world 字段） */
    fun extractWorldEntries(root: JsonObject, inner: JsonObject? = null): List<WorldBookEntry> {
        val entries = ArrayList<WorldBookEntry>()
        val candidates = listOfNotNull(
            root.get("character_book"),
            root.get("world_book"),
            inner?.get("character_book"),
            inner?.get("world_book"),
            root.get("world"),
            inner?.get("world")
        )
        for (candidate in candidates) {
            if (candidate !is JsonObject) continue
            val entriesArr: JsonElement? = if (candidate.has("entries")) candidate.get("entries") else null
            if (entriesArr is JsonArray) {
                for (e in entriesArr) {
                    if (e !is JsonObject) continue
                    val enabled = if (e.has("enabled")) e.get("enabled").asBoolean else true
                    if (!enabled) continue
                    val content = if (e.has("content")) e.get("content").asString else continue
                    val keys = ArrayList<String>()
                    val keysEl = e.get("keys")
                    if (keysEl is JsonArray) {
                        for (k in keysEl) if (k.isJsonPrimitive) keys.add(k.asString)
                    } else if (keysEl?.isJsonPrimitive == true) {
                        keys.add(keysEl.asString)
                    }
                    if (keys.isEmpty()) continue
                    val comment = if (e.has("comment") && !e.get("comment").isJsonNull) {
                        e.get("comment").asString
                    } else null
                    entries.add(
                        WorldBookEntry(
                            keys = keys,
                            content = content,
                            enabled = true,
                            comment = comment
                        )
                    )
                }
            }
        }
        return entries
    }

    private data class Fields(
        val name: String?,
        val description: String?,
        val systemPrompt: String?,
        val firstMessage: String?,
        val greeting: String?,
        val scenario: String?,
        val persona: String?,
        val world: String?,
        val exampleDialogue: String?
    ) {
        fun isEmpty(): Boolean = name == null && description == null && systemPrompt == null
    }

    private val emptyFields = Fields(null, null, null, null, null, null, null, null, null)

    private fun extractFields(obj: JsonObject): Fields {
        fun str(key: String): String? = try {
            if (obj.has(key) && !obj.get(key).isJsonNull) obj.get(key).asString else null
        } catch (e: Exception) { null }

        return Fields(
            name = str("name"),
            description = str("description"),
            systemPrompt = str("system_prompt") ?: str("system"),
            firstMessage = str("first_mes") ?: str("first_message"),
            greeting = str("greeting"),
            scenario = str("scenario"),
            persona = str("persona"),
            world = str("world"),
            exampleDialogue = str("example_dialogue") ?: str("mes_example")
        )
    }

    fun isJson(text: String): Boolean {
        return try {
            JsonParser.parseString(text).isJsonObject
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 从角色卡原始 JSON 提取世界书条目（供导入时批量入库）。
     */
    fun extractWorldEntriesAsData(rawJson: String?): List<WorldEntryData> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return try {
            val root = JsonParser.parseString(rawJson).asJsonObject
            extractWorldEntries(root).map { entry ->
                WorldEntryData(
                    keys = entry.keys.joinToString(","),
                    content = entry.content,
                    enabled = entry.enabled,
                    priority = 100,
                    comment = entry.comment
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 解析世界书文件（支持 PNG 与 JSON）：
     * - PNG：读取 tEXt 块中的 chara（角色卡）或 world 数据，提取世界书条目
     * - JSON：兼容 SillyTavern World Info 文件格式
     */
    fun parseWorldFile(file: java.io.File): List<WorldEntryData> {
        val bytes = file.readBytes()
        // PNG 魔数
        val isPng = bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        if (isPng) {
            val charaJson = extractCharaFromPng(file)
                ?: throw IllegalArgumentException("PNG 中未找到角色卡/世界书数据（chara 块）")
            val decoded = try {
                String(android.util.Base64.decode(charaJson, android.util.Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: Exception) {
                charaJson
            }
            val root = JsonParser.parseString(decoded).asJsonObject
            val inner: JsonObject? = if (root.has("data") && root.get("data").isJsonObject) {
                root.getAsJsonObject("data")
            } else null
            val entries = extractWorldEntries(root, inner)
            if (entries.isEmpty()) throw IllegalArgumentException("该 PNG 中不包含世界书条目")
            return entries.map {
                WorldEntryData(
                    keys = it.keys.joinToString(","),
                    content = it.content,
                    enabled = it.enabled,
                    priority = 100,
                    comment = it.comment
                )
            }
        }
        return parseWorldFileJson(String(bytes, Charsets.UTF_8))
    }

    private fun parseWorldFileJson(jsonString: String): List<WorldEntryData> {
        val root = try {
            JsonParser.parseString(jsonString)
        } catch (e: Exception) {
            throw IllegalArgumentException("不是有效的 JSON 文件")
        }

        val entriesArr: JsonArray? = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> {
                val obj = root.asJsonObject
                when {
                    obj.has("entries") && obj.get("entries").isJsonArray -> obj.getAsJsonArray("entries")
                    obj.has("world") && obj.get("world").isJsonObject &&
                            obj.getAsJsonObject("world").has("entries") &&
                            obj.getAsJsonObject("world").get("entries").isJsonArray ->
                        obj.getAsJsonObject("world").getAsJsonArray("entries")
                    else -> null
                }
            }
            else -> null
        }

        val entriesList: JsonArray = entriesArr
            ?: throw IllegalArgumentException("未找到世界书条目（需要 entries 数组）")

        val result = ArrayList<WorldEntryData>()
        for (e in entriesList) {
            if (!e.isJsonObject) continue
            val obj = e.asJsonObject
            val enabled = if (obj.has("enabled") && !obj.get("enabled").isJsonNull) {
                obj.get("enabled").asBoolean
            } else true
            val content = if (obj.has("content") && !obj.get("content").isJsonNull) {
                obj.get("content").asString
            } else continue
            val keys = ArrayList<String>()
            val keysEl = obj.get("keys")
            if (keysEl != null && keysEl.isJsonArray) {
                for (k in keysEl.asJsonArray) if (k.isJsonPrimitive) keys.add(k.asString)
            } else if (keysEl != null && keysEl.isJsonPrimitive) {
                keys.add(keysEl.asString)
            }
            if (keys.isEmpty()) continue
            val priority = if (obj.has("priority") && obj.get("priority").isJsonPrimitive) {
                runCatching { obj.get("priority").asInt }.getOrDefault(100)
            } else 100
            val comment = if (obj.has("comment") && !obj.get("comment").isJsonNull) {
                obj.get("comment").asString
            } else null
            result.add(WorldEntryData(keys.joinToString(","), content, enabled, priority, comment))
        }
        if (result.isEmpty()) throw IllegalArgumentException("世界书文件中没有有效条目")
        return result
    }

    data class WorldEntryData(
        val keys: String,
        val content: String,
        val enabled: Boolean = true,
        val priority: Int = 100,
        val comment: String? = null
    )

    /** 从世界书文件字节中提取标题（SillyTavern world info 的 name/title 字段），取不到返回 null */
    fun extractWorldName(bytes: ByteArray): String? {
        val isPng = bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        val json: String = if (isPng) {
            runCatching {
                val png = java.io.File.createTempFile("tmp", ".png")
                try {
                    png.writeBytes(bytes)
                    val charaJson = extractCharaFromPng(png) ?: return@runCatching null
                    String(android.util.Base64.decode(charaJson, android.util.Base64.DEFAULT), Charsets.UTF_8)
                } finally { png.delete() }
            }.getOrNull() ?: return null
        } else {
            String(bytes, Charsets.UTF_8)
        }
        return runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            val obj: JsonObject? = if (root.has("data") && root.get("data").isJsonObject) {
                root.getAsJsonObject("data")
            } else root
            val world = obj?.get("world")?.takeIf { it.isJsonObject }?.asJsonObject
            val name = root.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                ?: root.get("title")?.takeIf { it.isJsonPrimitive }?.asString
                ?: world?.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                ?: world?.get("title")?.takeIf { it.isJsonPrimitive }?.asString
                ?: obj?.get("name")?.takeIf { it.isJsonPrimitive }?.asString
            name?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
