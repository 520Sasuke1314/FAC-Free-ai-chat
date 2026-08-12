package com.yourapp.chat.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CrashLog {
    private const val FILE_NAME = "crash.log"

    fun log(context: Context, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val text = "===== ${System.currentTimeMillis()} =====\n$sw\n"
            appendText(context, text)
        } catch (_: Exception) {
        }
    }

    /** 追加任意诊断文本到崩溃日志文件（SseClient 流式诊断用） */
    fun append(context: Context, text: String) {
        try {
            appendText(context, "===== ${System.currentTimeMillis()} =====\n$text\n")
        } catch (_: Exception) {
        }
    }

    private fun appendText(context: Context, text: String) {
        try {
            File(context.filesDir, FILE_NAME).appendText(text)
            runCatching { File(context.cacheDir, FILE_NAME).appendText(text) }
        } catch (_: Exception) {
        }
    }

    fun hasLog(context: Context): Boolean {
        return try {
            File(context.filesDir, FILE_NAME).exists() || File(context.cacheDir, FILE_NAME).exists()
        } catch (e: Exception) {
            false
        }
    }

    fun read(context: Context): String {
        return try {
            val f = File(context.filesDir, FILE_NAME)
            if (f.exists()) f.readText().takeLast(6000)
            else {
                val c = File(context.cacheDir, FILE_NAME)
                if (c.exists()) c.readText().takeLast(6000) else ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
        runCatching { File(context.cacheDir, FILE_NAME).delete() }
    }
}
