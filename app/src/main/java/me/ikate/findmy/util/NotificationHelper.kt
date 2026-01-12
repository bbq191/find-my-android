package me.ikate.findmy.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.ikate.findmy.MainActivity

/**
 * 通知帮助类
 * 管理应用的本地通知
 */
object NotificationHelper {

    private const val CHANNEL_ID_SHARE_REQUEST = "location_share_requests"
    private const val CHANNEL_ID_LOCATION_UPDATE = "location_updates"

    /**
     * 创建通知渠道（Android 8.0+）
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 位置共享请求渠道
            val shareRequestChannel = NotificationChannel(
                CHANNEL_ID_SHARE_REQUEST,
                "位置共享邀请",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "收到位置共享邀请时的通知"
                enableVibration(true)
            }

            // 位置更新渠道
            val locationUpdateChannel = NotificationChannel(
                CHANNEL_ID_LOCATION_UPDATE,
                "位置更新",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "联系人位置更新通知"
            }

            notificationManager.createNotificationChannel(shareRequestChannel)
            notificationManager.createNotificationChannel(locationUpdateChannel)
        }
    }

    /**
     * 检查是否有通知权限（Android 13+）
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 13 以下不需要运行时权限
        }
    }

    /**
     * 显示位置共享邀请通知
     * @param context 上下文
     * @param senderName 发送者名称
     * @param shareId 共享 ID
     */
    fun showShareRequestNotification(
        context: Context,
        senderName: String,
        shareId: String
    ) {
        if (!hasNotificationPermission(context)) {
            android.util.Log.w("NotificationHelper", "没有通知权限，跳过显示通知")
            return
        }

        // 点击通知打开应用
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            shareId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SHARE_REQUEST)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 使用系统图标
            .setContentTitle("新的位置共享邀请")
            .setContentText("$senderName 邀请您查看位置")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(shareId.hashCode(), notification)
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "显示通知失败：没有权限", e)
        }
    }

    /**
     * 显示位置更新通知
     * @param context 上下文
     * @param contactName 联系人名称
     * @param address 新位置地址
     */
    fun showLocationUpdateNotification(
        context: Context,
        contactName: String,
        address: String
    ) {
        if (!hasNotificationPermission(context)) {
            return
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            contactName.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_LOCATION_UPDATE)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("$contactName 更新了位置")
            .setContentText(address)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(contactName.hashCode(), notification)
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "显示通知失败：没有权限", e)
        }
    }
}
