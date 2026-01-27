package me.ikate.findmy.service

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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tencent.map.geolocation.TencentLocation
import com.tencent.map.geolocation.TencentLocationListener
import com.tencent.map.geolocation.TencentLocationManager
import com.tencent.map.geolocation.TencentLocationRequest
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.ikate.findmy.MainActivity
import me.ikate.findmy.R
import me.ikate.findmy.data.local.FindMyDatabase
import me.ikate.findmy.data.local.entity.GeofenceEntity

/**
 * 围栏监控前台服务
 *
 * 专门用于持续监控地理围栏的前台服务。
 * 在 S24 Ultra 上，为了实现精准且及时的围栏触发，必须使用前台服务保持位置监控。
 *
 * 特点：
 * 1. 使用 IMPORTANCE_LOW 通知渠道，通知折叠在底部，无声音/震动
 * 2. 支持智能开关 - 有围栏时启动，无围栏时停止
 * 3. 使用腾讯定位 SDK 进行高频位置监控
 */
class GeofenceForegroundService : Service(), TencentLocationListener {

    companion object {
        private const val TAG = "GeofenceForegroundSvc"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "geofence_monitoring_channel"

        // Intent Actions
        const val ACTION_START = "me.ikate.findmy.geofence.START"
        const val ACTION_STOP = "me.ikate.findmy.geofence.STOP"

        // 位置更新间隔（毫秒）- 高频模式用于围栏检测
        private const val LOCATION_INTERVAL_MS = 10_000L  // 10秒

        @Volatile
        private var instance: GeofenceForegroundService? = null

        // 我的当前位置（用于 LEFT_BEHIND 类型围栏检测）
        @Volatile
        private var currentOwnerLocation: LatLng? = null

        fun isRunning(): Boolean = instance != null

        /**
         * 获取我的当前位置（用于 LEFT_BEHIND 围栏检测）
         */
        fun getOwnerLocation(): LatLng? = currentOwnerLocation

        /**
         * 启动围栏监控服务
         */
        fun start(context: Context): Boolean {
            val intent = Intent(context, GeofenceForegroundService::class.java).apply {
                action = ACTION_START
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "围栏监控服务启动命令已发送")
                true
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "后台启动前台服务被拒绝 (Android 12+ 限制)")
                false
            } catch (e: Exception) {
                Log.e(TAG, "启动围栏监控服务失败", e)
                false
            }
        }

        /**
         * 停止围栏监控服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, GeofenceForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 腾讯定位管理器
    private var locationManager: TencentLocationManager? = null

    // 围栏管理器
    private lateinit var geofenceManager: GeofenceManager

    // 数据库
    private lateinit var database: FindMyDatabase

    // 当前监控的围栏数量
    private var activeGeofenceCount = 0

    // 围栏观察任务
    private var observeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "围栏监控服务创建")

        // 初始化
        geofenceManager = GeofenceManager.getInstance(applicationContext)
        database = FindMyDatabase.getInstance(applicationContext)

        // 创建低优先级通知渠道
        createNotificationChannel()

        // 初始化腾讯定位
        initLocationManager()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "启动围栏监控服务")
                startForegroundWithType(createNotification())
                startLocationMonitoring()
                observeActiveGeofences()
            }
            ACTION_STOP -> {
                Log.d(TAG, "停止围栏监控服务")
                stopSelf()
            }
            else -> {
                // 无 action 时也启动服务（用于系统重启场景）
                if (intent == null) {
                    Log.d(TAG, "服务被系统重启，正在恢复围栏监控...")
                    startForegroundWithType(createNotification())
                    startLocationMonitoring()
                    observeActiveGeofences()
                }
            }
        }
        return START_STICKY
    }

    /**
     * 启动前台服务并指定正确的服务类型
     */
    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 需要显式指定 foregroundServiceType
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * 创建低优先级通知渠道
     * 通知会折叠在通知栏底部，不发出声音或震动
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "围栏监控",
                NotificationManager.IMPORTANCE_LOW  // 低优先级：折叠、无声音、无震动
            ).apply {
                description = "监控联系人进出围栏区域"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET  // 锁屏不显示内容
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

        val contentText = if (activeGeofenceCount > 0) {
            "正在监控 $activeGeofenceCount 个位置提醒"
        } else {
            "围栏监控已就绪"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Find My")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)  // 低优先级
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)  // 锁屏不显示
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)  // 延迟显示
            .build()
    }

    /**
     * 更新通知内容
     */
    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }

    /**
     * 初始化腾讯定位管理器
     */
    private fun initLocationManager() {
        locationManager = TencentLocationManager.getInstance(applicationContext).apply {
            // 设置坐标系为 GCJ-02（腾讯坐标系）
            setCoordinateType(TencentLocationManager.COORDINATE_TYPE_GCJ02)
        }
    }

    /**
     * 开始位置监控
     */
    private fun startLocationMonitoring() {
        val request = TencentLocationRequest.create().apply {
            interval = LOCATION_INTERVAL_MS
            requestLevel = TencentLocationRequest.REQUEST_LEVEL_GEO  // 包含地理信息
            isAllowGPS = true
            isIndoorLocationMode = true  // 室内定位增强
        }

        val errorCode = locationManager?.requestLocationUpdates(request, this)
        if (errorCode == 0) {
            Log.d(TAG, "位置监控已启动，间隔: ${LOCATION_INTERVAL_MS}ms")
        } else {
            Log.e(TAG, "位置监控启动失败，错误码: $errorCode")
        }
    }

    /**
     * 停止位置监控
     */
    private fun stopLocationMonitoring() {
        locationManager?.removeUpdates(this)
        Log.d(TAG, "位置监控已停止")
    }

    /**
     * 观察激活的围栏数量变化
     */
    private fun observeActiveGeofences() {
        observeJob?.cancel()
        observeJob = serviceScope.launch {
            database.geofenceDao().observeActiveGeofences().collect { geofences ->
                val previousCount = activeGeofenceCount
                activeGeofenceCount = geofences.size

                if (previousCount != activeGeofenceCount) {
                    Log.d(TAG, "激活围栏数量变化: $previousCount -> $activeGeofenceCount")
                    updateNotification()
                }

                // 如果没有激活的围栏，可以考虑停止服务
                // 但这里我们保持服务运行，由外部控制器决定
            }
        }
    }

    /**
     * 位置更新回调
     */
    override fun onLocationChanged(location: TencentLocation, error: Int, reason: String?) {
        if (error == TencentLocation.ERROR_OK) {
            val latLng = LatLng(location.latitude, location.longitude)
            Log.d(TAG, "位置更新: (${location.latitude}, ${location.longitude})")

            // 保存我的当前位置（用于 LEFT_BEHIND 围栏检测）
            currentOwnerLocation = latLng
            // 实际的围栏触发逻辑由 GeofenceManager.checkGeofenceForContact 在联系人位置更新时处理
        } else {
            Log.w(TAG, "位置获取失败: $error - $reason")
        }
    }

    /**
     * 状态变化回调
     */
    override fun onStatusUpdate(name: String?, status: Int, desc: String?) {
        Log.d(TAG, "定位状态: $name, status=$status, desc=$desc")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "围栏监控服务销毁")

        // 停止位置监控
        stopLocationMonitoring()

        // 释放定位管理器引用（防止泄漏）
        locationManager = null

        // 清理围栏管理器
        if (::geofenceManager.isInitialized) {
            geofenceManager.destroy()
        }

        // 取消协程
        observeJob?.cancel()
        serviceScope.cancel()

        // 清除我的位置缓存
        currentOwnerLocation = null

        // 最后清除实例引用
        instance = null
    }
}
