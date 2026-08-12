package com.yourapp.chat.ui.screen.favorites

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourapp.chat.data.local.entity.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

/** 收藏行时间格式（缓存，避免列表滚动/重组时反复 new） */
private val RowTimeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit
) {
    val vm: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.info) {
        val msg = state.info ?: return@LaunchedEffect
        vm.clearInfo()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isSelectionMode) "已选 ${state.selectedIds.size} 条" else "消息收藏") },
                navigationIcon = {
                    if (state.isSelectionMode) {
                        IconButton(onClick = { vm.setSelectionMode(false) }) {
                            Icon(Icons.Filled.Close, contentDescription = "退出多选")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (state.isSelectionMode) {
                        IconButton(onClick = { vm.selectAll() }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "全选")
                        }
                        IconButton(onClick = { vm.deleteSelected() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除所选")
                        }
                        TextButton(onClick = { exportZip(context, state.items.filter { it.message.id in state.selectedIds }) }) {
                            Text("导出zip")
                        }
                    } else {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("进入多选模式") },
                                onClick = { menuOpen = false; vm.setSelectionMode(true) }
                            )
                            DropdownMenuItem(
                                text = { Text("导出全部为zip") },
                                onClick = { menuOpen = false; exportZip(context, state.items) }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(40.dp))
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Text("还没有收藏任何消息", color = MaterialTheme.colorScheme.outline)
                Text(
                    "在 AI 回复下方点「喜欢」即可收藏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = { it.message.id }, contentType = { "favorite" }) { item ->
                    FavoriteRow(
                        item = item,
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(0),
                            fadeOutSpec = tween(0),
                            placementSpec = tween(220)
                        ),
                        selectionMode = state.isSelectionMode,
                        selected = state.selectedIds.contains(item.message.id),
                        onClick = {
                            if (state.isSelectionMode) vm.toggleSelect(item.message.id)
                            else onOpenChat(item.message.conversationId)
                        },
                        onLongClick = {
                            if (!state.isSelectionMode) vm.setSelectionMode(true)
                            vm.toggleSelect(item.message.id)
                        },
                        onSwipeRight = { if (!state.isSelectionMode) vm.togglePinFavorite(item.message.id, !item.message.pinned) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteRow(
    item: FavoriteItem,
    modifier: Modifier = Modifier,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeRight: () -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 80.dp.toPx() }
    
    // 拖动偏移动画
    var dragOffset by remember { mutableStateOf(0f) }
    val animatedOffset = animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )
    
    // 置顶图标动画状态
    val isPinned = item.message.pinned
    var pinRotation by remember { mutableStateOf(0f) }
    val animatedPinRotation = animateFloatAsState(
        targetValue = pinRotation,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    )
    
    // 触发置顶动画
    LaunchedEffect(isPinned) {
        pinRotation = if (isPinned) 360f else 0f
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = animatedOffset.value }
            // 圆润卡片：整体裁剪圆角 + 浅色底（去掉分隔线，收藏列表更柔和）
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // pointerInput 以 isPinned 为 key：置顶状态变化时重启手势检测，
            // 避免旧的检测器继续消费拖动手势导致"拖了没反应/状态错乱"
            .pointerInput(isPinned) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        total = 0f
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        total += dragAmount
                        // 仅响应向右滑动，超过限位器前可自由拖动
                        if (total > 0) {
                            dragOffset = kotlin.math.min(total, thresholdPx * 1.5f)
                        }
                    },
                    onDragEnd = {
                        // 限位器判定：达到阈值则置顶，否则复位
                        if (total >= thresholdPx) {
                            onSwipeRight()
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(Modifier.width(4.dp))
        } else {
            // 置顶图标带旋转动画
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
                // 拖动预览/默认图标用普通 if/else 切换（列表行内不用 AnimatedVisibility，
                // 它会在滚动组合新行时为每行创建过渡状态机，长列表滚动卡顿）
                val showPinPreview = dragOffset > thresholdPx * 0.3f
                if (showPinPreview) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "向右滑动置顶",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(Icons.Filled.Star, contentDescription = "收藏", modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = item.message.content.take(60),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.conversationTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = RowTimeFmt.format(Date(item.message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        if (selectionMode && selected) {
            Icon(Icons.Filled.Check, contentDescription = "已选", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun exportZip(context: Context, items: List<FavoriteItem>) {
    if (items.isEmpty()) {
        Toast.makeText(context, "没有可导出的收藏", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = java.io.File(dir, "消息收藏_${System.currentTimeMillis()}.zip")
        ZipOutputStream(file.outputStream().buffered()).use { zos ->
            items.forEachIndexed { i, item ->
                val entry = ZipEntry("favorite_${i + 1}.txt")
                zos.putNextEntry(entry)
                zos.write(
                    ("【${item.conversationTitle}】\n时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.message.timestamp))}\n\n${item.message.content}")
                        .toByteArray(Charsets.UTF_8)
                )
                zos.closeEntry()
            }
        }
        Toast.makeText(context, "已导出：${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
    }
}
