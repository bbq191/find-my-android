package me.ikate.findmy.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * 权限状态检查器
 * 检查应用所需的各项权限是否已授予
 */
object PermissionStatusChecker {

    /**
     * 权限状态数据类
     */
    data class PermissionStatus(
        val hasLocationPermission: Boolean,
        val hasBackgroundLocationPermission: Boolean,
        val hasBatteryOptimizationDisabled: Boolean,
        val hasNotificationPermission: Boolean,
        val hasDeviceAdminActive: Boolean,
        val missingCriticalPermissions: List<String>,
        val missingOptionalPermissions: List<String>
    ) {
        /**
         * 是否所有必要权限都已授予
         */
        val hasCriticalPermissions: Boolean
            get() = hasLocationPermission && hasBackgroundLocationPermission

        /**
         * 是否所有权限都已授予
         */
        val hasAllPermissions: Boolean
            get() = hasLocationPermission &&
                    hasBackgroundLocationPermission &&
                    hasBatteryOptimizationDisabled &&
                    hasNotificationPermission &&
                    hasDeviceAdminActive

        /**
         * 是否需要显示权限提示
         */
        val shouldShowPermissionPrompt: Boolean
            get() = !hasCriticalPermissions || missingOptionalPermissions.isNotEmpty()
    }

    /**
     * 检查所有权限状态
     */
    fun checkAllPermissions(context: Context): PermissionStatus {
        val hasLocation = hasLocationPermission(context)
        val hasBackgroundLocation = hasBackgroundLocationPermission(context)
        val hasBatteryOptimization = isBatteryOptimizationDisabled(context)
        val hasNotification = hasNotificationPermission(context)
        val hasDeviceAdmin = DeviceAdminHelper.isAdminActive(context)

        val missingCritical = mutableListOf<String>()
        val missingOptional = mutableListOf<String>()

        if (!hasLocation) missingCritical.add("位置权限")
        if (!hasBackgroundLocation) missingCritical.add("后台定位")
        if (!hasBatteryOptimization) missingOptional.add("电池优化")
        if (!hasNotification) missingOptional.add("通知权限")
        if (!hasDeviceAdmin) missingOptional.add("设备管理员")

        return PermissionStatus(
            hasLocationPermission = hasLocation,
            hasBackgroundLocationPermission = hasBackgroundLocation,
            hasBatteryOptimizationDisabled = hasBatteryOptimization,
            hasNotificationPermission = hasNotification,
            hasDeviceAdminActive = hasDeviceAdmin,
            missingCriticalPermissions = missingCritical,
            missingOptionalPermissions = missingOptional
        )
    }

    /**
     * 检查是否有位置权限（精准定位）
     */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查是否有后台定位权限
     */
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 10 以下不需要单独的后台定位权限
            hasLocationPermission(context)
        }
    }

    /**
     * 检查电池优化是否已关闭
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 检查是否有通知权限
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 13 以下默认有通知权限
            true
        }
    }

    /**
     * 检查是否应该显示权限恢复提示
     * 考虑用户上次关闭提示的时间，避免频繁打扰
     */
    fun shouldShowPermissionRecoveryPrompt(context: Context): Boolean {
        val status = checkAllPermissions(context)

        // 如果所有必要权限都已授予，不需要提示
        if (status.hasCriticalPermissions) {
            return false
        }

        // 检查是否今天已经提示过
        if (OnboardingPreferences.isPermissionGuidePromptedToday(context)) {
            return false
        }

        return true
    }

    /**
     * 记录权限提示已显示
     */
    fun markPermissionPromptShown(context: Context) {
        OnboardingPreferences.setPermissionGuideDismissedTime(context)
    }

    /**
     * 获取缺失权限的描述文本
     */
    fun getMissingPermissionsDescription(status: PermissionStatus): String {
        val missing = status.missingCriticalPermissions + status.missingOptionalPermissions
        return when {
            missing.isEmpty() -> "所有权限已授予"
            missing.size == 1 -> "缺少${missing[0]}"
            else -> "缺少 ${missing.joinToString("、")}"
        }
    }
}
