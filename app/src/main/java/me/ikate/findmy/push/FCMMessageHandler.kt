package me.ikate.findmy.push

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.JsonParser
import me.ikate.findmy.MainActivity
import me.ikate.findmy.util.NotificationHelper
import android.provider.Settings
import me.ikate.findmy.service.MqttForegroundService
import me.ikate.findmy.service.SoundPlaybackService
import me.ikate.findmy.domain.statemachine.LocationStateMachine
import me.ikate.findmy.domain.communication.CommunicationManager
import me.ikate.findmy.data.remote.firestore.CommandExecutor
import me.ikate.findmy.worker.GeofenceSyncWorker

/**
 * FCM 推送消息处理器
 * 处理不同类型的推送消息
 */
object FCMMessageHandler {

    private const val TAG = "FCMMessageHandler"
    private val gson = Gson()

    /**
     * 处理数据消息
     * 用于后台唤醒定位等静默操作
     */
    fun handleDataMessage(context: Context, message: String?) {
        Log.d(TAG, "处理数据消息: $message")

        if (message.isNullOrBlank()) return

        // FCM 唤醒后确保 MQTT 连接
        try {
            val communicationManager = CommunicationManager.getInstance(context)
            communicationManager.ensureMqttConnection()
        } catch (e: Exception) {
            Log.w(TAG, "确保 MQTT 连接失败", e)
        }

        try {
            val jsonObject = JsonParser.parseString(message).asJsonObject
            val msgType = jsonObject.get("type")?.asString ?: return

            // 消息去重检查
            val messageId = jsonObject.get("messageId")?.asString
            if (messageId != null) {
                val communicationManager = CommunicationManager.getInstance(context)
                if (communicationManager.isMessageProcessed(messageId)) {
                    Log.d(TAG, "消息已处理过，跳过: $messageId")
                    return
                }
            }

            when (msgType) {
                FCMConfig.MSG_TYPE_LOCATION_REQUEST -> {
                    handleLocationRequest(context, jsonObject)
                }

                FCMConfig.MSG_TYPE_DEVICE_COMMAND -> {
                    handleDeviceCommand(context, jsonObject)
                }

                FCMConfig.MSG_TYPE_SHARE_REQUEST -> {
                    handleShareRequest(context, jsonObject)
                }

                FCMConfig.MSG_TYPE_SHARE_ACCEPTED -> {
                    handleShareAccepted(context, jsonObject)
                }

                FCMConfig.MSG_TYPE_SYNC_COMMANDS -> {
                    handleSyncCommands(context)
                }

                FCMConfig.MSG_TYPE_GEOFENCE_SYNC -> {
                    handleGeofenceSync(context)
                }

                FCMConfig.MSG_TYPE_GEOFENCE_EVENT -> {
                    handleGeofenceEvent(context, jsonObject)
                }

                else -> {
                    Log.w(TAG, "未知消息类型: $msgType")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析数据消息失败", e)
        }
    }

    /**
     * 处理位置请求
     * 支持单次定位和持续追踪两种模式
     */
    private fun handleLocationRequest(context: Context, jsonObject: com.google.gson.JsonObject) {
        Log.d(TAG, "处理位置请求")

        val requesterId = jsonObject.get("requesterId")?.asString ?: ""
        val mode = jsonObject.get("mode")?.asString ?: "single"  // single 或 continuous
        Log.d(TAG, "位置请求来自: $requesterId, 模式: $mode")

        when (mode) {
            "continuous" -> {
                // 持续追踪模式 - 启动状态机进入实时追踪
                Log.d(TAG, "触发实时追踪模式")
                val stateMachine = LocationStateMachine.getInstance(context)
                stateMachine.handleEvent(
                    LocationStateMachine.StateEvent.TrackingRequested(
                        requesterId = requesterId,
                        reason = "fcm_continuous_request"
                    )
                )
                // 确保 MQTT 前台服务运行
                MqttForegroundService.start(context)
            }
            "stop" -> {
                // 停止追踪
                Log.d(TAG, "停止实时追踪")
                val stateMachine = LocationStateMachine.getInstance(context)
                stateMachine.handleEvent(LocationStateMachine.StateEvent.StopTracking)
            }
            "heartbeat" -> {
                // 心跳消息
                Log.d(TAG, "收到 FCM 心跳")
                val stateMachine = LocationStateMachine.getInstance(context)
                stateMachine.handleEvent(
                    LocationStateMachine.StateEvent.HeartbeatReceived(requesterId)
                )
            }
            else -> {
                // 默认单次定位
                val workRequest = OneTimeWorkRequestBuilder<me.ikate.findmy.worker.LocationReportWorker>()
                    .setInputData(
                        workDataOf(
                            "trigger_reason" to "push_request",
                            "requester_id" to requesterId
                        )
                    )
                    .build()

                WorkManager.getInstance(context).enqueue(workRequest)
                Log.d(TAG, "已触发位置上报任务")
            }
        }
    }

    /**
     * 处理设备控制命令
     */
    private fun handleDeviceCommand(context: Context, jsonObject: com.google.gson.JsonObject) {
        val command = jsonObject.get("command")?.asString ?: return

        when (command) {
            FCMConfig.COMMAND_PLAY_SOUND -> {
                playFindSound(context)
            }

            FCMConfig.COMMAND_STOP_SOUND -> {
                stopFindSound(context)
            }

            FCMConfig.COMMAND_LOST_MODE -> {
                enableLostMode(context, jsonObject)
            }

            FCMConfig.COMMAND_REPORT_LOCATION -> {
                triggerLocationReport(context)
            }

            else -> {
                Log.w(TAG, "未知设备命令: $command")
            }
        }
    }

    /**
     * 播放查找设备提示音
     * 委托给 SoundPlaybackService 统一管理，确保停止时能正确停止声音和振动
     */
    private fun playFindSound(context: Context) {
        Log.d(TAG, "播放查找提示音（委托给 SoundPlaybackService）")
        SoundPlaybackService.startPlaying(context, soundType = SoundPlaybackService.SoundType.GENTLE)
    }

    /**
     * 停止响铃和震动
     * 委托给 SoundPlaybackService 统一管理
     */
    fun stopFindSound(context: Context) {
        Log.d(TAG, "停止响铃（委托给 SoundPlaybackService）")
        SoundPlaybackService.stopPlaying(context)
    }

    /**
     * 启用丢失模式
     * 优先尝试直接启动 Activity，失败则发送全屏通知
     */
    private fun enableLostMode(context: Context, jsonObject: com.google.gson.JsonObject) {
        Log.d(TAG, "启用丢失模式")
        val message = jsonObject.get("message")?.asString ?: "此设备已丢失"
        val phoneNumber = jsonObject.get("phoneNumber")?.asString ?: ""
        val playSound = jsonObject.get("playSound")?.asBoolean ?: true
        val requesterUid = jsonObject.get("requesterUid")?.asString

        // 保存丢失模式状态
        context.getSharedPreferences("lost_mode", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_enabled", true)
            .putString("message", message)
            .putString("phone", phoneNumber)
            .apply()

        // 方案1：如果有悬浮窗权限，直接启动 Activity（最可靠）
        if (Settings.canDrawOverlays(context)) {
            try {
                val activityIntent = me.ikate.findmy.ui.LostModeActivity.createIntent(
                    context, message, phoneNumber
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
                context.startActivity(activityIntent)
                Log.d(TAG, "丢失模式 Activity 已通过悬浮窗权限启动")

                // 播放声音
                if (playSound) {
                    playFindSound(context)
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "悬浮窗权限启动 Activity 失败: ${e.message}")
            }
        } else {
            Log.d(TAG, "未授予悬浮窗权限，尝试其他方式")
        }

        // 方案2：尝试启动前台服务
        try {
            me.ikate.findmy.service.LostModeService.enable(
                context = context,
                message = message,
                phoneNumber = phoneNumber,
                playSound = playSound,
                requesterUid = requesterUid
            )
            Log.d(TAG, "丢失模式服务已启动")
            return
        } catch (e: Exception) {
            Log.w(TAG, "启动前台服务失败，降级为全屏通知: ${e.message}")
        }

        // 方案3：发送全屏通知（最后的降级方案）
        try {
            val fullScreenIntent = me.ikate.findmy.ui.LostModeActivity.createIntent(
                context, message, phoneNumber
            )

            val notification = NotificationHelper.showLostModeNotification(
                context = context,
                fullScreenIntent = fullScreenIntent,
                message = message,
                phoneNumber = phoneNumber
            )

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            notificationManager.notify(NotificationHelper.getLostModeNotificationId(), notification)

            Log.d(TAG, "丢失模式全屏通知已发送")

            // 播放声音
            if (playSound) {
                playFindSound(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "所有丢失模式启动方式都失败", e)
        }
    }

    /**
     * 触发位置上报
     */
    private fun triggerLocationReport(context: Context) {
        Log.d(TAG, "触发位置上报")

        val workRequest = OneTimeWorkRequestBuilder<me.ikate.findmy.worker.LocationReportWorker>()
            .setInputData(workDataOf("trigger_reason" to "push_command"))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    /**
     * 处理分享请求
     * 收到他人发送的位置共享邀请
     */
    private fun handleShareRequest(context: Context, jsonObject: com.google.gson.JsonObject) {
        val senderId = jsonObject.get("senderId")?.asString ?: return
        val senderName = jsonObject.get("senderName")?.asString ?: "未知用户"
        Log.d(TAG, "收到分享请求: $senderName ($senderId)")

        // 显示分享请求通知
        NotificationHelper.showShareRequestNotification(
            context = context,
            senderName = senderName,
            shareId = senderId
        )
    }

    /**
     * 处理分享接受
     * 对方接受了你的位置共享邀请
     */
    private fun handleShareAccepted(context: Context, jsonObject: com.google.gson.JsonObject) {
        val accepterId = jsonObject.get("accepterId")?.asString ?: return
        val accepterName = jsonObject.get("accepterName")?.asString ?: "用户"
        Log.d(TAG, "分享请求被接受: $accepterName ($accepterId)")

        // 显示分享接受通知
        NotificationHelper.showShareAcceptedNotification(
            context = context,
            accepterName = accepterName
        )
    }

    /**
     * 处理指令同步请求 (双通道模式)
     *
     * FCM 仅作为唤醒信号，实际指令从 Firestore 读取
     * 流程:
     * 1. FCM 唤醒设备
     * 2. 从 Firestore 读取 PENDING 状态的指令
     * 3. 执行指令并更新状态
     */
    private fun handleSyncCommands(context: Context) {
        Log.d(TAG, "收到指令同步请求，开始从 Firestore 同步指令")

        // 使用 CommandExecutor 同步并执行指令
        val executor = CommandExecutor.getInstance(context)
        executor.syncAndExecuteCommands()
    }

    /**
     * 处理围栏配置同步请求
     * 当其他设备更新了围栏配置时，触发同步
     */
    private fun handleGeofenceSync(context: Context) {
        Log.d(TAG, "收到围栏同步请求，启动同步 Worker")
        GeofenceSyncWorker.enqueue(context)
    }

    /**
     * 处理围栏触发事件通知
     * 当联系人进入/离开围栏时收到通知
     */
    private fun handleGeofenceEvent(context: Context, jsonObject: com.google.gson.JsonObject) {
        val contactName = jsonObject.get("contactName")?.asString ?: "联系人"
        val locationName = jsonObject.get("locationName")?.asString ?: "指定位置"
        val eventType = jsonObject.get("eventType")?.asString ?: "ENTER"

        Log.d(TAG, "收到围栏事件: $contactName $eventType $locationName")

        val title = if (eventType == "ENTER") "到达通知" else "离开通知"
        val message = if (eventType == "ENTER") {
            "$contactName 已到达 $locationName"
        } else {
            "$contactName 已离开 $locationName"
        }

        NotificationHelper.showGeofenceNotification(
            context = context,
            title = title,
            message = message,
            isEntering = eventType == "ENTER"
        )
    }

    /**
     * 处理通知点击
     */
    fun handleNotificationClicked(context: Context, payload: String?) {
        Log.d(TAG, "通知被点击: $payload")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_notification", true)
            putExtra("notification_payload", payload)
        }
        context.startActivity(intent)
    }

    /**
     * 上报 FCM Token 到服务器
     * 通过 MQTT 发送 Token 以便服务器端推送
     */
    fun reportTokenToServer(context: Context, token: String) {
        Log.d(TAG, "上报 FCM Token 到服务器: $token")

        // 通过 MQTT 服务上报 Token
        MqttForegroundService.reportFcmToken(context, token)
    }
}
