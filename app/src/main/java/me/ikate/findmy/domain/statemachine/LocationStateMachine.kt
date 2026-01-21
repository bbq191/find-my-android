package me.ikate.findmy.domain.statemachine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.ikate.findmy.service.LocationReportService

/**
 * 位置状态机
 *
 * 实现 iOS Find My 风格的三态切换：
 * - IDLE (静默守望): WorkManager 15分钟周期定位，低功耗
 * - LIVE_TRACKING (实时追踪): 前台服务 2秒高频定位，高精度
 * - 自动休眠: 60秒无心跳自动切回 IDLE
 *
 * 状态切换流程:
 * ```
 *                    FCM/MQTT 唤醒
 *     IDLE ────────────────────────────► LIVE_TRACKING
 *      ▲                                      │
 *      │                                      │
 *      │         60秒无心跳 / 主动停止         │
 *      └──────────────────────────────────────┘
 * ```
 */
class LocationStateMachine(
    private val context: Context,
    private val onStateChanged: ((LocationState, LocationState) -> Unit)? = null
) {
    companion object {
        private const val TAG = "LocationStateMachine"

        /** 实时追踪定位间隔 (毫秒) */
        const val LIVE_TRACKING_INTERVAL_MS = 2000L

        /** 心跳超时时间 (毫秒) - 60秒无心跳自动休眠 */
        const val HEARTBEAT_TIMEOUT_MS = 60_000L

        /** 最大实时追踪时长 (毫秒) - 5分钟强制休眠 */
        const val MAX_TRACKING_DURATION_MS = 5 * 60 * 1000L

        @Volatile
        private var instance: LocationStateMachine? = null

        fun getInstance(context: Context): LocationStateMachine {
            return instance ?: synchronized(this) {
                instance ?: LocationStateMachine(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * 位置状态枚举
     */
    enum class LocationState {
        /** 静默守望 - 低功耗模式，WorkManager 周期定位 */
        IDLE,

        /** 实时追踪 - 高频定位模式，前台服务 */
        LIVE_TRACKING
    }

    /**
     * 状态切换事件
     */
    sealed class StateEvent {
        /** 收到追踪请求 (FCM/MQTT 唤醒) */
        data class TrackingRequested(
            val requesterId: String,
            val reason: String = "remote_request"
        ) : StateEvent()

        /** 收到心跳 */
        data class HeartbeatReceived(val fromId: String) : StateEvent()

        /** 主动停止追踪 */
        data object StopTracking : StateEvent()

        /** 心跳超时 */
        data object HeartbeatTimeout : StateEvent()

        /** 达到最大追踪时长 */
        data object MaxDurationReached : StateEvent()

        /** 应用进入前台 */
        data object AppForeground : StateEvent()

        /** 应用进入后台 */
        data object AppBackground : StateEvent()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 当前状态
    private val _currentState = MutableStateFlow(LocationState.IDLE)
    val currentState: StateFlow<LocationState> = _currentState.asStateFlow()

    // 当前追踪的请求者 ID
    private val _trackingRequesterId = MutableStateFlow<String?>(null)
    val trackingRequesterId: StateFlow<String?> = _trackingRequesterId.asStateFlow()

    // 实时追踪开始时间
    private var trackingStartTime: Long = 0L

    // 最后一次心跳时间
    private var lastHeartbeatTime: Long = 0L

    // 实时定位任务
    private var liveTrackingJob: Job? = null

    // 心跳监控任务
    private var heartbeatMonitorJob: Job? = null

    // 最大时长监控任务
    private var maxDurationJob: Job? = null

    // 位置上报服务
    private val locationReportService by lazy { LocationReportService(context) }

    /**
     * 处理状态事件
     */
    fun handleEvent(event: StateEvent) {
        val oldState = _currentState.value

        when (event) {
            is StateEvent.TrackingRequested -> {
                if (oldState == LocationState.IDLE) {
                    Log.i(TAG, "收到追踪请求: requesterId=${event.requesterId}, reason=${event.reason}")
                    transitionToLiveTracking(event.requesterId)
                } else {
                    // 已在追踪中，刷新心跳
                    Log.d(TAG, "已在追踪中，刷新心跳")
                    refreshHeartbeat()
                }
            }

            is StateEvent.HeartbeatReceived -> {
                if (oldState == LocationState.LIVE_TRACKING) {
                    Log.d(TAG, "收到心跳: fromId=${event.fromId}")
                    refreshHeartbeat()
                }
            }

            is StateEvent.StopTracking -> {
                if (oldState == LocationState.LIVE_TRACKING) {
                    Log.i(TAG, "主动停止追踪")
                    transitionToIdle("user_stopped")
                }
            }

            is StateEvent.HeartbeatTimeout -> {
                if (oldState == LocationState.LIVE_TRACKING) {
                    Log.i(TAG, "心跳超时，自动休眠")
                    transitionToIdle("heartbeat_timeout")
                }
            }

            is StateEvent.MaxDurationReached -> {
                if (oldState == LocationState.LIVE_TRACKING) {
                    Log.i(TAG, "达到最大追踪时长，强制休眠")
                    transitionToIdle("max_duration")
                }
            }

            is StateEvent.AppForeground -> {
                Log.d(TAG, "应用进入前台")
                // 可以根据需要刷新心跳
            }

            is StateEvent.AppBackground -> {
                Log.d(TAG, "应用进入后台")
                // 后台时继续追踪（前台服务保证）
            }
        }
    }

    /**
     * 切换到实时追踪状态
     */
    private fun transitionToLiveTracking(requesterId: String) {
        val oldState = _currentState.value
        _currentState.value = LocationState.LIVE_TRACKING
        _trackingRequesterId.value = requesterId

        trackingStartTime = System.currentTimeMillis()
        lastHeartbeatTime = System.currentTimeMillis()

        // 启动实时定位
        startLiveTracking()

        // 启动心跳监控
        startHeartbeatMonitor()

        // 启动最大时长监控
        startMaxDurationMonitor()

        Log.i(TAG, "状态切换: $oldState -> LIVE_TRACKING (requesterId=$requesterId)")
        onStateChanged?.invoke(oldState, LocationState.LIVE_TRACKING)
    }

    /**
     * 切换到静默守望状态
     */
    private fun transitionToIdle(reason: String) {
        val oldState = _currentState.value
        val wasRequesterId = _trackingRequesterId.value

        // 停止所有任务
        stopLiveTracking()
        stopHeartbeatMonitor()
        stopMaxDurationMonitor()

        _currentState.value = LocationState.IDLE
        _trackingRequesterId.value = null

        val duration = if (trackingStartTime > 0) {
            (System.currentTimeMillis() - trackingStartTime) / 1000
        } else 0

        Log.i(TAG, "状态切换: $oldState -> IDLE (reason=$reason, duration=${duration}s, requesterId=$wasRequesterId)")
        onStateChanged?.invoke(oldState, LocationState.IDLE)
    }

    /**
     * 启动实时定位
     */
    private fun startLiveTracking() {
        liveTrackingJob?.cancel()
        liveTrackingJob = scope.launch {
            Log.d(TAG, "开始实时定位循环 (间隔: ${LIVE_TRACKING_INTERVAL_MS}ms)")

            // 立即上报一次
            reportLocationNow()

            while (isActive && _currentState.value == LocationState.LIVE_TRACKING) {
                delay(LIVE_TRACKING_INTERVAL_MS)
                if (_currentState.value == LocationState.LIVE_TRACKING) {
                    reportLocationNow()
                }
            }

            Log.d(TAG, "实时定位循环结束")
        }
    }

    /**
     * 停止实时定位
     */
    private fun stopLiveTracking() {
        liveTrackingJob?.cancel()
        liveTrackingJob = null
    }

    /**
     * 启动心跳监控
     */
    private fun startHeartbeatMonitor() {
        heartbeatMonitorJob?.cancel()
        heartbeatMonitorJob = scope.launch {
            Log.d(TAG, "开始心跳监控 (超时: ${HEARTBEAT_TIMEOUT_MS}ms)")

            while (isActive && _currentState.value == LocationState.LIVE_TRACKING) {
                delay(5000) // 每5秒检查一次

                val timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeatTime
                if (timeSinceLastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                    Log.w(TAG, "心跳超时: ${timeSinceLastHeartbeat}ms > ${HEARTBEAT_TIMEOUT_MS}ms")
                    handleEvent(StateEvent.HeartbeatTimeout)
                    break
                }
            }

            Log.d(TAG, "心跳监控结束")
        }
    }

    /**
     * 停止心跳监控
     */
    private fun stopHeartbeatMonitor() {
        heartbeatMonitorJob?.cancel()
        heartbeatMonitorJob = null
    }

    /**
     * 启动最大时长监控
     */
    private fun startMaxDurationMonitor() {
        maxDurationJob?.cancel()
        maxDurationJob = scope.launch {
            Log.d(TAG, "开始最大时长监控 (最大: ${MAX_TRACKING_DURATION_MS}ms)")

            delay(MAX_TRACKING_DURATION_MS)

            if (_currentState.value == LocationState.LIVE_TRACKING) {
                Log.w(TAG, "达到最大追踪时长: ${MAX_TRACKING_DURATION_MS}ms")
                handleEvent(StateEvent.MaxDurationReached)
            }
        }
    }

    /**
     * 停止最大时长监控
     */
    private fun stopMaxDurationMonitor() {
        maxDurationJob?.cancel()
        maxDurationJob = null
    }

    /**
     * 刷新心跳
     */
    fun refreshHeartbeat() {
        lastHeartbeatTime = System.currentTimeMillis()
        Log.d(TAG, "心跳已刷新")
    }

    /**
     * 立即上报位置
     */
    private suspend fun reportLocationNow() {
        try {
            val result = locationReportService.reportCurrentLocation(timeout = 10000L)
            result.fold(
                onSuccess = { device ->
                    Log.d(TAG, "实时位置上报成功: ${device.location}")
                },
                onFailure = { error ->
                    Log.w(TAG, "实时位置上报失败: ${error.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "位置上报异常", e)
        }
    }

    /**
     * 获取追踪统计信息
     */
    fun getTrackingStats(): TrackingStats {
        return TrackingStats(
            state = _currentState.value,
            requesterId = _trackingRequesterId.value,
            trackingDurationMs = if (trackingStartTime > 0 && _currentState.value == LocationState.LIVE_TRACKING) {
                System.currentTimeMillis() - trackingStartTime
            } else 0,
            timeSinceLastHeartbeatMs = if (lastHeartbeatTime > 0 && _currentState.value == LocationState.LIVE_TRACKING) {
                System.currentTimeMillis() - lastHeartbeatTime
            } else 0
        )
    }

    /**
     * 追踪统计数据
     */
    data class TrackingStats(
        val state: LocationState,
        val requesterId: String?,
        val trackingDurationMs: Long,
        val timeSinceLastHeartbeatMs: Long
    )

    /**
     * 强制重置为 IDLE 状态
     */
    fun forceReset() {
        Log.w(TAG, "强制重置状态机")
        transitionToIdle("force_reset")
    }

    /**
     * 释放资源
     */
    fun destroy() {
        Log.d(TAG, "销毁状态机")
        stopLiveTracking()
        stopHeartbeatMonitor()
        stopMaxDurationMonitor()
        scope.cancel()
        instance = null
    }
}
