package me.ikate.findmy.push

import android.content.Context
import android.util.Log
import com.igexin.sdk.GTIntentService
import com.igexin.sdk.message.GTCmdMessage
import com.igexin.sdk.message.GTNotificationMessage
import com.igexin.sdk.message.GTTransmitMessage

/**
 * 个推推送消息接收服务
 * 处理推送消息、通知点击、Client ID 等回调
 */
class GeTuiPushService : GTIntentService() {

    companion object {
        private const val TAG = "GeTuiPushService"
    }

    /**
     * 推送进程启动回调
     */
    override fun onReceiveServicePid(context: Context?, pid: Int) {
        Log.d(TAG, "推送服务进程启动: PID=$pid")
    }

    /**
     * 收到透传消息
     * 用于后台唤醒定位、设备控制等静默操作
     */
    override fun onReceiveMessageData(context: Context?, message: GTTransmitMessage?) {
        Log.d(TAG, "收到透传消息: taskId=${message?.taskId}, messageId=${message?.messageId}")
        Log.d(TAG, "透传内容: ${message?.payload?.let { String(it) }}")

        if (context == null || message == null) return

        // 将 payload 转为字符串交给消息处理器处理
        val payloadStr = message.payload?.let { String(it) } ?: return
        GeTuiMessageHandler.handleTransmitMessage(context, payloadStr)
    }

    /**
     * Client ID 获取回调
     * Client ID 是设备的唯一标识，用于推送
     */
    override fun onReceiveClientId(context: Context?, clientId: String?) {
        Log.d(TAG, "个推 Client ID: $clientId")

        if (context != null && !clientId.isNullOrBlank()) {
            GeTuiMessageHandler.saveClientId(context, clientId)
        }
    }

    /**
     * 收到命令消息（用于别名、标签操作结果回调）
     */
    override fun onReceiveCommandResult(context: Context?, message: GTCmdMessage?) {
        Log.d(TAG, "收到命令消息: action=${message?.action}")
        // 命令执行结果可通过 message.action 判断类型
        // 具体数值参考个推 SDK 文档
    }

    /**
     * 通知到达回调（展示前）
     */
    override fun onNotificationMessageArrived(context: Context?, message: GTNotificationMessage?) {
        Log.d(TAG, "通知到达: ${message?.title} - ${message?.content}")

        // 可以在此处理通知到达后的逻辑
        // 如更新角标、播放提示音等
    }

    /**
     * 通知点击回调
     */
    override fun onNotificationMessageClicked(context: Context?, message: GTNotificationMessage?) {
        Log.d(TAG, "通知被点击: ${message?.title}")

        if (context == null || message == null) return

        // 处理通知点击事件
        // 可以根据 message.payload 跳转到指定页面
        val payload = message.payload
        if (!payload.isNullOrBlank()) {
            GeTuiMessageHandler.handleNotificationClicked(context, payload)
        }
    }

    /**
     * 推送在线状态变化
     */
    override fun onReceiveOnlineState(context: Context?, isOnline: Boolean) {
        Log.d(TAG, "推送在线状态: ${if (isOnline) "在线" else "离线"}")
    }
}
