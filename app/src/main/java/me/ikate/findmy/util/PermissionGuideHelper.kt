package me.ikate.findmy.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 权限引导帮助类
 * 用于检查和引导用户开启位置共享所需的关键权限
 */
object PermissionGuideHelper {

    /**
     * 检查是否已授予后台定位权限
     */
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 10 以下没有单独的后台定位权限
            true
        }
    }

    /**
     * 检查是否已关闭电池优化
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    /**
     * 检查位置共享所需的所有关键权限
     * @return Pair<是否全部满足, 缺失的权限列表>
     */
    fun checkLocationSharePermissions(context: Context): Pair<Boolean, List<String>> {
        val missingPermissions = mutableListOf<String>()

        if (!hasBackgroundLocationPermission(context)) {
            missingPermissions.add("后台定位")
        }

        if (!isBatteryOptimizationDisabled(context)) {
            missingPermissions.add("电池无限制")
        }

        return Pair(missingPermissions.isEmpty(), missingPermissions)
    }

    /**
     * 打开后台定位权限设置页面
     */
    fun openBackgroundLocationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * 打开电池优化设置页面
     */
    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // 如果上面的 Intent 不支持，打开通用设置页面
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            }
        }
    }

    /**
     * 打开应用设置页面（通用）
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * 获取权限引导文本
     */
    fun getPermissionGuideText(missingPermissions: List<String>): String {
        return buildString {
            append("为了确保位置共享功能正常运行，需要开启以下权限：\n\n")
            missingPermissions.forEachIndexed { index, permission ->
                append("${index + 1}. $permission\n")
            }
            append("\n点击下方按钮前往设置。")
        }
    }

    /**
     * 获取后台定位权限的详细说明
     */
    fun getBackgroundLocationGuide(): String {
        return """
            设置步骤：
            1. 在应用信息页面，找到「权限」
            2. 点击「位置信息」
            3. 选择「始终允许」

            这样应用才能在后台持续共享您的位置。
        """.trimIndent()
    }

    /**
     * 获取电池优化的详细说明
     */
    fun getBatteryOptimizationGuide(): String {
        return """
            设置步骤：
            1. 在电池优化页面，找到「Find My」
            2. 选择「不限制」或「无限制」

            这样可以确保应用在后台持续运行，不会被系统杀死。
        """.trimIndent()
    }
}
