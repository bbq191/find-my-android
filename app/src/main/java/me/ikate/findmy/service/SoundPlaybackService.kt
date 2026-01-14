package me.ikate.findmy.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ikate.findmy.util.NotificationHelper

/**
 * 播放声音服务
 * 用于远程查找设备时播放提示音
 *
 * 特性：
 * - 即使静音也能播放（使用 STREAM_ALARM）
 * - 最大音量播放
 * - 伴随振动
 * - 2分钟后自动停止
 * - 支持远程停止
 */
class SoundPlaybackService : Service() {

    companion object {
        private const val TAG = "SoundPlaybackService"
        private const val NOTIFICATION_ID = 9999
        private const val MAX_PLAY_DURATION_MS = 2 * 60 * 1000L // 2分钟
        private const val ACTION_PLAY = "me.ikate.findmy.ACTION_PLAY_SOUND"
        private const val ACTION_STOP = "me.ikate.findmy.ACTION_STOP_SOUND"
        private const val EXTRA_REQUESTER_UID = "requester_uid"

        /**
         * 启动播放声音
         */
        fun startPlaying(context: Context, requesterUid: String? = null) {
            val intent = Intent(context, SoundPlaybackService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_REQUESTER_UID, requesterUid)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止播放声音
         */
        fun stopPlaying(context: Context) {
            val intent = Intent(context, SoundPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var originalVolume: Int = 0
    private var audioManager: AudioManager? = null
    private var stopJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val requesterUid = intent.getStringExtra(EXTRA_REQUESTER_UID)
                startPlayback(requesterUid)
            }
            ACTION_STOP -> {
                stopPlayback()
            }
        }
        return START_NOT_STICKY
    }

    private fun startPlayback(requesterUid: String?) {
        Log.d(TAG, "开始播放查找提示音，请求者: $requesterUid")

        // 启动前台服务
        val notification = NotificationHelper.createForegroundNotification(
            context = this,
            title = "正在播放查找提示音",
            message = "点击停止播放"
        )
        startForeground(NOTIFICATION_ID, notification)

        // 保存原始音量
        audioManager?.let {
            originalVolume = it.getStreamVolume(AudioManager.STREAM_ALARM)
            // 设置为最大音量
            val maxVolume = it.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            it.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
        }

        // 开始播放铃声
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@SoundPlaybackService, alarmUri)
                isLooping = true
                prepare()
                start()
            }

            Log.d(TAG, "铃声开始播放")
        } catch (e: Exception) {
            Log.e(TAG, "播放铃声失败", e)
        }

        // 开始振动
        startVibration()

        // 设置自动停止计时器
        stopJob?.cancel()
        stopJob = serviceScope.launch {
            delay(MAX_PLAY_DURATION_MS)
            Log.d(TAG, "播放时间到，自动停止")
            stopPlayback()
        }
    }

    private fun startVibration() {
        try {
            val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000) // 振动模式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 0) // 0 = 循环
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
            Log.d(TAG, "振动已开启")
        } catch (e: Exception) {
            Log.e(TAG, "启动振动失败", e)
        }
    }

    private fun stopPlayback() {
        Log.d(TAG, "停止播放查找提示音")

        // 取消自动停止计时器
        stopJob?.cancel()
        stopJob = null

        // 停止播放
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "停止播放失败", e)
        }

        // 停止振动
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "停止振动失败", e)
        }

        // 恢复原始音量
        audioManager?.let {
            it.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
        }

        // 停止前台服务
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }
}
