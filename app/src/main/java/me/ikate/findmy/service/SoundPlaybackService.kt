package me.ikate.findmy.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import me.ikate.findmy.R
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    /**
     * 报警音类型
     */
    enum class SoundType {
        /** 紧急报警（刺耳，用于丢失模式） */
        URGENT,
        /** 柔和提示（用于普通查找） */
        GENTLE
    }

    companion object {
        private const val TAG = "SoundPlaybackService"
        private const val NOTIFICATION_ID = 9999
        private const val MAX_PLAY_DURATION_MS = 2 * 60 * 1000L // 2分钟
        private const val ACTION_PLAY = "me.ikate.findmy.ACTION_PLAY_SOUND"
        private const val ACTION_STOP = "me.ikate.findmy.ACTION_STOP_SOUND"
        private const val EXTRA_REQUESTER_UID = "requester_uid"
        private const val EXTRA_SOUND_TYPE = "sound_type"
        private const val CHANNEL_ID_SOUND = "sound_playback"

        // 播放状态 - 供 UI 观察
        private val _isPlaying = MutableStateFlow(false)
        val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

        /**
         * 启动播放声音
         *
         * @param context Context
         * @param requesterUid 请求者 UID
         * @param soundType 报警音类型，默认紧急模式
         */
        fun startPlaying(
            context: Context,
            requesterUid: String? = null,
            soundType: SoundType = SoundType.URGENT
        ) {
            val intent = Intent(context, SoundPlaybackService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_REQUESTER_UID, requesterUid)
                putExtra(EXTRA_SOUND_TYPE, soundType.name)
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
    private var audioFocusRequest: AudioFocusRequest? = null
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
                val soundTypeName = intent.getStringExtra(EXTRA_SOUND_TYPE) ?: SoundType.URGENT.name
                val soundType = try {
                    SoundType.valueOf(soundTypeName)
                } catch (e: Exception) {
                    SoundType.URGENT
                }
                startPlayback(requesterUid, soundType)
            }
            ACTION_STOP -> {
                stopPlayback()
            }
        }
        return START_NOT_STICKY
    }

    private fun startPlayback(requesterUid: String?, soundType: SoundType = SoundType.URGENT) {
        Log.d(TAG, "开始播放查找提示音，请求者: $requesterUid, 类型: $soundType")

        // 更新播放状态
        _isPlaying.value = true

        // 创建通知渠道
        createNotificationChannel()

        // 创建带停止按钮的通知
        val notification = createSoundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // 请求音频焦点（暂停其他音乐播放）
        requestAudioFocus()

        // 保存原始音量并设置为最大
        audioManager?.let {
            originalVolume = it.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = it.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            it.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
        }

        // 开始播放铃声（优先使用自定义音源）
        try {
            val audioUri = getAudioUri(soundType)
            Log.d(TAG, "使用音源: $audioUri")

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@SoundPlaybackService, audioUri)
                isLooping = true
                prepare()
                start()
            }

            Log.d(TAG, "铃声开始播放")
        } catch (e: Exception) {
            Log.e(TAG, "播放铃声失败", e)
            // 尝试降级到系统默认铃声
            tryFallbackSound()
        }

        // 开始振动（根据类型选择振动模式）
        startVibration(soundType)

        // 设置自动停止计时器
        stopJob?.cancel()
        stopJob = serviceScope.launch {
            delay(MAX_PLAY_DURATION_MS)
            Log.d(TAG, "播放时间到，自动停止")
            stopPlayback()
        }
    }

    /**
     * 获取音频资源 URI
     * 优先使用自定义音源，不存在则使用系统铃声
     */
    private fun getAudioUri(soundType: SoundType): Uri {
        // 尝试加载自定义音源
        val customResId = when (soundType) {
            SoundType.URGENT -> getCustomSoundResId("siren")
            SoundType.GENTLE -> getCustomSoundResId("siren_gentle") ?: getCustomSoundResId("siren")
        }

        if (customResId != null && customResId != 0) {
            return Uri.parse("android.resource://${packageName}/$customResId")
        }

        // 降级到系统铃声
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    /**
     * 通过名称获取 raw 资源 ID
     */
    private fun getCustomSoundResId(name: String): Int? {
        return try {
            val resId = resources.getIdentifier(name, "raw", packageName)
            if (resId != 0) resId else null
        } catch (e: Exception) {
            Log.w(TAG, "获取自定义音源失败: $name", e)
            null
        }
    }

    /**
     * 尝试降级播放系统默认铃声
     */
    private fun tryFallbackSound() {
        try {
            val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@SoundPlaybackService, fallbackUri)
                isLooping = true
                prepare()
                start()
            }
            Log.d(TAG, "降级使用系统铃声")
        } catch (e: Exception) {
            Log.e(TAG, "降级播放也失败", e)
        }
    }

    /**
     * 请求音频焦点
     * 会暂停其他应用的音乐播放
     */
    private fun requestAudioFocus() {
        audioManager?.let { am ->
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()

            audioFocusRequest = focusRequest
            val result = am.requestAudioFocus(focusRequest)
            Log.d(TAG, "音频焦点请求结果: $result")
        }
    }

    /**
     * 释放音频焦点
     */
    private fun abandonAudioFocus() {
        audioFocusRequest?.let { request ->
            audioManager?.abandonAudioFocusRequest(request)
            audioFocusRequest = null
        }
    }

    /**
     * 开始振动
     * 根据报警类型选择不同的振动模式
     */
    private fun startVibration(soundType: SoundType = SoundType.URGENT) {
        try {
            // 紧急模式：短促密集振动
            // 柔和模式：较长间隔的振动
            val pattern = when (soundType) {
                SoundType.URGENT -> longArrayOf(0, 200, 100, 200, 100, 200, 400, 200, 100, 200, 100, 200, 800)
                SoundType.GENTLE -> longArrayOf(0, 500, 1000, 500, 1000)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 0) // 0 = 循环
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
            Log.d(TAG, "振动已开启，模式: $soundType")
        } catch (e: Exception) {
            Log.e(TAG, "启动振动失败", e)
        }
    }

    private fun stopPlayback() {
        Log.d(TAG, "停止播放查找提示音")

        // 更新播放状态
        _isPlaying.value = false

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

        // 释放音频焦点
        abandonAudioFocus()

        // 停止前台服务
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_SOUND,
                "查找设备提示音",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "播放查找设备提示音时的通知"
                setSound(null, null) // 通知本身不发声
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建带停止按钮的通知
     */
    private fun createSoundNotification(): android.app.Notification {
        // 点击通知停止播放的 PendingIntent
        val stopIntent = Intent(this, SoundPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_SOUND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("正在播放查找提示音")
            .setContentText("点击停止播放")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(stopPendingIntent)  // 点击通知停止
            .addAction(
                android.R.drawable.ic_media_pause,
                "停止",
                stopPendingIntent
            )  // 添加停止按钮
            .build()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }
}
