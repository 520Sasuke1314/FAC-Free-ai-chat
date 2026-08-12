package com.yourapp.chat.ui.screen.imports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourapp.chat.ui.screen.character.CharacterListScreen
import com.yourapp.chat.ui.screen.skills.SkillsScreen
import com.yourapp.chat.ui.screen.world.WorldListScreen

/**
 * 导入页：角色卡 / 世界书 / Skills 三个 Tab。
 * 复用了原角色卡/世界书页面的列表内容（showBack=false 时无返回按钮）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onOpenDetail: (Long) -> Unit
) {
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Column {
                Text(
                    text = "导入",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
                TabRow(selectedTabIndex = tab) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text("角色卡") }
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text("世界书") }
                    )
                    Tab(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        text = { Text("Skills") }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> CharacterListScreen(
                    onBack = {},
                    onOpenDetail = onOpenDetail,
                    showBack = false
                )
                1 -> WorldListScreen(
                    onBack = {},
                    showBack = false
                )
                else -> SkillsScreen(
                    onBack = {},
                    showBack = false
                )
            }
        }
    }
}
