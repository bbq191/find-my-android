package me.ikate.findmy.push

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.JsonParser
import me.ikate.findmy.MainActivity
import me.ikate.findmy.util.NotificationHelper
import me.ikate.findmy.worker.LocationReportWorker

/**
 * 个推推送消息处理器
 * 处理不同类型的推送消息
 */
object GeTuiMessageHandler {

    private const val TAG = "GeTuiMessageHandler"
    private val gson = Gson()

    // SharedPreferences key
    private const val PREFS_NAME = "getui_prefs"
    private const val KEY_CLIENT_ID = "client_id"

    // 响铃相关（用于停止响铃）
    private var currentMediaPlayer: MediaPlayer? = null
    private var currentVibrator: android.os.Vibrator? = null
    private var stopHandler: android.os.Handler? = null
    private var stopRunnable: Runnable? = null

    /**
     * 处理透传消息
     * 用于后台唤醒定位等静默操作
     */
    fun handleTransmitMessage(context: Context, message: String?) {
        Log.d(TAG, "处理透传消息: $message")

        if (message.isNullOrBlank()) return

        try {
            val jsonObject = JsonParser.parseString(message).asJsonObject
            val msgType = jsonObject.get("type")?.asString ?: return

            when (msgType) {
                GeTuiConfig.MSG_TYPE_LOCATION_REQUEST -> {
                    handleLocationRequest(context, jsonObject)
                }

                GeTuiConfig.MSG_TYPE_DEVICE_COMMAND -> {
                    handleDeviceCommand(context, jsonObject)
                }

                GeTuiConfig.MSG_TYPE_SHARE_REQUEST -> {
                    handleShareRequest(context, jsonObject)
                }

                GeTuiConfig.MSG_TYPE_SHARE_ACCEPTED -> {
                    handleShareAccepted(context, jsonObject)
                }

                else -> {
                    Log.w(TAG, "未知消息类型: $msgType")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析透传消息失败", e)
        }
    }

    /**
     * 处理位置请求
     */
    private fun handleLocationRequest(context: Context, jsonObject: com.google.gson.JsonObject) {
        Log.d(TAG, "处理位置请求")

        val requesterId = jsonObject.get("requesterId")?.asString
        Log.d(TAG, "位置请求来自: $requesterId")

        val workRequest = OneTimeWorkRequestBuilder<LocationReportWorker>()
            .setInputData(
                workDataOf(
                    "trigger_reason" to "push_request",
                    "requester_id" to (requesterId ?: "")
                )
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d(TAG, "已触发位置上报任务")
    }

    /**
     * 处理设备控制命令
     */
    private fun handleDeviceCommand(context: Context, jsonObject: com.google.gson.JsonObject) {
        val command = jsonObject.get("command")?.asString ?: return

        when (command) {
            GeTuiConfig.COMMAND_PLAY_SOUND -> {
                playFindSound(context)
            }

            GeTuiConfig.COMMAND_STOP_SOUND -> {
                stopFindSound()
            }

            GeTuiConfig.COMMAND_LOST_MODE -> {
                enableLostMode(context, jsonObject)
            }

            GeTuiConfig.COMMAND_REPORT_LOCATION -> {
                triggerLocationReport(context)
            }

            else -> {
                Log.w(TAG, "未知设备命令: $command")
            }
        }
    }

    /**
     * 播放查找设备提示音（持续响铃和震动直到停止）
     */
    private fun playFindSound(context: Context) {
        Log.d(TAG, "播放查找提示音")

        // 先停止之前的响铃
        stopFindSound()

        try {
            // 震动（循环模式，索引0表示从头开始循环）
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 500)  // 震动模式
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))  // 0 = 从索引0开始循环
            currentVibrator = vibrator

            // 播放铃声（循环播放）
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, ringtoneUri)
                isLooping = true  // 循环播放
                prepare()
                start()
            }
            currentMediaPlayer = mediaPlayer

            // 设置30秒后自动停止（安全措施）
            stopHandler = android.os.Handler(context.mainLooper)
            stopRunnable = Runnable { stopFindSound() }
            stopHandler?.postDelayed(stopRunnable!!, 30000)

            Log.d(TAG, "响铃已开始，30秒后自动停止")

        } catch (e: Exception) {
            Log.e(TAG, "播放提示音失败", e)
        }
    }

    /**
     * 停止响铃和震动
     */
    fun stopFindSound() {
        Log.d(TAG, "停止响铃")

        try {
            // 取消自动停止的定时器
            stopRunnable?.let { stopHandler?.removeCallbacks(it) }
            stopHandler = null
            stopRunnable = null

            // 停止震动
            currentVibrator?.cancel()
            currentVibrator = null

            // 停止播放
            currentMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            currentMediaPlayer = null

            Log.d(TAG, "响铃已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止响铃失败", e)
        }
    }

    /**
     * 启用丢失模式
     */
    private fun enableLostMode(context: Context, jsonObject: com.google.gson.JsonObject) {
        Log.d(TAG, "启用丢失模式")
        val message = jsonObject.get("message")?.asString ?: "此设备已丢失"
        val phoneNumber = jsonObject.get("phoneNumber")?.asString ?: ""
        val playSound = jsonObject.get("playSound")?.asBoolean ?: true
        val requesterUid = jsonObject.get("requesterUid")?.asString

        // 调用 LostModeService 启动丢失模式
        me.ikate.findmy.service.LostModeService.enable(
            context = context,
            message = message,
            phoneNumber = phoneNumber,
            playSound = playSound,
            requesterUid = requesterUid
        )
    }

    /**
     * 触发位置上报
     */
    private fun triggerLocationReport(context: Context) {
        Log.d(TAG, "触发位置上报")

        val workRequest = OneTimeWorkRequestBuilder<LocationReportWorker>()
            .setInputData(workDataOf("trigger_reason" to "push_command"))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    /**
     * 处理分享请求
     * 收到他人发送的位置共享邀请
     */
    private fun handleShareRequest(context: Context, jsonObject: com.google.gson.JsonObject) {
        val senderId = jsonObject.get("senderId")?.asString ?: return
        val senderName = jsonObject.get("senderName")?.asString ?: "未知用户"
        Log.d(TAG, "收到分享请求: $senderName ($senderId)")

        // 显示分享请求通知
        NotificationHelper.showShareRequestNotification(
            context = context,
            senderName = senderName,
            shareId = senderId
        )
    }

    /**
     * 处理分享接受
     * 对方接受了你的位置共享邀请
     */
    private fun handleShareAccepted(context: Context, jsonObject: com.google.gson.JsonObject) {
        val accepterId = jsonObject.get("accepterId")?.asString ?: return
        val accepterName = jsonObject.get("accepterName")?.asString ?: "用户"
        Log.d(TAG, "分享请求被接受: $accepterName ($accepterId)")

        // 显示分享接受通知
        NotificationHelper.showShareAcceptedNotification(
            context = context,
            accepterName = accepterName
        )
    }

    /**
     * 处理通知点击
     */
    fun handleNotificationClicked(context: Context, payload: String?) {
        Log.d(TAG, "通知被点击: $payload")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_notification", true)
            putExtra("notification_payload", payload)
        }
        context.startActivity(intent)
    }

    /**
     * 保存 Client ID
     */
    fun saveClientId(context: Context, clientId: String) {
        Log.d(TAG, "保存 Client ID: $clientId")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLIENT_ID, clientId)
            .apply()
    }

    /**
     * 获取保存的 Client ID
     */
    fun getClientId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CLIENT_ID, null)
    }
}
