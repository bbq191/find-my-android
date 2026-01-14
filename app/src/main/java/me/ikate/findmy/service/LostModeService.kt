package me.ikate.findmy.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.edit
import me.ikate.findmy.R
import me.ikate.findmy.ui.LostModeAuthActivity
import me.ikate.findmy.util.NotificationHelper

/**
 * 丢失模式服务
 * 当设备被标记为丢失时，显示全屏覆盖层，显示机主联系信息
 *
 * 功能：
 * - 显示丢失消息
 * - 显示联系电话（可点击拨打）
 * - 持续播放提示音（可选）
 * - 高频位置更新
 */
class LostModeService : Service() {

    companion object {
        private const val TAG = "LostModeService"
        private const val NOTIFICATION_ID = 8888
        private const val PREFS_NAME = "lost_mode"
        private const val KEY_IS_ENABLED = "is_enabled"
        private const val KEY_MESSAGE = "message"
        private const val KEY_PHONE = "phone"

        private const val ACTION_ENABLE = "me.ikate.findmy.ACTION_ENABLE_LOST_MODE"
        private const val ACTION_DISABLE = "me.ikate.findmy.ACTION_DISABLE_LOST_MODE"
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
         * 检查是否处于丢失模式
         */
        fun isEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_IS_ENABLED, false)
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
        }
        return START_STICKY
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
        }

        // 启动前台服务
        val notification = NotificationHelper.createForegroundNotification(
            context = this,
            title = "丢失模式已启用",
            message = "设备正处于丢失模式"
        )
        startForeground(NOTIFICATION_ID, notification)

        // 显示覆盖层
        showOverlay(message, phoneNumber)

        // 播放声音
        if (playSound) {
            SoundPlaybackService.startPlaying(this, requesterUid)
        }

        // 启动高频位置追踪
        // 通过 FCM 触发连续追踪
        Log.d(TAG, "丢失模式已启用，开始高频位置更新")
    }

    private fun disableLostMode() {
        Log.d(TAG, "关闭丢失模式")

        // 清除状态
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_IS_ENABLED, false)
            remove(KEY_MESSAGE)
            remove(KEY_PHONE)
        }

        // 移除覆盖层
        hideOverlay()

        // 停止声音
        SoundPlaybackService.stopPlaying(this)

        // 停止前台服务
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showOverlay(message: String, phoneNumber: String) {
        if (isOverlayShowing) {
            hideOverlay()
        }

        try {
            // 创建覆盖层视图
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            overlayView = inflater.inflate(R.layout.lost_mode_overlay, null)

            // 设置消息和电话
            overlayView?.findViewById<TextView>(R.id.tvMessage)?.text = message
            overlayView?.findViewById<TextView>(R.id.tvPhone)?.apply {
                text = if (phoneNumber.isNotBlank()) "联系电话: $phoneNumber" else ""
                visibility = if (phoneNumber.isNotBlank()) View.VISIBLE else View.GONE
            }

            // 设置拨打电话按钮
            overlayView?.findViewById<Button>(R.id.btnCall)?.apply {
                visibility = if (phoneNumber.isNotBlank()) View.VISIBLE else View.GONE
                setOnClickListener {
                    val callIntent = Intent(Intent.ACTION_DIAL).apply {
                        data = android.net.Uri.parse("tel:$phoneNumber")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(callIntent)
                }
            }

            // 关闭按钮（需要验证机主身份）
            overlayView?.findViewById<Button>(R.id.btnDismiss)?.setOnClickListener {
                // 启动身份验证 Activity
                launchAuthActivity()
            }

            // 窗口参数
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // FLAG_SHOW_WHEN_LOCKED 虽已弃用，但对于 WindowManager 系统覆盖层仍是必需的
            // Activity 可用 setShowWhenLocked()，但 Service 中的 overlay 无替代方案
            @Suppress("DEPRECATION")
            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            windowManager?.addView(overlayView, params)
            isOverlayShowing = true

            Log.d(TAG, "丢失模式覆盖层已显示")
        } catch (e: Exception) {
            Log.e(TAG, "显示覆盖层失败", e)
            // 如果没有覆盖层权限，发送通知代替
            NotificationHelper.sendDebugNotification(
                this,
                "设备已丢失",
                "$message\n$phoneNumber"
            )
        }
    }

    /**
     * 启动身份验证 Activity
     */
    private fun launchAuthActivity() {
        try {
            val intent = Intent(this, LostModeAuthActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "启动身份验证 Activity 失败", e)
        }
    }

    private fun hideOverlay() {
        try {
            overlayView?.let {
                windowManager?.removeView(it)
                overlayView = null
            }
            isOverlayShowing = false
            Log.d(TAG, "丢失模式覆盖层已移除")
        } catch (e: Exception) {
            Log.e(TAG, "移除覆盖层失败", e)
        }
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }
}
