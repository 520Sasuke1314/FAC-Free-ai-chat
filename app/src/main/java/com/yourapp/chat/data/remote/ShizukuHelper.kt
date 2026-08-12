package com.yourapp.chat.data.remote

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Shizuku 授权封装：联网搜索需要 Shizuku 权限。
 * 依赖 Shizuku 应用（dev.rikka.shizuku）已安装并启动服务。
 */
object ShizukuHelper {

    const val REQUEST_CODE = 100001

    /** Shizuku 服务是否可连接（需要设备安装并启动 Shizuku） */
    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    /** 本应用是否已获得 Shizuku 授权 */
    fun isGranted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    /** 弹出 Shizuku 授权窗口（Shizuku Manager 会自行显示对话框），结果经 [addRequestPermissionListener] 回调 */
    fun requestPermission(requestCode: Int = REQUEST_CODE) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            // 忽略：待监听器或后续重试兜底
        }
    }

    /** 注册授权结果监听器并返回该监听器实例（供移除时使用） */
    fun addRequestPermissionListener(
        callback: (requestCode: Int, grantResult: Int) -> Unit
    ): Shizuku.OnRequestPermissionResultListener {
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            callback(requestCode, grantResult)
        }
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (e: Exception) {
        }
        return listener
    }

    /** 移除授权结果监听器 */
    fun removeRequestPermissionListener(binder: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.removeRequestPermissionResultListener(binder)
        } catch (e: Exception) {
        }
    }
}