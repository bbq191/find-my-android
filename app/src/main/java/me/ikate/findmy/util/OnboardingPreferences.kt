package me.ikate.findmy.util

import android.content.Context
import androidx.core.content.edit

/**
 * 首次启动向导偏好设置管理器
 * 存储用户是否已完成各阶段的引导流程
 */
object OnboardingPreferences {

    private const val PREFS_NAME = "onboarding_prefs"

    // 设置键
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    private const val KEY_ONBOARDING_VERSION = "onboarding_version"
    private const val KEY_LAST_COMPLETED_STEP = "last_completed_step"
    private const val KEY_DEVICE_ADMIN_PROMPTED = "device_admin_prompted"
    private const val KEY_BATTERY_OPTIMIZATION_PROMPTED = "battery_optimization_prompted"
    private const val KEY_PERMISSION_GUIDE_DISMISSED_TIME = "permission_guide_dismissed_time"

    // 当前引导版本（版本升级时可重新触发引导）
    private const val CURRENT_ONBOARDING_VERSION = 1

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 检查是否已完成首次启动向导
     * 如果向导版本更新，也会返回 false
     */
    fun isOnboardingCompleted(context: Context): Boolean {
        val prefs = getPrefs(context)
        val completed = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        val version = prefs.getInt(KEY_ONBOARDING_VERSION, 0)
        return completed && version >= CURRENT_ONBOARDING_VERSION
    }

    /**
     * 标记首次启动向导已完成
     */
    fun setOnboardingCompleted(context: Context) {
        getPrefs(context).edit {
            putBoolean(KEY_ONBOARDING_COMPLETED, true)
            putInt(KEY_ONBOARDING_VERSION, CURRENT_ONBOARDING_VERSION)
        }
    }

    /**
     * 获取上次完成的步骤（用于断点续行）
     */
    fun getLastCompletedStep(context: Context): Int {
        return getPrefs(context).getInt(KEY_LAST_COMPLETED_STEP, 0)
    }

    /**
     * 保存当前完成的步骤
     */
    fun setLastCompletedStep(context: Context, step: Int) {
        getPrefs(context).edit {
            putInt(KEY_LAST_COMPLETED_STEP, step)
        }
    }

    /**
     * 检查是否已提示过设备管理员激活
     */
    fun isDeviceAdminPrompted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DEVICE_ADMIN_PROMPTED, false)
    }

    /**
     * 标记已提示过设备管理员激活
     */
    fun setDeviceAdminPrompted(context: Context) {
        getPrefs(context).edit {
            putBoolean(KEY_DEVICE_ADMIN_PROMPTED, true)
        }
    }

    /**
     * 检查是否已提示过电池优化
     */
    fun isBatteryOptimizationPrompted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BATTERY_OPTIMIZATION_PROMPTED, false)
    }

    /**
     * 标记已提示过电池优化
     */
    fun setBatteryOptimizationPrompted(context: Context) {
        getPrefs(context).edit {
            putBoolean(KEY_BATTERY_OPTIMIZATION_PROMPTED, true)
        }
    }

    /**
     * 获取权限引导对话框上次关闭时间
     * 用于控制提示频率（每天最多一次）
     */
    fun getPermissionGuideDismissedTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_PERMISSION_GUIDE_DISMISSED_TIME, 0)
    }

    /**
     * 记录权限引导对话框关闭时间
     */
    fun setPermissionGuideDismissedTime(context: Context) {
        getPrefs(context).edit {
            putLong(KEY_PERMISSION_GUIDE_DISMISSED_TIME, System.currentTimeMillis())
        }
    }

    /**
     * 检查今天是否已提示过权限引导
     */
    fun isPermissionGuidePromptedToday(context: Context): Boolean {
        val lastTime = getPermissionGuideDismissedTime(context)
        if (lastTime == 0L) return false

        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        return (now - lastTime) < oneDayMs
    }

    /**
     * 重置所有引导状态（用于测试或用户请求）
     */
    fun resetOnboarding(context: Context) {
        getPrefs(context).edit {
            clear()
        }
    }
}
