package me.ikate.findmy.data.repository

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.ikate.findmy.util.DeviceIdProvider

/**
 * 简化认证仓库
 * 直接使用 Android ID 作为用户标识，无需 Firebase Auth
 *
 * Android ID 特性：
 * - 同一设备恢复出厂设置后会变化
 * - 不同应用获取的值相同（Android 8.0 后按应用签名区分）
 * - 适用于设备追踪场景
 */
class AuthRepository(private val context: Context? = null) {

    companion object {
        private const val TAG = "AuthRepository"

        /**
         * 获取用户 ID（静态方法，便于各处调用）
         * 委托给 DeviceIdProvider 统一管理
         */
        fun getUserId(context: Context): String {
            return DeviceIdProvider.getDeviceId(context)
        }
    }

    // 认证状态（始终为已登录）
    private val _authState = MutableStateFlow(true)

    /**
     * 监听认证状态变化
     * 简化实现：始终返回 true（已登录）
     */
    fun observeAuthState(): Flow<Boolean> = _authState.asStateFlow()

    /**
     * 获取当前用户 ID
     * 直接返回 Android ID
     */
    @SuppressLint("HardwareIds")
    fun getCurrentUserId(): String? {
        return context?.let { getUserId(it) }
    }

    /**
     * 检查是否已登录
     * 简化实现：只要能获取到 Android ID 就算已登录
     */
    fun isSignedIn(): Boolean {
        return getCurrentUserId() != null
    }

    /**
     * 登录（简化实现）
     * 只需要验证能获取到 Android ID
     */
    suspend fun signIn(): Result<String> {
        val userId = getCurrentUserId()
        return if (userId != null) {
            android.util.Log.d(TAG, "用户已就绪: $userId")
            Result.success(userId)
        } else {
            Result.failure(Exception("无法获取设备 ID"))
        }
    }

    /**
     * 登出（空实现）
     * Android ID 无需登出
     */
    fun signOut() {
        android.util.Log.d(TAG, "登出（无操作）")
    }
}
