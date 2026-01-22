package me.ikate.findmy.util

/**
 * 设备优化参数配置提供器
 *
 * 根据设备类型提供最优的定位、传感器和动画参数配置。
 * 针对三星 Galaxy S24 Ultra 进行了特殊优化：
 * - GPS 定位参数：增加等待时间以提高首次定位成功率
 * - 传感器参数：调整阈值以适应三星传感器特性
 * - 动画参数：利用 120Hz 屏幕优化动画体验
 */
object DeviceOptimizationConfig {

    /**
     * GPS 定位参数配置
     *
     * @property gpsWaitTimeMs GPS 等待时间（毫秒），在此期间优先等待 GPS 定位
     * @property timeoutMs 定位超时时间（毫秒）
     * @property minAccuracyMeters 最小精度要求（米），低于此精度的定位将被接受
     */
    data class GpsConfig(
        val gpsWaitTimeMs: Long,
        val timeoutMs: Long,
        val minAccuracyMeters: Float = 100f
    )

    /**
     * 传感器参数配置
     *
     * @property stillThreshold 静止阈值（m/s²），加速度变化低于此值判定为静止
     * @property stateConfirmCount 状态确认次数，连续检测到相同状态才确认变化
     * @property samplingPeriodUs 传感器采样间隔（微秒）
     */
    data class SensorConfig(
        val stillThreshold: Float,
        val stateConfirmCount: Int,
        val samplingPeriodUs: Int = 50000
    )

    /**
     * 动画参数配置
     *
     * @property positionAnimDurationMs 位置移动动画时长（毫秒），用于固定时长模式
     * @property cameraAnimDurationMs 相机跟随动画时长（毫秒）
     * @property preferHighRefreshRate 是否优先使用高刷新率
     * @property minAnimDurationMs 最小动画时长（毫秒），防止过快导致抖动
     * @property maxAnimDurationMs 最大动画时长（毫秒），防止断网后动画过长
     * @property defaultAnimDurationMs 断网恢复时的默认动画时长（毫秒）
     * @property bearingThresholdMeters 航向角更新阈值（米），移动距离小于此值时不更新航向角
     * @property enableDynamicDuration 是否启用动态时长（根据数据间隔自动调整）
     * @property enableBuffer 是否启用预测缓冲（极致丝滑但有延迟）
     */
    data class AnimationConfig(
        val positionAnimDurationMs: Long,
        val cameraAnimDurationMs: Long,
        val preferHighRefreshRate: Boolean,
        val minAnimDurationMs: Long = 500L,
        val maxAnimDurationMs: Long = 10_000L,
        val defaultAnimDurationMs: Long = 2_000L,
        val bearingThresholdMeters: Float = 5f,
        val enableDynamicDuration: Boolean = true,
        val enableBuffer: Boolean = false
    )

    // 默认参数（适用于大多数设备）
    private val DEFAULT_GPS_CONFIG = GpsConfig(
        gpsWaitTimeMs = 5000L,
        timeoutMs = 20000L
    )

    private val DEFAULT_SENSOR_CONFIG = SensorConfig(
        stillThreshold = 0.3f,
        stateConfirmCount = 3
    )

    private val DEFAULT_ANIMATION_CONFIG = AnimationConfig(
        positionAnimDurationMs = 1000L,
        cameraAnimDurationMs = 800L,
        preferHighRefreshRate = false,
        minAnimDurationMs = 500L,
        maxAnimDurationMs = 10_000L,
        defaultAnimDurationMs = 2_000L,
        bearingThresholdMeters = 5f,
        enableDynamicDuration = true,
        enableBuffer = true  // 默认开启预测缓冲，实现极致丝滑
    )

    // 三星 Galaxy S24 Ultra 优化参数
    private val SAMSUNG_S24_ULTRA_GPS_CONFIG = GpsConfig(
        gpsWaitTimeMs = 8000L,   // 增加 GPS 等待时间，三星设备首次定位较慢
        timeoutMs = 25000L,      // 增加超时时间，确保室内场景能获取到网络定位
        minAccuracyMeters = 80f  // 稍微提高精度要求
    )

    private val SAMSUNG_S24_ULTRA_SENSOR_CONFIG = SensorConfig(
        stillThreshold = 0.35f,   // 三星传感器噪声略大，提高静止阈值
        stateConfirmCount = 5,    // 增加确认次数，减少误判
        samplingPeriodUs = 50000
    )

    private val SAMSUNG_S24_ULTRA_ANIMATION_CONFIG = AnimationConfig(
        positionAnimDurationMs = 800L,  // 利用 120Hz 屏幕，缩短动画时长
        cameraAnimDurationMs = 600L,    // 相机动画更流畅
        preferHighRefreshRate = true,   // 追踪模式启用 120Hz
        minAnimDurationMs = 400L,       // S24U 高刷屏可以承受更短的最小时长
        maxAnimDurationMs = 8_000L,     // 断网检测更积极
        defaultAnimDurationMs = 1_500L, // 120Hz 下默认时长可以更短
        bearingThresholdMeters = 4f,    // 更精确的 GPS 允许更小的阈值
        enableDynamicDuration = true,
        enableBuffer = true             // 开启预测缓冲，实现 iOS Find My 级别丝滑
    )

    /**
     * 获取 GPS 定位参数配置
     */
    fun getGpsConfig(): GpsConfig {
        return when {
            SamsungDeviceDetector.isGalaxyS24Ultra() -> SAMSUNG_S24_ULTRA_GPS_CONFIG
            SamsungDeviceDetector.isGalaxyS24Series() -> SAMSUNG_S24_ULTRA_GPS_CONFIG.copy(
                gpsWaitTimeMs = 7000L,
                timeoutMs = 23000L
            )
            SamsungDeviceDetector.isSamsungDevice() -> DEFAULT_GPS_CONFIG.copy(
                gpsWaitTimeMs = 6000L,
                timeoutMs = 22000L
            )
            else -> DEFAULT_GPS_CONFIG
        }
    }

    /**
     * 获取传感器参数配置
     */
    fun getSensorConfig(): SensorConfig {
        return when {
            SamsungDeviceDetector.isGalaxyS24Ultra() -> SAMSUNG_S24_ULTRA_SENSOR_CONFIG
            SamsungDeviceDetector.isGalaxyS24Series() -> SAMSUNG_S24_ULTRA_SENSOR_CONFIG.copy(
                stateConfirmCount = 4
            )
            SamsungDeviceDetector.isSamsungDevice() -> DEFAULT_SENSOR_CONFIG.copy(
                stillThreshold = 0.32f,
                stateConfirmCount = 4
            )
            else -> DEFAULT_SENSOR_CONFIG
        }
    }

    /**
     * 获取动画参数配置
     */
    fun getAnimationConfig(): AnimationConfig {
        return when {
            SamsungDeviceDetector.isGalaxyS24Ultra() -> SAMSUNG_S24_ULTRA_ANIMATION_CONFIG
            SamsungDeviceDetector.supports120Hz() -> DEFAULT_ANIMATION_CONFIG.copy(
                positionAnimDurationMs = 850L,
                cameraAnimDurationMs = 650L,
                preferHighRefreshRate = true,
                minAnimDurationMs = 450L,
                defaultAnimDurationMs = 1_800L,
                bearingThresholdMeters = 4.5f
            )
            else -> DEFAULT_ANIMATION_CONFIG
        }
    }

    /**
     * 获取当前设备的配置摘要
     */
    fun getConfigSummary(): String {
        val gps = getGpsConfig()
        val sensor = getSensorConfig()
        val anim = getAnimationConfig()

        return buildString {
            appendLine("=== Device Optimization Config ===")
            appendLine("Device: ${if (SamsungDeviceDetector.isGalaxyS24Ultra()) "Samsung S24 Ultra"
                else if (SamsungDeviceDetector.isSamsungDevice()) "Samsung Device"
                else "Standard Device"}")
            appendLine()
            appendLine("GPS Config:")
            appendLine("  - Wait Time: ${gps.gpsWaitTimeMs}ms")
            appendLine("  - Timeout: ${gps.timeoutMs}ms")
            appendLine()
            appendLine("Sensor Config:")
            appendLine("  - Still Threshold: ${sensor.stillThreshold}")
            appendLine("  - Confirm Count: ${sensor.stateConfirmCount}")
            appendLine()
            appendLine("Animation Config:")
            appendLine("  - Position Anim: ${anim.positionAnimDurationMs}ms")
            appendLine("  - Camera Anim: ${anim.cameraAnimDurationMs}ms")
            appendLine("  - High Refresh Rate: ${anim.preferHighRefreshRate}")
            appendLine("  - Dynamic Duration: ${anim.enableDynamicDuration}")
            appendLine("  - Duration Range: ${anim.minAnimDurationMs}ms ~ ${anim.maxAnimDurationMs}ms")
            appendLine("  - Default Duration: ${anim.defaultAnimDurationMs}ms")
            appendLine("  - Bearing Threshold: ${anim.bearingThresholdMeters}m")
            appendLine("  - Buffer Enabled: ${anim.enableBuffer}")
        }
    }
}
