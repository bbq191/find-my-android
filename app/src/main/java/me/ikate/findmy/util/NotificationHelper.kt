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
import me.ikate.findmy.BuildConfig
import me.ikate.findmy.MainActivity
import me.ikate.findmy.R

/**
 * 通知帮助类
 * 管理应用的本地通知
 */
object NotificationHelper {

    private const val CHANNEL_ID_SHARE_REQUEST = "location_share_requests"
    private const val CHANNEL_ID_LOCATION_UPDATE = "location_updates"
    private const val CHANNEL_ID_GEOFENCE = "geofence_alerts"

    /**
     * 创建主 Activity 的 PendingIntent
     * 统一处理 FLAG_IMMUTABLE 和 FLAG_UPDATE_CURRENT
     *
     * @param context 上下文
     * @param requestCode 请求码（用于区分不同的 PendingIntent）
     * @return PendingIntent
     */
    private fun createMainActivityPendingIntent(
        context: Context,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

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

            // 地理围栏渠道
            val geofenceChannel = NotificationChannel(
                CHANNEL_ID_GEOFENCE,
                "地理围栏提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "联系人进入或离开指定区域时的通知"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(shareRequestChannel)
            notificationManager.createNotificationChannel(locationUpdateChannel)
            notificationManager.createNotificationChannel(geofenceChannel)
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

        val pendingIntent = createMainActivityPendingIntent(context, shareId.hashCode())

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SHARE_REQUEST)
            .setSmallIcon(R.drawable.ic_notification) // 使用系统图标
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
     * 显示分享接受通知
     * 当对方接受了你的位置共享邀请时显示
     * @param context 上下文
     * @param accepterName 接受者名称
     */
    fun showShareAcceptedNotification(
        context: Context,
        accepterName: String
    ) {
        if (!hasNotificationPermission(context)) {
            android.util.Log.w("NotificationHelper", "没有通知权限，跳过显示通知")
            return
        }

        val pendingIntent = createMainActivityPendingIntent(context, "accepted_$accepterName".hashCode())

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SHARE_REQUEST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("位置共享邀请已被接受")
            .setContentText("$accepterName 接受了您的位置共享邀请")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify("accepted_$accepterName".hashCode(), notification)
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "显示通知失败：没有权限", e)
        }
    }

    /**
     * 显示共享暂停通知
     * 当对方暂停与你的位置共享时显示
     * @param context 上下文
     * @param contactName 联系人名称
     */
    fun showSharePausedNotification(
        context: Context,
        contactName: String
    ) {
        if (!hasNotificationPermission(context)) {
            android.util.Log.w("NotificationHelper", "没有通知权限，跳过显示通知")
            return
        }

        val pendingIntent = createMainActivityPendingIntent(context, "paused_$contactName".hashCode())

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SHARE_REQUEST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("位置共享已暂停")
            .setContentText("$contactName 暂停了与您的位置共享")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify("paused_$contactName".hashCode(), notification)
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "显示通知失败：没有权限", e)
        }
    }

    /**
     * 显示共享恢复通知
     * 当对方恢复与你的位置共享时显示
     * @param context 上下文
     * @param contactName 联系人名称
     */
    fun showShareResumedNotification(
        context: Context,
        contactName: String
    ) {
        if (!hasNotificationPermission(context)) {
            android.util.Log.w("NotificationHelper", "没有通知权限，跳过显示通知")
            return
        }

        val pendingIntent = createMainActivityPendingIntent(context, "resumed_$contactName".hashCode())

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SHARE_REQUEST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("位置共享已恢复")
            .setContentText("$contactName 恢复了与您的位置共享")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify("resumed_$contactName".hashCode(), notification)
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "显示通知失败：没有权限", e)
        }
    }

    /**
     * 显示共享过期通知
     * 当位置共享到期时显示（过期是一种特殊的暂停，任一方可恢复）
     * @param context 上下文
     * @param contactName 联系人名称
     */
    fun showShareExpiredNotification(
        context: Context,
        contactName: String
    ) {
        if (!hasNotificationPermission(context)) {
            android.util.Log.w("NotificationHelper", "没有通知权限，跳过显示通知")
            return
        }

        val pendingIntent = createMainActivityPendingIntent(context, "expired_$contactName".hashCode())

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SHARE_REQUEST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("位置共享已过期")
            .setContentText("与 $contactName 的位置共享已到期，可点击续期")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify("expired_$contactName".hashCode(), notification)
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "显示通知失败：没有权限", e)
        }
    }

    /**
     * 显示联系人被移除通知
     * 当对方将你从联系人列表中移除时显示
     * @param context 上下文
     * @param removerName 移除者名称
     */
    fun showShareRemovedNotification(
        context: Context,
        removerName: String
    ) {
        if (!hasNotificationPermission(context)) {
            android.util.Log.w("NotificationHelper", "没有通知权限，跳过显示通知")
            return
        }

        val pendingIntent = createMainActivityPendingIntent(context, "removed_$removerName".hashCode())

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SHARE_REQUEST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("位置共享已停止")
            .setContentText("$removerName 已将您移出联系人列表")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify("removed_$removerName".hashCode(), notification)
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

        val pendingIntent = createMainActivityPendingIntent(context, contactName.hashCode())

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_LOCATION_UPDATE)
            .setSmallIcon(R.drawable.ic_notification)
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

    // ====================================================================
    // 调试通知（用于开发调试）
    // ====================================================================

    private const val CHANNEL_ID_DEBUG = "debug_notifications"

    /**
     * 发送调试通知
     * 用于验证 FCM 消息接收、Worker 启动等状态
     * 仅在 DEBUG 构建时生效
     *
     * @param context 上下文
     * @param title 通知标题
     * @param message 通知内容
     */
    fun sendDebugNotification(context: Context, title: String, message: String) {
        // 仅在 DEBUG 模式下发送调试通知
        if (!BuildConfig.DEBUG) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 创建调试通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_DEBUG,
                "调试通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "开发调试用通知"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_DEBUG)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationHelper", "发送调试通知失败：没有权限")
        }
    }

    /**
     * 创建前台服务通知
     * 用于 Worker 和 Service 的前台通知
     *
     * @param context 上下文
     * @param title 通知标题
     * @param message 通知内容
     * @param channelId 通知渠道 ID
     * @return 通知对象
     */
    fun createForegroundNotification(
        context: Context,
        title: String,
        message: String,
        channelId: String = CHANNEL_ID_LOCATION_UPDATE
    ): android.app.Notification {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    // ====================================================================
    // 丢失模式通知
    // ====================================================================

    private const val CHANNEL_ID_LOST_MODE = "lost_mode_alerts"
    private const val NOTIFICATION_ID_LOST_MODE = 8888

    /**
     * 创建丢失模式通知渠道
     */
    fun createLostModeChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channel = NotificationChannel(
                CHANNEL_ID_LOST_MODE,
                "丢失模式",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "设备丢失时的紧急通知"
                enableVibration(true)
                setBypassDnd(true) // 绕过勿扰模式
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 显示丢失模式全屏通知
     * 使用 Full-Screen Intent 在后台启动 Activity
     *
     * @param context 上下文
     * @param fullScreenIntent 全屏 Activity 的 Intent
     * @param message 丢失消息
     * @param phoneNumber 联系电话
     */
    fun showLostModeNotification(
        context: Context,
        fullScreenIntent: Intent,
        message: String,
        phoneNumber: String
    ): android.app.Notification {
        // 确保渠道已创建
        createLostModeChannel(context)

        // 创建全屏 PendingIntent
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 创建点击通知的 PendingIntent（与全屏相同）
        val contentIntent = PendingIntent.getActivity(
            context,
            1,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationText = if (phoneNumber.isNotBlank()) {
            "$message\n联系电话: $phoneNumber"
        } else {
            message
        }

        return NotificationCompat.Builder(context, CHANNEL_ID_LOST_MODE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("设备已丢失")
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true) // 不可滑动删除
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true) // 关键：全屏意图
            .build()
    }

    /**
     * 取消丢失模式通知
     */
    fun cancelLostModeNotification(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(NOTIFICATION_ID_LOST_MODE)
    }

    /**
     * 获取丢失模式通知 ID
     */
    fun getLostModeNotificationId(): Int = NOTIFICATION_ID_LOST_MODE

    // ====================================================================
    // 地理围栏通知
    // ====================================================================

    /**
     * 显示地理围栏通知
     * @param context 上下文
     * @param title 通知标题
     * @param message 通知内容
     * @param isEntering 是否为进入事件
     */
    fun showGeofenceNotification(
        context: Context,
        title: String,
        message: String,
        isEntering: Boolean
    ) {
        if (!hasNotificationPermission(context)) {
            android.util.Log.w("NotificationHelper", "没有通知权限，跳过地理围栏通知")
            return
        }

        val pendingIntent = createMainActivityPendingIntent(context, title.hashCode())

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_GEOFENCE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 300, 100, 300))
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(title.hashCode(), notification)
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "显示地理围栏通知失败：没有权限", e)
        }
    }
}
