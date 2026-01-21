package me.ikate.findmy.util

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.WindowManager
import java.lang.ref.WeakReference

/**
 * 刷新率控制管理器
 *
 * 在支持高刷新率的设备上（如三星 Galaxy S24 Ultra 的 120Hz 屏幕），
 * 根据应用状态动态调整屏幕刷新率：
 * - 追踪模式：请求 120Hz 以获得流畅的动画效果
 * - 空闲状态：使用系统自适应模式以节省电量
 * - 省电模式：强制 60Hz 以延长续航
 */
class RefreshRateManager(activity: Activity) {

    companion object {
        private const val TAG = "RefreshRateManager"

        // 常用刷新率
        const val REFRESH_RATE_60HZ = 60f
        const val REFRESH_RATE_90HZ = 90f
        const val REFRESH_RATE_120HZ = 120f
        const val REFRESH_RATE_ADAPTIVE = 0f  // 系统自适应
    }

    private val activityRef = WeakReference(activity)

    // 当前刷新率模式
    private var currentMode: RefreshMode = RefreshMode.ADAPTIVE

    /**
     * 刷新率模式
     */
    enum class RefreshMode {
        HIGH,       // 高刷新率（120Hz）
        STANDARD,   // 标准刷新率（60Hz）
        ADAPTIVE    // 系统自适应
    }

    /**
     * 请求高刷新率模式
     *
     * 适用场景：
     * - 追踪联系人/设备位置时
     * - 地图动画播放时
     * - 需要流畅视觉体验时
     */
    fun requestHighRefreshRate() {
        if (!SamsungDeviceDetector.supports120Hz()) {
            Log.d(TAG, "设备不支持 120Hz，忽略高刷请求")
            return
        }

        setPreferredRefreshRate(REFRESH_RATE_120HZ)
        currentMode = RefreshMode.HIGH
        Log.d(TAG, "已请求 120Hz 高刷新率")
    }

    /**
     * 设置系统自适应模式
     *
     * 适用场景：
     * - 停止追踪时
     * - 应用进入空闲状态时
     * - 用户未主动操作时
     */
    fun setAdaptiveMode() {
        setPreferredRefreshRate(REFRESH_RATE_ADAPTIVE)
        currentMode = RefreshMode.ADAPTIVE
        Log.d(TAG, "已切换到自适应刷新率模式")
    }

    /**
     * 请求标准刷新率模式（60Hz）
     *
     * 适用场景：
     * - 省电模式下
     * - 电量较低时
     */
    fun requestStandardRefreshRate() {
        setPreferredRefreshRate(REFRESH_RATE_60HZ)
        currentMode = RefreshMode.STANDARD
        Log.d(TAG, "已请求 60Hz 标准刷新率")
    }

    /**
     * 获取当前刷新率模式
     */
    fun getCurrentMode(): RefreshMode = currentMode

    /**
     * 检查是否处于高刷新率模式
     */
    fun isHighRefreshRate(): Boolean = currentMode == RefreshMode.HIGH

    /**
     * 设置首选刷新率
     *
     * @param refreshRate 目标刷新率，0 表示系统自适应
     */
    private fun setPreferredRefreshRate(refreshRate: Float) {
        val activity = activityRef.get() ?: run {
            Log.w(TAG, "Activity 已被回收，无法设置刷新率")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 使用 preferredRefreshRate
                activity.window.attributes = activity.window.attributes.apply {
                    preferredRefreshRate = refreshRate
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0-10 使用 preferredDisplayModeId
                setPreferredDisplayModeByRefreshRate(activity, refreshRate)
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置刷新率失败", e)
        }
    }

    /**
     * 通过 Display Mode 设置刷新率（Android 6.0-10）
     */
    @Suppress("DEPRECATION")
    private fun setPreferredDisplayModeByRefreshRate(activity: Activity, targetRate: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        try {
            val display = activity.windowManager.defaultDisplay
            val supportedModes = display.supportedModes

            if (targetRate == REFRESH_RATE_ADAPTIVE) {
                // 自适应模式：清除首选模式
                activity.window.attributes = activity.window.attributes.apply {
                    preferredDisplayModeId = 0
                }
                return
            }

            // 查找匹配的显示模式
            val targetMode = supportedModes.find { mode ->
                kotlin.math.abs(mode.refreshRate - targetRate) < 1f
            } ?: supportedModes.maxByOrNull { it.refreshRate }

            targetMode?.let { mode ->
                activity.window.attributes = activity.window.attributes.apply {
                    preferredDisplayModeId = mode.modeId
                }
                Log.d(TAG, "已设置显示模式: ${mode.modeId} (${mode.refreshRate}Hz)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "通过 DisplayMode 设置刷新率失败", e)
        }
    }

    /**
     * 获取设备支持的最高刷新率
     */
    fun getMaxSupportedRefreshRate(): Float {
        val activity = activityRef.get() ?: return REFRESH_RATE_60HZ

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: REFRESH_RATE_60HZ
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay.supportedModes.maxOfOrNull { it.refreshRate }
                    ?: REFRESH_RATE_60HZ
            } else {
                REFRESH_RATE_60HZ
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取最高刷新率失败", e)
            REFRESH_RATE_60HZ
        }
    }

    /**
     * 获取当前实际刷新率
     */
    fun getCurrentRefreshRate(): Float {
        val activity = activityRef.get() ?: return REFRESH_RATE_60HZ

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display?.refreshRate ?: REFRESH_RATE_60HZ
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay.refreshRate
            }
        } catch (e: Exception) {
            Log.w(TAG, "获取当前刷新率失败", e)
            REFRESH_RATE_60HZ
        }
    }

    /**
     * 获取刷新率管理器状态摘要
     */
    fun getStatusSummary(): String {
        return buildString {
            appendLine("=== Refresh Rate Manager ===")
            appendLine("Current Mode: $currentMode")
            appendLine("Max Supported: ${getMaxSupportedRefreshRate()}Hz")
            appendLine("Current Rate: ${getCurrentRefreshRate()}Hz")
            appendLine("Supports 120Hz: ${SamsungDeviceDetector.supports120Hz()}")
        }
    }
}
