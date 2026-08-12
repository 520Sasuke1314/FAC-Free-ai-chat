package com.yourapp.chat.ui.screen.skills

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Science
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourapp.chat.data.local.entity.SkillEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(onBack: () -> Unit, showBack: Boolean = true) {
    val vm: SkillViewModel = viewModel(factory = SkillViewModel.Factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf<SkillEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showGithubDialog by remember { mutableStateOf(false) }
    var fetching by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("无法读取文件")
            val name = text.trim().lineSequence()
                .firstOrNull { it.startsWith("#") }
                ?.removePrefix("#")?.trim()?.take(40)
                ?: "从文件导入"
            editing = SkillEntity(name = name, content = text, source = "file")
        } catch (e: Exception) {
            vm.setError(e.message ?: "导入失败")
        }
    }

    LaunchedEffect(state.info) {
        state.info?.let {
            snackbar.showSnackbar(it)
            vm.clearInfo()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar("失败：$it", duration = SnackbarDuration.Long)
            vm.clearInfo()
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Skills") },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (fetching) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp))
                    } else {
                        IconButton(onClick = { showGithubDialog = true }) {
                            Icon(Icons.Filled.Link, contentDescription = "从 GitHub 导入")
                        }
                        IconButton(onClick = { fileLauncher.launch(arrayOf("text/markdown", "text/plain", "*/*")) }) {
                            Icon(Icons.Filled.FileUpload, contentDescription = "导入 SKILL.md")
                        }
                        IconButton(onClick = { creating = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "自定义技能")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.skills.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.width(0.dp))
                Text("暂无技能", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(0.dp).padding(top = 8.dp))
                Text(
                    "点右上角：+ 自定义，📄 导入 SKILL.md 文件，🔗 输入 GitHub 链接自动获取",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item {
                    Text(
                        text = "技能用于赋予 AI 特定能力，可在对话中参考使用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(state.skills, key = { it.id }) { skill ->
                    SkillRow(
                        skill = skill,
                        onEdit = { editing = skill },
                        onDelete = { vm.delete(skill) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showGithubDialog) {
        GithubImportDialog(
            onConfirm = { url ->
                showGithubDialog = false
                fetching = true
                scope.launch {
                    try {
                        val (name, content) = vm.fetchFromGithub(url)
                        editing = SkillEntity(name = name, content = content, source = "github:$url")
                    } catch (e: Exception) {
                        vm.setError(e.message ?: "获取失败")
                    } finally {
                        fetching = false
                    }
                }
            },
            onDismiss = { showGithubDialog = false }
        )
    }

    if (creating) {
        SkillEditorDialog(
            initial = null,
            onSave = { name, desc, content ->
                creating = false
                vm.save(0L, name, desc, content, "custom")
            },
            onDismiss = { creating = false }
        )
    }

    editing?.let { skill ->
        SkillEditorDialog(
            initial = skill,
            onSave = { name, desc, content ->
                vm.save(skill.id, name, desc, content, skill.source)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun SkillRow(
    skill: SkillEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Science,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(skill.name, style = MaterialTheme.typography.titleSmall)
            if (skill.description.isNotBlank()) {
                Text(
                    skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                skill.content.take(80),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Text(
            text = when {
                skill.source.startsWith("github") -> "GitHub"
                skill.source == "file" -> "文件"
                else -> "自定义"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "删除")
        }
    }
}

@Composable
private fun GithubImportDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从 GitHub 导入") },
        text = {
            Column {
                Text(
                    "支持输入仓库链接（自动尝试 SKILL.md）或直接的文件链接，例如：\n" +
                            "github.com/owner/repo\n" +
                            "raw.githubusercontent.com/owner/repo/HEAD/SKILL.md",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(0.dp).padding(top = 8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("GitHub 链接") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url.trim()) },
                enabled = url.isNotBlank()
            ) { Text("获取") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun SkillEditorDialog(
    initial: SkillEntity?,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "自定义技能" else "编辑技能") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true
                )
                Spacer(Modifier.width(0.dp).padding(top = 8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("简介（可选）") },
                    singleLine = true
                )
                Spacer(Modifier.width(0.dp).padding(top = 8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("技能内容（Markdown）") },
                    minLines = 6
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, desc, content) },
                enabled = content.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
