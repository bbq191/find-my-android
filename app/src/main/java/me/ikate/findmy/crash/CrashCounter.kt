package me.ikate.findmy.crash

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 崩溃计数器
 *
 * 用于防止崩溃循环：
 * - 记录连续崩溃次数和时间
 * - 5 分钟内崩溃 >= 3 次时进入安全模式
 * - 超过 5 分钟自动重置计数
 */
class CrashCounter(context: Context) {

    companion object {
        private const val TAG = "CrashCounter"
        private const val PREFS_NAME = "crash_counter_prefs"
        private const val KEY_CRASH_COUNT = "crash_count"
        private const val KEY_LAST_CRASH_TIME = "last_crash_time"
        private const val KEY_FIRST_CRASH_TIME = "first_crash_time"

        // 崩溃循环检测阈值
        const val CRASH_LOOP_THRESHOLD = 3
        const val CRASH_LOOP_WINDOW_MS = 5 * 60 * 1000L // 5 分钟
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * 获取当前崩溃次数
     */
    fun getCount(): Int {
        checkAndResetIfExpired()
        return prefs.getInt(KEY_CRASH_COUNT, 0)
    }

    /**
     * 获取上次崩溃时间
     */
    fun getLastCrashTime(): Long {
        return prefs.getLong(KEY_LAST_CRASH_TIME, 0)
    }

    /**
     * 获取第一次崩溃时间（在当前窗口内）
     */
    fun getFirstCrashTime(): Long {
        return prefs.getLong(KEY_FIRST_CRASH_TIME, 0)
    }

    /**
     * 增加崩溃计数
     * 使用 commit() 而非 apply() 确保同步写入
     */
    fun increment() {
        val now = System.currentTimeMillis()
        val currentCount = getCount()
        val firstCrashTime = if (currentCount == 0) now else getFirstCrashTime()

        prefs.edit()
            .putInt(KEY_CRASH_COUNT, currentCount + 1)
            .putLong(KEY_LAST_CRASH_TIME, now)
            .putLong(KEY_FIRST_CRASH_TIME, firstCrashTime)
            .commit() // 使用 commit() 确保崩溃前写入完成

        Log.d(TAG, "崩溃计数增加: ${currentCount + 1}")
    }

    /**
     * 重置计数器
     */
    fun reset() {
        prefs.edit()
            .putInt(KEY_CRASH_COUNT, 0)
            .putLong(KEY_LAST_CRASH_TIME, 0)
            .putLong(KEY_FIRST_CRASH_TIME, 0)
            .apply()

        Log.d(TAG, "崩溃计数已重置")
    }

    /**
     * 检查是否处于崩溃循环
     * 5 分钟内崩溃 >= 3 次视为崩溃循环
     */
    fun isInCrashLoop(): Boolean {
        val count = getCount()
        val firstCrashTime = getFirstCrashTime()
        val now = System.currentTimeMillis()

        if (count >= CRASH_LOOP_THRESHOLD) {
            val elapsed = now - firstCrashTime
            if (elapsed < CRASH_LOOP_WINDOW_MS) {
                Log.w(TAG, "检测到崩溃循环: $count 次崩溃在 ${elapsed / 1000} 秒内")
                return true
            }
        }

        return false
    }

    /**
     * 检查并重置过期的计数
     * 如果距离第一次崩溃超过 5 分钟，重置计数
     */
    private fun checkAndResetIfExpired() {
        val firstCrashTime = prefs.getLong(KEY_FIRST_CRASH_TIME, 0)
        if (firstCrashTime == 0L) return

        val now = System.currentTimeMillis()
        if (now - firstCrashTime > CRASH_LOOP_WINDOW_MS) {
            Log.d(TAG, "崩溃窗口已过期，重置计数")
            reset()
        }
    }

    /**
     * 标记应用成功启动
     * 应在应用完成初始化后调用，表示本次启动成功
     */
    fun markSuccessfulStart() {
        // 如果应用成功启动并运行了一段时间，可以考虑重置计数
        // 这里暂时不自动重置，由用户决定
        Log.d(TAG, "应用启动成功标记")
    }
}
