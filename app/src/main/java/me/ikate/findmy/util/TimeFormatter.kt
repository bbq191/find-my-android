package me.ikate.findmy.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 时间格式化工具类
 * 统一处理时间显示格式
 */
object TimeFormatter {

    /**
     * 格式化更新时间为友好显示
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的字符串，如 "刚刚"、"5分钟前"、"2小时前"
     */
    fun formatUpdateTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "刚刚"
            diff < 3600_000 -> "${diff / 60_000}分钟前"
            diff < 86400_000 -> "${diff / 3600_000}小时前"
            else -> {
                val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    /**
     * 格式化时间为完整日期时间
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的字符串，如 "2025-01-14 15:30"
     */
    fun formatFullDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * 格式化时间为简短日期
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的字符串，如 "01-14"
     */
    fun formatShortDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * 判断是否在线（5分钟内更新视为在线）
     * @param lastUpdateTime 最后更新时间戳
     * @return 是否在线
     */
    fun isOnline(lastUpdateTime: Long): Boolean {
        return (System.currentTimeMillis() - lastUpdateTime) < 5 * 60 * 1000
    }

    /**
     * 判断位置是否可能已过期（超过 30 分钟未更新）
     * 用于提示用户当前显示的位置可能是 MQTT Retained 消息（缓存的旧数据）
     * @param lastUpdateTime 最后更新时间戳
     * @return 是否可能已过期
     */
    fun isLocationStale(lastUpdateTime: Long): Boolean {
        if (lastUpdateTime <= 0) return true
        return (System.currentTimeMillis() - lastUpdateTime) > 30 * 60 * 1000  // 30分钟
    }

    /**
     * 判断位置是否严重过期（超过 24 小时未更新）
     * @param lastUpdateTime 最后更新时间戳
     * @return 是否严重过期
     */
    fun isLocationVeryStale(lastUpdateTime: Long): Boolean {
        if (lastUpdateTime <= 0) return true
        return (System.currentTimeMillis() - lastUpdateTime) > 24 * 60 * 60 * 1000  // 24小时
    }

    /**
     * 获取位置新鲜度状态
     * @param lastUpdateTime 最后更新时间戳
     * @return LocationFreshness 枚举
     */
    fun getLocationFreshness(lastUpdateTime: Long): LocationFreshness {
        if (lastUpdateTime <= 0) return LocationFreshness.UNKNOWN
        val diff = System.currentTimeMillis() - lastUpdateTime
        return when {
            diff < 5 * 60 * 1000 -> LocationFreshness.FRESH       // 5分钟内
            diff < 30 * 60 * 1000 -> LocationFreshness.RECENT     // 30分钟内
            diff < 24 * 60 * 60 * 1000 -> LocationFreshness.STALE // 24小时内
            else -> LocationFreshness.VERY_STALE                   // 超过24小时
        }
    }

    /**
     * 位置新鲜度枚举
     */
    enum class LocationFreshness {
        FRESH,       // 新鲜（5分钟内）
        RECENT,      // 较新（30分钟内）
        STALE,       // 可能过期（24小时内）
        VERY_STALE,  // 严重过期（超过24小时）
        UNKNOWN      // 未知（无时间戳）
    }
}
