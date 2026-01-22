package me.ikate.findmy.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import me.ikate.findmy.service.FindMyDeviceAdminReceiver

/**
 * 设备管理员功能封装类
 * 提供企业级 MDM 功能的统一接口
 *
 * 功能：
 * - 检查/请求设备管理员激活
 * - 远程锁定设备
 * - 远程擦除数据（恢复出厂设置）
 * - 密码管理
 */
object DeviceAdminHelper {

    private const val TAG = "DeviceAdminHelper"

    /**
     * 获取 DevicePolicyManager 实例
     */
    private fun getDevicePolicyManager(context: Context): DevicePolicyManager {
        return context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    /**
     * 获取设备管理员组件名
     */
    fun getComponentName(context: Context): ComponentName {
        return FindMyDeviceAdminReceiver.getComponentName(context)
    }

    /**
     * 检查设备管理员是否已激活
     */
    fun isAdminActive(context: Context): Boolean {
        return try {
            getDevicePolicyManager(context).isAdminActive(getComponentName(context))
        } catch (e: Exception) {
            Log.e(TAG, "检查设备管理员状态失败", e)
            false
        }
    }

    /**
     * 创建请求激活设备管理员的 Intent
     * 需要在 Activity 中使用 startActivityForResult 调用
     */
    fun createActivationIntent(context: Context): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                buildString {
                    appendLine("启用设备管理员后，Find My 应用可以：")
                    appendLine()
                    appendLine("• 远程锁定设备 - 防止他人使用您的手机")
                    appendLine("• 远程擦除数据 - 在无法找回时保护隐私")
                    appendLine()
                    appendLine("⚠️ 注意：激活后需要在设置中取消激活才能卸载应用")
                }
            )
        }
    }

    /**
     * 创建跳转到设备管理员设置页面的 Intent
     * 用于取消激活设备管理员
     */
    fun createDeactivationIntent(context: Context): Intent {
        return Intent().apply {
            action = "android.settings.SECURITY_SETTINGS"
        }
    }

    /**
     * 锁定设备屏幕
     *
     * @return true 如果锁定成功，false 如果设备管理员未激活或锁定失败
     */
    fun lockScreen(context: Context): Boolean {
        if (!isAdminActive(context)) {
            Log.w(TAG, "设备管理员未激活，无法锁屏")
            return false
        }

        return try {
            getDevicePolicyManager(context).lockNow()
            Log.d(TAG, "设备已锁定")
            true
        } catch (e: Exception) {
            Log.e(TAG, "锁定设备失败", e)
            false
        }
    }

    /**
     * 擦除设备数据（恢复出厂设置）
     *
     * ⚠️ 警告：此操作不可逆，将删除设备上所有数据！
     *
     * @param reason 擦除原因（用于日志记录）
     * @param wipeExternalStorage 是否同时擦除外部存储
     * @return true 如果擦除命令已发送，false 如果设备管理员未激活或操作失败
     */
    fun wipeData(
        context: Context,
        reason: String = "Remote wipe requested",
        wipeExternalStorage: Boolean = true
    ): Boolean {
        if (!isAdminActive(context)) {
            Log.w(TAG, "设备管理员未激活，无法擦除数据")
            return false
        }

        return try {
            val dpm = getDevicePolicyManager(context)
            var flags = 0

            if (wipeExternalStorage) {
                flags = flags or DevicePolicyManager.WIPE_EXTERNAL_STORAGE
            }

            // Android 9+ 支持擦除恢复保护数据
            flags = flags or DevicePolicyManager.WIPE_RESET_PROTECTION_DATA

            Log.w(TAG, "执行设备擦除: reason=$reason, flags=$flags")
            dpm.wipeData(flags, reason)
            true
        } catch (e: Exception) {
            Log.e(TAG, "擦除设备数据失败", e)
            false
        }
    }

    /**
     * 获取密码输入失败次数
     */
    fun getFailedPasswordAttempts(context: Context): Int {
        if (!isAdminActive(context)) {
            return 0
        }

        return try {
            getDevicePolicyManager(context).currentFailedPasswordAttempts
        } catch (e: Exception) {
            Log.e(TAG, "获取密码失败次数失败", e)
            0
        }
    }

    /**
     * 设置密码输入失败后自动擦除的阈值
     *
     * @param maxAttempts 最大失败次数，设为 0 禁用
     */
    fun setMaxFailedPasswordsForWipe(context: Context, maxAttempts: Int): Boolean {
        if (!isAdminActive(context)) {
            Log.w(TAG, "设备管理员未激活，无法设置密码失败阈值")
            return false
        }

        return try {
            getDevicePolicyManager(context).setMaximumFailedPasswordsForWipe(
                getComponentName(context),
                maxAttempts
            )
            Log.d(TAG, "密码失败擦除阈值已设置为: $maxAttempts")
            true
        } catch (e: Exception) {
            Log.e(TAG, "设置密码失败阈值失败", e)
            false
        }
    }

    /**
     * 设备管理员功能状态
     */
    data class AdminStatus(
        val isActive: Boolean,
        val canLock: Boolean,
        val canWipe: Boolean,
        val failedPasswordAttempts: Int
    )

    /**
     * 获取设备管理员功能状态
     */
    fun getAdminStatus(context: Context): AdminStatus {
        val isActive = isAdminActive(context)
        return AdminStatus(
            isActive = isActive,
            canLock = isActive,
            canWipe = isActive,
            failedPasswordAttempts = if (isActive) getFailedPasswordAttempts(context) else 0
        )
    }
}
