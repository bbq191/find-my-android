package me.ikate.findmy.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
import me.ikate.findmy.domain.statemachine.LocationStateMachine

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
        private const val RESTART_ALARM_REQUEST_CODE = 2001

        // Intent Actions
        const val ACTION_START = "me.ikate.findmy.mqtt.START"
        const val ACTION_STOP = "me.ikate.findmy.mqtt.STOP"
        const val ACTION_RESTART = "me.ikate.findmy.mqtt.RESTART"

        // 单例实例，用于检查服务是否运行
        @Volatile
        private var instance: MqttForegroundService? = null

        fun isRunning(): Boolean = instance != null

        /**
         * 启动服务
         *
         * @return true 如果启动命令已发送，false 如果启动被拒绝（如 Android 12+ 后台限制）
         */
        fun start(context: Context): Boolean {
            val intent = Intent(context, MqttForegroundService::class.java).apply {
                action = ACTION_START
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "服务启动命令已发送")
                true
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                // Android 12+ 限制：从后台无法启动前台服务
                Log.w(TAG, "后台启动前台服务被拒绝 (Android 12+ 限制)")
                false
            } catch (e: Exception) {
                Log.e(TAG, "启动服务失败", e)
                false
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

        /**
         * 上报 FCM Token 到服务器
         * 通过 MQTT 发送 Token 以便服务器端使用 FCM 推送
         */
        fun reportFcmToken(context: Context, token: String) {
            instance?.reportFcmTokenInternal(token) ?: run {
                Log.w(TAG, "服务未运行，无法上报 FCM Token")
            }
        }

        /**
         * 安排服务重启（用于进程被杀后的恢复）
         * Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限才能使用精确闹钟
         */
        fun scheduleRestart(context: Context, delayMs: Long = 1000) {
            val intent = Intent(context, MqttForegroundService::class.java).apply {
                action = ACTION_RESTART
            }
            val pendingIntent = PendingIntent.getService(
                context,
                RESTART_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerTime = SystemClock.elapsedRealtime() + delayMs

            try {
                // Android 12+ 需要检查精确闹钟权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        Log.d(TAG, "已安排服务在 ${delayMs}ms 后精确重启")
                    } else {
                        // 没有精确闹钟权限，使用非精确闹钟
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        Log.d(TAG, "已安排服务在 ${delayMs}ms 后重启（非精确）")
                    }
                } else {
                    // Android 11 及以下直接使用精确闹钟
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d(TAG, "已安排服务在 ${delayMs}ms 后精确重启")
                }
            } catch (e: SecurityException) {
                // 权限异常时使用非精确闹钟作为备选
                Log.w(TAG, "无法设置精确闹钟，使用非精确闹钟: ${e.message}")
                try {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d(TAG, "已安排服务在 ${delayMs}ms 后重启（非精确备选）")
                } catch (e2: Exception) {
                    Log.e(TAG, "无法安排服务重启: ${e2.message}")
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var authRepository: AuthRepository
    private lateinit var contactRepository: ContactRepository
    private lateinit var locationReportService: LocationReportService

    // 位置状态机
    private lateinit var stateMachine: LocationStateMachine

    // 防止重复订阅请求流
    @Volatile
    private var isObservingRequests = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "MqttForegroundService 创建")

        // 初始化依赖
        authRepository = AuthRepository(applicationContext)
        contactRepository = ContactRepository(applicationContext)
        locationReportService = LocationReportService(application)

        // 初始化位置状态机
        stateMachine = LocationStateMachine.getInstance(applicationContext)
        observeStateMachine()

        // 创建通知渠道
        createNotificationChannel()
    }

    /**
     * 监听状态机状态变化
     */
    private fun observeStateMachine() {
        serviceScope.launch {
            stateMachine.currentState.collect { state ->
                Log.d(TAG, "状态机状态变化: $state")
                updateNotificationForState(state)
            }
        }
    }

    /**
     * 根据状态更新通知
     */
    private fun updateNotificationForState(state: LocationStateMachine.LocationState) {
        val notification = when (state) {
            LocationStateMachine.LocationState.IDLE -> createNotification()
            LocationStateMachine.LocationState.LIVE_TRACKING -> createLiveTrackingNotification()
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 创建实时追踪状态的通知
     */
    private fun createLiveTrackingNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stats = stateMachine.getTrackingStats()
        val durationSec = stats.trackingDurationMs / 1000

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Find My - 实时追踪中")
            .setContentText("已追踪 ${durationSec}秒 | 请求者: ${stats.requesterId ?: "未知"}")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_RESTART -> {
                Log.d(TAG, "启动/重启 MQTT 前台服务 (action: ${intent.action})")
                startForegroundWithType(createNotification())
                initMqttConnection()
            }
            ACTION_STOP -> {
                Log.d(TAG, "停止 MQTT 前台服务")
                stopSelf()
            }
            else -> {
                // 无 action 时也启动服务（用于系统重启场景）
                if (intent == null) {
                    Log.d(TAG, "服务被系统重启，正在恢复...")
                    startForegroundWithType(createNotification())
                    initMqttConnection()
                }
            }
        }
        return START_STICKY // 服务被杀死后自动重启
    }

    /**
     * 启动前台服务并指定正确的服务类型
     * Android 14+ 需要显式指定 foregroundServiceType
     */
    private fun startForegroundWithType(notification: Notification) {
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        )
    }

    /**
     * 当用户从任务列表划掉 APP 时调用
     * 安排服务重启以保持后台功能
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "用户划掉了 APP，安排服务重启...")

        // 使用 AlarmManager 安排重启
        scheduleRestart(applicationContext, 1000)

        // 同时发送广播作为备用方案
        ServiceRestartReceiver.sendRestartBroadcast(applicationContext)
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
                NotificationManager.IMPORTANCE_DEFAULT  // 提高重要性以降低被杀概率
            ).apply {
                description = "保持位置共享功能在后台运行"
                setShowBadge(false)
                // 禁用声音和振动，避免打扰用户
                setSound(null, null)
                enableVibration(false)
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
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)  // 提高优先级
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)  // 立即显示
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

                    // 订阅围栏主题（事件 + 同步）
                    val geofenceResult = mqttService.subscribeToGeofenceTopics(userId)
                    if (geofenceResult.isSuccess) {
                        Log.d(TAG, "已订阅围栏主题")
                    }

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
                // 只订阅已接受共享且对方共享给我的联系人（包括双向共享）
                if (contact.shareStatus == ShareStatus.ACCEPTED &&
                    (contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME ||
                     contact.shareDirection == ShareDirection.MUTUAL) &&
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
     * 使用 isObservingRequests 标志防止重复订阅
     */
    private fun observeRequests(mqttService: LocationMqttService) {
        if (isObservingRequests) {
            Log.d(TAG, "已在监听请求，跳过重复订阅")
            return
        }
        isObservingRequests = true

        serviceScope.launch {
            try {
                mqttService.requestUpdates.collect { request ->
                    Log.d(TAG, "收到请求: type=${request.type}, from=${request.requesterUid}")
                    handleRequest(request)
                }
            } catch (e: Exception) {
                Log.e(TAG, "请求监听异常", e)
                isObservingRequests = false
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
                    // 刷新心跳（如果正在追踪）
                    stateMachine.refreshHeartbeat()
                }
                RequestMessage.TYPE_CONTINUOUS -> {
                    // 持续追踪请求 - 触发状态机进入实时追踪模式
                    Log.d(TAG, "收到持续追踪请求，启动实时追踪模式...")
                    stateMachine.handleEvent(
                        LocationStateMachine.StateEvent.TrackingRequested(
                            requesterId = request.requesterUid,
                            reason = "mqtt_continuous_request"
                        )
                    )
                }
                RequestMessage.TYPE_STOP_TRACKING -> {
                    // 停止追踪请求
                    Log.d(TAG, "收到停止追踪请求")
                    stateMachine.handleEvent(LocationStateMachine.StateEvent.StopTracking)
                }
                RequestMessage.TYPE_HEARTBEAT -> {
                    // 心跳消息 - 刷新状态机心跳
                    Log.d(TAG, "收到心跳消息，来自: ${request.requesterUid}")
                    stateMachine.handleEvent(
                        LocationStateMachine.StateEvent.HeartbeatReceived(request.requesterUid)
                    )
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

    /**
     * 上报 FCM Token 到 MQTT 服务器
     */
    private fun reportFcmTokenInternal(token: String) {
        serviceScope.launch {
            try {
                val mqttService = DeviceRepository.getMqttService(applicationContext)
                val userId = authRepository.getCurrentUserId()

                if (userId != null) {
                    mqttService.publishFcmToken(userId, token)
                    Log.d(TAG, "FCM Token 已通过 MQTT 上报")
                } else {
                    Log.w(TAG, "用户未登录，无法上报 FCM Token")
                }
            } catch (e: Exception) {
                Log.e(TAG, "上报 FCM Token 失败", e)
            }
        }
    }
}
