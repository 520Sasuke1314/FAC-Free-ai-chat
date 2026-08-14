package com.yourapp.chat.ui.screen.conversationlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourapp.chat.data.local.dao.ConversationWithLast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    onOpenChat: (Long) -> Unit,
    onOpenConfig: () -> Unit,
    onOpenOfficial: () -> Unit = {},
    onOpenFavorites: () -> Unit = {}
) {
    val vm: ConversationListViewModel = viewModel(factory = ConversationListViewModel.Factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showNewDialog by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
var renaming by remember { mutableStateOf<ConversationWithLast?>(null) }

    // 当前 API 摘要：通道 + 模型 + 脱敏 key，点击进 API 配置页
    val currentProfile = state.apiProfiles.firstOrNull { it.id == state.selectedProfileId }
    val maskedKey = currentProfile?.apiKey?.let {
        if (it.length > 8) "${it.take(5)}…${it.takeLast(4)}" else "sk-***"
    } ?: "未配置 API"

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (searching) "搜索对话" else "对话") },
                    actions = {
                        if (!searching) {
                            IconButton(onClick = {
                                searching = true
                                searchText = ""
                                vm.setSearchQuery("")
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = "搜索对话")
                            }
                            // 消息收藏列表（原蓝鲸鱼入口）
                            IconButton(onClick = onOpenFavorites) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "消息收藏",
                                    tint = androidx.compose.ui.graphics.Color(0xFF4D6BFE)
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                searching = false
                                searchText = ""
                                vm.setSearchQuery("")
                            }) {
                                Icon(Icons.Filled.Add, contentDescription = "退出搜索")
                            }
                        }
                    }
                )
                if (searching) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            vm.setSearchQuery(it)
                        },
                        placeholder = { Text("搜索标题或消息内容…") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        },
        floatingActionButton = {
            if (!searching) {
                IconButton(onClick = { showNewDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "新建对话")
                }
            }
        }
    ) { padding ->
        if (state.conversations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.width(0.dp))
                Text(
                    if (searching) "没有匹配的对话" else "还没有对话，点击右下角 + 新建",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
} else {
            // LazyColumn 虚拟化：只渲染可见行，配合极简行（无手势无动画），
            // 即使几百条对话也流畅。避免 Column 全量渲染导致每帧测量/布局所有行。
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                state = rememberLazyListState(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    state.conversations,
                    key = { it.conversation.id },
                    contentType = { "conversation" }
                ) { item ->
                    ConversationRow(
                        item = item,
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(0),
                            fadeOutSpec = tween(0),
                            placementSpec = tween(220)
                        ),
                        onOpen = { onOpenChat(item.conversation.id) },
                        onRename = { renaming = item },
                        onDelete = { vm.deleteConversation(item.conversation) },
                        onTogglePin = { vm.togglePin(item.conversation) }
                    )
                }
            }
        }
    }

    if (showNewDialog) {
        NewConversationDialog(
            onConfirm = { title ->
                showNewDialog = false
                vm.createConversationAndOpen(title, onOpenChat)
            },
            onDismiss = { showNewDialog = false }
        )
    }

    renaming?.let { item ->
        RenameDialog(
            currentName = item.conversation.title,
            onConfirm = { newTitle ->
                vm.renameConversation(item.conversation.id, newTitle)
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    item: ConversationWithLast,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    val isPinned = item.conversation.pinned
    var menuOpen by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val thresholdPx = with(density) { 80.dp.toPx() }

    // 右滑置顶：拖动偏移动画（沿用收藏页一致实现）
    var dragOffset by remember { mutableStateOf(0f) }
    val animatedOffset = animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )

    // 置顶图标旋转动画
    var pinRotation by remember { mutableStateOf(0f) }
    val animatedPinRotation = animateFloatAsState(
        targetValue = pinRotation,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    )
    LaunchedEffect(isPinned) {
        pinRotation = if (isPinned) 360f else 0f
    }

    Row(
        modifier = modifier
            // 置顶条目抬升 z 序：避免飞行动画被其余条目盖住
            .zIndex(if (isPinned) 1f else 0f)
            .fillMaxWidth()
            .graphicsLayer { translationX = animatedOffset.value }
            // 圆角实色卡片
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isPinned) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            // 右滑置顶：达到阈值切换置顶（与收藏页一致）
            .pointerInput(isPinned) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        total = 0f
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        total += dragAmount
                        if (total > 0) {
                            dragOffset = kotlin.math.min(total, thresholdPx * 1.5f)
                        }
                    },
                    onDragEnd = {
                        if (total >= thresholdPx) {
                            onTogglePin()
                        }
                        total = 0f
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        total = 0f
                        dragOffset = 0f
                    }
                )
            }
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { menuOpen = true }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 置顶图标带旋转动画；未置顶时右滑超过 30% 显示置顶预览图标
        if (isPinned) {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = "已置顶",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .graphicsLayer { rotationZ = animatedPinRotation.value }
                    .size(24.dp)
            )
        } else {
            val showPinPreview = dragOffset > thresholdPx * 0.3f
            if (showPinPreview) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = "向右滑动置顶",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.conversation.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                item.lastTime?.let { ts ->
                    Text(
                        text = remember(ts) { formatTime(ts) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            item.lastMessage?.let {
                // 预览只取前 60 字（与收藏页一致）：完整正文动辄几十 KB，测字开销大
                Text(
                    text = it.take(60),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "删除")
        }
        // 长按菜单：放在 Row 内，锚点绑定到行本身（与收藏页一致，Row 为根节点）
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (isPinned) "取消置顶" else "置顶") },
                onClick = {
                    menuOpen = false
                    onTogglePin()
                },
                leadingIcon = {
                    Icon(Icons.Filled.PushPin, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = {
                    menuOpen = false
                    onRename()
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null) // 占位图标
                }
            )
        }
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名对话") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("标题") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun NewConversationDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建对话") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("对话标题") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.ifBlank { "新对话" }) },
                enabled = title.isNotBlank()
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 相对时间：刚刚 / N分钟前 / N小时前 / 昨天 / N天前 / 具体日期 */
private val DateFmt = SimpleDateFormat("MM-dd", Locale.getDefault())
private fun formatTime(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        diff < 2 * 86_400_000 -> "昨天"
        diff < 7 * 86_400_000 -> "${diff / 86_400_000}天前"
else -> DateFmt.format(Date(ts))
    }
}
