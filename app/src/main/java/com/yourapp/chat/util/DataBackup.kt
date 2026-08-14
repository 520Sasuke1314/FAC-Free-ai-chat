package com.yourapp.chat.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 全量数据导出 / 导入：Room 数据库文件 + SharedPreferences + 崩溃日志，打包为 zip。
 * 导出：设置页「导出全部数据」→ 选择保存位置（CreateDocument）。
 * 导入：设置页「导入备份」→ 选择 zip，覆盖恢复（旧文件先改名 .bak_import 自动留存），
 * 完成后通过 AlarmManager 拉起应用冷启动，保证数据库/偏好设置重新加载。
 */
object DataBackup {

    const val DB_FILE = "chat_database.db"

    /** 导出全部数据到 uri（zip）。返回界面提示文本 */
    suspend fun export(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        checkpointWal(context)
        val dbPath = context.getDatabasePath(DB_FILE)
        val prefsDir = File(context.dataDir, "shared_prefs")
        val crashLog = File(context.filesDir, "crash.log")
        val mediaDir = File(context.filesDir, "media")
        var count = 0
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("无法写入所选位置")
        ZipOutputStream(out).use { zip ->
            listOf(dbPath, File(dbPath.path + "-wal"), File(dbPath.path + "-shm"))
                .filter { it.exists() && it.length() > 0 }
                .forEach { f ->
                    zip.putNextEntry(ZipEntry("db/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
            prefsDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".xml") }
                ?.forEach { f ->
                    zip.putNextEntry(ZipEntry("prefs/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
            // 图标/图片资源（角色卡、世界书等外部文件），递归打包，保留相对路径
            if (mediaDir.isDirectory) {
                val base = mediaDir.absolutePath
                mediaDir.walkBottomUp().filter { it.isFile }.forEach { f ->
                    val rel = f.absolutePath.removePrefix(base).trimStart('/', '\\')
                    zip.putNextEntry(ZipEntry("media/$rel"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
            }
            if (crashLog.exists()) {
                zip.putNextEntry(ZipEntry("crash.log"))
                crashLog.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                count++
            }
        }
        "已导出 $count 个文件（数据库 + 设置 + 图片 + 日志）。\n注意：备份包含 API 密钥与官网登录信息，请妥善保管。"
    }

    /** 从 zip 恢复数据。返回界面提示文本 */
    suspend fun import(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val entries = LinkedHashMap<String, ByteArray>()
        var total = 0L
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取所选文件")
        ZipInputStream(input).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val name = e.name
                    if (name.startsWith("db/") || name.startsWith("prefs/")
                        || name.startsWith("media/") || name == "crash.log") {
                        val bytes = zip.readBytes()
                        entries[name] = bytes
                        total += bytes.size
                        if (total > 200L * 1024 * 1024) throw IllegalStateException("备份文件过大（>200MB）")
                    }
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        if (entries.keys.none { it == "db/$DB_FILE" }) {
            throw IllegalStateException("不是有效的 ChatApp 备份（缺少数据库文件）")
        }

        // 现有文件先改名留存，再写入备份内容（失败可手动找回）
        val dbPath = context.getDatabasePath(DB_FILE)
        val prefsDir = File(context.dataDir, "shared_prefs")
        val crashLog = File(context.filesDir, "crash.log")
        fun moveToBak(f: File) {
            if (f.exists()) {
                val bak = File(f.parentFile, f.name + ".bak_import")
                bak.delete()
                runCatching { f.renameTo(bak) }
            }
        }
        listOf(dbPath, File(dbPath.path + "-wal"), File(dbPath.path + "-shm")).forEach { moveToBak(it) }
        prefsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".xml") }?.forEach { moveToBak(it) }
        moveToBak(crashLog)
        val mediaDir = File(context.filesDir, "media")
        if (mediaDir.isDirectory) mediaDir.walkTopDown().sortedByDescending { it.absolutePath.length }.forEach { moveToBak(it) }

        entries.forEach { (name, bytes) ->
            val target = when {
                name.startsWith("db/") -> File(dbPath.parentFile, name.removePrefix("db/"))
                name.startsWith("prefs/") -> File(prefsDir, name.removePrefix("prefs/"))
                name.startsWith("media/") -> File(mediaDir, name.removePrefix("media/"))
                else -> crashLog
            }
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
        "已写入 ${entries.size} 个文件。重启应用后生效（旧数据已保留为 *.bak_import）。"
    }

    /** WAL 检查点：把未落盘的改动刷进主库文件，导出/拷贝更安全 */
    private fun checkpointWal(context: Context) {
        runCatching {
            val app = com.yourapp.chat.ChatApplication.instance
            app.database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        }
    }

    /** 立即重启应用：AlarmManager 拉起启动页后自杀进程（导入后必调，保证冷启动重新加载） */
    fun restartApp(context: Context) {
        runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pi = android.app.PendingIntent.getActivity(
                context,
                0x201,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            am.setExact(android.app.AlarmManager.RTC, System.currentTimeMillis() + 400, pi)
        }
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
