package me.ikate.findmy.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import me.ikate.findmy.util.DeviceOptimizationConfig
import kotlin.math.sqrt

/**
 * 活动识别管理器（混合实现）
 *
 * 采用混合策略：
 * 1. 优先使用 Google Activity Recognition API（更准确，支持更多活动类型）
 * 2. 降级方案：基于加速度传感器实现（不依赖 Google Play Services）
 *
 * 使用 Google API 的条件：
 * - Google Play Services 可用
 * - 已授予 ACTIVITY_RECOGNITION 权限（Android 10+）
 *
 * 检测原理（传感器模式）：
 * 1. 使用加速度计测量设备运动强度
 * 2. 计算加速度变化幅度判断运动状态
 * 3. 结合 GPS 速度数据进行校准
 */
class ActivityRecognitionManager(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "ActivityRecognition"

        // 加速度阈值 (m/s²) - 传感器模式使用
        private const val WALKING_THRESHOLD = 1.5f     // 步行阈值
        private const val RUNNING_THRESHOLD = 4.0f     // 跑步阈值
        private const val VEHICLE_THRESHOLD = 0.8f     // 车辆（平稳但有轻微震动）

        // 采样配置 - 传感器模式使用
        private const val SAMPLE_WINDOW_SIZE = 50      // 采样窗口大小
    }

    // 设备优化参数（从 DeviceOptimizationConfig 获取，支持三星设备特殊配置）
    private val sensorConfig = DeviceOptimizationConfig.getSensorConfig()
    private val stillThreshold = sensorConfig.stillThreshold           // 默认 0.3f，三星 S24 Ultra 为 0.35f
    private val stateConfirmCount = sensorConfig.stateConfirmCount     // 默认 3，三星 S24 Ultra 为 5
    private val samplingPeriodUs = sensorConfig.samplingPeriodUs       // 采样间隔（微秒）

    // Google Activity Recognition 管理器
    private val googleManager by lazy { GoogleActivityRecognitionManager(context) }

    // 传感器相关
    private val sensorManager: SensorManager? by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    private val accelerometer: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    // 加速度采样缓冲
    private val accelerationBuffer = mutableListOf<Float>()
    private var lastActivityType = SmartLocationConfig.ActivityType.UNKNOWN
    private var consecutiveStateCount = 0

    // 监听回调
    private var activityCallback: ((SmartLocationConfig.ActivityType) -> Unit)? = null
    private var isMonitoring = false

    // 当前使用的识别模式
    private var currentMode: RecognitionMode = RecognitionMode.NONE

    /**
     * 识别模式
     */
    enum class RecognitionMode {
        NONE,           // 未启动
        GOOGLE_API,     // 使用 Google Activity Recognition API
        SENSOR          // 使用传感器（降级模式）
    }

    /**
     * 获取当前识别模式
     */
    fun getCurrentMode(): RecognitionMode = currentMode

    /**
     * 检查 Google API 是否可用
     */
    fun isGoogleApiAvailable(): Boolean = googleManager.isAvailable()

    /**
     * 检查是否有 ACTIVITY_RECOGNITION 权限
     */
    fun hasActivityRecognitionPermission(): Boolean = googleManager.hasActivityRecognitionPermission()

    /**
     * 检查 Google Play Services 是否可用
     */
    fun isGooglePlayServicesAvailable(): Boolean = googleManager.isGooglePlayServicesAvailable()

    /**
     * 检查传感器是否可用
     */
    fun isSensorAvailable(): Boolean = accelerometer != null

    /**
     * 开始监听活动转换
     * 优先使用 Google API，不可用时降级到传感器
     */
    fun startActivityTransitionUpdates(
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        // 优先尝试 Google API
        if (googleManager.isAvailable()) {
            Log.i(TAG, "使用 Google Activity Recognition API")
            googleManager.setActivityChangeCallback { activity ->
                activityCallback?.invoke(activity)
            }
            googleManager.startActivityTransitionUpdates(
                onSuccess = {
                    currentMode = RecognitionMode.GOOGLE_API
                    isMonitoring = true
                    Log.i(TAG, "活动识别已启动（Google API 模式）")
                    onSuccess()
                },
                onFailure = { e ->
                    Log.w(TAG, "Google API 启动失败，尝试降级到传感器模式", e)
                    startSensorMode(onSuccess, onFailure)
                }
            )
        } else {
            // Google API 不可用，使用传感器
            val reason = when {
                !googleManager.isGooglePlayServicesAvailable() -> "Google Play Services 不可用"
                !googleManager.hasActivityRecognitionPermission() -> "缺少 ACTIVITY_RECOGNITION 权限"
                else -> "未知原因"
            }
            Log.i(TAG, "Google API 不可用（$reason），使用传感器模式")
            startSensorMode(onSuccess, onFailure)
        }
    }

    /**
     * 启动传感器模式
     */
    private fun startSensorMode(
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (accelerometer == null) {
            Log.w(TAG, "加速度传感器不可用")
            onFailure(UnsupportedOperationException("加速度传感器不可用"))
            return
        }

        try {
            sensorManager?.registerListener(
                this,
                accelerometer,
                samplingPeriodUs
            )
            currentMode = RecognitionMode.SENSOR
            isMonitoring = true
            Log.d(TAG, "活动识别已启动（传感器模式）")
            onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "启动传感器模式失败", e)
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
            when (currentMode) {
                RecognitionMode.GOOGLE_API -> {
                    googleManager.stopActivityTransitionUpdates(onSuccess, onFailure)
                }
                RecognitionMode.SENSOR -> {
                    sensorManager?.unregisterListener(this)
                    accelerationBuffer.clear()
                    onSuccess()
                }
                RecognitionMode.NONE -> {
                    onSuccess()
                }
            }
            currentMode = RecognitionMode.NONE
            isMonitoring = false
            Log.d(TAG, "活动识别已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止活动识别失败", e)
            onFailure(e)
        }
    }

    /**
     * 设置活动变化回调
     */
    fun setActivityChangeCallback(callback: (SmartLocationConfig.ActivityType) -> Unit) {
        activityCallback = callback
        // 同时设置给 Google 管理器
        googleManager.setActivityChangeCallback(callback)
    }

    /**
     * 获取当前活动类型
     * 优先使用实时传感器数据，备选使用保存的状态
     */
    fun getCurrentActivity(
        onResult: (SmartLocationConfig.ActivityType) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        // 如果有足够的传感器数据，使用实时计算
        if (currentMode == RecognitionMode.SENSOR && accelerationBuffer.size >= SAMPLE_WINDOW_SIZE / 2) {
            val activity = analyzeCurrentActivity()
            onResult(activity)
        } else {
            // 使用上次保存的活动状态
            val lastActivity = SmartLocationConfig.getLastActivity(context)
            onResult(lastActivity)
        }
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
     * 传感器数据变化回调（传感器模式使用）
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (currentMode != RecognitionMode.SENSOR) return
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        // 计算加速度幅度（去除重力影响的近似值）
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // 计算总加速度
        val totalAcceleration = sqrt(x * x + y * y + z * z)
        // 减去重力加速度，得到运动加速度
        val motionAcceleration = kotlin.math.abs(totalAcceleration - SensorManager.GRAVITY_EARTH)

        // 添加到缓冲区
        accelerationBuffer.add(motionAcceleration)
        if (accelerationBuffer.size > SAMPLE_WINDOW_SIZE) {
            accelerationBuffer.removeAt(0)
        }

        // 当收集足够样本时进行分析
        if (accelerationBuffer.size >= SAMPLE_WINDOW_SIZE) {
            val detectedActivity = analyzeCurrentActivity()

            // 状态变化确认机制
            if (detectedActivity == lastActivityType) {
                consecutiveStateCount++
            } else {
                consecutiveStateCount = 1
            }

            // 连续检测到相同状态才确认变化
            if (consecutiveStateCount >= stateConfirmCount &&
                detectedActivity != SmartLocationConfig.getLastActivity(context)
            ) {
                onActivityChanged(detectedActivity)
            }

            lastActivityType = detectedActivity
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 精度变化时的处理（仅在精度较低时警告）
        if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
            Log.w(TAG, "传感器精度较低: $accuracy")
        }
    }

    /**
     * 分析当前活动类型（传感器模式）
     */
    private fun analyzeCurrentActivity(): SmartLocationConfig.ActivityType {
        if (accelerationBuffer.isEmpty()) {
            return SmartLocationConfig.ActivityType.UNKNOWN
        }

        // 计算平均加速度和标准差
        val avgAcceleration = accelerationBuffer.average().toFloat()
        val variance = accelerationBuffer.map { (it - avgAcceleration) * (it - avgAcceleration) }
            .average().toFloat()
        val stdDev = sqrt(variance)

        // 根据加速度特征判断活动类型
        return when {
            // 静止：低平均值，低变化
            avgAcceleration < stillThreshold && stdDev < 0.2f -> {
                SmartLocationConfig.ActivityType.STILL
            }
            // 车辆：低平均值，但有规律震动
            avgAcceleration < VEHICLE_THRESHOLD && stdDev > 0.3f && stdDev < 1.0f -> {
                SmartLocationConfig.ActivityType.IN_VEHICLE
            }
            // 步行：中等加速度，有规律节奏
            avgAcceleration < WALKING_THRESHOLD && stdDev > 0.5f -> {
                SmartLocationConfig.ActivityType.WALKING
            }
            // 跑步：高加速度，大幅度变化
            avgAcceleration < RUNNING_THRESHOLD && stdDev > 1.5f -> {
                SmartLocationConfig.ActivityType.RUNNING
            }
            // 高速移动（可能是骑行或车辆）
            avgAcceleration >= RUNNING_THRESHOLD -> {
                if (stdDev > 2.0f) {
                    SmartLocationConfig.ActivityType.ON_BICYCLE
                } else {
                    SmartLocationConfig.ActivityType.IN_VEHICLE
                }
            }
            // 默认
            else -> SmartLocationConfig.ActivityType.UNKNOWN
        }
    }

    /**
     * 活动状态变化处理（传感器模式）
     */
    private fun onActivityChanged(newActivity: SmartLocationConfig.ActivityType) {
        val oldActivity = SmartLocationConfig.getLastActivity(context)
        Log.d(TAG, "活动状态变化: ${oldActivity.displayName} -> ${newActivity.displayName}")

        // 保存新状态
        SmartLocationConfig.saveLastActivity(context, newActivity)

        // 更新静止状态追踪
        if (newActivity == SmartLocationConfig.ActivityType.STILL) {
            SmartLocationConfig.recordStillState(context)
        } else {
            SmartLocationConfig.resetStillState(context)
        }

        // 通知回调
        activityCallback?.invoke(newActivity)

        // 发送广播
        ActivityTransitionReceiver.sendActivityTransition(
            context,
            newActivity,
            ActivityTransitionReceiver.ACTIVITY_TRANSITION_ENTER,
            source = "sensor"
        )
    }

    /**
     * 使用 GPS 速度校准活动类型
     * 当 GPS 提供速度信息时，可以更准确地判断活动类型
     */
    fun calibrateWithGpsSpeed(speedMps: Float): SmartLocationConfig.ActivityType {
        val sensorActivity = if (accelerationBuffer.size >= SAMPLE_WINDOW_SIZE / 2) {
            analyzeCurrentActivity()
        } else {
            SmartLocationConfig.ActivityType.UNKNOWN
        }

        val speedActivity = SmartLocationConfig.inferActivityFromSpeed(speedMps)

        // 传感器和速度都指向静止 = 确定静止
        if (sensorActivity == SmartLocationConfig.ActivityType.STILL &&
            speedActivity == SmartLocationConfig.ActivityType.STILL
        ) {
            return SmartLocationConfig.ActivityType.STILL
        }

        // 速度高但传感器显示平稳 = 可能在车辆中
        if (speedMps > SmartLocationConfig.DRIVING_SPEED_THRESHOLD &&
            sensorActivity == SmartLocationConfig.ActivityType.STILL
        ) {
            return SmartLocationConfig.ActivityType.IN_VEHICLE
        }

        // 优先使用速度判断（更准确）
        return if (speedActivity != SmartLocationConfig.ActivityType.UNKNOWN &&
            speedActivity != SmartLocationConfig.ActivityType.STILL
        ) {
            speedActivity
        } else {
            sensorActivity
        }
    }

    /**
     * 获取监听状态
     */
    fun isMonitoring(): Boolean = isMonitoring

    /**
     * 获取状态描述（用于 UI 显示）
     */
    fun getStatusDescription(): String {
        return when {
            !isMonitoring -> "未启动"
            currentMode == RecognitionMode.GOOGLE_API -> "运行中（Google API）"
            currentMode == RecognitionMode.SENSOR -> "运行中（传感器模式）"
            else -> "未知状态"
        }
    }
}
