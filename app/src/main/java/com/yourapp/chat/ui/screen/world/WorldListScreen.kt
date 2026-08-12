package com.yourapp.chat.ui.screen.world

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourapp.chat.data.local.entity.WorldBookEntity
import com.yourapp.chat.data.local.entity.WorldEntryEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldListScreen(onBack: () -> Unit, showBack: Boolean = true) {
    val vm: WorldListViewModel = viewModel(factory = WorldListViewModel.Factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { vm.importFile(it) }
    }

    LaunchedEffect(state.importMessage) {
        state.importMessage?.let {
            snackbar.showSnackbar(if (state.importError) "导入失败：$it" else it)
            vm.clearImportMessage()
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("全局世界书") },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch("*/*") }) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "导入世界书文件")
                    }
                    IconButton(onClick = { vm.openNew() }) {
                        Icon(Icons.Filled.Add, contentDescription = "新增手动条目")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Text(
                    text = "导入的世界书文件会作为「世界书」显示，点击展开查看其中的设定条目。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // 世界书集合（导入的文件）
            if (state.books.isEmpty()) {
                item {
                    Text(
                        "暂无导入的世界书，点右上角 📤 导入 PNG/JSON 世界书文件",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            items(state.books, key = { it.id }) { book ->
                WorldBookCardHeader(
                    book = book,
                    loaded = state.entriesByBook.containsKey(book.id),
                    expanded = state.expandedBookIds.contains(book.id),
                    entryCount = state.entriesByBook[book.id].orEmpty().size,
                    onToggle = { vm.toggleBookExpanded(book.id) },
                    onRename = { newName -> vm.renameBook(book, newName) },
                    onDeleteBook = { vm.deleteBook(book) }
                )
            }

            // 展开的集合条目：拆成独立可虚拟化的行，避免大集合（几百条目）
            // 在单个 item 里 forEach 整段组合导致滚动卡顿
            state.books.forEach { book ->
                if (state.expandedBookIds.contains(book.id)) {
                    val entries = state.entriesByBook[book.id].orEmpty()
                    if (entries.isEmpty()) {
                        item(key = "book_empty_${book.id}") {
                            Text(
                                "该世界书没有条目",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        items(entries, key = { it.id }) { entry ->
                            ExpandedEntryRow(
                                entry = entry,
                                onDelete = { vm.delete(entry) },
                                onEdit = { vm.openEdit(entry) }
                            )
                        }
                    }
                }
            }

            // 手动添加的条目
            if (state.manualEntries.isNotEmpty()) {
                item {
                    Text(
                        "手动条目",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(state.manualEntries, key = { it.id }) { entry ->
                    ManualEntryRow(
                        entry = entry,
                        onDelete = { vm.delete(entry) },
                        onEdit = { vm.openEdit(entry) }
                    )
                }
            }
        }
    }

    if (state.showEditor) {
        val editing = state.editing
        if (editing != null) {
            WorldEntryEditorDialog(
                entry = editing,
                onSave = { keys, content, priority, comment ->
                    vm.save(keys, content, priority, comment)
                },
                onDismiss = { vm.dismissEditor() }
            )
        }
    }
}

@Composable
private fun WorldBookCardHeader(
    book: WorldBookEntity,
    loaded: Boolean,
    expanded: Boolean,
    entryCount: Int,
    onToggle: () -> Unit,
    onRename: (String) -> Unit,
    onDeleteBook: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column {
            // 集合头
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (loaded) "$entryCount 条设定 · 点击${if (expanded) "收起" else "展开"}"
                        else "点击加载",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
                TextButton(onClick = { showRenameDialog = true }) { Text("改名") }
                IconButton(onClick = onDeleteBook) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除世界书")
                }
            }
            HorizontalDivider()
        }
    }

    if (showRenameDialog) {
        RenameBookDialog(
            currentName = book.name,
            onConfirm = { newName ->
                showRenameDialog = false
                onRename(newName)
            },
            onDismiss = { showRenameDialog = false }
        )
    }
}

@Composable
private fun RenameBookDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名世界书") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.ifBlank { currentName }) },
                enabled = name.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ExpandedEntryRow(
    entry: WorldEntryEntity,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.keys,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
        }
        // 移除导入页面的开关，世界书在对话中由用户手动选择启用
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "删除条目")
        }
    }
    HorizontalDivider(Modifier.padding(start = 12.dp))
}

@Composable
private fun ManualEntryRow(
    entry: WorldEntryEntity,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.keys,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
        }
        // 移除导入页面的开关，世界书在对话中由用户手动选择启用
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "删除")
        }
    }
    HorizontalDivider()
}

@Composable
private fun WorldEntryEditorDialog(
    entry: WorldEntryEntity,
    onSave: (String, String, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var keys by remember { mutableStateOf(entry.keys) }
    var content by remember { mutableStateOf(entry.content) }
    var priority by remember { mutableStateOf(entry.priority.toString()) }
    var comment by remember { mutableStateOf(entry.comment ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry.id == 0L) "新增世界书条目" else "编辑世界书条目") },
        text = {
            // 使用垂直滚动，防止内容过长时优先级和备注不可见
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = keys,
                    onValueChange = { keys = it },
                    label = { Text("触发关键词（逗号分隔）") },
                    singleLine = true
                )
                Spacer(Modifier.width(0.dp).padding(top = 8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("注入内容") },
                    minLines = 3
                )
                Spacer(Modifier.width(0.dp).padding(top = 8.dp))
                OutlinedTextField(
                    value = priority,
                    onValueChange = { priority = it.filter { c -> c.isDigit() } },
                    label = { Text("优先级（越小越优先）") },
                    singleLine = true
                )
                Spacer(Modifier.width(0.dp).padding(top = 8.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("备注（可选）") },
                    singleLine = true
                )
                // 移除全局启用开关，改为在对话中选择是否启用该世界书
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(keys, content, priority.toIntOrNull() ?: 100, comment)
                },
                enabled = keys.isNotBlank() && content.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
