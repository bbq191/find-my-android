package me.ikate.findmy.service

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Google Activity Recognition 管理器
 *
 * 使用 Google Play Services 的 Activity Recognition API 来检测用户活动状态
 * 该 API 比传感器方案更准确，支持更多活动类型：
 * - IN_VEHICLE: 在车辆中
 * - ON_BICYCLE: 骑自行车
 * - ON_FOOT: 步行
 * - RUNNING: 跑步
 * - WALKING: 行走
 * - STILL: 静止
 * - TILTING: 倾斜（设备角度变化）
 * - UNKNOWN: 未知
 *
 * 注意事项：
 * 1. Android 10+ 需要 ACTIVITY_RECOGNITION 运行时权限
 * 2. 需要 Google Play Services 支持
 */
class GoogleActivityRecognitionManager(private val context: Context) {

    companion object {
        private const val TAG = "GoogleActivityRecognition"

        // 检测间隔（毫秒）
        private const val DETECTION_INTERVAL_MS = 3000L

        // 广播 Action
        const val ACTION_ACTIVITY_TRANSITION = "me.ikate.findmy.GOOGLE_ACTIVITY_TRANSITION"
    }

    private var activityRecognitionClient: ActivityRecognitionClient? = null
    private var pendingIntent: PendingIntent? = null
    private var activityCallback: ((SmartLocationConfig.ActivityType) -> Unit)? = null
    private var isMonitoring = false
    private var transitionReceiver: BroadcastReceiver? = null

    /**
     * 检查 Google Play Services 是否可用
     */
    fun isGooglePlayServicesAvailable(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(context)
        return resultCode == ConnectionResult.SUCCESS
    }

    /**
     * 检查是否有 ACTIVITY_RECOGNITION 权限
     * Android 10+ 需要运行时权限
     */
    fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 9 及以下不需要此权限
            true
        }
    }

    /**
     * 检查是否可以使用 Google Activity Recognition
     */
    fun isAvailable(): Boolean {
        return isGooglePlayServicesAvailable() && hasActivityRecognitionPermission()
    }

    /**
     * 开始监听活动转换
     */
    fun startActivityTransitionUpdates(
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (!isGooglePlayServicesAvailable()) {
            val error = UnsupportedOperationException("Google Play Services 不可用")
            Log.w(TAG, error.message ?: "Unknown error")
            onFailure(error)
            return
        }

        if (!hasActivityRecognitionPermission()) {
            val error = SecurityException("缺少 ACTIVITY_RECOGNITION 权限")
            Log.w(TAG, error.message ?: "Permission denied")
            onFailure(error)
            return
        }

        try {
            // 初始化客户端
            activityRecognitionClient = ActivityRecognition.getClient(context)

            // 创建活动转换请求
            val transitions = buildActivityTransitions()
            val request = ActivityTransitionRequest(transitions)

            // 创建 PendingIntent
            val intent = Intent(ACTION_ACTIVITY_TRANSITION).apply {
                setPackage(context.packageName)
            }
            pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            // 注册广播接收器
            registerTransitionReceiver()

            // 请求活动转换更新
            activityRecognitionClient?.requestActivityTransitionUpdates(request, pendingIntent!!)
                ?.addOnSuccessListener {
                    isMonitoring = true
                    Log.i(TAG, "Google Activity Recognition 监听已启动")
                    onSuccess()
                }
                ?.addOnFailureListener { e ->
                    Log.e(TAG, "启动 Google Activity Recognition 失败", e)
                    unregisterTransitionReceiver()
                    onFailure(e)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "权限不足", e)
            onFailure(e)
        } catch (e: Exception) {
            Log.e(TAG, "启动活动识别失败", e)
            onFailure(e)
        }
    }

    /**
     * 停止监听活动转换
     */
    fun stopActivityTransitionUpdates(
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        try {
            pendingIntent?.let { pi ->
                activityRecognitionClient?.removeActivityTransitionUpdates(pi)
                    ?.addOnSuccessListener {
                        isMonitoring = false
                        Log.i(TAG, "Google Activity Recognition 监听已停止")
                        onSuccess()
                    }
                    ?.addOnFailureListener { e ->
                        Log.e(TAG, "停止活动识别失败", e)
                        onFailure(e)
                    }
            }
            unregisterTransitionReceiver()
        } catch (e: Exception) {
            Log.e(TAG, "停止活动识别异常", e)
            onFailure(e)
        }
    }

    /**
     * 设置活动变化回调
     */
    fun setActivityChangeCallback(callback: (SmartLocationConfig.ActivityType) -> Unit) {
        activityCallback = callback
    }

    /**
     * 获取活动类型的 Flow
     */
    fun activityFlow(): Flow<SmartLocationConfig.ActivityType> = callbackFlow {
        val callback: (SmartLocationConfig.ActivityType) -> Unit = { activity ->
            trySend(activity)
        }
        setActivityChangeCallback(callback)
        startActivityTransitionUpdates()

        awaitClose {
            stopActivityTransitionUpdates()
        }
    }

    /**
     * 构建需要监听的活动转换列表
     */
    private fun buildActivityTransitions(): List<ActivityTransition> {
        val transitions = mutableListOf<ActivityTransition>()

        // 监听的活动类型
        val activityTypes = listOf(
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.RUNNING,
            DetectedActivity.WALKING,
            DetectedActivity.STILL
        )

        // 为每个活动类型添加进入和退出转换
        for (activityType in activityTypes) {
            transitions.add(
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
            )
            transitions.add(
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build()
            )
        }

        return transitions
    }

    /**
     * 注册活动转换广播接收器
     */
    private fun registerTransitionReceiver() {
        transitionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_ACTIVITY_TRANSITION) {
                    if (ActivityTransitionResult.hasResult(intent)) {
                        val result = ActivityTransitionResult.extractResult(intent)
                        result?.transitionEvents?.forEach { event ->
                            handleTransitionEvent(event)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(ACTION_ACTIVITY_TRANSITION)
        ContextCompat.registerReceiver(
            context,
            transitionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /**
     * 取消注册广播接收器
     */
    private fun unregisterTransitionReceiver() {
        transitionReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "取消注册接收器失败", e)
            }
        }
        transitionReceiver = null
    }

    /**
     * 处理活动转换事件
     */
    private fun handleTransitionEvent(event: ActivityTransitionEvent) {
        val activityType = convertToActivityType(event.activityType)
        val transitionType = if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
            "进入"
        } else {
            "退出"
        }

        Log.d(TAG, "活动转换: $transitionType ${activityType.displayName}")

        // 只在进入新活动状态时通知
        if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
            // 保存状态
            SmartLocationConfig.saveLastActivity(context, activityType)

            // 更新静止状态追踪
            if (activityType == SmartLocationConfig.ActivityType.STILL) {
                SmartLocationConfig.recordStillState(context)
            } else {
                SmartLocationConfig.resetStillState(context)
            }

            // 通知回调
            activityCallback?.invoke(activityType)

            // 发送广播给 ActivityTransitionReceiver 处理位置上报逻辑
            ActivityTransitionReceiver.sendActivityTransition(
                context,
                activityType,
                ActivityTransitionReceiver.ACTIVITY_TRANSITION_ENTER,
                source = "google_api"
            )
        }
    }

    /**
     * 将 Google 活动类型转换为内部活动类型
     */
    private fun convertToActivityType(detectedActivity: Int): SmartLocationConfig.ActivityType {
        return when (detectedActivity) {
            DetectedActivity.IN_VEHICLE -> SmartLocationConfig.ActivityType.IN_VEHICLE
            DetectedActivity.ON_BICYCLE -> SmartLocationConfig.ActivityType.ON_BICYCLE
            DetectedActivity.RUNNING -> SmartLocationConfig.ActivityType.RUNNING
            DetectedActivity.WALKING -> SmartLocationConfig.ActivityType.WALKING
            DetectedActivity.STILL -> SmartLocationConfig.ActivityType.STILL
            DetectedActivity.ON_FOOT -> SmartLocationConfig.ActivityType.WALKING // ON_FOOT 归类为步行
            else -> SmartLocationConfig.ActivityType.UNKNOWN
        }
    }

    /**
     * 获取活动类型名称（用于日志）
     */
    private fun getActivityName(activityType: Int): String {
        return when (activityType) {
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.TILTING -> "TILTING"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.UNKNOWN -> "UNKNOWN"
            else -> "UNDEFINED"
        }
    }

    /**
     * 获取监听状态
     */
    fun isMonitoring(): Boolean = isMonitoring
}
