package com.yourapp.chat.ui.screen.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourapp.chat.ChatApplication
import com.yourapp.chat.data.local.entity.MessageEntity
import com.yourapp.chat.data.local.entity.WorldBookEntity
import com.yourapp.chat.data.local.entity.WorldEntryEntity
import com.yourapp.chat.data.remote.ApiTester
import com.yourapp.chat.data.remote.ShizukuHelper

/** 流式跟随阈值：视口顶部若干条以内视为「停留在生成区域」，保持平滑跟随；超过即停止跟随 */
private const val FOLLOW_THRESHOLD = 3

/**
 * 思考块正则：兼容半。?[思考]...[/思考] 与全。?【思考。?..。?思考】。? * 部分模型（如 deepseek-v4-flash 思考模式）会把推理过程直接写入正文而非 reasoning_content 字段。? */
private val ThinkingBlockRegex = Regex(
    "(\\[思考\\]|【思考】)(.*?)(\\[/思考\\]|【/思考】)",
    RegexOption.DOT_MATCHES_ALL
)

/**
 * 官网搜索来源标记：deepseek 官网联网搜索会在每句话后附加【reference:x】（x 。?0-20 的来源编号）。? * 仅在使用官网免费对话时隐藏，避免正文出现生硬的引用标记。? */
private val ReferenceRegex = Regex("【reference:\\d+】|\\[reference:\\d+\\]")

/** 消息时间显示：今天内只显。?时分秒，跨天则显。?年月日时分秒 */
private val TimeFmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
private fun formatMessageTime(timestamp: Long): String {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(timestamp))
}
private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
            ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

/** 附件大小上限 10MB */
private const val MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024

/**
 * 读取用户选择的附件（图片 / 文本 / JSON）。
 * - 图片：读取字节转 base64 data URL（data:image/xxx;base64,..）
 * - 文本/JSON：读取 UTF-8 文本内容（截断到 50000 字符）
 * 超过 10MB 或读取失败时直接忽略。
 */
private fun handleAttachment(
    context: Context,
    uri: android.net.Uri,
    onReady: (com.yourapp.chat.ui.screen.chat.Attachment) -> Unit
) {
    val resolver = context.contentResolver
    val name = queryDisplayName(resolver, uri) ?: "附件"
    val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1
    if (size > MAX_ATTACHMENT_BYTES) return
    try {
        val mime = resolver.getType(uri) ?: ""
        val isImage = mime.startsWith("image/")
        if (isImage) {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val dataUrl = "data:${mime.ifBlank { "image/jpeg" }};base64,$b64"
            onReady(com.yourapp.chat.ui.screen.chat.Attachment(name = name, mimeType = mime, isImage = true, content = dataUrl))
        } else {
            val text = resolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() } ?: return
            onReady(com.yourapp.chat.ui.screen.chat.Attachment(name = name, mimeType = mime, isImage = false, content = text.take(50000)))
        }
    } catch (_: Exception) {
    }
}

private fun queryDisplayName(resolver: android.content.ContentResolver, uri: android.net.Uri): String? {
    return try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    conversationId: Long,
    onBack: () -> Unit
) {
    val vm: ChatViewModel = viewModel(
        key = "chat_$conversationId",
        factory = ChatViewModel.factory(conversationId)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    // 离开对话（导航返回）时复位整页覆盖状态（如设置页），避免再次进入仍停留在设置。?
    DisposableEffect(conversationId) {
        onDispose { vm.resetOverlay() }
    }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingMessage by remember { mutableStateOf<MessageEntity?>(null) }    // 设置作为独立整页展示：进入设置时替换整个聊天界面
    val context = LocalContext.current
    // 附件选择器：图片 / 文本 / JSON（限制 10MB）
    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handleAttachment(context, it) { a -> vm.addAttachment(a) } }
    }
    val conv = state.conversation
    // 是否官网免费对话（deepseek_web）：决定设置项、消息操作与时间显示
    val isWeb = state.apiProfiles.firstOrNull { it.id == state.selectedProfileId }?.provider == "deepseek_web"
    // Shizuku 授权监听器：仅注册一次；用户点击授权后回调给 ViewModel
    DisposableEffect(Unit) {
        val binder = ShizukuHelper.addRequestPermissionListener { requestCode, grantResult ->
            if (requestCode == ShizukuHelper.REQUEST_CODE) {
                vm.onShizukuResult(grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED)
            }
        }
        onDispose { ShizukuHelper.removeRequestPermissionListener(binder) }
    }
    // 当 shizukuGrantRequested 为 true 时，尝试弹出授权窗口（只处理一次）
    LaunchedEffect(state.shizukuGrantRequested) {
        if (state.shizukuGrantRequested) {
            if (!ShizukuHelper.isAvailable()) {
                vm.onShizukuResult(false)
                android.widget.Toast.makeText(context, "未检测到 Shizuku，请安装并启动 Shizuku 应用", android.widget.Toast.LENGTH_LONG).show()
            } else if (!ShizukuHelper.isGranted()) {
                ShizukuHelper.requestPermission()
            } else {
                vm.onShizukuResult(true)
            }
        }
    }
    // 聊天页 ↔ 对话设置页：与导航页面切换相同的淡入淡出（Crossfade 交叉溶解，
    // 过渡期两层短暂共存，结束后不含残留透明层，滚动性能不受影响）
    Crossfade(
        targetState = state.showSettings && conv != null,
        animationSpec = tween(260),
        label = "chatSettingsCrossfade"
    ) { isSettings ->
        if (isSettings && conv != null) {
        ChatSettingsPage(
            conversation = conv,
            keepCount = state.compressKeepCount,
            isWeb = isWeb,
            onKeepCountChange = { vm.setCompressKeepCount(it) },
            onSave = { showThinking, maxTokens, maxCtx, temp, topK, topP ->
                vm.updateConversationSettings(showThinking, maxTokens, maxCtx, temp, topK, topP, "", "")
            },
            thinkingEnabled = state.thinkingEnabled,
            onThinkingChange = { vm.setThinking(it) },
            thinkingLevel = state.thinkingLevel,
            onThinkingLevelChange = { vm.setThinkingLevel(it) },
            searchEnabled = state.searchEnabled,
            onSearchChange = { vm.setSearch(it) },
            currentModel = state.apiProfiles.firstOrNull { it.id == state.selectedProfileId }?.model,
            currentProfileName = state.apiProfiles.firstOrNull { it.id == state.selectedProfileId }?.name,
            apiProfiles = state.apiProfiles,
            selectedProfileId = state.selectedProfileId,
            onSelectApi = { vm.selectApi(it) },
            onModelClick = { vm.toggleModelPicker() },
            compressionModel = state.compressionModel,
            onCompressionModelClick = { vm.toggleCompressionModelPicker() },
            visionModel = state.visionModel,
            onVisionModelClick = { vm.toggleVisionModelPicker() },
            savedTextModels = state.savedTextModels,
            savedVisionModels = state.savedVisionModels,
            onBack = { vm.toggleSettings() },
            injectionInterval = state.injectionInterval,
            onInjectionIntervalChange = { vm.setInjectionInterval(it) }
        )
        if (state.showModelPicker) {
            val curProfile = state.apiProfiles.firstOrNull { it.id == state.selectedProfileId }
            SavedModelPickerDialog(
                title = "选择聊天模型",
                models = state.savedTextModels,
                apiProfiles = state.apiProfiles,
                current = curProfile?.model,
                onSelect = { vm.selectModel(it) },
                onDismiss = { vm.toggleModelPicker() }
            )
        }
        if (state.showCompressionModelPicker) {
            val curProfile = state.apiProfiles.firstOrNull { it.id == state.selectedProfileId }
            SavedModelPickerDialog(
                title = "选择压缩模型",
                models = state.savedTextModels,
                apiProfiles = state.apiProfiles,
                current = state.compressionModel.ifBlank { curProfile?.model },
                onSelect = { vm.setCompressionModel(it) },
                onDismiss = { vm.toggleCompressionModelPicker() }
            )
        }
        if (state.showVisionModelPicker) {
            SavedModelPickerDialog(
                title = "选择识图模型",
                models = state.savedVisionModels,
                apiProfiles = state.apiProfiles,
                current = state.visionModel,
                onSelect = { vm.setVisionModel(it) },
                onDismiss = { vm.toggleVisionModelPicker() }
            )
        }
        return@Crossfade
    }

    // 使用 derivedStateOf 优化流式内容解析，避免每帧重新计算正。?    // 思考链已与正文分开展示：优先取 streamingThinking；非流式/旧数据时回退解析拼接内容
    // 注意：与完成态保持同一套 trim 规则——生成中与完成后文本逐字符一致，
    // 完成后切换数据源时气泡高度零变化，不会"弹跳"。
    val streamingThinkingContent by remember(state) {
        derivedStateOf {
            state.streamingThinking?.trim()?.takeIf { it.isNotBlank() }
                ?: state.streamingContent?.let { content ->
                    ThinkingBlockRegex.findAll(content)
                        .map { it.groupValues[2].trim() }
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                        .ifBlank { null }
                }
        }
    }

    val streamingBodyContent by remember(state) {
        derivedStateOf {
            if (isWeb) {
                // 官网：正文保留思考文本（完成态同样不剥离），仅剥离来源标注 + trim，与完成态逐字一致
                state.streamingContent?.let { it.replace(ReferenceRegex, "").trim().ifBlank { "…" } }
            } else if (state.streamingThinking != null) {
                // 思考已单独推送，streamingContent 为纯正文（剥离思考块 + trim，与完成态一致）
                state.streamingContent?.let { it.replace(ThinkingBlockRegex, "").trim().ifBlank { "…" } }
            } else {
                state.streamingContent?.let { it.replace(ThinkingBlockRegex, "").trim().ifEmpty { "…" } }
            }
        }
    }

    // —。?右侧搜索/定位面板状。?—。?
val scope = rememberCoroutineScope()
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Long>>(emptyList()) }
    var matchIndex by remember { mutableStateOf(-1) }
    var resultLines by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var highlightId by remember { mutableStateOf<Long?>(null) }
    // 消息 id -> LazyColumn(reverseLayout) 索引：chronological 索引 i 对应反序索引 size-1-i
    val idToIndex = remember(state.messages) {
        state.messages.mapIndexed { i, m -> m.id to (state.messages.size - 1 - i) }.toMap()
    }
    // 可定位消息（上一。?下一句）：只定位用户消息，按时间顺序
    val navIds = remember(state.messages) {
        state.messages.filter { it.role == "user" }.map { it.id }
    }
    var navIndex by remember { mutableStateOf(-1) }
    // 右侧面板：静止不动时弹出，一段时间后自动收回；滑动对话时立即收回
    var panelVisible by remember { mutableStateOf(false) }
    var lastScrollTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var panelShownAt by remember { mutableStateOf(0L) }
    // 本次空闲是否已弹出过：弹出并自动收回后不再重复弹出，直到用户再次滑动
    var panelPokedThisIdle by remember { mutableStateOf(false) }
    fun pokePanel() {
        panelVisible = true
        panelShownAt = System.currentTimeMillis()
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(250)
            val now = System.currentTimeMillis()
            val idle = now - lastScrollTime > 800
            if (idle && !panelVisible && !panelPokedThisIdle) {
                pokePanel()
                panelPokedThisIdle = true
            } else if (panelVisible && !searchOpen && now - panelShownAt > 3000) {
                panelVisible = false
            }
        }
    }

    // 是否停靠在底部：用户手动上滑浏览历史时停止自动跟随。?    // LazyColumn 使用 reverseLayout，索。?0 即底部（最新消息），因此停靠底。?= 首项可见。?
var isAtBottom by remember { mutableStateOf(true) }
    // 流式期间。?跟随"开关：只要用户仍停留在生成区域（视口顶部若干条以内）就保持平滑跟随。?    // 一旦回翻历史远离生成区就彻底停止，绝不把用户硬拽到底部。?
var followStream by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex
        }.collect { idx ->
            val nearBottom = idx <= FOLLOW_THRESHOLD
            when {
                nearBottom -> {
                    isAtBottom = true
                    followStream = true
                }
                else -> {
                    isAtBottom = false
                    followStream = false
                }
            }
            // 滑动即交互：标记滚动时间并立即收回面板，同时允许下一次空闲重新弹出
            lastScrollTime = System.currentTimeMillis()
            panelVisible = false
            panelPokedThisIdle = false
        }
    }

    /**
     * 。?idToIndex 映射出的（反向布局）索引定位到「屏幕顶部」。?     * 反向布局。?scrollToItem 会把条目贴到底部边缘（输入框上方），并非用户想要。?靠在最。?。?     * 这里先贴到底部、等一帧拿到条目尺寸后，再。?scrollOffset 把条目顶边抬到视口顶部。?     */
    suspend fun scrollLocateTop(idx: Int) {
        listState.scrollToItem(idx)
        kotlinx.coroutines.delay(50) // 等一帧让布局完成后再测量
        runCatching {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == idx } ?: return
            val viewport = info.viewportEndOffset - info.viewportStartOffset
            val scrollOffset = (viewport - item.size).coerceAtLeast(0)
            listState.animateScrollToItem(idx, scrollOffset)
        }
    }

    fun jumpToMessage(id: Long) {
        val idx = idToIndex[id] ?: return
        highlightId = id
        pokePanel()
        scope.launch { scrollLocateTop(idx) }
    }

    /** 上一。?下一句：只在用户消息之间跳转定位，并把目标消息标红、停在屏幕顶。?*/
    fun navigateSentence(dir: Int) {
        if (navIds.isEmpty()) return
        var base = navIndex
        if (base !in navIds.indices) {
            // 未定位过：从当前看得到的用户消息推导位置
            val visibleId = state.messages.getOrNull(state.messages.size - 1 - listState.firstVisibleItemIndex)?.id
            base = navIds.indexOf(visibleId).takeIf { it >= 0 } ?: 0
        }
        navIndex = (base + dir).coerceIn(0, navIds.size - 1)
        jumpToMessage(navIds[navIndex])
    }

    fun runSearch() {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            matches = emptyList()
            resultLines = emptyMap()
            matchIndex = -1
            return
        }
        val found = mutableListOf<Long>()
        val lines = mutableMapOf<Long, String>()
        for (m in state.messages) {
            if (m.role == "system") continue
            val line = m.content.lines().firstOrNull { it.contains(q) }
                ?: if (m.content.contains(q)) m.content else null
            if (line != null) {
                found.add(m.id)
                lines[m.id] = line.trim()
            }
        }
        matches = found
        resultLines = lines
        matchIndex = if (found.isEmpty()) -1 else 0
        if (found.isNotEmpty()) jumpToMessage(found[0])
    }

    fun navigateMatch(dir: Int) {
        if (matches.isEmpty()) return
        matchIndex = ((matchIndex + dir) + matches.size) % matches.size
        jumpToMessage(matches[matchIndex])
    }

    fun closeSearch() {
        searchOpen = false
        searchQuery = ""
        matches = emptyList()
        resultLines = emptyMap()
        matchIndex = -1
        highlightId = null
        pokePanel()
    }

    // 首次进入对话：等待 LazyList 布局就绪后再定位到底部，避免首帧错过滚动时机（参考 rikkahub）
    var firstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(key1 = state.messages.size, key2 = listState) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        if (firstLoad) {
            // 使用 snapshotFlow 等待布局真正 ready（itemCount > 0 且 layoutInfo 可用）
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .filter { it > 0 }
                .first()
                .also { _ ->
                    listState.scrollToItem(0)
                    firstLoad = false
                }
        } else {
            // 后续新消息到达时平滑滚动到底部（仅当用户停靠在底部）
            if (isAtBottom) listState.animateScrollToItem(0)
        }
    }

    // 官网同步完成后：同步会整批替换消息（新的 id），导致 LazyColumn 丢失滚动位置跳到顶部。
    // 这里在同步版本变化后强制回到列表底部（最新消息），符合"别把我滑到最上面"的诉求。
    // 使用 snapshotFlow 等待布局 ready 再滚动，避免同步后首帧错过。
    LaunchedEffect(state.syncVersion, listState) {
        if (state.syncVersion > 0 && state.messages.isNotEmpty()) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .filter { it > 0 }
                .first()
                .also { _ -> listState.scrollToItem(0) }
        }
    }

    // 平滑流式：reverseLayout 让 index 0 锚定在底部，生成中的消息在 index 0 向上增长，
    // 只要停在底部（followStream），视图会自动贴合底部，无需逐 token 滚动。
    // 若已偏离底部才需要瞬时贴回，避免 animateScrollToItem 被每个 token 反复重启造成抖动。
    LaunchedEffect(state.isSending, followStream, listState) {
        if (state.isSending && state.messages.isNotEmpty() && followStream) {
            if (!listState.isScrolledToBottom()) {
                listState.scrollToItem(0)
            }
        }
    }

    // 生成完成后立即无动画锁定到底部。完成瞬间数据库回写全文、消息高度变化，
    // 若仍 animateScrollToItem 与内容高度动画竞争，会出现"往下顶一下再复位"的跳动；
    // 改为同步 scrollToItem(0) 一次性贴死到底部，杜绝该跳动。
    LaunchedEffect(state.isSending, listState) {
        if (!state.isSending && state.messages.isNotEmpty() && followStream) {
            listState.scrollToItem(0)
        }
    }

    // 提示：先同步清空 info（避免因 showSnackbar 挂起被取消而残留，导致再次进入对话又弹一次）。?    // 再在独立作用域内显示（不阻塞、不拦截触摸、半透明背景、较短时长）。?
    LaunchedEffect(state.info) {
        val msg = state.info ?: return@LaunchedEffect
        vm.clearInfo()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(
            message = msg,
            duration = SnackbarDuration.Short,
            withDismissAction = true
        )
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    // 不拦截任何点击，让手势穿透
                    .pointerInput(Unit) {},
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        modifier = Modifier
                            // 半透明背景、内容不干扰点击
                            .graphicsLayer { alpha = 0.92f },
                        containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            )
        },
        topBar = {
            TopAppBar(
                title = { Text(state.conversation?.title ?: "对话") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 官网免费通道不支持压缩上下文，隐藏压缩按。?
                    if (!isWeb) {
                        IconButton(onClick = { vm.compressContext() }) {
                            Icon(Icons.Filled.Compress, contentDescription = "压缩上下文")
                        }
                    }
                    IconButton(onClick = { vm.toggleWorldPicker() }) {
                        Icon(Icons.Filled.MenuBook, contentDescription = "世界书置")
                    }
                    IconButton(onClick = { vm.toggleCardPicker() }) {
                        Icon(Icons.Filled.Person, contentDescription = "应用角色")
                    }
                    // 官网免费时显示同步按。?
                    if (isWeb) {
                        IconButton(onClick = { vm.syncOfficialMessages() }) {
                            Icon(Icons.Filled.Sync, contentDescription = "同步官网消息")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column {
                state.error?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                // 处理状态显示（如"正在识别图片…"），类似 rikkahub 的 OCR 状态
                AnimatedVisibility(
                    visible = state.processingStatus != null,
                    enter = expandVertically(
                        expandFrom = androidx.compose.ui.Alignment.Top,
                        animationSpec = tween(200)
                    ) + fadeIn(tween(150)),
                    exit = shrinkVertically(
                        shrinkTowards = androidx.compose.ui.Alignment.Top,
                        animationSpec = tween(150)
                    ) + fadeOut(tween(150))
                ) {
                    state.processingStatus?.let { status ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // 自建 API 联网搜索：显示"正在浏览网页…"
                AnimatedVisibility(
                    visible = state.webBrowsing,
                    enter = expandVertically(
                        expandFrom = androidx.compose.ui.Alignment.Top,
                        animationSpec = tween(200)
                    ) + fadeIn(tween(150)),
                    exit = shrinkVertically(
                        shrinkTowards = androidx.compose.ui.Alignment.Top,
                        animationSpec = tween(150)
                    ) + fadeOut(tween(150))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "正在浏览网页…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
                if (state.attachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        state.attachments.forEach { att ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
                                ) {
                                    Icon(
                                        if (att.isImage) Icons.Filled.Image else if (att.name.endsWith(".json", true)) Icons.Filled.Description else Icons.Filled.AttachFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(att.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    IconButton(onClick = { vm.removeAttachment(att.name) }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Filled.Close, contentDescription = "移除附件", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 设置按钮：位于消息框左侧，打开独立的对话设置页
                    IconButton(onClick = { vm.toggleSettings() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "对话设置")
                    }
                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = vm::onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入消息…") },
                        enabled = !state.isSending
                    )
                    if (state.isSending) {
                        // 生成中：发送键变为中止键，随时停止 AI 回答
                        IconButton(onClick = { vm.stopSending() }) {
                            Icon(Icons.Filled.Close, contentDescription = "中止生成")
                        }
                    } else {
                        // 附件按钮：添加图片 / 文本 / JSON（官网免费通道不支持图片附件，仅文本/JSON）
                        IconButton(onClick = {
                            attachLauncher.launch(
                                if (isWeb) {
                                    arrayOf("text/plain", "application/json", "text/json", "text/markdown")
                                } else {
                                    arrayOf("image/*", "text/plain", "application/json", "text/json", "text/markdown")
                                }
                            )
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = "添加附件")
                        }
                        IconButton(
                            onClick = { vm.sendMessage() },
                            enabled = state.inputText.isNotBlank() || state.attachments.isNotEmpty()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发")
                        }
                    }
                }
            }
        }
    ) { padding ->
        // 反序列表：reverseLayout 让最新消息固定在底部，索。?0 = 最新，
        // 自动滚动跟随底部时始终看到思考链/正文的最新进度。?
val reversedMessages = remember(state.messages) { state.messages.asReversed() }
        // 生成中的消息 = 列表最后一条且内容仍为空占位的 assistant 消息。
        // 不能只看"最后一条"：发送图片需先调识图模型（耗时期间列表末尾还是上一条 AI 回复），
        // 若把它当成生成中消息，会显示成"…/思考中…"（上一条回复暂时消失，直到占位消息插入）。
        val generatingMsgId = if (state.isSending) {
            state.messages.lastOrNull()
                ?.takeIf { it.role == "assistant" && it.content.isEmpty() }?.id
        } else null
        
        // 使用 derivedStateOf 避免每帧重新计算流式内容解析
        val streamingThinkingContent = remember(state) {
            derivedStateOf { state.streamingThinking }
        }
        val streamingBodyContent = remember(state) {
            derivedStateOf { state.streamingContent }
        }
        
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 16.dp),
                state = listState,
                reverseLayout = true,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // 优化：启用内容类型优化，减少重组
                userScrollEnabled = true
            ) {
                items(
                    reversedMessages,
                    key = { it.id },
                    contentType = { m -> if (m.role == "system") "system" else if (m.role == "user") "user" else "assistant" }
                ) { msg ->
                    // system 消息（角色卡提示/世界信息/早期总结）为注入内容，对用户不可。?
                    if (msg.role != "system") {
                        val generating = generatingMsgId != null && generatingMsgId == msg.id
                        // 预解析生成中消息的思。?正文部分，避。?MessageBubble 重复计算正则
                        val (thinkingPart, bodyPart) = if (generating) {
                            streamingThinkingContent.value to streamingBodyContent.value
                        } else {
                            // 非生成中消息。?MessageBubble 内部解析（用 remember 缓存
                            null to null
                        }
                        MessageBubble(
                            message = msg,
                            vm = vm,
                            generating = generating,
                            // 生成中该消息的内容来自内存态流式内容（避免依赖 Room 刷新。?                            streamingContent = if (generating) state.streamingContent else null,
                            // 预解析的思。?正文部分（仅生成中消息有效）
                            precomputedThinking = thinkingPart,
                            precomputedBody = bodyPart,
                            showThinkingEnabled = state.conversation?.showThinking ?: true,
                            deepThinking = state.thinkingEnabled,
                            isWeb = isWeb,
                            highlighted = msg.id == highlightId,
                            onEditRequest = { editingMessage = it }
                        )
                    }
                }
            }
            // 搜索面板（置于右侧控制面板之下，右侧按钮可覆盖其上）
            if (searchOpen) {
                SearchPanel(
                    modifier = Modifier.align(Alignment.TopCenter),
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = { closeSearch() },
                    onSearch = { runSearch() },
                    results = matches,
                    resultLines = resultLines,
                    total = matches.size,
                    onSelect = { id -> jumpToMessage(id) }
                )
            }
            // 右侧定位/搜索控制面板：回到顶。?/ 搜索 / 回到底部。?            // 无操。?3 秒后自动滑回右侧边缘，交互时弹出。?
            RightControlPanel(
                modifier = Modifier.align(Alignment.CenterEnd),
                visible = panelVisible,
                onTop = {
                    pokePanel()
                    scope.launch { listState.scrollToItem(state.messages.size.coerceAtLeast(1) - 1) }
                },
                onBottom = {
                    pokePanel()
                    scope.launch { listState.scrollToItem(0) }
                },
                onSearch = {
                    searchOpen = !searchOpen
                    pokePanel()
                }
            )
        }
    }

    if (state.showCardPicker) {
        CardPickerDialog(
            cards = state.availableCards,
            currentCardId = state.conversation?.characterCardId,
            useCharacterCard = state.conversation?.useCharacterCard ?: true,
            onUseCharacterCardChange = { vm.setUseCharacterCard(it) },
            onApply = { vm.applyCard(it) },
            onDismiss = { vm.toggleCardPicker() }
        )
    }

    if (state.showWorldPicker) {
        WorldPickerDialog(
            cardWorldEntries = state.cardWorldEntries,
            books = state.worldBooks,
            entriesByBook = state.entriesByBook,
            onLoadBook = { vm.loadBookEntries(it) },
            onToggleEntry = { vm.toggleWorldEntry(it) },
            onToggleBook = { vm.toggleSelectedWorldBook(it) },
            selectedWorldBookIds = state.selectedWorldBookIds,
            onDismiss = { vm.toggleWorldPicker() }
        )
    }

    if (state.showApiPicker) {
        ApiPickerDialog(
            profiles = state.apiProfiles,
            selectedId = state.selectedProfileId,
            webLocked = state.conversationUsedApi,
            onSelect = { vm.selectApi(it) },
            onDismiss = { vm.toggleApiPicker() }
        )
    }

    if (state.showModelPicker) {
        val curProfile = state.apiProfiles.firstOrNull { it.id == state.selectedProfileId }
        ModelPickerDialog(
            profile = curProfile,
            currentModel = curProfile?.model,
            savedModels = state.savedTextModels,
            apiProfiles = state.apiProfiles,
            onSelect = { vm.selectModel(it) },
            onDismiss = { vm.toggleModelPicker() }
        )
    }

    editingMessage?.let { target ->
        EditMessageDialog(
            initialText = target.content,
            onDismiss = { editingMessage = null },
            onConfirm = { newText ->
                editingMessage = null
                vm.editUserMessage(target.id, newText)
            }
        )
    }
    
    // 分支选择器（回溯用）：包含用户与 AI 消息
    if (state.showBranchPicker) {
        BranchPickerDialog(
            messages = state.messages,
            selectedId = state.branchSelectedMessageId,
            onSelect = { id -> vm.branchAt(id); vm.toggleBranchPicker() },
            onDismiss = { vm.toggleBranchPicker() }
        )
    }
    
    // 改写选择。?
    if (state.showRewritePicker) {
        RewritePickerDialog(
            messages = state.messages,
            selectedId = state.rewriteSelectedMessageId,
            isWeb = isWeb,
            onSelect = { id -> vm.rewrite(id); vm.toggleRewritePicker() },
            onDismiss = { vm.toggleRewritePicker() }
        )
    }
    }
}

@Composable
private fun EditMessageDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑消息") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("编辑后 AI 将重新回复") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text("确认并重新生") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 分支选择器（回溯用）：显示用户与 AI 消息，默认选中最后一条，并把选中消息自动滚到顶部
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchPickerDialog(
    messages: List<MessageEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    // 选中项自动滚到列表顶部：若消息很多没有足够空间，就尽量滚动到顶部
    LaunchedEffect(selectedId, messages.size) {
        val idx = messages.indexOfFirst { it.id == selectedId }
        if (idx >= 0) {
            if (listState.canScrollBackward) listState.animateScrollToItem(idx)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择回溯位置（用户 / AI 消息）") },
        text = {
            if (messages.isEmpty()) {
                Text("暂无消息")
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isSelected = msg.id == selectedId
                        val contentPreview = msg.content.take(60)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(msg.id) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelected) {
                                Icon(Icons.Filled.Check, contentDescription = "已选", tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (msg.role == "user") "用户：$contentPreview" else "AI：$contentPreview",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (msg.role == "user") MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = formatMessageTime(msg.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Filled.ArrowRight, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 改写选择器：显示所有非系统消息，默认选中最后一条，空间不足时在底部展示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RewritePickerDialog(
    messages: List<MessageEntity>,
    selectedId: Long?,
    isWeb: Boolean,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val filteredMessages = messages.filter { it.role != "system" }
    val listState = rememberLazyListState()
    // 选中项自动滚到列表顶部
    LaunchedEffect(selectedId, filteredMessages.size) {
        val idx = filteredMessages.indexOfFirst { it.id == selectedId }
        if (idx >= 0) {
            if (listState.canScrollBackward) listState.animateScrollToItem(idx)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择改写位置") },
        text = {
            if (filteredMessages.isEmpty()) {
                Text("暂无可改写消息")
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredMessages, key = { it.id }) { msg ->
                        val isSelected = msg.id == selectedId
                        val roleLabel = if (msg.role == "user") "用户" else "AI"
                        val contentPreview = msg.content.take(60)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isWeb || msg.role == "user") { onSelect(msg.id) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (msg.role == "user") Icons.Filled.Person else Icons.Filled.Psychology,
                                contentDescription = null,
                                tint = if (isWeb && msg.role != "user") MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "$roleLabel: $contentPreview",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isWeb && msg.role != "user") MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = formatMessageTime(msg.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Filled.Check, contentDescription = "已选", tint = MaterialTheme.colorScheme.primary)
                            }
                            if (isWeb && msg.role != "user") {
                                Text(
                                    text = "官网不可改写",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun MessageBubble(
    message: MessageEntity,
    vm: ChatViewModel,
    generating: Boolean = false,
    streamingContent: String? = null,
    // 预解析的思。?正文部分（仅生成中消息有效，避免重复计算正则
    precomputedThinking: String? = null,
    precomputedBody: String? = null,
    showThinkingEnabled: Boolean,
    // 是否开启深度思考开关（。?showThinkingEnabled 同时开启时，AI 回复自动展开思维链）
    deepThinking: Boolean = false,
    // 官网免费对话（deepseek_web）不支持思维链，整段正文直接展示
    isWeb: Boolean = false,
    // 是否高亮该消息（搜索定位时标记当前命中项
    highlighted: Boolean = false,
    onEditRequest: (MessageEntity) -> Unit
) {
    val isUser = message.role == "user"
    val context = LocalContext.current
    // 生成中优先使用内存态流式内容；否则用数据库内容
    val content = streamingContent ?: message.content
    // 思考块解析：兼容半。?[思考]...[/思考] 与全。?【思考。?..。?思考】（部分模型会把思考写入正文）。?    // 合并所有思考块、过滤空白块（空思考块不显示收纳篮）。官网免费对话不解析思考链。?
val thinkingPart = if (isWeb) null else if (generating) precomputedThinking else remember(content) {
        ThinkingBlockRegex.findAll(content)
            .map { it.groupValues[2].trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .ifBlank { null }
    }
    val bodyPart = if (generating) precomputedBody else remember(content) {
        val raw = if (isWeb) content.trim() else content.replace(ThinkingBlockRegex, "").trim()
        // 官网免费对话：隐藏每句话后的【reference:x】来源标。?
val stripped = if (isWeb) raw.replace(ReferenceRegex, "").trim() else raw
        stripped.ifEmpty { "…" }
    }
    val hasThinking = !thinkingPart.isNullOrBlank()
    // 生成中且正文还没出现 。?显示思。?生成动画
    val streamingThinking = generating && hasThinking && bodyPart == "…"
    // 深度思考开启时，思维链默认向下展开（避免收纳篮把内容顶上去）；
    // 未开启深度思考时默认收起，点击箭头可展开/收起
    val autoExpand = deepThinking
    var showThinking by remember { mutableStateOf(autoExpand) }
    // 深度思考开启时：流式生成过程中（即使思考内容后才到达）也保持展开
    LaunchedEffect(autoExpand, hasThinking, generating) {
        if (autoExpand && hasThinking && generating) showThinking = true
    }

    fun copyContent() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("消息", message.content))
    }

    // AI 回复文字浅蓝背景（搜索定位高亮时用主题色覆盖）；深色模式用主题容器保证文字可。?
    val aiBubbleColor = if (isSystemInDarkTheme())
        MaterialTheme.colorScheme.secondaryContainer else Color(0xFFE3F2FD)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = when {
                highlighted -> MaterialTheme.colorScheme.tertiaryContainer
                isUser -> MaterialTheme.colorScheme.primaryContainer
                message.role == "system" -> MaterialTheme.colorScheme.surfaceVariant
                else -> aiBubbleColor
            },
            shape = MaterialTheme.shapes.extraLarge,
            // 文字横贯整行：AI 从左到右，用户从右到左。
            // 用户消息用 wrapContentWidth：气泡只包裹文字，四边内边距一致，
            // 使文字到背景边框的距离与左侧 AI 气泡相同（而非通栏后右侧空一大块）。
            // animateContentSize：流式生成时气泡背景随文字平滑扩展（参考 rikkahub）
            modifier = (if (isUser)
                    Modifier.wrapContentWidth().padding(end = 16.dp)
                else
                    Modifier.fillMaxWidth())
                .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy))
        ) {
            Column(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
            ) {
                if (message.role == "system") {
                    Text(
                        text = "【系统】",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                // 思考内容收纳篮：小箭头 + 小号浅色标题，点击展开/收起；深度思考开启时默认展开。
                // 深度思考开关关闭时整块隐藏（思考已禁用，不应展示历史思考内容）
                if (showThinkingEnabled && deepThinking && !thinkingPart.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showThinking = !showThinking }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showThinking || streamingThinking) {
                                Icons.Filled.ExpandLess
                            } else {
                                Icons.Filled.ExpandMore
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = if (streamingThinking) "思考中…" else "思考内容",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // AnimatedVisibility 始终组合（仅。?visible 控制），
                    // 保证展开/收起。?enter/exit 动画真正播放。?
                    AnimatedVisibility(
                        visible = showThinking || streamingThinking,
                        enter = expandVertically(
                            expandFrom = androidx.compose.ui.Alignment.Top,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)
                        ) + fadeIn(tween(200)),
                        exit = shrinkVertically(
                            shrinkTowards = androidx.compose.ui.Alignment.Top,
                            animationSpec = tween(180)
                        ) + fadeOut(tween(150))
                    ) {
                        // 思维链圆框：背景更深，与正文气泡区分
                        // animateContentSize：思考链流式增长时逐行平滑展开，不再"一顿一顿"
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .padding(start = 16.dp, top = 2.dp)
                                .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy))
                            ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    text = thinkingPart,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (streamingThinking) {
                                    AnimatedDots(text = "", paddingTop = 4)
                                }
                            }
                        }
                    }
                }
                if (generating && bodyPart == "…") {
                    AnimatedDots(text = if (thinkingPart.isNullOrBlank()) "正在生成" else "", paddingTop = 4)
                } else {
                    // 正文：避免流式增长时逐字跳变；用户消息右对齐
                    // 移除 animateContentSize 以防止生成完成后的跳动问。?
                    Text(
                        text = bodyPart ?: "…",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = if (isUser) TextAlign.End else TextAlign.Start,
                    )
                }
                // 用户消息附件：图片缩略图 / 文件 chip（元数据存于 message.attachmentsJson）
                val attachments = remember(message.attachmentsJson, isUser) {
                    if (!isUser || message.attachmentsJson.isNullOrBlank()) emptyList()
                    else runCatching {
                        val arr = org.json.JSONArray(message.attachmentsJson)
                        (0 until arr.length()).map { i ->
                            val o = arr.getJSONObject(i)
                            AttachMeta(
                                name = o.optString("name"),
                                mime = o.optString("mime"),
                                isImage = o.optBoolean("isImage"),
                                dataUrl = o.optString("dataUrl")
                            )
                        }
                    }.getOrDefault(emptyList())
                }
                if (attachments.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                    ) {
                        attachments.forEach { a ->
                            if (a.isImage && a.dataUrl.isNotBlank()) {
                                val bmp = remember(a.dataUrl) { decodeSampledImage(a.dataUrl, 1024) }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = a.name,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .widthIn(max = 220.dp)
                                            .heightIn(max = 220.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    AttachChip(a)
                                }
                            } else {
                                AttachChip(a)
                            }
                        }
                    }
                }
                // 用户消息：识图描述仅注入聊天模型上下文，不在界面显示（rikkahub OCR 思路）
                // AI 消息：联网搜索访问的网页来源（存于 attachmentsJson，供"查看访问的网页"按钮使用）
                if (!isUser && !message.attachmentsJson.isNullOrBlank()) {
                    val webSources = remember(message.attachmentsJson) {
                        runCatching {
                            val arr = org.json.JSONArray(message.attachmentsJson)
                            val sources = mutableListOf<Pair<String, String>>()
                            (0 until arr.length()).forEach { i ->
                                val o = arr.getJSONObject(i)
                                val t = o.optString("title")
                                val u = o.optString("url")
                                if (t.isNotBlank() && u.isNotBlank()) sources.add(t to u)
                            }
                            if (sources.isNotEmpty()) sources else null
                        }.getOrNull()
                    }
                    if (webSources != null) {
                        var showSources by remember { mutableStateOf(false) }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSources = !showSources }
                        ) {
                            Icon(
                                Icons.Filled.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "访问的网页 (${webSources.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                if (showSources) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        AnimatedVisibility(visible = showSources) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                webSources.forEach { (title, url) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                if (intent.resolveActivity(context.packageManager) != null) {
                                                    context.startActivity(intent)
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            title,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // 每条消息下方操作按钮（替代长按菜单）：AI 靠左、用户靠右，与文字方向一。?
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            // 官网免费对话不显示每条消息的时间（官网记录无精确本地时间语义。?
            if (!isWeb) {
                Text(
                    text = if (isSameDay(message.timestamp, System.currentTimeMillis())) {
                        // 复用顶层缓存的时间格式化器，避免每条消息气泡在流式重组时反复 new SimpleDateFormat
                        TimeFmt.format(java.util.Date(message.timestamp))
                    } else {
                        formatMessageTime(message.timestamp)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(10.dp))
            }
            if (isUser) {
                // 用户消息：回溯（官网免费不支持，隐藏）/ 编辑 / 删除 / 复制 / 喜欢
                if (!isWeb) {
                    MessageAction(
                        icon = Icons.Filled.CallSplit,
                        label = "回溯",
                        onClick = { vm.toggleBranchPicker() }
                    )
                }
                MessageAction(
                    icon = Icons.Filled.Edit,
                    label = "编辑",
                    onClick = { onEditRequest(message) }
                )
                MessageAction(
                    icon = Icons.Filled.Delete,
                    label = "删除",
                    onClick = { vm.deleteMessage(message.id) }
                )
                MessageAction(
                    icon = Icons.Filled.ContentCopy,
                    label = "复制",
                    onClick = { copyContent() }
                )
                MessageAction(
                    icon = if (message.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = if (message.isFavorite) "已喜" else "喜欢",
                    onClick = { vm.toggleFavorite(message.id, !message.isFavorite) },
                    tint = if (message.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (message.role != "system") {
                // AI 消息：复制 / 重新生成 / 喜欢（编辑、删除官网免费不支持，隐藏）
                MessageAction(
                    icon = Icons.Filled.ContentCopy,
                    label = "复制",
                    onClick = { copyContent() }
                )
                // 重新生成：官网免费对话同样可用
                MessageAction(
                    icon = Icons.Filled.Refresh,
                    label = "重新生成",
                    onClick = { vm.regenerate(message.id) }
                )
                if (!isWeb) {
                    // AI 消息同样支持回溯（分支选择器已包含 AI 消息）
                    MessageAction(
                        icon = Icons.Filled.CallSplit,
                        label = "回溯",
                        onClick = { vm.toggleBranchPicker() }
                    )
                    MessageAction(
                        icon = Icons.Filled.Edit,
                        label = "编辑",
                        onClick = { onEditRequest(message) }
                    )
                    MessageAction(
                        icon = Icons.Filled.Delete,
                        label = "删除",
                        onClick = { vm.deleteMessage(message.id) }
                    )
                }
                MessageAction(
                    icon = if (message.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = if (message.isFavorite) "已喜" else "喜欢",
                    onClick = { vm.toggleFavorite(message.id, !message.isFavorite) },
                    tint = if (message.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MessageAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = tint
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardPickerDialog(
    cards: List<com.yourapp.chat.data.local.entity.CharacterCardEntity>,
    currentCardId: Long?,
    useCharacterCard: Boolean,
    onUseCharacterCardChange: (Boolean) -> Unit,
    onApply: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择角色") },
        text = {
            Column {
                if (cards.isEmpty()) {
                    Text("暂无角色卡，请先在「角色卡」页面导")
                }
                // 角色卡注入开。?
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("角色卡注", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = useCharacterCard,
                        onCheckedChange = onUseCharacterCardChange
                    )
                }
                HorizontalDivider()
                cards.forEach { card ->
                    val isCurrent = card.id == currentCardId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(card.name)
                            if (isCurrent) {
                                Text(
                                    "当前使用",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (!isCurrent) {
                            TextButton(onClick = { onApply(card.id) }) { Text("选择") }
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun WorldPickerDialog(
    cardWorldEntries: List<WorldEntryEntity>,
    books: List<WorldBookEntity>,
    entriesByBook: Map<Long, List<WorldEntryEntity>>,
    onLoadBook: (Long) -> Unit,
    onToggleEntry: (WorldEntryEntity) -> Unit,
    onToggleBook: (Long) -> Unit, // 切换世界书选中状
    selectedWorldBookIds: List<Long>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("世界书选择") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "发送消息时，启用条目命中关键词会自动注入上下文",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                // 当前对话选中的特定世界书（一键启。?停用。?
                if (selectedWorldBookIds.isNotEmpty()) {
                    Text(
                        "当前对话选中的特定世界书（优先注入）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    selectedWorldBookIds.forEach { bookId ->
                        val book = books.find { it.id == bookId }
                        val isSelected = selectedWorldBookIds.contains(bookId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(book?.name ?: "未知世界书 #$bookId", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${entriesByBook[bookId]?.size ?: 0} 条设",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1
                                )
                            }
                            Switch(
                                checked = isSelected,
                                onCheckedChange = { onToggleBook(bookId) }
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
                }

                // 角色卡世界书（条目直接展示）
                if (cardWorldEntries.isNotEmpty()) {
                    Text(
                        "角色卡世界书",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    cardWorldEntries.forEach { entry ->
                        WorldEntryPickRow(entry, onToggleEntry)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }

                // 导入的世界书集合（一个文件一个，点击展开）
                books.forEach { book ->
                    val isSelected = selectedWorldBookIds.contains(book.id)
                    WorldBookPickSection(
                        book = book,
                        entries = entriesByBook[book.id].orEmpty(),
                        loaded = entriesByBook.containsKey(book.id),
                        onExpand = { onLoadBook(book.id) },
                        onToggleEntry = onToggleEntry,
                        isSelected = isSelected,
                        onToggleBook = { onToggleBook(book.id) }
                    )
                }

                // 手动条目
                val manual = entriesByBook[-1L].orEmpty()
                if (manual.isNotEmpty()) {
                    if (books.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        "手动条目",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    manual.forEach { entry ->
                        WorldEntryPickRow(entry, onToggleEntry)
                    }
                }

                if (cardWorldEntries.isEmpty() && books.isEmpty() && manual.isEmpty()) {
                    Text("暂无世界书。可在「菜单-全局世界书」导入文件或手动新增")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

@Composable
private fun WorldBookPickSection(
    book: WorldBookEntity,
    entries: List<WorldEntryEntity>,
    loaded: Boolean,
    onExpand: () -> Unit,
    onToggleEntry: (WorldEntryEntity) -> Unit,
    isSelected: Boolean,
    onToggleBook: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!loaded) onExpand()
                    expanded = !expanded
                }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(book.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (loaded) "${entries.size} 条设" else "点击展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            // 世界书级别的一键启。?停用
            Switch(
                checked = isSelected,
                onCheckedChange = { onToggleBook() }
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null
            )
        }
        if (expanded && loaded) {
            entries.forEach { entry ->
                WorldEntryPickRow(entry, onToggleEntry)
            }
        }
    }
}

@Composable
private fun WorldEntryPickRow(
    entry: WorldEntryEntity,
    onToggleEntry: (WorldEntryEntity) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.MenuBook,
            contentDescription = null,
            tint = if (entry.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.keys, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = entry.content.take(40),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
        }
        TextButton(onClick = { onToggleEntry(entry) }) {
            Text(if (entry.enabled) "停用" else "启用")
        }
    }
}

@Composable
private fun ApiPickerDialog(
    profiles: List<com.yourapp.chat.data.local.entity.ApiProfileEntity>,
    selectedId: Long?,
    webLocked: Boolean,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择 API") },
        text = {
            Column {
                if (profiles.isEmpty()) {
                    Text("暂无 API 配置，请先在「API 配置」中添加")
                }
                profiles.forEach { profile ->
                    val preset = com.yourapp.chat.data.remote.ApiPresets.byProvider(profile.provider)
                    val isSelected = profile.id == selectedId
                    // 对话已使用过非官。?API 。?官网免费选项锁定（切换会丢失对话记忆。?
val isWeb = profile.provider == "deepseek_web"
                    val locked = isWeb && webLocked
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !locked) { onSelect(profile.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (preset != null) {
                            com.yourapp.chat.ui.screen.config.AiIcon(
                                iconRes = preset.iconRes,
                                label = preset.label,
                                color = if (locked) 0xFF9E9E9E else preset.color,
                                size = 32
                            )
                        } else {
                            com.yourapp.chat.ui.screen.config.FallbackBadge(
                                label = profile.provider.take(2).uppercase(),
                                color = if (locked) 0xFF9E9E9E else 0xFF607D8B,
                                size = 32
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (locked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                            )
                            if (locked) {
                                Text(
                                    text = "本对话已使用 API，切换到官网免费将丢失对话记忆（已禁止）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    text = profile.model.ifBlank { profile.baseUrl },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = "当前", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}
@Composable
private fun ModelPickerDialog(
    profile: com.yourapp.chat.data.local.entity.ApiProfileEntity?,
    currentModel: String?,
    savedModels: List<com.yourapp.chat.data.local.entity.SavedModelEntity> = emptyList(),
    apiProfiles: List<com.yourapp.chat.data.local.entity.ApiProfileEntity> = emptyList(),
    title: String = "选择模型",
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var custom by remember { mutableStateOf(currentModel.orEmpty()) }

    // 打开时自动拉取该 API 的可用模型列。?
    LaunchedEffect(profile?.id) {
        val p = profile ?: return@LaunchedEffect
        if (p.provider == "deepseek_web") {
            models = emptyList()
            return@LaunchedEffect
        }
        if (p.baseUrl.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        try {
            val list = ApiTester.testAndListModels(ChatApplication.instance.okHttpClient, p.baseUrl, p.apiKey)
            models = list
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    val isWeb = profile?.provider == "deepseek_web"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "当前 API：${profile?.name ?: "未选择"} · 当前模型：${currentModel?.ifBlank { "默认" } ?: "…"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(8.dp))
                if (isWeb) {
                    Text(
                        "官网免费通道使用账号绑定的模型，不支持手动切换。\n可从中选择已保存的其他 API 模型（选中后自动切换到对应配置）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else if (loading) {
                    Text("正在拉取可用模型…", style = MaterialTheme.typography.bodySmall)
                } else if (error != null) {
                    Text(
                        "无法获取模型列表：$error（可手动输入）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (isWeb) {
                    savedModels.forEach { m ->
                        val ownerName = apiProfiles.firstOrNull { it.id == m.apiProfileId }?.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(m.model) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.model, style = MaterialTheme.typography.bodyMedium)
                                if (ownerName != null) {
                                    Text(
                                        "配置：$ownerName",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            if (m.model == currentModel) {
                                Icon(Icons.Filled.Check, contentDescription = "当前", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else {
                    models.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(m) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (m == currentModel) {
                                Icon(Icons.Filled.Check, contentDescription = "当前", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (!isWeb) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        label = { Text("或手动输入模型名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (!isWeb) {
                TextButton(
                    onClick = { onSelect(custom.trim()) },
                    enabled = custom.isNotBlank()
                ) { Text("确定") }
            } else {
                TextButton(onClick = onDismiss) { Text("完成") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 从「保存的模型」中选择（已按能力过滤）。无可用模型时提示去「保存的模型」添加。 */
@Composable
private fun SavedModelPickerDialog(
    title: String,
    models: List<com.yourapp.chat.data.local.entity.SavedModelEntity>,
    apiProfiles: List<com.yourapp.chat.data.local.entity.ApiProfileEntity> = emptyList(),
    current: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (models.isEmpty()) {
                    Text(
                        "没有可选的已保存模型。请到「API 配置 → 保存的模型」添加模型并勾选对应能力。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(models, key = { it.id }) { m ->
                            val isCurrent = m.model == current
                            val ownerName = apiProfiles.firstOrNull { it.id == m.apiProfileId }?.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(m.model) }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.model, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        buildString {
                                            append("能力：${if (m.canText) "文本" else ""}${if (m.canText && m.canVision) " / " else ""}${if (m.canVision) "识图" else ""}")
                                            if (ownerName != null) append(" · 配置：$ownerName")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                if (isCurrent) {
                                    Icon(Icons.Filled.Check, contentDescription = "当前", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSettingsPage(
    conversation: com.yourapp.chat.data.local.entity.ConversationEntity,
    keepCount: Int,
    isWeb: Boolean,
    onKeepCountChange: (Int) -> Unit,
    onSave: (Boolean, Int, Int, Double, Int, Double) -> Unit,
    thinkingEnabled: Boolean,
    onThinkingChange: (Boolean) -> Unit,
    thinkingLevel: Int,
    onThinkingLevelChange: (Int) -> Unit,
    searchEnabled: Boolean,
    onSearchChange: (Boolean) -> Unit,
    currentModel: String?,
    currentProfileName: String?,
    apiProfiles: List<com.yourapp.chat.data.local.entity.ApiProfileEntity>,
    selectedProfileId: Long?,
    onSelectApi: (Long) -> Unit,
    onModelClick: () -> Unit,
    compressionModel: String,
    onCompressionModelClick: () -> Unit,
    visionModel: String,
    onVisionModelClick: () -> Unit,
    savedTextModels: List<com.yourapp.chat.data.local.entity.SavedModelEntity>,
    savedVisionModels: List<com.yourapp.chat.data.local.entity.SavedModelEntity>,
    onBack: () -> Unit,
    injectionInterval: Int,
    onInjectionIntervalChange: (Int) -> Unit
) {
    var showThinking by remember { mutableStateOf(conversation.showThinking) }
    var maxTokens by remember { mutableStateOf(conversation.maxOutputTokens.toString()) }
    var maxCtx by remember { mutableStateOf(conversation.maxContextMessages.toString()) }
    var temperature by remember { mutableStateOf(if (conversation.temperature >= 0) conversation.temperature.toString() else "") }
    var topK by remember { mutableStateOf(if (conversation.topK > 0) conversation.topK.toString() else "") }
    var topP by remember { mutableStateOf(if (conversation.topP >= 0) conversation.topP.toString() else "") }
    var keep by remember { mutableStateOf(keepCount.toString()) }
    var injectionIntervalText by remember { mutableStateOf(injectionInterval.toString()) }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("对话设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        onKeepCountChange(keep.toIntOrNull() ?: 32)
                        onInjectionIntervalChange(injectionIntervalText.toIntOrNull() ?: 25)
                        onSave(
                            showThinking,
                            maxTokens.toIntOrNull() ?: 0,
                            maxCtx.toIntOrNull() ?: 0,
                            temperature.toDoubleOrNull() ?: -1.0,
                            topK.toIntOrNull() ?: 0,
                            topP.toDoubleOrNull() ?: -1.0
                        )
                        onBack()
                    }) { Text("保存") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 三组设置改为圆角卡片包裹（不再用横线分割），标题放大 2 倍
            SettingsSectionCard("工具") {
            // 深度思考总开关：关掉禁止模型思考；打开后非官网可选择思考力度（官网力度由服务端控制）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("深度思考", modifier = Modifier.weight(1f))
                Switch(checked = thinkingEnabled, onCheckedChange = onThinkingChange)
            }
            if (thinkingEnabled && !isWeb) {
                Spacer(Modifier.height(4.dp))
                val levelOptions = listOf(
                    Triple(-1, "默认", "交给模型决定力度"),
                    Triple(1, "低", "较少思考"),
                    Triple(2, "中", "适度思考"),
                    Triple(3, "高", "较多思考"),
                    Triple(4, "最强", "全力思考")
                )
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        levelOptions.forEach { (level, label, _) ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (thinkingLevel == level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { onThinkingLevelChange(level) }
                            ) {
                                Text(
                                    text = label,
                                    color = if (thinkingLevel == level) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                    Text(
                        levelOptions.firstOrNull { it.first == thinkingLevel }?.third ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("联网搜索", modifier = Modifier.weight(1f))
                Switch(checked = searchEnabled, onCheckedChange = onSearchChange)
            }
            // 官网免费对话：不发送搜索引擎参数，隐藏选择。?
            // 自建 API 联网搜索固定使用 Bing，需要 Shizuku 授权
            if (!isWeb) {
                Text(
                    "自建 API 联网搜索固定使用 Bing，需要 Shizuku 授权",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            // 官网免费对话的思维链由服务端控制展示，本地开关无意义，隐。?
            // 深度思考开关关闭时该行无意义（思考已禁用，也一并隐藏）
            if (!isWeb && thinkingEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("展示思考内容", modifier = Modifier.weight(1f))
                    Switch(checked = showThinking, onCheckedChange = { showThinking = it })
                }
            }
            }
            Spacer(Modifier.height(12.dp))
            SettingsSectionCard("模型") {
            // 聊天模型块：右侧选择按钮，点击后弹窗列出保存的文本能力模型
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("聊天模型", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = currentModel?.ifBlank { "默认" } ?: "…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = onModelClick) { Text("选择模型") }
            }
            // 上下文压缩模型块：右侧选择按钮；官网免费不支持压缩，隐藏
            if (!isWeb) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("上下文压缩模型", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = compressionModel.ifBlank { "默认（同聊天模型）" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (compressionModel.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = onCompressionModelClick) { Text("选择模型") }
                }
            }
            // 识图模型块：右侧选择按钮；官网免费不支持识图，隐藏
            if (!isWeb) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("识图模型", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = visionModel.ifBlank { "未配置（图片附件不处理）" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (visionModel.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = onVisionModelClick) { Text("选择模型") }
                }
            }
            if (savedTextModels.isEmpty()) {
                Text(
                    "暂无已保存的文本模型，请到「API 配置 → 保存的模型」中添加并勾选文本能力",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            }
            Spacer(Modifier.height(12.dp))
            SettingsSectionCard("生成参数") {
            // 提示词注入轮次：每 N 轮对话注入一次（角色卡、世界书、用户人设），官网免费对话也可用
            // 1 = 用户发一条 + AI 回复一条
            OutlinedTextField(
                value = injectionIntervalText,
                onValueChange = { injectionIntervalText = it.filter { c -> c.isDigit() } },
                label = { Text("提示词注入轮次（默认 25，0=每轮都注入）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // 官网免费对话不支持这些生成参数，整段隐藏
            if (!isWeb) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it.filter { c -> c.isDigit() } },
                    label = { Text("最大输出 Token（0 = 无限）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxCtx,
                    onValueChange = { maxCtx = it.filter { c -> c.isDigit() } },
                    label = { Text("最大上下文消息数（0 = 不限制）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keep,
                    onValueChange = { keep = it.filter { c -> c.isDigit() } },
                    label = { Text("压缩上下文保留条数（默认 32 条）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = temperature,
                    onValueChange = { temperature = it },
                    label = { Text("温度（0.0-2.0，留空不设置）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = topK,
                    onValueChange = { topK = it.filter { c -> c.isDigit() } },
                    label = { Text("Top-K（留空不设置）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = topP,
                    onValueChange = { topP = it },
                    label = { Text("Top-P（0-1，留空不设置）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            }
        }
    }
}

/**
 * 对话设置页的圆角设置卡片：标题为原 titleSmall 的 2 倍（28sp），
 * 内容包裹在圆角浅色卡片中，替代原来的横线分割。
 */
@Composable
private fun SettingsSectionCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 28.sp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

/**
 * 打字/思考动画指示：文本 + 循环 1。? 个点的省略号。? * 生成中显示在底部栏与消息气泡内，让用户感知模型仍在工作。? */
@Composable
private fun AnimatedDots(text: String, paddingTop: Int = 0) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotsPhase"
    )
    val dotCount = phase.toInt() + 1
    Row(
        modifier = Modifier.padding(top = paddingTop.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(2.dp))
        }
        Text(
            text = ".".repeat(dotCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * 用户消息附件元数据（存于 Message.attachmentsJson）
 */
private data class AttachMeta(
    val name: String,
    val mime: String,
    val isImage: Boolean,
    val dataUrl: String = ""
)

/**
 * 文件附件 chip：显示文件名 + 类型图标（图片解码失败时也退化为 chip）
 */
@Composable
private fun AttachChip(meta: AttachMeta) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                meta.mime.startsWith("image/") -> Icons.Filled.Image
                meta.name.endsWith(".json", true) -> Icons.Filled.Description
                else -> Icons.Filled.AttachFile
            },
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = meta.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 将 dataUrl 图片按最长边 maxPx 采样解码为 Bitmap（避免大图直接解码 OOM）
 */
private fun decodeSampledImage(dataUrl: String, maxPx: Int): android.graphics.Bitmap? {
    return try {
        val bytes = android.util.Base64.decode(dataUrl.substringAfter(','), android.util.Base64.DEFAULT)
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) sample *= 2
        val full = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, full)
    } catch (_: Exception) {
        null
    }
}

/**
 * 右侧定位/搜索控制面板。? *
 * 布局（自上而下）：
 *  - 回到顶部（双上箭头，最早消息）
 *  - 搜索键（居中，圆形高亮，开关搜索面板）
 *  - 回到底部（双下箭头，最新消息）
 *
 * 无操作时自动滑回右侧边缘（visible=false），交互时弹出。? */
@Composable
private fun RightControlPanel(
    modifier: Modifier = Modifier,
    visible: Boolean,
    onTop: () -> Unit,
    onBottom: () -> Unit,
    onSearch: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(150))
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(44.dp)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NavButton(icon = Icons.Filled.KeyboardDoubleArrowUp, desc = "回到顶部", onClick = onTop)
            // 居中的搜索键：放大、圆底、主。?
            IconButton(
                onClick = onSearch,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        shape = MaterialTheme.shapes.large
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索本对",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            NavButton(icon = Icons.Filled.KeyboardDoubleArrowDown, desc = "回到底部", onClick = onBottom)
        }
    }
}

/** 定位面板的小图标按钮（带透明度淡出的禁用态） */
@Composable
private fun NavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** reverseLayout 下是否已贴合底部（index 0 即最新消息在底部，offset 为 0） */
private fun androidx.compose.foundation.lazy.LazyListState.isScrolledToBottom(): Boolean =
    firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

/**
 * 对话内搜索面板：顶部覆盖层，输入关键词后列出所有命中消息，
 * 每条结果显示该消息中包含关键词的那一行（单行截断），点击可跳转定位。? * 右侧的上/下匹配键会依次在结果之间跳转。? */
@Composable
private fun SearchPanel(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onSearch: () -> Unit,
    results: List<Long>,
    resultLines: Map<Long, String>,
    total: Int,
    onSelect: (Long) -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("搜索本对话内容") },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "清空")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearch() }
                    )
                )
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }
            Text(
                text = if (query.isBlank()) "输入关键词后点搜" else "$total 条匹配",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
            if (results.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(results) { id ->
                        val line = resultLines[id].orEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(id) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = line,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
