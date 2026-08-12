package com.yourapp.chat.ui.screen.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
        // 换 Tab 时播放一次短淡入：动画结束即 alpha=1，不留常驻透明层，
        // 避免此前整页常驻 graphicsLayer 透明层导致的「除对话页外滚动卡顿」。
        val fadeIn = remember { Animatable(1f) }
        LaunchedEffect(tab) {
            if (fadeIn.value == 1f) fadeIn.snapTo(0f)
            fadeIn.animateTo(1f, tween(220))
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .graphicsLayer { alpha = fadeIn.value }
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