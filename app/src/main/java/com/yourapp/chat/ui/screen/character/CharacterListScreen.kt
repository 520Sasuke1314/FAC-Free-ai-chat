package com.yourapp.chat.ui.screen.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.yourapp.chat.data.local.entity.CharacterCardEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterListScreen(
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    showBack: Boolean = true
) {
    val vm: CharacterViewModel = viewModel(factory = CharacterViewModel.Factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selected by remember { mutableStateOf<CharacterCardEntity?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { vm.importCard(it) }
    }

    LaunchedEffect(state.info) {
        state.info?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearInfo()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar("导入失败：$it", duration = SnackbarDuration.Long)
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("角色卡") },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (state.importing) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp))
                    } else {
                        IconButton(onClick = { showCreate = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "自定义角色")
                        }
                        IconButton(onClick = { launcher.launch("*/*") }) {
                            Icon(Icons.Filled.FileUpload, contentDescription = "导入角色卡")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "支持导入 PNG 角色卡（chara 块）或 JSON 角色卡（SillyTavern/愚乐书格式）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(state.cards, key = { it.id }) { card ->
                CharacterCardRow(
                    card = card,
                    onClick = { selected = card },
                    onDelete = { vm.deleteCard(card) },
                    onToggle = { vm.toggleEnabled(card) }
                )
            }
        }
    }

    selected?.let { card ->
        CardInfoDialog(
            card = card,
            onDismiss = { selected = null },
            onSave = { name, desc, sys, first ->
                vm.updateCard(card, name, desc, sys, first)
            }
        )
    }
    if (showCreate) {
        CreateCardDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, desc, sys, first ->
                vm.createCard(name, desc, sys, first)
            }
        )
    }
}

/** 点击角色卡弹出的信息面板：可查看并编辑角色卡信息（姓名/描述/系统提示/开场白） */
@Composable
private fun CardInfoDialog(
    card: CharacterCardEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String?, systemPrompt: String?, firstMessage: String?) -> Unit
) {
    var name by remember { mutableStateOf(card.name) }
    var description by remember { mutableStateOf(card.description.orEmpty()) }
    var systemPrompt by remember { mutableStateOf(card.systemPrompt.orEmpty()) }
    var firstMessage by remember { mutableStateOf(card.firstMessage.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑角色卡") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("系统提示词") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = firstMessage,
                    onValueChange = { firstMessage = it },
                    label = { Text("AI 开场白") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "是否启用：${if (card.isEnabled) "是" else "否"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(name, description, systemPrompt, firstMessage)
                    onDismiss()
                },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 自定义创建角色：姓名 + 描述 + 系统提示词 + AI 开场白 */
@Composable
private fun CreateCardDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?, systemPrompt: String?, firstMessage: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var firstMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义角色") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名 *") },
                    placeholder = { Text("角色名称，必填") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    placeholder = { Text("角色背景 / 简介（可选）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("系统提示词") },
                    placeholder = { Text("定义角色的性格、语气、行为规则（可选）") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = firstMessage,
                    onValueChange = { firstMessage = it },
                    label = { Text("AI 开场白") },
                    placeholder = { Text("进入对话后 AI 说的第一句话（可选）") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(name, description, systemPrompt, firstMessage)
                    onDismiss()
                },
                enabled = name.isNotBlank()
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun CharacterCardRow(
    card: CharacterCardEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // 圆角卡片（与对话列表一致的圆润 UI）
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardThumbnail(card)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.titleMedium)
            card.description?.let {
                Text(
                    text = it.take(60),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 2
                )
            }
        }
        Switch(checked = card.isEnabled, onCheckedChange = { onToggle() })
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "删除")
        }
    }
}

@Composable
private fun CardThumbnail(card: CharacterCardEntity) {
    // 用 placeholder/error 兜底，损坏的 PNG 也不会崩溃
    AsyncImage(
        model = if (card.imagePath != null && File(card.imagePath).exists()) File(card.imagePath) else null,
        contentDescription = null,
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop,
        onError = { /* 图片损坏/加载失败时显示占位 */ },
        placeholder = painterResource(com.yourapp.chat.R.drawable.ic_launcher),
        error = painterResource(com.yourapp.chat.R.drawable.ic_launcher)
    )
}
