package me.ikate.findmy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机启动广播接收器
 * 在设备启动完成后自动启动 MqttForegroundService
 * 确保 APP 的后台功能在设备重启后仍能正常工作
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "收到开机广播: ${intent.action}")
                startMqttService(context)
            }
        }
    }

    private fun startMqttService(context: Context) {
        try {
            Log.d(TAG, "开机后启动 MQTT 前台服务")
            MqttForegroundService.start(context)
        } catch (e: Exception) {
            Log.e(TAG, "启动 MQTT 服务失败", e)
        }
    }
}
