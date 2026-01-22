package me.ikate.findmy.crash

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.widget.Toast
import kotlin.system.exitProcess

/**
 * 全局未捕获异常处理器
 *
 * 功能：
 * 1. 捕获所有未处理的异常
 * 2. 收集崩溃信息并保存到本地
 * 3. 避免系统"应用已停止运行"弹窗
 * 4. 优雅退出应用
 */
class CrashHandler private constructor() : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"
        private const val EXIT_DELAY_MS = 1500L

        @Volatile
        private var instance: CrashHandler? = null

        fun getInstance(): CrashHandler {
            return instance ?: synchronized(this) {
                instance ?: CrashHandler().also { instance = it }
            }
        }
    }

    private var context: Context? = null
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var crashInfoCollector: CrashInfoCollector? = null
    private var crashLogStorage: CrashLogStorage? = null
    private var crashCounter: CrashCounter? = null
    private var isInitialized = false

    // 主线程 Handler，用于显示 Toast
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 初始化崩溃处理器
     *
     * @param ctx 应用上下文
     * @param collector 信息收集器
     * @param storage 日志存储器
     * @param counter 崩溃计数器（可选，Phase 3 实现）
     */
    fun init(
        ctx: Context,
        collector: CrashInfoCollector,
        storage: CrashLogStorage,
        counter: CrashCounter? = null
    ) {
        if (isInitialized) {
            Log.w(TAG, "CrashHandler 已初始化，跳过重复初始化")
            return
        }

        context = ctx.applicationContext
        crashInfoCollector = collector
        crashLogStorage = storage
        crashCounter = counter

        // 保存系统默认处理器
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        // 设置自己为全局处理器
        Thread.setDefaultUncaughtExceptionHandler(this)

        isInitialized = true
        Log.d(TAG, "CrashHandler 初始化完成")
    }

    /**
     * 处理未捕获异常
     */
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e(TAG, "捕获到未处理异常: ${throwable.javaClass.name}", throwable)

        val handled = try {
            handleException(thread, throwable)
        } catch (e: Exception) {
            Log.e(TAG, "处理异常时发生错误", e)
            false
        }

        if (!handled && defaultHandler != null) {
            // 如果处理失败，交给系统默认处理器
            Log.d(TAG, "处理失败，交给系统默认处理器")
            defaultHandler?.uncaughtException(thread, throwable)
        } else {
            // 优雅退出
            gracefulExit()
        }
    }

    /**
     * 处理异常
     *
     * @return 是否成功处理
     */
    private fun handleException(thread: Thread, throwable: Throwable): Boolean {
        val collector = crashInfoCollector ?: return false
        val storage = crashLogStorage ?: return false

        // 1. 收集崩溃信息
        val crashInfo = collector.collect(
            throwable = throwable,
            threadName = thread.name
        )

        // 2. 保存到本地
        val saved = storage.save(crashInfo)
        if (!saved) {
            Log.e(TAG, "崩溃日志保存失败")
        }

        // 3. 更新崩溃计数器
        crashCounter?.increment()

        // 4. 在主线程显示 Toast（不阻塞当前线程）
        showCrashToast()

        Log.d(TAG, "崩溃处理完成: crashId=${crashInfo.crashId}")
        return true
    }

    /**
     * 显示崩溃提示 Toast
     */
    private fun showCrashToast() {
        val ctx = context ?: return

        mainHandler.post {
            try {
                Toast.makeText(
                    ctx,
                    "很抱歉，程序出现异常即将退出",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                // 忽略 Toast 显示失败
                Log.w(TAG, "显示 Toast 失败", e)
            }
        }
    }

    /**
     * 优雅退出应用
     */
    private fun gracefulExit() {
        try {
            // 等待一段时间确保日志写入完成和 Toast 显示
            Thread.sleep(EXIT_DELAY_MS)
        } catch (e: InterruptedException) {
            // 忽略中断
        }

        // 杀掉进程
        Process.killProcess(Process.myPid())
        exitProcess(1)
    }

    /**
     * 手动触发崩溃（仅用于测试）
     */
    fun testCrash() {
        throw RuntimeException("Test crash triggered by CrashHandler.testCrash()")
    }
}
