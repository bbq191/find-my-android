package me.ikate.findmy.domain.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ikate.findmy.service.ActivityRecognitionManager
import me.ikate.findmy.service.GeofenceManager
import me.ikate.findmy.service.SmartLocationConfig
import me.ikate.findmy.worker.SmartLocationWorker
import java.util.concurrent.TimeUnit

/**
 * 智能定位协调器
 *
 * 对标 iOS Find My 的智能定位策略，整合多种触发机制：
 * - Geofence 触发：离开/进入围栏时立即上报
 * - 活动识别触发：活动状态变化时上报
 * - WiFi BSSID 变化触发：网络环境变化时上报
 * - 周期性触发：根据活动状态动态调整间隔
 * - 显著位置变化触发：移动超过阈值时上报
 *
 * 状态机：
 * ```
 * ┌──────────────┐     Activity    ┌──────────────┐
 * │    DORMANT   │ ───────────────►│    AWARE     │
 * │   (深度休眠)  │◄──────────────── │   (感知中)   │
 * └──────────────┘    30分钟静止    └──────────────┘
 *        ▲                                │
 *        │                                │ Geofence/WiFi
 *        │                                ▼
 *        │                         ┌──────────────┐
 *        └─────────────────────────│   ACTIVE     │
 *              位置已上报           │   (活跃上报)  │
 *                                  └──────────────┘
 * ```
 */
class SmartLocator private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SmartLocator"
        private const val WORK_NAME_PERIODIC = "smart_location_periodic"
        // 自身位置围栏配置
        private const val SELF_GEOFENCE_ID = "self_location_fence"
        private const val SELF_GEOFENCE_RADIUS_METERS = 200f

        // WiFi 检测间隔
        private const val WIFI_CHECK_INTERVAL_MS = 60_000L

        @Volatile
        private var instance: SmartLocator? = null

        fun getInstance(context: Context): SmartLocator {
            return instance ?: synchronized(this) {
                instance ?: SmartLocator(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * 定位器状态
     */
    enum class LocatorState {
        /** 深度休眠 - 最小功耗模式 */
        DORMANT,
        /** 感知中 - 监听活动/WiFi变化 */
        AWARE,
        /** 活跃上报 - 高频定位模式 */
        ACTIVE
    }

    /**
     * 位置上报触发原因
     */
    enum class TriggerReason(val displayName: String) {
        PERIODIC("周期性"),
        ACTIVITY_CHANGE("活动变化"),
        GEOFENCE_EXIT("离开围栏"),
        GEOFENCE_ENTER("进入围栏"),
        WIFI_CHANGE("WiFi变化"),
        SIGNIFICANT_LOCATION("显著位置变化"),
        MANUAL("手动请求"),
        REMOTE_REQUEST("远程请求")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 状态
    private val _state = MutableStateFlow(LocatorState.AWARE)
    val state: StateFlow<LocatorState> = _state.asStateFlow()

    // 当前活动类型
    private val _currentActivity = MutableStateFlow(SmartLocationConfig.ActivityType.UNKNOWN)
    val currentActivity: StateFlow<SmartLocationConfig.ActivityType> = _currentActivity.asStateFlow()

    // 上次上报信息
    private val _lastReportInfo = MutableStateFlow<ReportInfo?>(null)
    val lastReportInfo: StateFlow<ReportInfo?> = _lastReportInfo.asStateFlow()

    // 组件
    private val activityManager by lazy { ActivityRecognitionManager(context) }
    private val geofenceManager by lazy { GeofenceManager.getInstance(context) }
    private val workManager by lazy { WorkManager.getInstance(context) }

    // WiFi 监听
    private var wifiCheckJob: Job? = null
    private var lastWifiBssid: String? = null

    // 网络回调
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Geofence 广播接收器
    private var geofenceReceiver: BroadcastReceiver? = null

    // 自身位置围栏（用于检测自己是否移动）
    private var selfGeofenceEnabled = false

    /**
     * 上报信息
     */
    data class ReportInfo(
        val timestamp: Long,
        val triggerReason: TriggerReason,
        val latitude: Double,
        val longitude: Double,
        val activity: SmartLocationConfig.ActivityType
    )

    /**
     * 启动智能定位
     */
    fun start() {
        Log.i(TAG, "启动智能定位器")

        // 1. 启动活动识别
        startActivityRecognition()

        // 2. 启动 WiFi 变化监听
        startWifiMonitoring()

        // 3. 注册 Geofence 广播接收
        registerGeofenceReceiver()

        // 4. 启动周期性工作
        schedulePeriodicWork()

        // 5. 初始化状态
        _state.value = LocatorState.AWARE
    }

    /**
     * 停止智能定位
     */
    fun stop() {
        Log.i(TAG, "停止智能定位器")

        // 停止活动识别
        activityManager.stopActivityTransitionUpdates()

        // 停止 WiFi 监听
        stopWifiMonitoring()

        // 注销 Geofence 接收器
        unregisterGeofenceReceiver()

        // 取消周期性工作
        workManager.cancelUniqueWork(WORK_NAME_PERIODIC)

        _state.value = LocatorState.DORMANT
    }

    /**
     * 启动活动识别
     */
    private fun startActivityRecognition() {
        if (!activityManager.isSensorAvailable()) {
            Log.w(TAG, "加速度传感器不可用，跳过活动识别")
            return
        }

        activityManager.setActivityChangeCallback { newActivity ->
            onActivityChanged(newActivity)
        }

        activityManager.startActivityTransitionUpdates(
            onSuccess = {
                Log.d(TAG, "活动识别已启动")
            },
            onFailure = { e ->
                Log.e(TAG, "活动识别启动失败", e)
            }
        )
    }

    /**
     * 活动状态变化处理
     */
    private fun onActivityChanged(newActivity: SmartLocationConfig.ActivityType) {
        val oldActivity = _currentActivity.value
        _currentActivity.value = newActivity

        Log.d(TAG, "活动变化: ${oldActivity.displayName} -> ${newActivity.displayName}")

        // 从静止变为移动 - 立即上报
        if (oldActivity == SmartLocationConfig.ActivityType.STILL &&
            newActivity != SmartLocationConfig.ActivityType.STILL
        ) {
            triggerLocationReport(TriggerReason.ACTIVITY_CHANGE)

            // 退出深度休眠
            if (_state.value == LocatorState.DORMANT) {
                _state.value = LocatorState.AWARE
            }
        }

        // 从移动变为静止 - 记录静止开始
        if (oldActivity != SmartLocationConfig.ActivityType.STILL &&
            newActivity == SmartLocationConfig.ActivityType.STILL
        ) {
            SmartLocationConfig.recordStillState(context)

            // 检查是否进入深度休眠
            scope.launch {
                delay(SmartLocationConfig.DEEP_STATIONARY_THRESHOLD_MINUTES * 60 * 1000)
                if (_currentActivity.value == SmartLocationConfig.ActivityType.STILL &&
                    SmartLocationConfig.isDeepStationary(context)
                ) {
                    Log.d(TAG, "进入深度休眠模式")
                    _state.value = LocatorState.DORMANT
                }
            }
        }

        // 更新周期性工作间隔
        schedulePeriodicWork()
    }

    /**
     * 启动 WiFi 变化监听
     */
    private fun startWifiMonitoring() {
        // 记录初始 WiFi
        lastWifiBssid = SmartLocationConfig.getCurrentWifiBssid(context)
        SmartLocationConfig.saveLastWifiBssid(context, lastWifiBssid)

        // 方式1：使用 ConnectivityManager 监听网络变化
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                checkWifiChange()
            }

            override fun onLost(network: Network) {
                checkWifiChange()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    checkWifiChange()
                }
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)

        // 方式2：定期检查（备用）
        wifiCheckJob = scope.launch {
            while (true) {
                delay(WIFI_CHECK_INTERVAL_MS)
                checkWifiChange()
            }
        }

        Log.d(TAG, "WiFi 监听已启动")
    }

    /**
     * 停止 WiFi 监听
     */
    private fun stopWifiMonitoring() {
        wifiCheckJob?.cancel()
        wifiCheckJob = null

        networkCallback?.let {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(it)
        }
        networkCallback = null

        Log.d(TAG, "WiFi 监听已停止")
    }

    /**
     * 检查 WiFi 是否变化
     */
    private fun checkWifiChange() {
        val currentBssid = SmartLocationConfig.getCurrentWifiBssid(context)

        // WiFi 发生变化
        if (currentBssid != lastWifiBssid) {
            Log.d(TAG, "WiFi 变化: $lastWifiBssid -> $currentBssid")

            lastWifiBssid = currentBssid
            SmartLocationConfig.saveLastWifiBssid(context, currentBssid)

            // 如果当前是静止状态，WiFi 变化可能意味着位置变化
            if (_currentActivity.value == SmartLocationConfig.ActivityType.STILL) {
                triggerLocationReport(TriggerReason.WIFI_CHANGE)
            }
        }
    }

    /**
     * 注册 Geofence 广播接收器
     */
    private fun registerGeofenceReceiver() {
        geofenceReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == GeofenceManager.ACTION_GEOFENCE_TRIGGERED) {
                    handleGeofenceTrigger(intent)
                }
            }
        }

        val filter = IntentFilter(GeofenceManager.ACTION_GEOFENCE_TRIGGERED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(geofenceReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(geofenceReceiver, filter)
        }

        Log.d(TAG, "Geofence 接收器已注册")
    }

    /**
     * 注销 Geofence 接收器
     */
    private fun unregisterGeofenceReceiver() {
        geofenceReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "注销 Geofence 接收器失败", e)
            }
        }
        geofenceReceiver = null
    }

    /**
     * 处理 Geofence 触发
     */
    private fun handleGeofenceTrigger(intent: Intent) {
        val transitionType = intent.getIntExtra("transition_type", -1)
        val fenceId = intent.getStringExtra("fence_id")

        Log.d(TAG, "Geofence 触发: fenceId=$fenceId, transition=$transitionType")

        val reason = when (transitionType) {
            GeofenceManager.GEOFENCE_TRANSITION_ENTER -> TriggerReason.GEOFENCE_ENTER
            GeofenceManager.GEOFENCE_TRANSITION_EXIT -> TriggerReason.GEOFENCE_EXIT
            else -> return
        }

        // 自身围栏触发 - 说明自己移动了
        if (fenceId == SELF_GEOFENCE_ID) {
            triggerLocationReport(reason)
        }
    }

    /**
     * 设置自身位置围栏
     * 当自己离开当前位置围栏时触发上报
     */
    fun setupSelfGeofence(latitude: Double, longitude: Double) {
        if (!selfGeofenceEnabled) return

        geofenceManager.addGeofence(
            contactId = SELF_GEOFENCE_ID,
            contactName = "自身位置",
            locationName = "当前位置",
            center = LatLng(latitude, longitude),
            radiusMeters = SELF_GEOFENCE_RADIUS_METERS,
            notifyOnEnter = false,
            notifyOnExit = true,
            onSuccess = {
                Log.d(TAG, "自身位置围栏已设置: ($latitude, $longitude)")
            },
            onFailure = { error ->
                Log.e(TAG, "设置自身位置围栏失败: $error")
            }
        )
    }

    /**
     * 启用/禁用自身围栏
     */
    fun setSelfGeofenceEnabled(enabled: Boolean) {
        selfGeofenceEnabled = enabled
        if (!enabled) {
            geofenceManager.removeGeofence(SELF_GEOFENCE_ID)
        }
    }

    /**
     * 调度周期性工作
     */
    private fun schedulePeriodicWork() {
        val activity = _currentActivity.value
        val intervalMinutes = SmartLocationConfig.calculateSmartInterval(context, activity)

        Log.d(TAG, "调度周期性工作: 活动=${activity.displayName}, 间隔=${intervalMinutes}分钟")

        // WorkManager 最小间隔是 15 分钟
        val actualInterval = maxOf(intervalMinutes, 15L)

        val workRequest = PeriodicWorkRequestBuilder<SmartLocationWorker>(
            actualInterval, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES  // 弹性时间
        )
            .setInputData(
                workDataOf(
                    SmartLocationWorker.KEY_TRIGGER_REASON to "periodic",
                    SmartLocationWorker.KEY_ACTIVITY_TYPE to activity.name
                )
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * 触发位置上报
     */
    fun triggerLocationReport(reason: TriggerReason) {
        Log.d(TAG, "触发位置上报: ${reason.displayName}")

        // 检查是否应该上报
        val decision = SmartLocationConfig.evaluateReportDecision(
            context,
            reason.name.lowercase(),
            _currentActivity.value
        )

        if (!decision.shouldReport && reason != TriggerReason.MANUAL && reason != TriggerReason.REMOTE_REQUEST) {
            Log.d(TAG, "根据智能策略跳过上报: ${decision.reason}")
            return
        }

        // 使用 OneTimeWorkRequest 立即执行
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<SmartLocationWorker>()
            .setInputData(
                workDataOf(
                    SmartLocationWorker.KEY_TRIGGER_REASON to reason.name.lowercase(),
                    SmartLocationWorker.KEY_ACTIVITY_TYPE to _currentActivity.value.name,
                    SmartLocationWorker.KEY_FORCE_REPORT to (reason == TriggerReason.MANUAL || reason == TriggerReason.REMOTE_REQUEST)
                )
            )
            .build()

        workManager.enqueue(workRequest)

        // 切换到活跃状态
        if (_state.value != LocatorState.ACTIVE) {
            _state.value = LocatorState.ACTIVE
        }
    }

    /**
     * 使用 GPS 速度校准活动识别
     */
    fun calibrateWithGpsSpeed(speedMps: Float) {
        if (speedMps < 0) return

        val calibratedActivity = activityManager.calibrateWithGpsSpeed(speedMps)

        if (calibratedActivity != _currentActivity.value) {
            Log.d(TAG, "GPS 速度校准活动: ${_currentActivity.value.displayName} -> ${calibratedActivity.displayName}")
            _currentActivity.value = calibratedActivity
            SmartLocationConfig.saveLastActivity(context, calibratedActivity)
        }
    }

    /**
     * 记录位置上报成功
     */
    fun recordReportSuccess(
        latitude: Double,
        longitude: Double,
        triggerReason: TriggerReason
    ) {
        _lastReportInfo.value = ReportInfo(
            timestamp = System.currentTimeMillis(),
            triggerReason = triggerReason,
            latitude = latitude,
            longitude = longitude,
            activity = _currentActivity.value
        )

        // 保存位置用于显著位置变化检测
        SmartLocationConfig.saveLastLocation(context, latitude, longitude)

        // 更新自身围栏
        if (selfGeofenceEnabled) {
            setupSelfGeofence(latitude, longitude)
        }

        // 切回感知状态
        _state.value = LocatorState.AWARE
    }

    /**
     * 获取定位器统计信息
     */
    fun getStats(): LocatorStats {
        return LocatorStats(
            state = _state.value,
            currentActivity = _currentActivity.value,
            lastReportTime = _lastReportInfo.value?.timestamp ?: 0,
            stationaryDurationMinutes = SmartLocationConfig.getStationaryDurationMinutes(context),
            isDeepStationary = SmartLocationConfig.isDeepStationary(context),
            batteryLevel = SmartLocationConfig.getBatteryLevel(context),
            isCharging = SmartLocationConfig.isCharging(context),
            isPowerSaveMode = SmartLocationConfig.isPowerSaveMode(context),
            isConnectedToWifi = SmartLocationConfig.isConnectedToWifi(context),
            selfGeofenceEnabled = selfGeofenceEnabled
        )
    }

    /**
     * 定位器统计
     */
    data class LocatorStats(
        val state: LocatorState,
        val currentActivity: SmartLocationConfig.ActivityType,
        val lastReportTime: Long,
        val stationaryDurationMinutes: Long,
        val isDeepStationary: Boolean,
        val batteryLevel: Int,
        val isCharging: Boolean,
        val isPowerSaveMode: Boolean,
        val isConnectedToWifi: Boolean,
        val selfGeofenceEnabled: Boolean
    )

    /**
     * 释放资源
     */
    fun destroy() {
        stop()
        scope.cancel()
        geofenceManager.destroy()
        instance = null
    }
}
