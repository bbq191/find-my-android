package me.ikate.findmy.service

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ikate.findmy.data.local.FindMyDatabase
import java.util.concurrent.TimeUnit

/**
 * 围栏服务智能开关控制器
 *
 * 职责：
 * 1. 监听 Room 数据库中的围栏状态
 * 2. 有激活围栏时自动启动 GeofenceForegroundService
 * 3. 无激活围栏时自动停止服务，切换到低功耗模式（FCM + WorkManager 心跳）
 *
 * 使用场景：
 * - 用户设置了围栏 → 启动前台服务，高频位置监控
 * - 用户删除/禁用所有围栏 → 停止前台服务，节省电量
 *
 * Samsung S24 Ultra 优化：
 * - OneUI 8.0+ 对后台服务限制严格
 * - 前台服务是唯一可靠的高频位置监控方式
 * - 智能开关可以在不需要时节省电量
 */
class GeofenceServiceController private constructor(private val context: Context) {

    companion object {
        private const val TAG = "GeofenceServiceCtrl"
        private const val HEARTBEAT_WORK_NAME = "geofence_heartbeat"
        private const val HEARTBEAT_INTERVAL_MINUTES = 15L

        @Volatile
        private var instance: GeofenceServiceController? = null

        fun getInstance(context: Context): GeofenceServiceController {
            return instance ?: synchronized(this) {
                instance ?: GeofenceServiceController(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val database = FindMyDatabase.getInstance(context)

    // 服务状态
    private val _serviceState = MutableStateFlow(ServiceState.IDLE)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    // 当前激活的围栏数量
    private val _activeGeofenceCount = MutableStateFlow(0)
    val activeGeofenceCount: StateFlow<Int> = _activeGeofenceCount.asStateFlow()

    // 观察任务
    private var observeJob: Job? = null

    // 是否已初始化
    private var isInitialized = false

    /**
     * 服务状态枚举
     */
    enum class ServiceState {
        IDLE,           // 空闲（无围栏，服务未运行）
        HIGH_POWER,     // 高功耗模式（前台服务运行中）
        LOW_POWER       // 低功耗模式（WorkManager 心跳）
    }

    /**
     * 初始化控制器
     * 应在 Application.onCreate 中调用
     */
    fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "控制器已初始化，跳过")
            return
        }
        isInitialized = true
        Log.d(TAG, "初始化围栏服务控制器")

        // 开始监听围栏状态
        startObserving()
    }

    /**
     * 开始监听围栏状态变化
     */
    private fun startObserving() {
        observeJob?.cancel()
        observeJob = scope.launch {
            database.geofenceDao().observeActiveGeofences().collect { geofences ->
                val count = geofences.size
                val previousCount = _activeGeofenceCount.value
                _activeGeofenceCount.value = count

                Log.d(TAG, "激活围栏数量: $previousCount -> $count")

                // 根据围栏数量决定服务状态
                if (count > 0) {
                    switchToHighPowerMode()
                } else {
                    switchToLowPowerMode()
                }
            }
        }
    }

    /**
     * 切换到高功耗模式
     * 启动前台服务，高频位置监控
     */
    private fun switchToHighPowerMode() {
        if (_serviceState.value == ServiceState.HIGH_POWER) {
            Log.d(TAG, "已处于高功耗模式，跳过切换")
            return
        }

        Log.d(TAG, "切换到高功耗模式 - 启动前台服务")
        _serviceState.value = ServiceState.HIGH_POWER

        // 停止低功耗心跳
        stopHeartbeatWork()

        // 启动前台服务
        val started = GeofenceForegroundService.start(context)
        if (!started) {
            Log.w(TAG, "前台服务启动失败，回退到低功耗模式")
            switchToLowPowerMode()
        }
    }

    /**
     * 切换到低功耗模式
     * 停止前台服务，使用 WorkManager 维持心跳
     */
    private fun switchToLowPowerMode() {
        if (_serviceState.value == ServiceState.LOW_POWER || _serviceState.value == ServiceState.IDLE) {
            Log.d(TAG, "已处于低功耗/空闲模式，跳过切换")
            return
        }

        Log.d(TAG, "切换到低功耗模式 - 停止前台服务")

        // 停止前台服务
        GeofenceForegroundService.stop(context)

        // 检查是否完全空闲
        if (_activeGeofenceCount.value == 0) {
            _serviceState.value = ServiceState.IDLE
            Log.d(TAG, "无激活围栏，进入空闲状态")
        } else {
            _serviceState.value = ServiceState.LOW_POWER
            // 启动低功耗心跳（作为备用）
            startHeartbeatWork()
        }
    }

    /**
     * 启动 WorkManager 心跳任务
     * 作为低功耗模式的备用方案
     *
     * Samsung S24 Ultra / One UI 8.0+ 优化：
     * - 使用灵活时间窗口（5分钟），允许系统批量执行以节省电量
     * - 使用指数退避策略，失败后自动重试
     * - 配合电池优化白名单使用效果更佳
     */
    private fun startHeartbeatWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 使用灵活时间窗口：允许系统在 [interval, interval + flexInterval] 范围内执行
        // 这让系统可以批量处理多个任务，节省电量
        val flexIntervalMinutes = 5L

        val heartbeatRequest = PeriodicWorkRequestBuilder<GeofenceHeartbeatWorker>(
            HEARTBEAT_INTERVAL_MINUTES, TimeUnit.MINUTES,
            flexIntervalMinutes, TimeUnit.MINUTES  // 灵活时间窗口
        )
            .setConstraints(constraints)
            // 指数退避策略：失败后 30秒 → 60秒 → 120秒 ... 重试
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30L,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HEARTBEAT_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            heartbeatRequest
        )
        Log.d(TAG, "心跳任务已启动，间隔: ${HEARTBEAT_INTERVAL_MINUTES}分钟，灵活窗口: ${flexIntervalMinutes}分钟")
    }

    /**
     * 停止 WorkManager 心跳任务
     */
    private fun stopHeartbeatWork() {
        WorkManager.getInstance(context).cancelUniqueWork(HEARTBEAT_WORK_NAME)
        Log.d(TAG, "心跳任务已停止")
    }

    /**
     * 强制刷新状态
     * 可用于 FCM 唤醒后重新评估是否需要启动服务
     */
    fun refreshState() {
        scope.launch {
            val count = database.geofenceDao().getActiveGeofenceCount()
            Log.d(TAG, "强制刷新状态，当前激活围栏: $count")

            _activeGeofenceCount.value = count
            if (count > 0) {
                switchToHighPowerMode()
            } else {
                switchToLowPowerMode()
            }
        }
    }

    /**
     * 手动启动服务（用于用户主动操作）
     */
    fun forceStartService() {
        Log.d(TAG, "强制启动服务")
        switchToHighPowerMode()
    }

    /**
     * 手动停止服务（用于用户主动操作）
     */
    fun forceStopService() {
        Log.d(TAG, "强制停止服务")
        GeofenceForegroundService.stop(context)
        _serviceState.value = ServiceState.IDLE
    }

    /**
     * 销毁控制器
     */
    fun destroy() {
        Log.d(TAG, "销毁围栏服务控制器")
        observeJob?.cancel()
        scope.cancel()
        isInitialized = false
        instance = null
    }
}

/**
 * 围栏心跳 Worker
 * 在低功耗模式下定期检查围栏状态
 */
class GeofenceHeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "GeofenceHeartbeatWkr"
    }

    override fun doWork(): Result {
        Log.d(TAG, "执行心跳检查")

        // 检查是否有激活的围栏
        val database = FindMyDatabase.getInstance(applicationContext)
        val count = database.geofenceDao().getActiveGeofenceCountSync()

        Log.d(TAG, "当前激活围栏数量: $count")

        if (count > 0) {
            // 有激活围栏，尝试启动前台服务
            Log.d(TAG, "检测到激活围栏，尝试启动前台服务")
            GeofenceServiceController.getInstance(applicationContext).refreshState()
        }

        return Result.success()
    }
}
