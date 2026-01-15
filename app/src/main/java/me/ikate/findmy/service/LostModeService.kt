package me.ikate.findmy.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.edit
import me.ikate.findmy.ui.LostModeActivity
import me.ikate.findmy.util.NotificationHelper

/**
 * 丢失模式服务
 * 当设备被标记为丢失时，显示全屏 Activity，显示机主联系信息
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

    override fun onBind(intent: Intent?): IBinder? = null

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

        // 启动丢失模式全屏 Activity
        launchLostModeActivity(message, phoneNumber)

        // 播放声音
        if (playSound) {
            SoundPlaybackService.startPlaying(this, requesterUid)
        }

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

        // 停止声音
        SoundPlaybackService.stopPlaying(this)

        // 停止前台服务
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * 启动丢失模式全屏 Activity
     */
    private fun launchLostModeActivity(message: String, phoneNumber: String) {
        try {
            val intent = LostModeActivity.createIntent(this, message, phoneNumber)
            startActivity(intent)
            Log.d(TAG, "丢失模式 Activity 已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动丢失模式 Activity 失败", e)
            // 发送通知作为备用方案
            NotificationHelper.sendDebugNotification(
                this,
                "设备已丢失",
                "$message\n联系电话: $phoneNumber"
            )
        }
    }
}
