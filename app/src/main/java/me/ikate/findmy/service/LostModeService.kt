package me.ikate.findmy.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import me.ikate.findmy.ui.LostModeActivity
import me.ikate.findmy.util.NotificationHelper

/**
 * 丢失模式服务
 * 当设备被标记为丢失时，锁定屏幕并显示全屏界面，防止他人使用
 *
 * 功能：
 * - 使用设备管理员锁定屏幕
 * - 显示全屏丢失模式界面
 * - 监听屏幕解锁，重新显示丢失模式界面
 * - 持续播放提示音（可选）
 */
class LostModeService : Service() {

    companion object {
        private const val TAG = "LostModeService"
        private const val PREFS_NAME = "lost_mode"
        private const val KEY_IS_ENABLED = "is_enabled"
        private const val KEY_MESSAGE = "message"
        private const val KEY_PHONE = "phone"
        private const val KEY_REQUESTER_UID = "requester_uid"

        private const val ACTION_ENABLE = "me.ikate.findmy.ACTION_ENABLE_LOST_MODE"
        private const val ACTION_DISABLE = "me.ikate.findmy.ACTION_DISABLE_LOST_MODE"
        private const val ACTION_SHOW_ACTIVITY = "me.ikate.findmy.ACTION_SHOW_LOST_MODE_ACTIVITY"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_PHONE = "phone"
        private const val EXTRA_PLAY_SOUND = "play_sound"
        private const val EXTRA_REQUESTER_UID = "requester_uid"

        /**
         * 启用丢失模式
         */
        fun enable(
            context: Context,
            message: String,
            phoneNumber: String,
            playSound: Boolean,
            requesterUid: String? = null
        ) {
            val intent = Intent(context, LostModeService::class.java).apply {
                action = ACTION_ENABLE
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_PHONE, phoneNumber)
                putExtra(EXTRA_PLAY_SOUND, playSound)
                putExtra(EXTRA_REQUESTER_UID, requesterUid)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 关闭丢失模式
         */
        fun disable(context: Context) {
            val intent = Intent(context, LostModeService::class.java).apply {
                action = ACTION_DISABLE
            }
            context.startService(intent)
        }

        /**
         * 显示丢失模式界面（用于屏幕解锁后重新显示）
         */
        fun showActivity(context: Context) {
            val intent = Intent(context, LostModeService::class.java).apply {
                action = ACTION_SHOW_ACTIVITY
            }
            context.startService(intent)
        }

        /**
         * 检查是否处于丢失模式
         */
        fun isEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_IS_ENABLED, false)
        }

        /**
         * 获取丢失模式信息
         */
        fun getLostModeInfo(context: Context): Pair<String, String>? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_IS_ENABLED, false)) return null
            val message = prefs.getString(KEY_MESSAGE, "此设备已丢失") ?: "此设备已丢失"
            val phone = prefs.getString(KEY_PHONE, "") ?: ""
            return Pair(message, phone)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var screenUnlockReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        registerScreenUnlockReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 移除所有待执行的 Handler 回调，防止内存泄漏
        handler.removeCallbacksAndMessages(null)
        unregisterScreenUnlockReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENABLE -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "此设备已丢失"
                val phone = intent.getStringExtra(EXTRA_PHONE) ?: ""
                val playSound = intent.getBooleanExtra(EXTRA_PLAY_SOUND, true)
                val requesterUid = intent.getStringExtra(EXTRA_REQUESTER_UID)

                enableLostMode(message, phone, playSound, requesterUid)
            }
            ACTION_DISABLE -> {
                disableLostMode()
            }
            ACTION_SHOW_ACTIVITY -> {
                showLostModeActivity()
            }
        }
        return START_STICKY
    }

    /**
     * 注册屏幕解锁监听器
     */
    private fun registerScreenUnlockReceiver() {
        if (screenUnlockReceiver != null) return

        screenUnlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_USER_PRESENT -> {
                        // 用户解锁屏幕后
                        Log.d(TAG, "检测到屏幕解锁")
                        if (isEnabled(context)) {
                            // 延迟一小段时间后显示丢失模式界面
                            handler.postDelayed({
                                showLostModeActivity()
                            }, 500)
                        }
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        // 屏幕亮起
                        Log.d(TAG, "检测到屏幕亮起")
                        if (isEnabled(this@LostModeService)) {
                            // 如果处于丢失模式，显示界面
                            handler.postDelayed({
                                showLostModeActivity()
                            }, 300)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        // Android 14+ 需要指定导出标志
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(screenUnlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenUnlockReceiver, filter)
        }
        Log.d(TAG, "屏幕解锁监听器已注册")
    }

    /**
     * 取消注册屏幕解锁监听器
     */
    private fun unregisterScreenUnlockReceiver() {
        screenUnlockReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "取消注册监听器失败", e)
            }
            screenUnlockReceiver = null
        }
    }

    private fun enableLostMode(
        message: String,
        phoneNumber: String,
        playSound: Boolean,
        requesterUid: String?
    ) {
        Log.d(TAG, "启用丢失模式: message=$message, phone=$phoneNumber, playSound=$playSound")

        // 保存状态
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_IS_ENABLED, true)
            putString(KEY_MESSAGE, message)
            putString(KEY_PHONE, phoneNumber)
            requesterUid?.let { putString(KEY_REQUESTER_UID, it) }
        }

        // 创建全屏 Activity 的 Intent
        val fullScreenIntent = LostModeActivity.createIntent(this, message, phoneNumber)

        // 使用全屏意图通知启动前台服务
        val notification = NotificationHelper.showLostModeNotification(
            context = this,
            fullScreenIntent = fullScreenIntent,
            message = message,
            phoneNumber = phoneNumber
        )
        startForeground(NotificationHelper.getLostModeNotificationId(), notification)

        // 播放声音
        if (playSound) {
            SoundPlaybackService.startPlaying(this, requesterUid)
        }

        // 使用设备管理员锁定屏幕
        if (FindMyDeviceAdminReceiver.isAdminActive(this)) {
            FindMyDeviceAdminReceiver.lockScreen(this)
            Log.d(TAG, "已通过设备管理员锁定屏幕")
        } else {
            Log.w(TAG, "设备管理员未激活，无法锁定屏幕")
        }

        // 尝试直接启动丢失模式界面
        showLostModeActivity()

        Log.d(TAG, "丢失模式已启用")
    }

    /**
     * 显示丢失模式界面
     */
    private fun showLostModeActivity() {
        val info = getLostModeInfo(this) ?: return
        val (message, phone) = info

        // 检查悬浮窗权限
        if (Settings.canDrawOverlays(this)) {
            try {
                val activityIntent = LostModeActivity.createIntent(this, message, phone).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(activityIntent)
                Log.d(TAG, "丢失模式界面已启动")
            } catch (e: Exception) {
                Log.e(TAG, "启动丢失模式界面失败", e)
            }
        } else {
            Log.w(TAG, "无悬浮窗权限，无法自动显示丢失模式界面")
            // 发送高优先级通知作为降级方案
            val fullScreenIntent = LostModeActivity.createIntent(this, message, phone)
            val notification = NotificationHelper.showLostModeNotification(
                context = this,
                fullScreenIntent = fullScreenIntent,
                message = message,
                phoneNumber = phone
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            notificationManager.notify(NotificationHelper.getLostModeNotificationId(), notification)
        }
    }

    private fun disableLostMode() {
        Log.d(TAG, "关闭丢失模式")

        // 清除状态
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_IS_ENABLED, false)
            remove(KEY_MESSAGE)
            remove(KEY_PHONE)
            remove(KEY_REQUESTER_UID)
        }

        // 停止声音
        SoundPlaybackService.stopPlaying(this)

        // 取消丢失模式通知
        NotificationHelper.cancelLostModeNotification(this)

        // 关闭丢失模式界面
        sendBroadcast(Intent(LostModeActivity.ACTION_CLOSE_LOST_MODE))

        // 停止前台服务
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
