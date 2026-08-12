package com.yourapp.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yourapp.chat.ui.navigation.NavGraph
import com.yourapp.chat.ui.theme.ChatAppTheme
import com.yourapp.chat.util.CrashLog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatAppTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    var showCrash by remember { mutableStateOf(CrashLog.hasLog(this@MainActivity)) }
                    if (showCrash) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("上次异常退出，崩溃日志：") },
                            text = {
                                Text(
                                    text = CrashLog.read(this@MainActivity),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { showCrash = false }) { Text("知道了") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    CrashLog.clear(this@MainActivity)
                                    showCrash = false
                                }) { Text("清除日志") }
                            }
                        )
                    }
                    NavGraph()
                }
            }
        }
    }
}
