package me.ikate.findmy.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

/**
 * 设备 ID 提供者
 * 统一管理 Android ID 的获取，避免重复代码
 *
 * Android ID 特性：
 * - 同一设备恢复出厂设置后会变化
 * - 不同应用获取的值相同（Android 8.0 后按应用签名区分）
 * - 适用于设备追踪场景
 */
class DeviceIdProvider(
    private val context: Context
) {
    @Volatile
    private var cachedDeviceId: String? = null

    /**
     * 获取设备 ID（Android ID）
     * 使用缓存避免重复查询 ContentResolver
     */
    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        return cachedDeviceId ?: synchronized(this) {
            cachedDeviceId ?: Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ).also { cachedDeviceId = it }
        }
    }

    companion object {
        @Volatile
        private var instance: DeviceIdProvider? = null

        /**
         * 获取单例实例
         */
        fun getInstance(context: Context): DeviceIdProvider {
            return instance ?: synchronized(this) {
                instance ?: DeviceIdProvider(context.applicationContext).also { instance = it }
            }
        }

        /**
         * 静态方法：获取设备 ID
         */
        @SuppressLint("HardwareIds")
        fun getDeviceId(context: Context): String {
            return getInstance(context).getDeviceId()
        }
    }
}
