package com.yourapp.chat.domain.model

data class WorldBookEntry(
    val keys: List<String>,
    val content: String,
    val enabled: Boolean = true,
    val comment: String? = null
)

data class CharacterCardData(
    val name: String,
    val description: String?,
    val systemPrompt: String?,
    val firstMessage: String?,
    val rawJson: String,
    val greeting: String? = null,
    val scenario: String? = null,
    val persona: String? = null,
    val world: String? = null,
    val exampleDialogue: String? = null,
    val worldEntries: List<WorldBookEntry> = emptyList()
)
