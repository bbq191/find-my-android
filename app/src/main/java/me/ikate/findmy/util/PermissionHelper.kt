package me.ikate.findmy.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 权限管理帮助类
 * 统一管理应用所需的各种权限
 */
object PermissionHelper {

    /**
     * 核心权限（必须授予才能使用应用）
     */
    val corePermissions = listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CONTACTS
    )

    /**
     * 高级权限（增强功能，可选）
     */
    val advancedPermissions: List<String>
        get() = buildList {
            // 后台定位权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            // 通知权限 (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

    /**
     * 检查核心权限是否全部授予
     */
    fun hasCorePermissions(context: Context): Boolean {
        return corePermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 检查是否有位置权限
     */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
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
     * 检查是否有通知权限
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 13 以下不需要此权限
        }
    }

    /**
     * 获取下一组需要请求的权限
     * 按优先级顺序：核心权限 -> 高级权限
     */
    fun getNextPermissionGroup(context: Context): List<String> {
        // 首先检查核心权限
        val missingCore = corePermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingCore.isNotEmpty()) {
            return missingCore
        }

        // 然后检查高级权限
        val missingAdvanced = advancedPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        return missingAdvanced
    }

    /**
     * 权限描述（用于 UI 显示）
     */
    fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.ACCESS_COARSE_LOCATION -> "粗略位置"
            Manifest.permission.ACCESS_FINE_LOCATION -> "精确位置"
            Manifest.permission.READ_CONTACTS -> "通讯录"
            Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "后台位置"
            Manifest.permission.POST_NOTIFICATIONS -> "通知"
            else -> permission
        }
    }
}
