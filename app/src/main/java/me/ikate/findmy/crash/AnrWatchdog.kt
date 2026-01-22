package me.ikate.findmy.crash

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * ANR 监控器
 *
 * 通过向主线程 Handler post 任务并检测是否及时执行来检测 ANR
 *
 * 工作原理：
 * 1. 在独立线程中定期向主线程 Handler post 一个更新标记的任务
 * 2. 等待指定时间后检查标记是否已更新
 * 3. 如果标记未更新，说明主线程被阻塞（ANR）
 * 4. 收集主线程堆栈信息并保存
 */
class AnrWatchdog(
    private val collector: CrashInfoCollector,
    private val storage: CrashLogStorage,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) : Thread("AnrWatchdog") {

    companion object {
        private const val TAG = "AnrWatchdog"
        private const val DEFAULT_TIMEOUT_MS = 5000L // 5 秒
        private const val CHECK_INTERVAL_MS = 500L
    }

    @Volatile
    private var isWatching = true

    @Volatile
    private var tick = 0L

    @Volatile
    private var reported = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val tickRunnable = Runnable {
        tick = System.currentTimeMillis()
    }

    override fun run() {
        Log.d(TAG, "AnrWatchdog 线程启动")

        while (isWatching && !isInterrupted) {
            // 重置状态
            tick = 0L
            reported = false

            // 向主线程 post 任务
            mainHandler.post(tickRunnable)

            // 等待超时时间
            try {
                sleep(timeoutMs)
            } catch (e: InterruptedException) {
                Log.d(TAG, "AnrWatchdog 被中断")
                break
            }

            // 检查是否 ANR
            if (tick == 0L && !reported) {
                // 主线程没有响应，检测到 ANR
                handleAnr()
            }
        }

        Log.d(TAG, "AnrWatchdog 线程退出")
    }

    /**
     * 处理 ANR
     */
    private fun handleAnr() {
        reported = true
        Log.e(TAG, "检测到 ANR！主线程 ${timeoutMs}ms 无响应")

        try {
            // 获取主线程堆栈
            val mainThread = Looper.getMainLooper().thread
            val stackTrace = mainThread.stackTrace

            // 收集 ANR 信息
            val crashInfo = collector.collectAnr(stackTrace)

            // 保存到本地
            val saved = storage.save(crashInfo)

            if (saved) {
                Log.d(TAG, "ANR 日志已保存: ${crashInfo.crashId}")
            } else {
                Log.e(TAG, "ANR 日志保存失败")
            }

            // 打印所有线程堆栈（用于调试）
            logAllThreads()
        } catch (e: Exception) {
            Log.e(TAG, "处理 ANR 时发生错误", e)
        }
    }

    /**
     * 打印所有线程堆栈
     */
    private fun logAllThreads() {
        Log.d(TAG, "=== 所有线程堆栈 ===")

        Thread.getAllStackTraces().forEach { (thread, stackTrace) ->
            Log.d(TAG, "Thread: ${thread.name} (${thread.state})")
            stackTrace.take(10).forEach { element ->
                Log.d(TAG, "    at $element")
            }
        }
    }

    /**
     * 停止监控
     */
    fun stopWatching() {
        isWatching = false
        interrupt()
    }
}
