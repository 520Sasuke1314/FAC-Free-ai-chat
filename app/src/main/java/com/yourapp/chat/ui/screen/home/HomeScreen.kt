package com.yourapp.chat.ui.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yourapp.chat.ui.screen.conversationlist.ConversationListScreen
import com.yourapp.chat.ui.screen.imports.ImportScreen
import com.yourapp.chat.ui.screen.settings.SettingsScreen

enum class HomeTab(val label: String, val icon: ImageVector) {
    Conversations("对话", Icons.Filled.Chat),
    Import("导入", Icons.Filled.FileUpload),
    Settings("设置", Icons.Filled.Settings)
}

@Composable
fun HomeScreen(
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenConfig: () -> Unit,
    onOpenOfficial: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenDetail: (Long) -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { onTabChange(t) },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        // 注意：不对内容套 graphicsLayer/alpha 淡入淡出层。整页内容一旦套上
        // RenderNode 透明层，低端设备/软件渲染下列表每帧都要做一次离屏合成，
        // 表现为「除对话页外所有 Tab 页滚动都卡」（对话页不在本容器内、一直流畅）。
        // 换 Tab 改为直接切换即可。
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                HomeTab.Conversations -> ConversationListScreen(
                    onOpenChat = onOpenChat,
                    onOpenConfig = onOpenConfig,
                    onOpenOfficial = onOpenOfficial,
                    onOpenFavorites = onOpenFavorites
                )
                HomeTab.Import -> ImportScreen(
                    onOpenDetail = onOpenDetail
                )
                HomeTab.Settings -> SettingsScreen(
                    onBack = {},
                    onOpenConfig = onOpenConfig,
                    onOpenOfficial = onOpenOfficial,
                    showBack = false
                )
            }
        }
    }
}