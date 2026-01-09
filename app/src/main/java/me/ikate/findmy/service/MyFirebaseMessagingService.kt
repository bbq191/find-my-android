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
        Log.d(TAG, "From: ${remoteMessage.from}")

        // 检查消息是否包含数据有效负载
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            // 处理位置请求消息
            when (remoteMessage.data["type"]) {
                "LOCATION_REQUEST" -> {
                    handleLocationRequest(remoteMessage.data)
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
        Log.d(TAG, "收到位置请求，来自: $requesterUid")

        // 检查是否超过防抖动冷却时间
        val prefs = getSharedPreferences("location_request", MODE_PRIVATE)
        val lastRequestTime = prefs.getLong("last_request_time", 0)
        val currentTime = System.currentTimeMillis()
        val cooldownMillis = 60 * 1000 // 1分钟冷却时间

        if (currentTime - lastRequestTime < cooldownMillis) {
            Log.d(
                TAG,
                "位置请求过于频繁，忽略本次请求 (冷却时间: ${(cooldownMillis - (currentTime - lastRequestTime)) / 1000}秒)"
            )
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

        Log.d(TAG, "已启动加急位置上报任务")
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
