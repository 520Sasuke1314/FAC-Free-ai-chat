package com.yourapp.chat.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yourapp.chat.ui.screen.chat.ChatScreen
import com.yourapp.chat.ui.screen.character.CharacterDetailScreen
import com.yourapp.chat.ui.screen.config.ApiConfigScreen
import com.yourapp.chat.ui.screen.favorites.FavoritesScreen
import com.yourapp.chat.ui.screen.home.HomeScreen
import com.yourapp.chat.ui.screen.home.HomeTab
import com.yourapp.chat.ui.screen.official.OfficialDeepSeekScreen

private sealed interface Screen {
    data object Home : Screen
    data class Chat(val conversationId: Long) : Screen
    data class CharacterDetail(val cardId: Long) : Screen
    data object Config : Screen
    data object Official : Screen
    data object Favorites : Screen
}

/**
 * 手动导航：不用 NavHost / AnimatedContent。
 *
 * 之前用 NavHost 时，任意一次页面切换都会在
 * AnimatedContentMeasurePolicy.measure <-> LayoutNode.remeasure 之间
 * 无限递归测量，抛 StackOverflowError（表现为点角色卡 / 进对话闪退）。
 * 升级 Compose 1.7.4、禁用过渡动画都无法规避，因为 NavHost 内部始终
 * 用 AnimatedContent 包裹目标页。
 *
 * 这里改为纯状态驱动导航 + 轻量 Crossfade（无测量策略递归），
 * 根页面 Home 为底部导航（对话 / 导入 / 设置），Chat / 角色卡详情 /
 * API 配置 / 官网模式 作为压栈子页面。
 * 所有独立页面切换均使用淡入淡出效果。
 */
@Composable
fun NavGraph() {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val current = backStack.last()
    var homeTab by remember { mutableStateOf(HomeTab.Conversations) }

    BackHandler(enabled = backStack.size > 1) {
        backStack.removeAt(backStack.lastIndex)
    }

    Crossfade(
        targetState = current,
        animationSpec = tween(200),
        label = "pageCrossfade"
    ) { screen ->
        when (screen) {
        is Screen.Home -> HomeScreen(
            tab = homeTab,
            onTabChange = { homeTab = it },
            onOpenChat = { backStack.add(Screen.Chat(it)) },
            onOpenConfig = { backStack.add(Screen.Config) },
            onOpenOfficial = { backStack.add(Screen.Official) },
            onOpenFavorites = { backStack.add(Screen.Favorites) },
            onOpenDetail = { backStack.add(Screen.CharacterDetail(it)) }
        )
        is Screen.Chat -> ChatScreen(
            conversationId = screen.conversationId,
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
        )
        is Screen.Favorites -> FavoritesScreen(
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
            onOpenChat = { backStack.add(Screen.Chat(it)) }
        )
        is Screen.CharacterDetail -> CharacterDetailScreen(
            cardId = screen.cardId,
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
        )
        is Screen.Config -> ApiConfigScreen(
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
        )
        is Screen.Official -> OfficialDeepSeekScreen(
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
        )
        }
    }
}
