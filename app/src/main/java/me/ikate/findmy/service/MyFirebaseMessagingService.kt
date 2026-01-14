package me.ikate.findmy.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import me.ikate.findmy.MainActivity
import me.ikate.findmy.worker.ContinuousLocationWorker
import me.ikate.findmy.worker.LocationReportWorker

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMsgService"
        private const val CHANNEL_ID = "location_share_channel"
    }

    /**
     * 当收到新 Token 时调用 (例如应用初次安装、清除数据后)
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    /**
     * 当应用在前台收到消息，或者后台收到数据消息时调用
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "📬 收到 FCM 消息！From: ${remoteMessage.from}")

        // 🔍 立即发送通知，确认消息到达
        sendDebugNotification("📬 FCM 消息到达", "From: ${remoteMessage.from}")

        // 检查消息是否包含数据有效负载
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            // 处理位置请求消息
            when (remoteMessage.data["type"]) {
                "LOCATION_REQUEST" -> {
                    handleLocationRequest(remoteMessage.data)
                }
                "LOCATION_TRACK_START" -> {
                    handleContinuousTrackingStart(remoteMessage.data)
                }
                "LOCATION_TRACK_STOP" -> {
                    handleContinuousTrackingStop()
                }
                else -> {
                    // 其他类型的数据消息，例如自动刷新联系人列表
                    Log.d(TAG, "Received unknown data message type")
                }
            }
        }

        // 检查消息是否包含通知有效负载
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            sendNotification(it.title ?: "新消息", it.body ?: "")
        }
    }

    /**
     * 处理位置请求：启动加急 Worker 立即上报位置
     */
    private fun handleLocationRequest(data: Map<String, String>) {
        val requesterUid = data["requesterUid"]
        Log.d(TAG, "收到来自: $requesterUid 的位置请求")

        // 🔍 调试：显示通知，验证FCM消息已到达
        sendDebugNotification("FCM已到达", "收到位置请求，来自: $requesterUid")

        // 检查是否超过防抖动冷却时间
        val prefs = getSharedPreferences("location_request", MODE_PRIVATE)
        val lastRequestTime = prefs.getLong("last_request_time", 0)
        val currentTime = System.currentTimeMillis()
        val cooldownMillis = 60 * 1000 // 1分钟冷却时间

        if (currentTime - lastRequestTime < cooldownMillis) {
            val remainingSeconds = (cooldownMillis - (currentTime - lastRequestTime)) / 1000
            Log.d(
                TAG,
                "位置请求过于频繁，忽略本次请求 (冷却时间: ${remainingSeconds}秒)"
            )
            // 🔍 调试：通知用户被防抖动拦截
            sendDebugNotification("请求被拦截", "冷却中，剩余 ${remainingSeconds}秒")
            return
        }

        // 更新最后请求时间
        prefs.edit { putLong("last_request_time", currentTime) }

        // 启动加急的单次定位任务
        val workRequest = OneTimeWorkRequestBuilder<LocationReportWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(
                workDataOf(
                    "isOneShot" to true,
                    "requesterUid" to requesterUid
                )
            )
            .build()

        // 使用唯一名称避免重复任务，使用 REPLACE 策略确保最新的请求被执行
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                "location_request_oneshot",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

        Log.d(TAG, "已启动加急位置上报任务，WorkRequest ID: ${workRequest.id}")
        // 🔍 调试：通知Worker已启动
        sendDebugNotification("Worker已启动", "任务ID: ${workRequest.id}")
    }

    /**
     * 处理短时实时追踪开始请求
     * 启动60秒的连续位置更新任务
     */
    private fun handleContinuousTrackingStart(data: Map<String, String>) {
        val requesterUid = data["requesterUid"]
        Log.d(TAG, "🎯 收到来自: $requesterUid 的实时追踪请求")

        sendDebugNotification("开始实时追踪", "来自: $requesterUid，持续60秒")

        // 检查是否有正在运行的追踪任务
        val prefs = getSharedPreferences("continuous_tracking", MODE_PRIVATE)
        val lastTrackingTime = prefs.getLong("last_tracking_time", 0)
        val currentTime = System.currentTimeMillis()
        val cooldownMillis = 120 * 1000 // 2分钟冷却时间（防止频繁启动）

        if (currentTime - lastTrackingTime < cooldownMillis) {
            val remainingSeconds = (cooldownMillis - (currentTime - lastTrackingTime)) / 1000
            Log.d(TAG, "追踪请求过于频繁，忽略本次请求 (冷却时间: ${remainingSeconds}秒)")
            sendDebugNotification("请求被拦截", "冷却中，剩余 ${remainingSeconds}秒")
            return
        }

        // 更新最后追踪时间
        prefs.edit { putLong("last_tracking_time", currentTime) }

        // 启动连续位置更新任务
        val workRequest = OneTimeWorkRequestBuilder<ContinuousLocationWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(
                workDataOf(
                    "requesterUid" to requesterUid
                )
            )
            .build()

        // 使用唯一名称，KEEP 策略：如果已在运行则忽略新请求
        // 这样可以防止多人同时追踪时互相干扰
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                "continuous_location_tracking",
                ExistingWorkPolicy.KEEP,  // 改为 KEEP：保护正在运行的任务
                workRequest
            )

        Log.d(TAG, "已启动连续位置追踪任务，WorkRequest ID: ${workRequest.id}")
    }

    /**
     * 处理停止追踪请求
     * 取消正在运行的连续位置更新任务
     */
    private fun handleContinuousTrackingStop() {
        Log.d(TAG, "⏹️ 收到停止追踪请求")
        sendDebugNotification("停止实时追踪", "已取消连续位置更新")

        // 取消正在运行的追踪任务
        WorkManager.getInstance(applicationContext)
            .cancelUniqueWork("continuous_location_tracking")

        Log.d(TAG, "连续位置追踪任务已取消")
    }

    /**
     * 🔍 调试工具：发送调试通知
     * 用于验证FCM消息接收和Worker启动状态
     */
    private fun sendDebugNotification(title: String, message: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // 创建调试通知渠道
        val debugChannelId = "debug_channel"
        val debugChannel = NotificationChannel(
            debugChannelId,
            "调试通知",
            NotificationManager.IMPORTANCE_HIGH // 高优先级，确保能看到
        )
        notificationManager.createNotificationChannel(debugChannel)

        val notification = NotificationCompat.Builder(this, debugChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔍 $title")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * 将 Token 保存到当前用户的 Firestore 文档中
     */
    private fun sendRegistrationToServer(token: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && !currentUser.isAnonymous) {
            val db = FirebaseFirestore.getInstance()
            // 使用 arrayUnion 确保一个用户可以有多个设备 Token
            db.collection("users").document(currentUser.uid)
                .update("fcmTokens", FieldValue.arrayUnion(token))
                .addOnSuccessListener { Log.d(TAG, "FCM Token updated") }
                .addOnFailureListener { e -> Log.w(TAG, "Error updating FCM token", e) }
        }
    }

    /**
     * 创建并显示通知
     */
    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Android O 以上需要 Notification Channel
        val channel = NotificationChannel(
            CHANNEL_ID,
            "位置共享通知",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map) // 临时使用系统图标，建议后续替换为 app icon
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(0, notificationBuilder.build())
    }
}
