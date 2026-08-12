package com.yourapp.chat.ui.screen.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourapp.chat.ChatApplication
import com.yourapp.chat.data.local.entity.ApiProfileEntity
import com.yourapp.chat.data.remote.ApiPresets
import com.yourapp.chat.data.remote.ApiTester
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigScreen(onBack: () -> Unit) {
    val vm: ApiConfigViewModel = viewModel(factory = ApiConfigViewModel.Factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.showSavedModels) "保存的模型" else "API 配置") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.showSavedModels) vm.backToAddApi() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (state.showSavedModels) {
            SavedModelsView(
                profiles = state.profiles,
                savedModels = state.savedModels,
                onAddModel = vm::openAddModel,
                onEditModel = vm::openEditModel,
                onDeleteModel = vm::deleteModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            AddApiView(
                state = state,
                onOpenNew = vm::openNew,
                onOpenEdit = vm::openEdit,
                onDelete = vm::delete,
                onSetDefault = vm::setDefault,
                onOpenWebLogin = vm::openWebLogin,
                onOpenSavedModels = vm::openSavedModels,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }

    if (state.showEditor) {
        val editing = state.editing
        if (editing != null) {
            if (editing.provider == "deepseek_web") {
                WebProfileEditorDialog(
                    profile = editing,
                    onSaveName = { name, makeDefault ->
                        vm.save(editing.copy(name = name), makeDefault)
                    },
                    onRelogin = {
                        vm.dismissEditor()
                        vm.openWebLogin()
                    },
                    onDismiss = { vm.dismissEditor() }
                )
            } else {
                ApiEditorDialog(
                    profile = editing,
                    onSave = { p, makeDefault -> vm.save(p, makeDefault) },
                    onDismiss = { vm.dismissEditor() }
                )
            }
        }
    }

    if (state.showModelEditor) {
        state.editingModel?.let { editingModel ->
            ModelEditorDialog(
                model = editingModel,
                onSave = { m -> vm.saveModel(m) },
                onDismiss = { vm.dismissModelEditor() }
            )
        }
    }

    if (state.showWebLogin) {
        WebLoginDialog(
            sending = state.webLoginSending,
            onLogin = { email, password -> vm.webLogin(email, password) },
            onDismiss = { vm.dismissWebLogin() }
        )
    }
}

@Composable
private fun AddApiView(
    state: ApiConfigUiState,
    onOpenNew: (ApiPresets.Preset) -> Unit,
    onOpenEdit: (ApiProfileEntity) -> Unit,
    onDelete: (ApiProfileEntity) -> Unit,
    onSetDefault: (ApiProfileEntity) -> Unit,
    onOpenWebLogin: () -> Unit,
    onOpenSavedModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        item {
            Text(
                "选择要配置的 AI，URL 和模型会自动填入（可修改）。可保存多个配置，在对话中切换使用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(16.dp)
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("保存的模型（文本 / 识图能力分类）", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenSavedModels) { Text("管理") }
            }
        }
        item {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier
                            .width(96.dp)
                            .clickable { onOpenWebLogin() }
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            AiIcon(
                                iconRes = ApiPresets.DEEPSEEK_WEB.iconRes,
                                label = ApiPresets.DEEPSEEK_WEB.label,
                                color = ApiPresets.DEEPSEEK_WEB.color,
                                size = 40
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "DeepSeek 官网免费\n(登录账号)",
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                }
                itemsIndexed(ApiPresets.ALL) { _, preset ->
                    PresetCard(preset, onClick = { onOpenNew(preset) })
                }
            }
        }
        item {
            Text(
                "已保存配置",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp)
            )
        }
        if (state.profiles.isEmpty()) {
            item {
                Text(
                    "暂无配置，从上方选择一个 AI 开始添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        items(state.profiles, key = { it.id }) { profile ->
            ProfileRow(
                profile = profile,
                onEdit = { onOpenEdit(profile) },
                onDelete = { onDelete(profile) },
                onSetDefault = { onSetDefault(profile) }
            )
        }
    }
}

@Composable
private fun PresetCard(preset: ApiPresets.Preset, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            AiIcon(iconRes = preset.iconRes, label = preset.label, color = preset.color, size = 40)
            Spacer(Modifier.height(6.dp))
            Text(
                text = preset.name,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

/** AI 图标：本地 drawable 资源（网上已下载）。vector 染成品牌色，位图（官方 logo）保持原色 */
@Composable
fun AiIcon(iconRes: Int, label: String, color: Long, size: Int = 40) {
    if (iconRes != 0) {
        val context = LocalContext.current
        val isVector = remember(iconRes) {
            val d = context.resources.getDrawable(iconRes, context.theme)
            d is android.graphics.drawable.VectorDrawable
        }
        Icon(
            painter = androidx.compose.ui.res.painterResource(iconRes),
            contentDescription = label,
            tint = if (isVector) Color(color) else androidx.compose.ui.graphics.Color.Unspecified,
            modifier = Modifier.size(size.dp)
        )
    } else {
        FallbackBadge(label, color, size)
    }
}

@Composable
fun FallbackBadge(label: String, color: Long, size: Int) {
    Surface(
        modifier = Modifier.size(size.dp),
        shape = CircleShape,
        color = Color(color)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun ProfileRow(
    profile: ApiProfileEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    val preset = ApiPresets.byProvider(profile.provider)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (preset != null) {
            AiIcon(iconRes = preset.iconRes, label = preset.label, color = preset.color, size = 36)
        } else {
            AiIcon(iconRes = 0, label = profile.provider.take(2).uppercase(), color = 0xFF607D8B, size = 36)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.name, style = MaterialTheme.typography.titleSmall)
                if (profile.isDefault) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Check, contentDescription = "默认", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                text = "${profile.baseUrl} · ${profile.model}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
        }
        TextButton(onClick = onSetDefault) { Text("默认") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "删除")
        }
    }
    HorizontalDivider()
}

/** 保存的模型管理子页：按配置分组，展示文本/识图能力，可新增/编辑/删除 */
@Composable
private fun SavedModelsView(
    profiles: List<ApiProfileEntity>,
    savedModels: List<com.yourapp.chat.data.local.entity.SavedModelEntity>,
    onAddModel: (Long) -> Unit,
    onEditModel: (com.yourapp.chat.data.local.entity.SavedModelEntity) -> Unit,
    onDeleteModel: (com.yourapp.chat.data.local.entity.SavedModelEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        item {
            Text(
                "为每个 API 配置声明其可用的模型及能力（文本 / 识图）。识图模型用于把图片转成文字描述。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(16.dp)
            )
        }
        if (profiles.isEmpty()) {
            item {
                Text(
                    "请先在「API 配置」中添加一个 API 配置。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        items(profiles, key = { it.id }) { profile ->
            val models = savedModels.filter { it.apiProfileId == profile.id }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${profile.baseUrl} · ${profile.model}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1
                    )
                }
                TextButton(onClick = { onAddModel(profile.id) }) { Text("+ 添加模型") }
            }
            if (models.isEmpty()) {
                Text(
                    "暂无保存模型",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
            } else {
                models.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditModel(model) }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(model.model, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            "文本·识图",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "${if (model.canText) "文本" else "—"}·${if (model.canVision) "识图" else "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (model.canText || model.canVision) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(onClick = { onDeleteModel(model) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除模型")
                        }
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

/** 新增 / 编辑保存模型：填模型名，勾选文本 / 识图能力 */
@Composable
private fun ModelEditorDialog(
    model: com.yourapp.chat.data.local.entity.SavedModelEntity,
    onSave: (com.yourapp.chat.data.local.entity.SavedModelEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(model.model) }
    var canText by remember { mutableStateOf(model.canText) }
    var canVision by remember { mutableStateOf(model.canVision) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (model.id == 0L) "添加保存模型" else "编辑保存模型") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("模型名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("文本", modifier = Modifier.weight(1f))
                    Switch(checked = canText, onCheckedChange = { canText = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("识图（图片转文字）", modifier = Modifier.weight(1f))
                    Switch(checked = canVision, onCheckedChange = { canVision = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(model.copy(model = name.trim(), canText = canText, canVision = canVision)) },
                enabled = name.isNotBlank() && (canText || canVision)
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ApiEditorDialog(
    profile: ApiProfileEntity,
    onSave: (ApiProfileEntity, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(profile.name) }
    var baseUrl by remember { mutableStateOf(profile.baseUrl) }
    var apiKey by remember { mutableStateOf(profile.apiKey) }
    var model by remember { mutableStateOf(profile.model) }
    var protocol by remember { mutableStateOf(profile.protocol) }
    var makeDefault by remember { mutableStateOf(profile.isDefault) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testError by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModels by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile.id == 0L) "添加 API 配置" else "编辑 API 配置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL（如 https://api.deepseek.com/v1）") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("模型名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            testing = true
                            testResult = null
                            testError = false
                            val client = ChatApplication.instance.okHttpClient
                            scope.launch {
                                try {
                                    val list = ApiTester.testAndListModels(client, baseUrl, apiKey.trim())
                                    models = list
                                    showModels = list.isNotEmpty()
                                    testResult = if (list.isNotEmpty()) "连接成功，发现 ${list.size} 个模型" else "连接成功"
                                    testError = false
                                    if (list.isNotEmpty() && model.isBlank()) model = list.first()
                                } catch (e: Exception) {
                                    testResult = e.message ?: "测试失败"
                                    testError = true
                                    showModels = false
                                } finally {
                                    testing = false
                                }
                            }
                        },
                        enabled = baseUrl.isNotBlank() && !testing
                    ) { Text(if (testing) "测试中…" else "测试") }
                }
                testResult?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                if (showModels && models.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("可用模型（点击选择）：", style = MaterialTheme.typography.labelMedium)
                    models.take(12).forEach { m ->
                        Text(
                            text = m,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { model = m }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("协议：")
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (protocol == "anthropic") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            "Anthropic 原生",
                            color = if (protocol == "anthropic") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clickable { protocol = "anthropic" }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (protocol == "openai") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            "OpenAI 兼容",
                            color = if (protocol == "openai") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clickable { protocol = "openai" }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("设为默认")
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = makeDefault, onCheckedChange = { makeDefault = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        profile.copy(name = name, baseUrl = baseUrl.trim().trimEnd('/'), apiKey = apiKey.trim(), model = model.trim(), protocol = protocol),
                        makeDefault
                    )
                },
                enabled = name.isNotBlank() && baseUrl.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun WebProfileEditorDialog(
    profile: ApiProfileEntity,
    onSaveName: (String, Boolean) -> Unit,
    onRelogin: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var makeDefault by remember { mutableStateOf(profile.isDefault) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑「${profile.name}」") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "「DeepSeek 官网免费」使用官网账号登录，无需 Base URL / API Key / 模型。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "如需更换官网账号，请重新登录：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onRelogin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重新登录官网账号（邮箱 / 手机号 + 密码）")
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("设为默认")
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = makeDefault, onCheckedChange = { makeDefault = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSaveName(name.trim(), makeDefault) },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun WebLoginDialog(
    sending: Boolean,
    onLogin: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登录 DeepSeek 官网") },
        text = {
            Column {
                Text(
                    "登录你的 DeepSeek 官网账号，即可免费对话（消息会同步到官网记录）。注意：调用网页版接口有封号风险，建议使用小号。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onLogin(email, password) },
                enabled = email.isNotBlank() && password.isNotBlank() && !sending
            ) { Text(if (sending) "登录中…" else "登录") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
