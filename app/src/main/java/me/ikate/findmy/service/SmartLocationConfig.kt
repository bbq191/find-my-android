package me.ikate.findmy.service

import android.content.Context
import android.content.SharedPreferences
import android.os.BatteryManager
import com.google.android.gms.location.DetectedActivity

/**
 * 智能位置上报配置
 * 根据用户活动状态和电量动态调整上报频率
 */
object SmartLocationConfig {

    private const val PREFS_NAME = "smart_location_prefs"
    private const val KEY_LAST_ACTIVITY = "last_activity"
    private const val KEY_LAST_LAT = "last_latitude"
    private const val KEY_LAST_LNG = "last_longitude"
    private const val KEY_LAST_REPORT_TIME = "last_report_time"
    private const val KEY_SMART_MODE_ENABLED = "smart_mode_enabled"

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

    /** 显著位置变化阈值（米） - 超过此距离立即上报 */
    const val SIGNIFICANT_DISTANCE_METERS = 200f

    /** 最小上报间隔（秒）- 防止过于频繁 */
    const val MIN_REPORT_INTERVAL_SECONDS = 60L

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

    /**
     * 将 Google Activity Recognition 类型转换为内部枚举
     */
    fun fromDetectedActivity(activityType: Int): ActivityType {
        return when (activityType) {
            DetectedActivity.STILL -> ActivityType.STILL
            DetectedActivity.WALKING -> ActivityType.WALKING
            DetectedActivity.RUNNING -> ActivityType.RUNNING
            DetectedActivity.ON_BICYCLE -> ActivityType.ON_BICYCLE
            DetectedActivity.IN_VEHICLE -> ActivityType.IN_VEHICLE
            DetectedActivity.ON_FOOT -> ActivityType.WALKING
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
        return distance >= SIGNIFICANT_DISTANCE_METERS
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
     */
    fun isSmartModeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SMART_MODE_ENABLED, true)
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
}
