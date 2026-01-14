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
}
