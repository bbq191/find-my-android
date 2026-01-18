package me.ikate.findmy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.ikate.findmy.MainActivity
import me.ikate.findmy.R
import kotlinx.coroutines.flow.first
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareStatus
import me.ikate.findmy.data.remote.mqtt.LocationMqttService
import me.ikate.findmy.data.remote.mqtt.MqttConfig
import me.ikate.findmy.data.remote.mqtt.MqttConnectionManager
import me.ikate.findmy.data.remote.mqtt.message.RequestMessage
import me.ikate.findmy.data.repository.AuthRepository
import me.ikate.findmy.data.repository.ContactRepository
import me.ikate.findmy.data.repository.DeviceRepository

/**
 * MQTT 前台服务
 * 在后台维护 MQTT 连接，即使 APP 关闭也能接收和发送消息
 *
 * 功能：
 * 1. 维护 MQTT 连接（自动重连）
 * 2. 订阅用户的请求主题
 * 3. 处理远程请求（位置请求、响铃、丢失模式等）
 * 4. 支持主动发送请求（刷新、追踪等）
 */
class MqttForegroundService : Service() {

    companion object {
        private const val TAG = "MqttForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "mqtt_service_channel"

        // Intent Actions
        const val ACTION_START = "me.ikate.findmy.mqtt.START"
        const val ACTION_STOP = "me.ikate.findmy.mqtt.STOP"

        // 单例实例，用于检查服务是否运行
        @Volatile
        private var instance: MqttForegroundService? = null

        fun isRunning(): Boolean = instance != null

        /**
         * 启动服务
         */
        fun start(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var authRepository: AuthRepository
    private lateinit var contactRepository: ContactRepository
    private lateinit var locationReportService: LocationReportService

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "MqttForegroundService 创建")

        // 初始化依赖
        authRepository = AuthRepository(applicationContext)
        contactRepository = ContactRepository(applicationContext)
        locationReportService = LocationReportService(application)

        // 创建通知渠道
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "启动 MQTT 前台服务")
                startForeground(NOTIFICATION_ID, createNotification())
                initMqttConnection()
            }
            ACTION_STOP -> {
                Log.d(TAG, "停止 MQTT 前台服务")
                stopSelf()
            }
        }
        return START_STICKY // 服务被杀死后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "MqttForegroundService 销毁")

        // 取消所有协程
        serviceScope.cancel()

        // 释放资源
        locationReportService.destroy()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "位置共享服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持位置共享功能在后台运行"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建前台服务通知
     */
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Find My")
            .setContentText("位置共享服务运行中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * 初始化 MQTT 连接
     */
    private fun initMqttConnection() {
        if (!MqttConfig.isConfigured()) {
            Log.d(TAG, "MQTT 未配置，跳过连接")
            return
        }

        serviceScope.launch {
            try {
                // 获取 MQTT 管理器并连接
                val mqttManager = DeviceRepository.getMqttManager(applicationContext)
                val mqttService = DeviceRepository.getMqttService(applicationContext)

                // 检查是否已连接
                if (mqttManager.isConnected()) {
                    Log.d(TAG, "MQTT 已连接")
                } else {
                    // 连接 MQTT
                    val result = mqttManager.connect()
                    if (result.isFailure) {
                        Log.e(TAG, "MQTT 连接失败", result.exceptionOrNull())
                        return@launch
                    }
                    Log.d(TAG, "MQTT 连接成功")
                }

                // 订阅当前用户的主题
                val userId = authRepository.getCurrentUserId()
                if (userId != null) {
                    // 订阅位置主题
                    mqttService.subscribeToUser(userId)

                    // 订阅共享主题
                    mqttService.subscribeToShareTopics(userId)

                    // 订阅请求主题
                    val requestResult = mqttService.subscribeToRequestTopic(userId)
                    if (requestResult.isSuccess) {
                        Log.d(TAG, "已订阅请求主题")
                        // 开始监听请求
                        observeRequests(mqttService)
                    }

                    // 订阅暂停状态主题
                    mqttService.subscribeToSharePauseTopic(userId)

                    // 订阅已有联系人的位置主题（恢复订阅）
                    subscribeToExistingContacts(mqttService)

                    // 发送离线消息队列
                    val sentCount = mqttService.flushPendingMessages()
                    if (sentCount > 0) {
                        Log.d(TAG, "已发送 $sentCount 条离线消息")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "初始化 MQTT 失败", e)
            }
        }
    }

    /**
     * 订阅已有联系人的位置主题
     * 应用启动时恢复订阅，确保能收到联系人的位置更新
     */
    private suspend fun subscribeToExistingContacts(mqttService: LocationMqttService) {
        try {
            // 获取所有已接受共享的联系人
            val contacts = contactRepository.observeMyContacts().first()
            var subscribedCount = 0

            contacts.forEach { contact ->
                // 只订阅已接受共享且对方共享给我的联系人
                if (contact.shareStatus == ShareStatus.ACCEPTED &&
                    contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME &&
                    !contact.isPaused) {
                    contact.targetUserId?.let { targetUserId ->
                        mqttService.subscribeToUser(targetUserId)
                        subscribedCount++
                        Log.d(TAG, "订阅联系人位置: $targetUserId (${contact.name})")
                    }
                }
            }

            if (subscribedCount > 0) {
                Log.d(TAG, "已订阅 $subscribedCount 个联系人的位置主题")
            }
        } catch (e: Exception) {
            Log.e(TAG, "订阅联系人位置主题失败", e)
        }
    }

    /**
     * 监听 MQTT 请求消息
     */
    private fun observeRequests(mqttService: LocationMqttService) {
        serviceScope.launch {
            mqttService.requestUpdates.collect { request ->
                Log.d(TAG, "收到请求: type=${request.type}, from=${request.requesterUid}")
                handleRequest(request)
            }
        }
    }

    /**
     * 处理收到的请求
     */
    private fun handleRequest(request: RequestMessage) {
        serviceScope.launch {
            // 检查是否应该响应该请求
            val shouldRespond = contactRepository.shouldRespondToRequest(request.requesterUid)
            if (!shouldRespond) {
                Log.d(TAG, "忽略来自 ${request.requesterUid} 的请求: 共享已暂停或无效")
                return@launch
            }

            when (request.type) {
                RequestMessage.TYPE_SINGLE -> {
                    // 单次位置请求 - 立即上报位置
                    Log.d(TAG, "收到位置请求，正在上报位置...")
                    reportLocationNow()
                }
                RequestMessage.TYPE_CONTINUOUS -> {
                    // 持续追踪请求 - 触发连续上报
                    Log.d(TAG, "收到持续追踪请求，开始连续上报...")
                    reportLocationNow()
                }
                RequestMessage.TYPE_PLAY_SOUND -> {
                    // 播放声音请求
                    Log.d(TAG, "收到播放声音请求，来自: ${request.requesterUid}")
                    SoundPlaybackService.startPlaying(
                        context = applicationContext,
                        requesterUid = request.requesterUid
                    )
                }
                RequestMessage.TYPE_STOP_SOUND -> {
                    // 停止声音
                    Log.d(TAG, "收到停止声音请求")
                    SoundPlaybackService.stopPlaying(applicationContext)
                }
                RequestMessage.TYPE_LOST_MODE -> {
                    // 丢失模式
                    Log.d(TAG, "收到丢失模式请求: message=${request.message}, phone=${request.phoneNumber}")
                    LostModeService.enable(
                        context = applicationContext,
                        message = request.message ?: "此设备已丢失",
                        phoneNumber = request.phoneNumber ?: "",
                        playSound = request.playSound,
                        requesterUid = request.requesterUid
                    )
                }
                RequestMessage.TYPE_DISABLE_LOST_MODE -> {
                    // 关闭丢失模式
                    Log.d(TAG, "收到关闭丢失模式请求")
                    LostModeService.disable(applicationContext)
                }
                else -> {
                    Log.w(TAG, "未知请求类型: ${request.type}")
                }
            }
        }
    }

    /**
     * 立即上报当前位置
     */
    private suspend fun reportLocationNow() {
        val result = locationReportService.reportCurrentLocation()
        result.fold(
            onSuccess = { Log.d(TAG, "位置上报成功") },
            onFailure = { Log.e(TAG, "位置上报失败", it) }
        )
    }
}
