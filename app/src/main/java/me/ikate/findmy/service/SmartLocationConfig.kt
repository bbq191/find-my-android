package me.ikate.findmy.service

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager

/**
 * 智能位置上报配置
 * 根据用户活动状态、电量、网络环境等动态调整上报频率
 *
 * 对标 iOS Find My 的智能策略：
 * - 显著位置变化检测 (类似 iOS Significant Location Change)
 * - 电量自适应 (类似 iOS 系统级省电)
 * - 活动感知调整 (类似 iOS CoreMotion 集成)
 * - WiFi 环境感知 (室内/室外判断)
 */
object SmartLocationConfig {

    private const val PREFS_NAME = "smart_location_prefs"
    private const val KEY_LAST_ACTIVITY = "last_activity"
    private const val KEY_LAST_LAT = "last_latitude"
    private const val KEY_LAST_LNG = "last_longitude"
    private const val KEY_LAST_REPORT_TIME = "last_report_time"
    private const val KEY_SMART_MODE_ENABLED = "smart_mode_enabled"
    private const val KEY_LAST_WIFI_BSSID = "last_wifi_bssid"
    private const val KEY_STATIONARY_START_TIME = "stationary_start_time"
    private const val KEY_CONSECUTIVE_STILL_COUNT = "consecutive_still_count"
    private const val KEY_LAST_SPEED = "last_speed"

    // ============== 上报间隔配置（分钟） ==============

    /** 静止状态：60分钟上报一次 */
    const val INTERVAL_STILL_MINUTES = 60L

    /** 步行状态：10分钟上报一次 */
    const val INTERVAL_WALKING_MINUTES = 10L

    /** 跑步/骑行状态：5分钟上报一次 */
    const val INTERVAL_RUNNING_MINUTES = 5L

    /** 驾车状态：3分钟上报一次 */
    const val INTERVAL_DRIVING_MINUTES = 3L

    /** 未知状态：15分钟上报一次（默认） */
    const val INTERVAL_UNKNOWN_MINUTES = 15L

    // ============== 电量自适应配置 ==============

    /** 低电量阈值（20%以下进入省电模式） */
    const val LOW_BATTERY_THRESHOLD = 20

    /** 中等电量阈值（50%以下降低频率） */
    const val MEDIUM_BATTERY_THRESHOLD = 50

    /** 低电量时的最小间隔（分钟） */
    const val LOW_BATTERY_MIN_INTERVAL_MINUTES = 30L

    /** 中等电量时的频率倍数 */
    const val MEDIUM_BATTERY_MULTIPLIER = 1.5f

    // ============== 显著位置变化配置 ==============
    // 参考 iOS: 500米阈值，不低于15分钟
    // 当前优化: 300米阈值，平衡精度与功耗

    /** 显著位置变化阈值（米） - 超过此距离立即上报 */
    const val SIGNIFICANT_DISTANCE_METERS = 300f

    /** 快速移动时的较小阈值（米） - 驾车/跑步时使用 */
    const val SIGNIFICANT_DISTANCE_FAST_METERS = 200f

    /** 最小上报间隔（秒）- 防止过于频繁 */
    const val MIN_REPORT_INTERVAL_SECONDS = 60L

    // ============== 智能静止检测配置 ==============

    /** 判定为持续静止的最小次数 */
    const val STATIONARY_CONFIRM_COUNT = 3

    /** 持续静止后的延长间隔倍数 */
    const val STATIONARY_INTERVAL_MULTIPLIER = 2.0f

    /** 深度静止阈值（分钟）- 超过此时间认为是深度静止 */
    const val DEEP_STATIONARY_THRESHOLD_MINUTES = 30L

    // ============== 速度相关配置 ==============

    /** 步行速度阈值 (m/s) - 约 5 km/h */
    const val WALKING_SPEED_THRESHOLD = 1.4f

    /** 跑步速度阈值 (m/s) - 约 10 km/h */
    const val RUNNING_SPEED_THRESHOLD = 2.8f

    /** 骑行速度阈值 (m/s) - 约 20 km/h */
    const val CYCLING_SPEED_THRESHOLD = 5.5f

    /** 驾车速度阈值 (m/s) - 约 40 km/h */
    const val DRIVING_SPEED_THRESHOLD = 11.0f

    /**
     * 活动类型枚举
     */
    enum class ActivityType(val displayName: String) {
        STILL("静止"),
        WALKING("步行"),
        RUNNING("跑步"),
        ON_BICYCLE("骑行"),
        IN_VEHICLE("驾车"),
        UNKNOWN("未知")
    }

    /**
     * 根据活动类型获取上报间隔
     */
    fun getIntervalForActivity(activityType: ActivityType): Long {
        return when (activityType) {
            ActivityType.STILL -> INTERVAL_STILL_MINUTES
            ActivityType.WALKING -> INTERVAL_WALKING_MINUTES
            ActivityType.RUNNING -> INTERVAL_RUNNING_MINUTES
            ActivityType.ON_BICYCLE -> INTERVAL_RUNNING_MINUTES
            ActivityType.IN_VEHICLE -> INTERVAL_DRIVING_MINUTES
            ActivityType.UNKNOWN -> INTERVAL_UNKNOWN_MINUTES
        }
    }

    // Activity Recognition 常量（替代 Google Play Services DetectedActivity）
    private const val ACTIVITY_IN_VEHICLE = 0
    private const val ACTIVITY_ON_BICYCLE = 1
    private const val ACTIVITY_ON_FOOT = 2
    private const val ACTIVITY_STILL = 3
    private const val ACTIVITY_WALKING = 7
    private const val ACTIVITY_RUNNING = 8

    /**
     * 将活动识别类型转换为内部枚举
     * 注意：当前使用内置传感器活动识别，此方法保留供未来扩展使用
     */
    fun fromDetectedActivity(activityType: Int): ActivityType {
        return when (activityType) {
            ACTIVITY_STILL -> ActivityType.STILL
            ACTIVITY_WALKING -> ActivityType.WALKING
            ACTIVITY_RUNNING -> ActivityType.RUNNING
            ACTIVITY_ON_BICYCLE -> ActivityType.ON_BICYCLE
            ACTIVITY_IN_VEHICLE -> ActivityType.IN_VEHICLE
            ACTIVITY_ON_FOOT -> ActivityType.WALKING
            else -> ActivityType.UNKNOWN
        }
    }

    /**
     * 获取电池电量
     */
    fun getBatteryLevel(context: Context): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    /**
     * 根据电量调整上报间隔
     */
    fun adjustIntervalForBattery(context: Context, baseIntervalMinutes: Long): Long {
        val batteryLevel = getBatteryLevel(context)

        return when {
            batteryLevel <= LOW_BATTERY_THRESHOLD -> {
                // 低电量模式：至少30分钟间隔
                maxOf(baseIntervalMinutes, LOW_BATTERY_MIN_INTERVAL_MINUTES)
            }
            batteryLevel <= MEDIUM_BATTERY_THRESHOLD -> {
                // 中等电量：频率降低1.5倍
                (baseIntervalMinutes * MEDIUM_BATTERY_MULTIPLIER).toLong()
            }
            else -> {
                // 正常电量：使用基础间隔
                baseIntervalMinutes
            }
        }
    }

    /**
     * 计算智能上报间隔
     */
    fun calculateSmartInterval(context: Context, activityType: ActivityType): Long {
        val baseInterval = getIntervalForActivity(activityType)
        return adjustIntervalForBattery(context, baseInterval)
    }

    /**
     * 计算两点之间的距离（米）
     */
    fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }

    /**
     * 检查是否为显著位置变化
     * 根据当前活动类型动态调整阈值
     */
    fun isSignificantLocationChange(
        context: Context,
        newLat: Double,
        newLng: Double
    ): Boolean {
        val prefs = getPrefs(context)
        val lastLat = prefs.getFloat(KEY_LAST_LAT, 0f).toDouble()
        val lastLng = prefs.getFloat(KEY_LAST_LNG, 0f).toDouble()

        // 如果没有历史记录，认为是显著变化
        if (lastLat == 0.0 && lastLng == 0.0) {
            return true
        }

        val distance = calculateDistance(lastLat, lastLng, newLat, newLng)

        // 根据活动类型选择阈值
        val activityType = getLastActivity(context)
        val threshold = when (activityType) {
            ActivityType.IN_VEHICLE,
            ActivityType.RUNNING,
            ActivityType.ON_BICYCLE -> SIGNIFICANT_DISTANCE_FAST_METERS
            else -> SIGNIFICANT_DISTANCE_METERS
        }

        return distance >= threshold
    }

    /**
     * 根据速度推断活动类型
     * 当传感器活动识别不可用时的备选方案
     */
    fun inferActivityFromSpeed(speedMps: Float): ActivityType {
        return when {
            speedMps < 0.5f -> ActivityType.STILL
            speedMps < WALKING_SPEED_THRESHOLD -> ActivityType.STILL
            speedMps < RUNNING_SPEED_THRESHOLD -> ActivityType.WALKING
            speedMps < CYCLING_SPEED_THRESHOLD -> ActivityType.RUNNING
            speedMps < DRIVING_SPEED_THRESHOLD -> ActivityType.ON_BICYCLE
            else -> ActivityType.IN_VEHICLE
        }
    }

    /**
     * 保存速度信息用于活动推断
     */
    fun saveLastSpeed(context: Context, speedMps: Float) {
        getPrefs(context).edit()
            .putFloat(KEY_LAST_SPEED, speedMps)
            .apply()
    }

    /**
     * 获取上次速度
     */
    fun getLastSpeed(context: Context): Float {
        return getPrefs(context).getFloat(KEY_LAST_SPEED, 0f)
    }

    /**
     * 检查是否满足最小上报间隔
     */
    fun canReportNow(context: Context): Boolean {
        val prefs = getPrefs(context)
        val lastReportTime = prefs.getLong(KEY_LAST_REPORT_TIME, 0L)
        val elapsed = System.currentTimeMillis() - lastReportTime
        return elapsed >= MIN_REPORT_INTERVAL_SECONDS * 1000
    }

    /**
     * 保存上报位置
     */
    fun saveLastLocation(context: Context, lat: Double, lng: Double) {
        getPrefs(context).edit()
            .putFloat(KEY_LAST_LAT, lat.toFloat())
            .putFloat(KEY_LAST_LNG, lng.toFloat())
            .putLong(KEY_LAST_REPORT_TIME, System.currentTimeMillis())
            .apply()
    }

    /**
     * 保存当前活动类型
     */
    fun saveLastActivity(context: Context, activityType: ActivityType) {
        getPrefs(context).edit()
            .putString(KEY_LAST_ACTIVITY, activityType.name)
            .apply()
    }

    /**
     * 获取上次活动类型
     */
    fun getLastActivity(context: Context): ActivityType {
        val name = getPrefs(context).getString(KEY_LAST_ACTIVITY, ActivityType.UNKNOWN.name)
        return try {
            ActivityType.valueOf(name ?: ActivityType.UNKNOWN.name)
        } catch (e: Exception) {
            ActivityType.UNKNOWN
        }
    }

    /**
     * 智能模式是否启用
     * 默认关闭，需要用户主动开启
     */
    fun isSmartModeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SMART_MODE_ENABLED, false)
    }

    /**
     * 设置智能模式
     */
    fun setSmartModeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_SMART_MODE_ENABLED, enabled)
            .apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ============== 智能静止检测 ==============

    /**
     * 记录静止状态
     * 连续多次检测到静止才确认为真正静止
     */
    fun recordStillState(context: Context) {
        val prefs = getPrefs(context)
        val currentCount = prefs.getInt(KEY_CONSECUTIVE_STILL_COUNT, 0)
        val startTime = prefs.getLong(KEY_STATIONARY_START_TIME, 0L)

        prefs.edit()
            .putInt(KEY_CONSECUTIVE_STILL_COUNT, currentCount + 1)
            .apply()

        // 首次进入静止，记录开始时间
        if (startTime == 0L) {
            prefs.edit()
                .putLong(KEY_STATIONARY_START_TIME, System.currentTimeMillis())
                .apply()
        }
    }

    /**
     * 重置静止状态计数
     * 当检测到移动时调用
     */
    fun resetStillState(context: Context) {
        getPrefs(context).edit()
            .putInt(KEY_CONSECUTIVE_STILL_COUNT, 0)
            .putLong(KEY_STATIONARY_START_TIME, 0L)
            .apply()
    }

    /**
     * 检查是否确认为持续静止状态
     */
    fun isConfirmedStationary(context: Context): Boolean {
        val count = getPrefs(context).getInt(KEY_CONSECUTIVE_STILL_COUNT, 0)
        return count >= STATIONARY_CONFIRM_COUNT
    }

    /**
     * 检查是否为深度静止状态
     * 超过30分钟的持续静止
     */
    fun isDeepStationary(context: Context): Boolean {
        val prefs = getPrefs(context)
        val startTime = prefs.getLong(KEY_STATIONARY_START_TIME, 0L)
        if (startTime == 0L) return false

        val stationaryMinutes = (System.currentTimeMillis() - startTime) / 1000 / 60
        return stationaryMinutes >= DEEP_STATIONARY_THRESHOLD_MINUTES
    }

    /**
     * 获取静止持续时间（分钟）
     */
    fun getStationaryDurationMinutes(context: Context): Long {
        val startTime = getPrefs(context).getLong(KEY_STATIONARY_START_TIME, 0L)
        if (startTime == 0L) return 0
        return (System.currentTimeMillis() - startTime) / 1000 / 60
    }

    // ============== WiFi 环境感知 ==============

    /**
     * 获取当前连接的WiFi BSSID
     * 用于判断是否在同一个WiFi环境（室内位置稳定性判断）
     */
    @Suppress("DEPRECATION")
    fun getCurrentWifiBssid(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            wifiInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" && it != "<unknown ssid>" }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 保存当前WiFi BSSID
     */
    fun saveLastWifiBssid(context: Context, bssid: String?) {
        getPrefs(context).edit()
            .putString(KEY_LAST_WIFI_BSSID, bssid ?: "")
            .apply()
    }

    /**
     * 获取上次WiFi BSSID
     */
    fun getLastWifiBssid(context: Context): String? {
        return getPrefs(context).getString(KEY_LAST_WIFI_BSSID, null)
    }

    /**
     * 检查WiFi环境是否变化
     * 用于辅助判断位置是否发生变化
     */
    fun hasWifiEnvironmentChanged(context: Context): Boolean {
        val currentBssid = getCurrentWifiBssid(context)
        val lastBssid = getLastWifiBssid(context)

        // 如果没有WiFi连接，无法判断
        if (currentBssid == null && lastBssid == null) {
            return false // 都没WiFi，假设环境未变
        }

        // WiFi连接状态变化
        if ((currentBssid == null) != (lastBssid == null)) {
            return true
        }

        // WiFi BSSID变化
        return currentBssid != lastBssid
    }

    /**
     * 检查是否连接到WiFi（可能在室内）
     */
    fun isConnectedToWifi(context: Context): Boolean {
        return getCurrentWifiBssid(context) != null
    }

    // ============== 系统省电模式检测 ==============

    /**
     * 检查系统是否处于省电模式
     */
    fun isPowerSaveMode(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isPowerSaveMode == true
    }

    /**
     * 检查是否正在充电
     */
    fun isCharging(context: Context): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return batteryManager?.isCharging == true
    }

    // ============== 综合智能决策 ==============

    /**
     * 综合评估是否应该上报位置
     * 考虑多个因素：活动状态、电量、WiFi环境、静止时长等
     */
    fun evaluateReportDecision(
        context: Context,
        triggerReason: String,
        currentActivity: ActivityType
    ): ReportDecision {
        val batteryLevel = getBatteryLevel(context)
        val isPowerSave = isPowerSaveMode(context)
        val isCharging = isCharging(context)
        val isConfirmedStill = isConfirmedStationary(context)
        val isDeepStill = isDeepStationary(context)
        val wifiChanged = hasWifiEnvironmentChanged(context)

        // 充电时，正常上报
        if (isCharging) {
            return ReportDecision(
                shouldReport = true,
                reason = "设备充电中，正常上报",
                suggestedInterval = getIntervalForActivity(currentActivity)
            )
        }

        // 系统省电模式 + 静止 = 大幅降低频率
        if (isPowerSave && currentActivity == ActivityType.STILL) {
            return ReportDecision(
                shouldReport = triggerReason == "activity_change" || wifiChanged,
                reason = "系统省电模式且静止",
                suggestedInterval = LOW_BATTERY_MIN_INTERVAL_MINUTES * 2
            )
        }

        // 低电量 + 深度静止 = 跳过
        if (batteryLevel <= LOW_BATTERY_THRESHOLD && isDeepStill) {
            return ReportDecision(
                shouldReport = false,
                reason = "低电量且深度静止，跳过上报",
                suggestedInterval = INTERVAL_STILL_MINUTES * 2
            )
        }

        // 低电量 + 静止 = 可能跳过
        if (batteryLevel <= LOW_BATTERY_THRESHOLD && currentActivity == ActivityType.STILL) {
            return ReportDecision(
                shouldReport = triggerReason == "activity_change",
                reason = "低电量且静止",
                suggestedInterval = LOW_BATTERY_MIN_INTERVAL_MINUTES
            )
        }

        // WiFi环境变化，可能位置变化
        if (wifiChanged && currentActivity == ActivityType.STILL) {
            return ReportDecision(
                shouldReport = true,
                reason = "WiFi环境变化，可能位置已改变",
                suggestedInterval = getIntervalForActivity(currentActivity)
            )
        }

        // 确认静止状态，延长间隔
        if (isConfirmedStill && currentActivity == ActivityType.STILL) {
            val baseInterval = getIntervalForActivity(currentActivity)
            val extendedInterval = (baseInterval * STATIONARY_INTERVAL_MULTIPLIER).toLong()
            return ReportDecision(
                shouldReport = triggerReason != "periodic" || !isDeepStill,
                reason = "确认静止状态",
                suggestedInterval = minOf(extendedInterval, 120L) // 最大2小时
            )
        }

        // 默认：根据活动类型和电量计算
        return ReportDecision(
            shouldReport = true,
            reason = "正常上报",
            suggestedInterval = calculateSmartInterval(context, currentActivity)
        )
    }

    /**
     * 上报决策结果
     */
    data class ReportDecision(
        val shouldReport: Boolean,
        val reason: String,
        val suggestedInterval: Long
    )
}
