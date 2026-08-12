package com.yourapp.chat.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yourapp.chat.ChatApplication
import com.yourapp.chat.util.CrashLog
import com.yourapp.chat.util.DataBackup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenConfig: () -> Unit = {},
    onOpenOfficial: () -> Unit = {},
    showBack: Boolean = true
) {
    val context = LocalContext.current
    val configRepo = remember { ChatApplication.instance.configRepository }
    var streamingEnabled by remember { mutableStateOf(configRepo.isStreamingEnabled()) }
    var nickname by remember { mutableStateOf(configRepo.getNickname()) }
    var persona by remember { mutableStateOf(configRepo.getPersona()) }
    var showAbout by remember { mutableStateOf(false) }
    var showExportConfirm by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var backupResult by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val scope = rememberCoroutineScope()

    // 导出全部数据 → zip（CreateDocument 选保存位置）
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                backupResult = runCatching { DataBackup.export(context, uri) }
                    .fold({ Pair(it, false) }, { Pair("导出失败：${it.message}", false) })
            }
        }
    }

    // 导入备份 zip → 覆盖恢复（完成后自动重启）
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                backupResult = runCatching { DataBackup.import(context, uri) }
                    .fold({ Pair(it, true) }, { Pair("导入失败：${it.message}", true) })
            }
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            // 个人信息（置顶）：昵称 + 自定义设定
            Text(
                text = "个人信息",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("昵称（自定义）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = persona,
                onValueChange = { persona = it },
                label = { Text("我的设定（可选）") },
                placeholder = { Text("例如：我希望 AI 用简短口语回复，称呼我为小蓝…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Row {
                TextButton(
                    onClick = {
                        configRepo.setNickname(nickname.trim())
                        configRepo.setPersona(persona.trim())
                    },
                    enabled = nickname.isNotBlank() || persona.isNotBlank()
                ) { Text("保存个人设置") }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // API 配置入口
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                onClick = onOpenConfig
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("API 配置", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "添加 / 切换 自定义 API（DeepSeek、OpenAI、中转、Ollama 等）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
            }
            HorizontalDivider()
            // DeepSeek 官网模式入口
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                onClick = onOpenOfficial
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("DeepSeek 官网模式", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "使用官网账号免费对话，与官网网页端共享会话",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
            }
            HorizontalDivider()

            // 消息流式输出
            var refreshMs by remember { mutableStateOf(configRepo.getStreamRefreshMs()) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "消息流式输出",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "关闭后 AI 回答生成完毕才一次性显示（减少界面频繁刷新）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = streamingEnabled,
                        onCheckedChange = {
                            streamingEnabled = it
                            configRepo.setStreamingEnabled(it)
                        }
                    )
                }
                if (streamingEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "刷新频率",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "AI 回答流式输出时界面的刷新间隔：数值越小文字越平滑、CPU 占用越高",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = "${refreshMs}ms",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = refreshMs.toFloat(),
                        onValueChange = {
                            refreshMs = it.toInt()
                            configRepo.setStreamRefreshMs(it.toInt())
                        },
                        onValueChangeFinished = {
                            configRepo.setStreamRefreshMs(refreshMs)
                        },
                        valueRange = 1f..500f,
                        steps = 498,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Text(
                        text = "1ms（最快，高 CPU） · 500ms（最省电）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            // 流式刷新频率演示文章：切换频率时按新节奏重新逐字显示，直观对比刷新效果
            if (streamingEnabled) {
                val demoText = "欢迎体验流式输出演示。切换下方刷新频率后，本段文字会以新的节奏重新逐字显示：" +
                    "频率越快（50ms），文字增长越平滑流畅；频率越慢（500ms），画面越\"一跳一跳\"但更省电。" +
                    "实际对话中可按设备性能自由选择。"
                var revealed by remember { mutableStateOf(0) }
                LaunchedEffect(refreshMs) {
                    revealed = 0
                    while (revealed < demoText.length) {
                        delay(refreshMs.toLong())
                        revealed = (revealed + 2).coerceAtMost(demoText.length)
                    }
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("演示文章", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = demoText.take(revealed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // 数据管理：导出 / 导入全部数据（zip）
            Text(
                text = "数据管理",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showExportConfirm = true }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("导出全部数据", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "对话 / 消息 / API 配置 / 角色卡 / 世界书 / 技能 / 设置，打包为 zip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showImportConfirm = true }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("导入备份", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "从 zip 恢复全部数据（覆盖当前内容，旧数据自动留存为 *.bak_import）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // 关于软件（页面最底部）
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                onClick = { showAbout = true }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("关于软件", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "版本号 · 功能说明 · 崩溃日志",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    if (showAbout) {
        AboutDialog(
            context = context,
            onDismiss = { showAbout = false }
        )
    }

    if (showExportConfirm) {
        AlertDialog(
            onDismissRequest = { showExportConfirm = false },
            title = { Text("导出全部数据") },
            text = {
                Text("将导出对话、消息、API 配置、角色卡、世界书、技能、设置与崩溃日志。\n\n" +
                        "注意：备份包含 API 密钥与在线登录信息，请妥善保管。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportConfirm = false
                    val stamp = java.text.SimpleDateFormat(
                        "yyyyMMdd_HHmm",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date())
                    exportLauncher.launch("ChatApp备份_$stamp.zip")
                }) { Text("选择位置并导出") }
            },
            dismissButton = {
                TextButton(onClick = { showExportConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("导入备份") },
            text = {
                Text("将从 zip 恢复全部数据，覆盖当前内容（现有数据会先自动备份为 *.bak_import 留存）。\n\n" +
                        "导入完成后应用会自动重启。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    importLauncher.launch("*/*")
                }) { Text("选择备份文件") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) { Text("取消") }
            }
        )
    }

    backupResult?.let { (msg, isImport) ->
        AlertDialog(
            onDismissRequest = { backupResult = null },
            title = { Text(if (isImport) "导入完成" else "导出完成") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { backupResult = null }) {
                    Text(if (isImport) "稍后重启" else "确定")
                }
            },
            dismissButton = {
                if (isImport) {
                    TextButton(onClick = {
                        backupResult = null
                        DataBackup.restartApp(context)
                    }) { Text("立即重启") }
                }
            }
        )
    }
}

/** 关于软件：版本号 + 功能说明 + 崩溃日志 */
@Composable
private fun AboutDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    var crashLogText by remember { mutableStateOf(CrashLog.read(context).ifBlank { "暂无崩溃日志" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于软件") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "ChatApp",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "版本号：v1.0.6",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "数据存储位置：应用私有目录（Room 数据库 + 文件系统）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(
                    text = "功能说明：\n" +
                            "· 自定义 API：支持所有 OpenAI 兼容接口（DeepSeek、OpenAI、中转、Ollama 等）\n" +
                            "· 深度思考 / 联网搜索：对话输入区可自由开关\n" +
                            "· 回溯消息：长按消息选择「回溯到此重新生成」开新分支\n" +
                            "· 改写消息：长按消息选择「删除该消息及后续」\n" +
                            "· 压缩上下文：对话过长时点右上角压缩按钮，自动总结早期消息\n" +
                            "· 角色卡：支持导入 PNG（chara 块）与 JSON 角色卡，可在对话中应用\n" +
                            "· 世界书：支持导入世界书 JSON 文件，角色卡世界书与全局世界书分开管理\n" +
                            "· Skills：导入 SKILL.md 或从 GitHub 自动获取\n" +
                            "· 消息流式输出：逐 token 显示 AI 回答（可关闭以减少刷新）\n" +
                            "· 思考收纳篮：点击小箭头可展开 / 收起 AI 的思考内容\n" +
                            "· 所有数据仅保存在本机",
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(
                    text = "崩溃日志（发生闪退时记录于此）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = crashLogText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Row {
                    TextButton(onClick = {
                        if (crashLogText.isNotBlank() && crashLogText != "暂无崩溃日志" && crashLogText != "崩溃日志已清除") {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("崩溃日志", crashLogText))
                            crashLogText = "已复制到剪贴板"
                        }
                    }) { Text("复制崩溃日志") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        CrashLog.clear(context)
                        crashLogText = "崩溃日志已清除"
                    }) { Text("清除崩溃日志") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
