package com.yourapp.chat.util

import android.content.Context
import android.net.Uri
import java.io.File

object FileUtil {
    fun readText(file: File): String = file.readText(Charsets.UTF_8)

    /** 把相册选中的图片 Uri 复制到应用私有目录，返回目标文件（失败返回 null） */
    fun copyUriToFile(context: Context, uri: Uri, dest: File): File? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        if (dest.exists() && dest.length() > 0) dest else null
    } catch (e: Exception) {
        null
    }

    fun isPng(file: File): Boolean {
        val sig = file.takeFirstBytes(8) ?: return false
        return sig.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    }

    private fun File.takeFirstBytes(n: Int): ByteArray? {
        if (!exists() || length() < n) return null
        return inputStream().use { it.readNBytes(n) }
    }
}
