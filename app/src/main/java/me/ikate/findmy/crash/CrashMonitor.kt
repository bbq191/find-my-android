package me.ikate.findmy.crash

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import me.ikate.findmy.data.repository.AuthRepository

/**
 * 崩溃监控入口
 *
 * 统一管理崩溃监控系统的初始化和配置：
 * - CrashHandler: 捕获未处理异常
 * - CrashInfoCollector: 收集崩溃信息
 * - CrashLogStorage: 本地日志存储
 * - CrashCounter: 崩溃计数（防止循环崩溃）
 * - AnrWatchdog: ANR 监控（Phase 3）
 */
object CrashMonitor {

    private const val TAG = "CrashMonitor"

    private var isInitialized = false
    private var crashInfoCollector: CrashInfoCollector? = null
    private var crashLogStorage: CrashLogStorage? = null
    private var crashCounter: CrashCounter? = null
    private var anrWatchdog: AnrWatchdog? = null

    /**
     * 初始化崩溃监控系统
     *
     * @param application Application 实例
     */
    fun init(application: Application) {
        if (isInitialized) {
            Log.w(TAG, "CrashMonitor 已初始化")
            return
        }

        Log.d(TAG, "开始初始化崩溃监控系统...")

        // 初始化各组件
        crashInfoCollector = CrashInfoCollector(application)
        crashLogStorage = CrashLogStorage(application)
        crashCounter = CrashCounter(application)

        // 检查是否处于崩溃循环
        if (crashCounter?.isInCrashLoop() == true) {
            Log.w(TAG, "检测到崩溃循环，进入安全模式")
            // TODO: 可以在这里禁用部分功能或显示恢复界面
        }

        // 初始化 CrashHandler
        CrashHandler.getInstance().init(
            ctx = application,
            collector = crashInfoCollector!!,
            storage = crashLogStorage!!,
            counter = crashCounter
        )

        // 注册 Activity 生命周期回调，跟踪前后台状态
        registerActivityLifecycleCallbacks(application)

        isInitialized = true
        Log.d(TAG, "崩溃监控系统初始化完成")
    }

    /**
     * 启动 ANR 监控
     * 在 Application 初始化完成后调用
     */
    fun startAnrWatchdog(context: Context) {
        if (anrWatchdog != null) {
            Log.w(TAG, "AnrWatchdog 已启动")
            return
        }

        anrWatchdog = AnrWatchdog(
            collector = crashInfoCollector ?: return,
            storage = crashLogStorage ?: return
        ).also {
            it.start()
        }

        Log.d(TAG, "AnrWatchdog 已启动")
    }

    /**
     * 停止 ANR 监控
     */
    fun stopAnrWatchdog() {
        anrWatchdog?.stopWatching()
        anrWatchdog = null
        Log.d(TAG, "AnrWatchdog 已停止")
    }

    /**
     * 设置当前用户 ID
     * 用于崩溃日志关联用户
     */
    fun setUserId(userId: String?) {
        crashInfoCollector?.userId = userId
    }

    /**
     * 获取待上传的崩溃日志数量
     */
    fun getPendingLogCount(): Int {
        return crashLogStorage?.getPendingLogs()?.size ?: 0
    }

    /**
     * 获取崩溃日志存储器
     */
    fun getStorage(): CrashLogStorage? = crashLogStorage

    /**
     * 获取崩溃计数器
     */
    fun getCounter(): CrashCounter? = crashCounter

    /**
     * 检查是否处于崩溃循环
     */
    fun isInCrashLoop(): Boolean {
        return crashCounter?.isInCrashLoop() == true
    }

    /**
     * 重置崩溃计数
     */
    fun resetCrashCount() {
        crashCounter?.reset()
    }

    /**
     * 清空所有崩溃日志
     */
    fun clearAllLogs() {
        crashLogStorage?.clearAll()
    }

    /**
     * 触发测试崩溃（仅用于调试）
     */
    fun testCrash() {
        CrashHandler.getInstance().testCrash()
    }

    /**
     * 注册 Activity 生命周期回调
     * 用于跟踪当前 Activity 和前后台状态
     */
    private fun registerActivityLifecycleCallbacks(application: Application) {
        var activityCount = 0

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                activityCount++
                crashInfoCollector?.isForeground = true
            }

            override fun onActivityResumed(activity: Activity) {
                crashInfoCollector?.currentActivityName = activity.javaClass.simpleName
            }

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                activityCount--
                if (activityCount == 0) {
                    crashInfoCollector?.isForeground = false
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
