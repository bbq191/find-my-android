package me.ikate.findmy.push

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import me.ikate.findmy.data.repository.AuthRepository
import me.ikate.findmy.service.MqttForegroundService
import me.ikate.findmy.util.DeviceIdProvider

/**
 * FCM 推送管理器
 * 负责 Token 管理、主题订阅等
 *
 * Token 注册流程:
 * 1. 保存到本地 SharedPreferences
 * 2. 通过 MQTT 上报（用于服务端记录）
 * 3. 通过 Cloud Functions HTTP API 注册（用于 FCM 推送）
 */
object FCMManager {

    private const val TAG = "FCMManager"
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * 获取当前 FCM Token
     * 如果 Token 不存在，会自动请求新 Token
     *
     * @return FCM Token，失败返回 null
     */
    suspend fun getToken(): String? {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "FCM Token: $token")
            token
        } catch (e: Exception) {
            Log.e(TAG, "获取 FCM Token 失败", e)
            null
        }
    }

    /**
     * 同步获取当前 FCM Token（使用回调）
     */
    fun getTokenAsync(onComplete: (String?) -> Unit) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d(TAG, "FCM Token: $token")
                onComplete(token)
            } else {
                Log.e(TAG, "获取 FCM Token 失败", task.exception)
                onComplete(null)
            }
        }
    }

    /**
     * 删除当前 Token（用于用户登出等场景）
     */
    suspend fun deleteToken() {
        try {
            FirebaseMessaging.getInstance().deleteToken().await()
            Log.d(TAG, "FCM Token 已删除")
        } catch (e: Exception) {
            Log.e(TAG, "删除 FCM Token 失败", e)
        }
    }

    /**
     * 订阅主题
     *
     * @param topic 主题名称
     */
    suspend fun subscribeToTopic(topic: String) {
        try {
            FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
            Log.d(TAG, "已订阅主题: $topic")
        } catch (e: Exception) {
            Log.e(TAG, "订阅主题失败: $topic", e)
        }
    }

    /**
     * 取消订阅主题
     *
     * @param topic 主题名称
     */
    suspend fun unsubscribeFromTopic(topic: String) {
        try {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
            Log.d(TAG, "已取消订阅主题: $topic")
        } catch (e: Exception) {
            Log.e(TAG, "取消订阅主题失败: $topic", e)
        }
    }

    /**
     * 保存 FCM Token 到本地
     */
    fun saveToken(context: Context, token: String) {
        Log.d(TAG, "保存 FCM Token: $token")
        context.getSharedPreferences(FCMConfig.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(FCMConfig.KEY_FCM_TOKEN, token)
            .apply()
    }

    /**
     * 获取本地保存的 FCM Token
     */
    fun getSavedToken(context: Context): String? {
        return context.getSharedPreferences(FCMConfig.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(FCMConfig.KEY_FCM_TOKEN, null)
    }

    /**
     * 启用/禁用自动初始化
     * 用于隐私合规场景：在用户同意前禁用自动初始化
     */
    fun setAutoInitEnabled(enabled: Boolean) {
        FirebaseMessaging.getInstance().isAutoInitEnabled = enabled
        Log.d(TAG, "FCM 自动初始化: ${if (enabled) "启用" else "禁用"}")
    }

    /**
     * 注册 Token 到所有后端服务
     * 包括：本地存储、MQTT、Cloud Functions
     *
     * @param context Android Context
     * @param token FCM Token
     */
    fun registerTokenToServer(context: Context, token: String) {
        Log.d(TAG, "注册 FCM Token 到服务器")

        // 1. 保存到本地
        saveToken(context, token)

        // 2. 通过 MQTT 上报
        try {
            MqttForegroundService.reportFcmToken(context, token)
            Log.d(TAG, "Token 已通过 MQTT 上报")
        } catch (e: Exception) {
            Log.w(TAG, "MQTT Token 上报失败", e)
        }

        // 3. 注册到 Cloud Functions
        scope.launch {
            registerTokenToCloudFunctions(context, token)
        }
    }

    /**
     * 注册 Token 到 Cloud Functions
     * 同时关联用户 UID，让服务端可以通过 UID 查找 Token
     */
    private suspend fun registerTokenToCloudFunctions(context: Context, token: String) {
        try {
            // 获取设备唯一标识
            val deviceId = getDeviceId(context)

            // 获取当前用户 UID（用于关联 Token）
            val uid = AuthRepository.getUserId(context)

            val result = PushWebhookService.registerToken(
                context = context,
                deviceId = deviceId,
                fcmToken = token,
                uid = uid
            )

            result.fold(
                onSuccess = {
                    Log.d(TAG, "Token 已注册到 Cloud Functions (uid=$uid)")
                },
                onFailure = { e ->
                    Log.w(TAG, "Cloud Functions Token 注册失败: ${e.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "注册 Token 到 Cloud Functions 异常", e)
        }
    }

    /**
     * 获取并注册当前 Token
     * 在应用启动时调用
     */
    suspend fun fetchAndRegisterToken(context: Context) {
        try {
            val token = getToken()
            if (token != null) {
                registerTokenToServer(context, token)
            } else {
                Log.w(TAG, "无法获取 FCM Token")
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取并注册 Token 失败", e)
        }
    }

    /**
     * 获取设备唯一标识
     * 用于 Cloud Functions Token 注册
     * 委托给 DeviceIdProvider 统一管理
     */
    fun getDeviceId(context: Context): String {
        return DeviceIdProvider.getDeviceId(context)
    }
}
