package me.ikate.findmy.data.model

import java.util.Calendar

/**
 * 共享时长枚举
 */
enum class ShareDuration(val displayName: String, val durationMillis: Long?) {
    ONE_HOUR("分享一小时", 3600_000L),
    END_OF_DAY("分享到今天结束", null),  // null 需要动态计算
    INDEFINITELY("始终分享", null);      // null 代表永久

    companion object {
        /**
         * 计算到今天结束的毫秒数 (23:59:59)
         */
        fun calculateEndOfDay(): Long {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            return calendar.timeInMillis
        }
    }
}
