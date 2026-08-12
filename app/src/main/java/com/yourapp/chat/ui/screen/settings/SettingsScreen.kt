package com.yourapp.chat.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yourapp.chat.ChatApplication
import com.yourapp.chat.util.CrashLog
import com.yourapp.chat.util.FileUtil
import java.io.File
import kotlinx.coroutines.delay

/** 流式刷新频率档位（毫秒） */
private val REFRESH_OPTIONS = listOf(50 to "50ms 流畅", 100 to "100ms 均衡", 200 to "200ms 省电", 500 to "500ms 极省电")

/** 可选头像（emoji） */
private val AVATAR_OPTIONS = listOf("🐳", "🐱", "🐶", "🦊", "🐼", "🦁", "🐸", "🐙", "🤖", "😀", "😎", "👽")

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
    var avatar by remember { mutableStateOf(configRepo.getAvatar()) }
    var persona by remember { mutableStateOf(configRepo.getPersona()) }
    var showAbout by remember { mutableStateOf(false) }

    // 相册选图 → 复制到应用私有目录，路径存为头像
    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val dest = File(context.filesDir, "avatar_${System.currentTimeMillis()}.jpg")
            FileUtil.copyUriToFile(context, it, dest)?.let { saved ->
                avatar = saved.absolutePath
                configRepo.setAvatar(saved.absolutePath)
            }
        }
    }

    // 解析头像：emoji 或本地图片路径
    val avatarBitmap = remember(avatar) {
        val path = when {
            avatar.startsWith("/") -> avatar
            avatar.startsWith("file:") -> Uri.parse(avatar).path
            else -> null
        }
        path?.let { BitmapFactory.decodeFile(it) }
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
            // 个人信息（置顶）：头像 + 昵称 + 自定义设定
            Text(
                text = "个人信息",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 当前头像
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap.asImageBitmap(),
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = avatar.ifBlank { "🐳" },
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("选择头像", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "点击下方表情或从相册选择图片",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                AVATAR_OPTIONS.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (emoji == avatar) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                2.dp,
                                if (emoji == avatar) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                CircleShape
                            )
                            .clickable {
                                avatar = emoji
                                configRepo.setAvatar(emoji)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // 从相册选择自定义头像
            TextButton(onClick = { avatarLauncher.launch("image/*") }) {
                Text("从相册选择自定义头像")
            }
            Spacer(Modifier.height(12.dp))
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
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "刷新频率",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "AI 回答流式输出时界面的刷新间隔，频率越高文字越平滑、CPU 占用越高",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        REFRESH_OPTIONS.forEach { (ms, label) ->
                            FilterChip(
                                selected = refreshMs == ms,
                                onClick = {
                                    refreshMs = ms
                                    configRepo.setStreamRefreshMs(ms)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
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
