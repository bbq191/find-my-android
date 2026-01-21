package me.ikate.findmy.service

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * 设备管理员接收器
 * 用于丢失模式的设备锁定功能
 *
 * 功能：
 * - 锁定屏幕
 * - 防止被卸载（需要先取消设备管理员权限）
 */
class FindMyDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "FindMyDeviceAdmin"

        /**
         * 获取设备管理员组件名
         */
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, FindMyDeviceAdminReceiver::class.java)
        }

        /**
         * 检查是否已激活设备管理员
         */
        fun isAdminActive(context: Context): Boolean {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as DevicePolicyManager
            return devicePolicyManager.isAdminActive(getComponentName(context))
        }

        /**
         * 请求激活设备管理员
         * 需要在 Activity 中调用
         */
        fun requestActivation(context: Context): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "启用设备管理员后，可在设备丢失时远程锁定屏幕，防止他人使用您的设备。"
                )
            }
        }

        /**
         * 锁定屏幕
         */
        fun lockScreen(context: Context): Boolean {
            if (!isAdminActive(context)) {
                Log.w(TAG, "设备管理员未激活，无法锁屏")
                return false
            }

            return try {
                val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as DevicePolicyManager
                devicePolicyManager.lockNow()
                Log.d(TAG, "屏幕已锁定")
                true
            } catch (e: Exception) {
                Log.e(TAG, "锁屏失败", e)
                false
            }
        }

        /**
         * 重置密码（可选，用于设置临时锁屏密码）
         * 注意：Android 8.0+ 此功能受限
         */
        fun resetPassword(context: Context, password: String): Boolean {
            if (!isAdminActive(context)) {
                Log.w(TAG, "设备管理员未激活，无法重置密码")
                return false
            }

            return try {
                val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as DevicePolicyManager
                // Android 8.0+ 已弃用此方法，仅对无密码设备有效
                @Suppress("DEPRECATION")
                devicePolicyManager.resetPassword(password, 0)
                Log.d(TAG, "密码已重置")
                true
            } catch (e: Exception) {
                Log.e(TAG, "重置密码失败", e)
                false
            }
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "设备管理员已启用")
        Toast.makeText(context, "设备管理员已启用", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "设备管理员已禁用")
        Toast.makeText(context, "设备管理员已禁用", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // 当用户尝试禁用设备管理员时显示警告
        return "禁用后将无法使用远程锁定功能。如果设备处于丢失模式，请先解除丢失模式。"
    }

    @Deprecated("Deprecated in DeviceAdminReceiver")
    override fun onPasswordFailed(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        super.onPasswordFailed(context, intent)
        Log.d(TAG, "密码输入错误")
    }

    @Deprecated("Deprecated in DeviceAdminReceiver")
    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        super.onPasswordSucceeded(context, intent)
        Log.d(TAG, "密码输入正确")
    }
}
