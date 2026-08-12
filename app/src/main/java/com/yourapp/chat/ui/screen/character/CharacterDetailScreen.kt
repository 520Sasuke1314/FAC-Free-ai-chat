package com.yourapp.chat.ui.screen.character

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourapp.chat.data.local.entity.WorldEntryEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    cardId: Long,
    onBack: () -> Unit
) {
    val vm: CharacterDetailViewModel =
        viewModel(
            key = "card_$cardId",
            factory = CharacterDetailViewModel.factory(cardId)
        )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val card = state.card

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(card?.name ?: "角色卡详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (card != null) {
                        IconButton(onClick = { vm.toggleEdit() }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (!state.loaded) {
                Text("加载中…")
                return@Column
            }
            if (card == null) {
                Text("未找到该角色卡", color = MaterialTheme.colorScheme.error)
                return@Column
            }

            DetailRow("名称", card.name)
            DetailRow("描述", card.description)
            DetailRow("系统提示", card.systemPrompt)
            DetailRow("开场白", card.firstMessage)
            DetailRow("是否启用", if (card.isEnabled) "是" else "否")
            DetailRow(
                "导入时间",
                java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    java.util.Locale.getDefault()
                ).format(java.util.Date(card.createdAt))
            )

            // 世界书（角色卡专属）
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("世界书（${state.worldEntries.size} 条）", style = MaterialTheme.typography.titleMedium)
            }
            if (state.worldEntries.isEmpty()) {
                Text(
                    "该角色卡不包含世界书条目。导入含 world/character_book 字段的角色卡后会自动入库。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                state.worldEntries.forEachIndexed { index, entry ->
                    WorldEntryRow(
                        index = index,
                        entry = entry,
                        onToggle = { vm.toggleEntry(entry) },
                        onDelete = { vm.deleteEntry(entry) }
                    )
                }
                Text(
                    "提示：发送消息时若包含条目关键词，对应世界信息会自动注入对话。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    if (state.showEdit && card != null) {
        CardEditDialog(
            card = card,
            onSave = { n, d, s, f -> vm.saveCard(n, d, s, f) },
            onDismiss = { vm.toggleEdit() }
        )
    }
}

@Composable
private fun WorldEntryRow(
    index: Int,
    entry: WorldEntryEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "条目 ${index + 1}：${entry.keys}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4
                )
                entry.comment?.let {
                    Text(
                        "注释：$it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Switch(checked = entry.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除条目")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
    HorizontalDivider()
}

@Composable
private fun CardEditDialog(
    card: com.yourapp.chat.data.local.entity.CharacterCardEntity,
    onSave: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(card.name) }
    var description by remember { mutableStateOf(card.description ?: "") }
    var systemPrompt by remember { mutableStateOf(card.systemPrompt ?: "") }
    var firstMessage by remember { mutableStateOf(card.firstMessage ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑角色卡") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    minLines = 2
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("系统提示") },
                    minLines = 4
                )
                OutlinedTextField(
                    value = firstMessage,
                    onValueChange = { firstMessage = it },
                    label = { Text("开场白") },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, description, systemPrompt, firstMessage) },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}