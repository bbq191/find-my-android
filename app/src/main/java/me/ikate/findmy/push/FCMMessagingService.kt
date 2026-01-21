package me.ikate.findmy.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson

/**
 * FCM 消息接收服务
 * 处理推送消息和 Token 刷新
 */
class FCMMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMMessagingService"
    }

    private val gson = Gson()

    /**
     * Token 刷新回调
     * 当 FCM Token 更新时调用，需要将新 Token 上报到服务器
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM Token 刷新: $token")

        // 注册 Token 到所有后端服务（本地、MQTT、Cloud Functions）
        FCMManager.registerTokenToServer(this, token)
    }

    /**
     * 收到消息回调
     * 处理数据消息和通知消息
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "收到 FCM 消息: from=${message.from}, messageId=${message.messageId}")

        // 处理数据消息（后台也能收到）
        if (message.data.isNotEmpty()) {
            Log.d(TAG, "数据消息: ${message.data}")
            handleDataMessage(message.data)
        }

        // 处理通知消息（仅前台收到，后台由系统处理）
        message.notification?.let { notification ->
            Log.d(TAG, "通知消息: title=${notification.title}, body=${notification.body}")
            // 通知消息由系统自动显示，无需额外处理
        }
    }

    /**
     * 处理数据消息
     * 将数据转换为 JSON 字符串交给 FCMMessageHandler 处理
     */
    private fun handleDataMessage(data: Map<String, String>) {
        try {
            // 将 data map 转换为 JSON 字符串
            val jsonString = gson.toJson(data)
            Log.d(TAG, "处理数据消息: $jsonString")

            // 交给消息处理器处理
            FCMMessageHandler.handleDataMessage(this, jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "处理数据消息失败", e)
        }
    }

    /**
     * 消息发送状态回调
     */
    @Deprecated("Deprecated in FCM SDK", ReplaceWith(""))
    override fun onMessageSent(msgId: String) {
        @Suppress("DEPRECATION")
        super.onMessageSent(msgId)
        Log.d(TAG, "消息已发送: $msgId")
    }

    /**
     * 消息发送失败回调
     */
    @Deprecated("Deprecated in FCM SDK", ReplaceWith(""))
    override fun onSendError(msgId: String, exception: Exception) {
        @Suppress("DEPRECATION")
        super.onSendError(msgId, exception)
        Log.e(TAG, "消息发送失败: $msgId", exception)
    }

    /**
     * 消息已删除回调
     * 当设备离线时间过长，部分消息可能被删除
     */
    override fun onDeletedMessages() {
        super.onDeletedMessages()
        Log.w(TAG, "部分消息已被删除（设备离线时间过长）")
    }
}
