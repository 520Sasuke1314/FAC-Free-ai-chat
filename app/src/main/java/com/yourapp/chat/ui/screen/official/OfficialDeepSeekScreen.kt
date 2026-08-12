package com.yourapp.chat.ui.screen.official

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourapp.chat.data.local.entity.MessageEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialDeepSeekScreen(onBack: () -> Unit) {
    val vm: OfficialViewModel = viewModel(factory = OfficialViewModel.Factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("DeepSeek 官网对话") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.loggedIn) {
                        TextButton(onClick = { vm.logout() }) { Text("退出登录") }
                    }
                }
            )
        },
        bottomBar = {
            if (state.loggedIn) {
                Column {
                    if (state.sending) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.width(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("正在生成（含 PoW 计算，可能较慢）…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.inputText,
                            onValueChange = vm::onInputChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("输入消息（将同步到你的官网账号）…") },
                            enabled = !state.sending
                        )
                        IconButton(
                            onClick = { vm.send() },
                            enabled = !state.sending && state.inputText.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (!state.loggedIn) {
            LoginPanel(
                vm = vm,
                sending = state.sending,
                error = state.error,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    OfficialMessageBubble(msg)
                }
            }
        }
    }

    if (state.showRiskDialog) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text("封号风险提示") },
            text = {
                Text(
                    "DeepSeek 官网对话功能通过调用网页版接口实现，可能违反 DeepSeek 服务条款，" +
                            "存在账号封禁风险。如要使用，建议使用小号登录。是否继续？"
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.acceptRisk() }) { Text("继续使用") }
            },
            dismissButton = {
                TextButton(onClick = { vm.declineRisk(); onBack() }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun LoginPanel(vm: OfficialViewModel, sending: Boolean, error: String?, modifier: Modifier = Modifier) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "登录 DeepSeek 官网账号",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.width(0.dp).padding(top = 16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("邮箱") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.width(0.dp).padding(top = 8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Spacer(Modifier.width(0.dp).padding(top = 8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(0.dp).padding(top = 16.dp))
        Button(
            onClick = { vm.login(email, password) },
            enabled = email.isNotBlank() && password.isNotBlank() && !sending
        ) {
            Text(if (sending) "登录中…" else "登录")
        }
        Spacer(Modifier.width(0.dp).padding(top = 12.dp))
        Text(
            "登录后，你在本应用发送的消息会同步到 DeepSeek 官网网页版的对话记录中。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun OfficialMessageBubble(message: MessageEntity) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Text(
                text = message.content.ifEmpty { "…" },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
