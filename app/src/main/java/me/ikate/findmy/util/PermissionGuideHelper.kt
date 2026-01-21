package me.ikate.findmy.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
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
     * 检查 WiFi 扫描是否开启
     * WiFi 扫描用于辅助定位，即使 WiFi 关闭也能扫描附近热点来确定位置
     *
     * 注意：这是系统级设置，位于 设置 → 位置信息 → 提高精确度 → WiFi 扫描
     */
    @Suppress("DEPRECATION")
    fun isWifiScanningEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            try {
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiManager.isScanAlwaysAvailable
            } catch (e: Exception) {
                // 如果无法获取状态，假设已开启（避免误报）
                true
            }
        } else {
            true
        }
    }

    /**
     * 检查蓝牙扫描是否开启
     * 蓝牙扫描用于辅助室内定位（蓝牙信标），即使蓝牙关闭也能扫描
     *
     * 注意：这是系统级设置，位于 设置 → 位置信息 → 提高精确度 → 蓝牙扫描
     * Android 没有直接的 API 来检测蓝牙扫描设置，通过 ContentResolver 读取系统设置
     */
    fun isBluetoothScanningEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            try {
                // 通过 Settings.Global 读取蓝牙扫描设置
                // ble_scan_always_enabled: 1 = 开启, 0 = 关闭
                val bleScanAlways = Settings.Global.getInt(
                    context.contentResolver,
                    "ble_scan_always_enabled",
                    1 // 默认假设开启
                )
                bleScanAlways == 1
            } catch (e: Exception) {
                // 如果无法获取状态，假设已开启（避免误报）
                true
            }
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

        if (!isWifiScanningEnabled(context)) {
            missingPermissions.add("WiFi扫描")
        }

        if (!isBluetoothScanningEnabled(context)) {
            missingPermissions.add("蓝牙扫描")
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
     * 打开 WiFi 扫描设置页面
     * 路径：设置 → 位置信息 → 提高精确度 → WiFi 扫描
     */
    fun openWifiScanningSettings(context: Context) {
        openLocationScanningSettings(context)
    }

    /**
     * 打开蓝牙扫描设置页面
     * 路径：设置 → 位置信息 → 提高精确度 → 蓝牙扫描
     * 注意：WiFi 扫描和蓝牙扫描在同一个设置页面
     */
    fun openBluetoothScanningSettings(context: Context) {
        openLocationScanningSettings(context)
    }

    /**
     * 打开位置扫描设置页面（WiFi 扫描和蓝牙扫描）
     * 路径：设置 → 位置信息 → 提高精确度
     */
    private fun openLocationScanningSettings(context: Context) {
        try {
            // 尝试直接打开位置信息扫描设置（Android 10+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // ACTION_LOCATION_SCANNING_SETTINGS 的字符串值
                val intent = Intent("android.settings.LOCATION_SCANNING_SETTINGS").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } else {
                // 旧版本打开位置信息设置
                openLocationSettings(context)
            }
        } catch (e: Exception) {
            // 如果失败，回退到位置信息设置
            openLocationSettings(context)
        }
    }

    /**
     * 打开位置信息设置页面
     */
    fun openLocationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // 如果失败，打开应用设置
            openAppSettings(context)
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
