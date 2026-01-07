package me.ikate.findmy.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import me.ikate.findmy.MainActivity
import me.ikate.findmy.R

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
            // 这里可以处理数据消息，例如自动刷新联系人列表
        }

        // 检查消息是否包含通知有效负载
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            sendNotification(it.title ?: "新消息", it.body ?: "")
        }
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

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android O 以上需要 Notification Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "位置共享通知",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map) // 临时使用系统图标，建议后续替换为 app icon
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(0, notificationBuilder.build())
    }
}
