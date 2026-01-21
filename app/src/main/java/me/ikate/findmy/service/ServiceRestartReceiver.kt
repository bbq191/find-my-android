package me.ikate.findmy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 服务重启广播接收器
 * 当用户从任务列表划掉 APP 导致服务被杀时，通过广播重启服务
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceRestartReceiver"
        const val ACTION_RESTART_SERVICE = "me.ikate.findmy.RESTART_MQTT_SERVICE"

        /**
         * 发送重启服务的广播
         */
        fun sendRestartBroadcast(context: Context) {
            try {
                val intent = Intent(context, ServiceRestartReceiver::class.java).apply {
                    action = ACTION_RESTART_SERVICE
                }
                context.sendBroadcast(intent)
                Log.d(TAG, "已发送服务重启广播")
            } catch (e: Exception) {
                Log.e(TAG, "发送重启广播失败", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_RESTART_SERVICE) {
            Log.d(TAG, "收到服务重启广播，正在重启 MQTT 服务...")
            try {
                MqttForegroundService.start(context)
            } catch (e: Exception) {
                Log.e(TAG, "重启服务失败", e)
            }
        }
    }
}
