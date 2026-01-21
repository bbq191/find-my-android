package me.ikate.findmy.push

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ikate.findmy.BuildConfig
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Firebase Cloud Functions HTTP API 服务
 * 用于调用 FCM 推送相关的 Cloud Functions
 *
 * API 端点:
 * - POST /registerToken - 注册 FCM Token
 * - POST /sendPush - 发送推送通知
 * - POST /sendPushBatch - 批量发送推送
 * - GET  /tokenStatus - 查询 Token 状态
 * - GET  /health - 健康检查
 */
object PushWebhookService {

    private const val TAG = "PushWebhookService"
    private val gson = Gson()

    /**
     * Cloud Functions 基础 URL
     * 格式: https://asia-northeast1-{project-id}.cloudfunctions.net
     */
    private val baseUrl: String
        get() = BuildConfig.FIREBASE_FUNCTIONS_URL

    /**
     * 检查是否已配置
     */
    fun isConfigured(): Boolean {
        return baseUrl.isNotBlank() && baseUrl.startsWith("http")
    }

    // ========================================================================
    // 请求类型常量
    // ========================================================================

    object RequestType {
        const val SINGLE = "single"
        const val CONTINUOUS = "continuous"
        const val STOP_CONTINUOUS = "stop_continuous"
        const val HEARTBEAT = "heartbeat"
        const val PLAY_SOUND = "play_sound"
        const val STOP_SOUND = "stop_sound"
        const val ENABLE_LOST_MODE = "enable_lost_mode"
        const val DISABLE_LOST_MODE = "disable_lost_mode"
        // 共享邀请相关
        const val SHARE_REQUEST = "share_request"      // 共享邀请
        const val SHARE_ACCEPTED = "share_accepted"    // 邀请被接受
        const val SHARE_REJECTED = "share_rejected"    // 邀请被拒绝
        const val SHARE_REMOVED = "share_removed"      // 联系人被移除
        const val SHARE_PAUSED = "share_paused"        // 共享已暂停
        const val SHARE_RESUMED = "share_resumed"      // 共享已恢复
        const val SHARE_EXPIRED = "share_expired"      // 共享已过期（特殊的暂停）
    }

    // ========================================================================
    // 数据类
    // ========================================================================

    /**
     * Token 注册请求
     * 包含用户 UID，让服务端可以通过 UID 查找 Token
     */
    data class TokenRegistrationRequest(
        @SerializedName("deviceId") val deviceId: String,
        @SerializedName("fcmToken") val fcmToken: String,
        @SerializedName("uid") val uid: String? = null,  // 用户 UID，用于通过 UID 查找 Token
        @SerializedName("platform") val platform: String = "android",
        @SerializedName("appVersion") val appVersion: String? = null
    )

    /**
     * 推送请求
     * 使用 targetUid 让服务端根据 UID 查找对应的 FCM Token
     */
    data class PushRequest(
        @SerializedName("targetUid") val targetUid: String,  // 目标用户 UID，服务端根据此查找 FCM Token
        @SerializedName("type") val type: String,
        @SerializedName("requesterId") val requesterId: String? = null,
        @SerializedName("message") val message: String? = null,
        @SerializedName("phoneNumber") val phoneNumber: String? = null,
        @SerializedName("playSound") val playSound: Boolean? = null,
        // 共享邀请相关字段
        @SerializedName("senderId") val senderId: String? = null,
        @SerializedName("senderName") val senderName: String? = null,
        @SerializedName("accepterId") val accepterId: String? = null,
        @SerializedName("accepterName") val accepterName: String? = null,
        @SerializedName("shareId") val shareId: String? = null
    )

    /**
     * 批量推送请求
     */
    data class BatchPushRequest(
        @SerializedName("targets") val targets: List<PushRequest>
    )

    /**
     * API 响应
     */
    data class ApiResponse(
        @SerializedName("success") val success: Boolean = false,
        @SerializedName("error") val error: String? = null,
        @SerializedName("messageId") val messageId: String? = null,
        @SerializedName("code") val code: String? = null,
        @SerializedName("tokenInvalid") val tokenInvalid: Boolean = false  // Token 是否无效
    )

    /**
     * 错误代码常量
     */
    object ErrorCode {
        const val TOKEN_NOT_REGISTERED = "TOKEN_NOT_REGISTERED"  // 目标用户未注册 Token
        const val TOKEN_INVALID = "TOKEN_INVALID"                // FCM Token 无效或过期
        const val USER_NOT_FOUND = "USER_NOT_FOUND"              // 目标用户不存在
        const val SEND_FAILED = "SEND_FAILED"                    // 发送失败
    }

    // ========================================================================
    // FCM Token 注册
    // ========================================================================

    /**
     * 注册 FCM Token 到 Cloud Functions
     * 同时关联用户 UID，让服务端可以通过 UID 查找 Token
     *
     * @param context Android Context
     * @param deviceId 设备唯一标识
     * @param fcmToken FCM Token
     * @param uid 用户 UID（可选，但建议提供以支持通过 UID 查找 Token）
     */
    suspend fun registerToken(
        context: Context,
        deviceId: String,
        fcmToken: String,
        uid: String? = null
    ): Result<Unit> {
        val request = TokenRegistrationRequest(
            deviceId = deviceId,
            fcmToken = fcmToken,
            uid = uid,
            platform = "android",
            appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Exception) {
                null
            }
        )

        return postRequest("registerToken", request).map { }
    }

    // ========================================================================
    // 位置请求
    // ========================================================================

    /**
     * 发送单次位置请求
     *
     * @param targetUid 目标用户的 UID
     * @param requesterId 请求者的 UID
     */
    suspend fun sendLocationRequest(
        targetUid: String,
        requesterId: String
    ): Result<ApiResponse> {
        val request = PushRequest(
            targetUid = targetUid,
            type = RequestType.SINGLE,
            requesterId = requesterId
        )
        return postRequest("sendPush", request)
    }

    /**
     * 开始实时追踪
     *
     * @param targetUid 目标用户的 UID
     * @param requesterId 请求者的 UID
     */
    suspend fun startContinuousTracking(
        targetUid: String,
        requesterId: String
    ): Result<ApiResponse> {
        val request = PushRequest(
            targetUid = targetUid,
            type = RequestType.CONTINUOUS,
            requesterId = requesterId
        )
        return postRequest("sendPush", request)
    }

    /**
     * 停止实时追踪
     *
     * @param targetUid 目标用户的 UID
     * @param requesterId 请求者的 UID
     */
    suspend fun stopContinuousTracking(
        targetUid: String,
        requesterId: String
    ): Result<ApiResponse> {
        val request = PushRequest(
            targetUid = targetUid,
            type = RequestType.STOP_CONTINUOUS,
            requesterId = requesterId
        )
        return postRequest("sendPush", request)
    }

    /**
     * 发送心跳
     *
     * @param targetUid 目标用户的 UID
     * @param requesterId 请求者的 UID
     */
    suspend fun sendHeartbeat(
        targetUid: String,
        requesterId: String
    ): Result<ApiResponse> {
        val request = PushRequest(
            targetUid = targetUid,
            type = RequestType.HEARTBEAT,
            requesterId = requesterId
        )
        return postRequest("sendPush", request)
    }

    // ========================================================================
    // 设备控制
    // ========================================================================

    /**
     * 播放查找提示音
     *
     * @param targetUid 目标用户的 UID
     * @param requesterId 请求者的 UID
     */
    suspend fun playSound(
        targetUid: String,
        requesterId: String
    ): Result<ApiResponse> {
        val request = PushRequest(
            targetUid = targetUid,
            type = RequestType.PLAY_SOUND,
            requesterId = requesterId
        )
        return postRequest("sendPush", request)
    }

    /**
     * 停止播放提示音
     *
     * @param targetUid 目标用户的 UID
     * @param requesterId 请求者的 UID
     */
    suspend fun stopSound(
        targetUid: String,
        requesterId: String
    ): Result<ApiResponse> {
        val request = PushRequest(
            targetUid = targetUid,
            type = RequestType.STOP_SOUND,
            requesterId = requesterId
        )
        return postRequest("sendPush", request)
    }

    /**
     * 启用丢失模式
     *
     * @param targetUid 目标用户的 UID
     * @param requesterId 请求者的 UID
     * @param message 丢失模式显示的消息
     * @param phoneNumber 联系电话
     * @param playSound 是否播放声音
     */
    suspend fun enableLostMode(
        targetUid: String,
        requesterId: String,
        message: String,
        phoneNumber: String,
        playSound: Boolean = true
    ): Result<ApiResponse> {
        val request = PushRequest(
            targetUid = targetUid,
            type = RequestType.ENABLE_LOST_MODE,
            requesterId = requesterId,
            message = message,
            phoneNumber = phoneNumber,
            playSound = playSound
        )
        return postRequest("sendPush", request)
    }

    /**
     * 关闭丢失模式
     *
     * @param targetUid 目标用户的 UID
     * @param requesterId 请求者的 UID
     */
    suspend fun disableLostMode(
        targetUid: String,
        requesterId: String
    ): Result<ApiResponse> {
        val request = PushRequest(
            targetUid = targetUid,
            type = RequestType.DISABLE_LOST_MODE,
            requesterId = requesterId
        )
        return postRequest("sendPush", request)
    }

    // ========================================================================
    // 共享邀请
    // ========================================================================

    /**
     * 发送共享邀请通知
     * 当用户发起位置共享邀请时，通过 FCM 推送通知目标用户
     *
     * @param targetUid 目标用户 UID
     * @param senderId 发送者 UID
     * @param senderName 发送者名称
     * @param shareId 共享 ID
     */
    suspend fun sendShareRequest(
        targetUid: String,
        senderId: String,
        senderName: String,
        shareId: String
    ): Result<ApiResponse> {
        val request = PushRequest(
            targetUid = targetUid,
            type = RequestType.SHARE_REQUEST,
            senderId = senderId,
            senderName = senderName,
            shareId = shareId
        )
        return postRequest("sendPush", request)
    }

    /**
     * 发送邀请被接受通知
     * 当用户接受位置共享邀请时，通过 FCM 推送通知邀请发起者
     *
     * @param targetUid 目标用户 UID（邀请发起者）
     * @param accepterId 接受者 UID
     * @param accepterName 接受者名称
     * @param shareId 共享 ID
     */
    suspend fun sendShareAccepted(
        targetUid: String,
        accepterId: String,
        accepterName: String,
        shareId: String
    ): Result<ApiResponse> {
        val request = PushRequest(
            targetUid = targetUid,
            type = RequestType.SHARE_ACCEPTED,
            accepterId = accepterId,
            accepterName = accepterName,
            shareId = shareId
        )
        return postRequest("sendPush", request)
    }

    /**
     * 发送邀请被拒绝通知
     * 当用户拒绝位置共享邀请时，通过 FCM 推送通知邀请发起者
     *
     * @param targetUid 目标用户 UID（邀请发起者）
     * @param rejecterId 拒绝者 UID
     * @param rejecterName 拒绝者名称
     * @param shareId 共享 ID
     */
    suspend fun sendShareRejected(
        targetUid: String,
        rejecterId: String,
        rejecterName: String,
        shareId: String
    ): Result<ApiResponse> {
        val request = PushRequest(
            targetUid = targetUid,
            type = RequestType.SHARE_REJECTED,
            senderId = rejecterId,      // 拒绝者作为消息发送者
            senderName = rejecterName,
            shareId = shareId
        )
        return postRequest("sendPush", request)
    }

    /**
     * 发送联系人被移除通知
     * 当用户将对方从联系人列表中移除时，通过 FCM 推送通知对方
     *
     * @param targetUid 目标用户 UID（被移除者）
     * @param removerId 移除者 UID
     * @param removerName 移除者名称
     * @param shareId 共享 ID
     */
    suspend fun sendShareRemoved(
        targetUid: String,
        removerId: String,
        removerName: String,
        shareId: String
    ): Result<ApiResponse> {
        val request = PushRequest(
            targetUid = targetUid,
            type = RequestType.SHARE_REMOVED,
            senderId = removerId,       // 移除者作为消息发送者
            senderName = removerName,
            shareId = shareId
        )
        return postRequest("sendPush", request)
    }

    /**
     * 发送共享暂停通知
     * 当用户暂停与对方的位置共享时，通过 FCM 推送通知对方
     *
     * @param targetUid 目标用户 UID
     * @param senderId 发送者 UID（暂停共享的用户）
     * @param senderName 发送者名称
     */
    suspend fun sendSharePaused(
        targetUid: String,
        senderId: String,
        senderName: String
    ): Result<ApiResponse> {
        val request = PushRequest(
            targetUid = targetUid,
            type = RequestType.SHARE_PAUSED,
            senderId = senderId,
            senderName = senderName
        )
        return postRequest("sendPush", request)
    }

    /**
     * 发送共享恢复通知
     * 当用户恢复与对方的位置共享时，通过 FCM 推送通知对方
     *
     * @param targetUid 目标用户 UID
     * @param senderId 发送者 UID（恢复共享的用户）
     * @param senderName 发送者名称
     */
    suspend fun sendShareResumed(
        targetUid: String,
        senderId: String,
        senderName: String
    ): Result<ApiResponse> {
        val request = PushRequest(
            targetUid = targetUid,
            type = RequestType.SHARE_RESUMED,
            senderId = senderId,
            senderName = senderName
        )
        return postRequest("sendPush", request)
    }

    /**
     * 发送共享过期通知
     * 当共享到期时，通知对方共享已过期（过期是一种特殊的暂停）
     * 任一方都可以后续恢复共享
     *
     * @param targetUid 目标用户 UID
     * @param senderId 发送者 UID（检测到过期的一方）
     * @param senderName 发送者名称
     */
    suspend fun sendShareExpired(
        targetUid: String,
        senderId: String,
        senderName: String
    ): Result<ApiResponse> {
        val request = PushRequest(
            targetUid = targetUid,
            type = RequestType.SHARE_EXPIRED,
            senderId = senderId,
            senderName = senderName
        )
        return postRequest("sendPush", request)
    }

    // ========================================================================
    // 批量操作
    // ========================================================================

    /**
     * 批量发送推送
     */
    suspend fun sendBatchPush(
        targets: List<PushRequest>
    ): Result<ApiResponse> {
        if (targets.isEmpty()) {
            return Result.success(ApiResponse(success = true))
        }
        if (targets.size > 500) {
            return Result.failure(IllegalArgumentException("Maximum 500 targets per request"))
        }

        val request = BatchPushRequest(targets = targets)
        return postRequest("sendPushBatch", request)
    }

    // ========================================================================
    // 状态查询
    // ========================================================================

    /**
     * 查询 Token 注册状态
     */
    suspend fun checkTokenStatus(deviceId: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/tokenStatus?deviceId=$deviceId")
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val responseCode = connection.responseCode
                val responseBody = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                if (responseCode == 200) {
                    val response = gson.fromJson(responseBody, Map::class.java)
                    val registered = response["registered"] as? Boolean ?: false
                    Result.success(registered)
                } else {
                    Result.failure(Exception("HTTP $responseCode"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "查询 Token 状态失败", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 健康检查
     */
    suspend fun healthCheck(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/health")
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val responseCode = connection.responseCode
                connection.disconnect()

                Result.success(responseCode == 200)
            } catch (e: Exception) {
                Log.e(TAG, "健康检查失败", e)
                Result.failure(e)
            }
        }
    }

    // ========================================================================
    // 内部方法
    // ========================================================================

    // 网络配置
    private const val CONNECT_TIMEOUT_MS = 30000  // 连接超时 30 秒
    private const val READ_TIMEOUT_MS = 30000     // 读取超时 30 秒
    private const val MAX_RETRIES = 2             // 最大重试次数

    /**
     * 发送 POST 请求（带重试）
     */
    private suspend fun <T> postRequest(
        endpoint: String,
        body: T
    ): Result<ApiResponse> {
        if (!isConfigured()) {
            Log.w(TAG, "Cloud Functions URL 未配置，跳过请求")
            return Result.success(ApiResponse(success = true))
        }

        return withContext(Dispatchers.IO) {
            var lastException: Exception? = null

            repeat(MAX_RETRIES + 1) { attempt ->
                try {
                    val result = doPostRequest(endpoint, body)
                    if (result.isSuccess) {
                        return@withContext result
                    }
                    // 非网络错误不重试
                    val error = result.exceptionOrNull()
                    if (error !is java.net.SocketTimeoutException &&
                        error !is java.net.ConnectException) {
                        return@withContext result
                    }
                    lastException = error as? Exception
                    Log.w(TAG, "请求超时，重试 ${attempt + 1}/$MAX_RETRIES")
                    kotlinx.coroutines.delay(1000L * (attempt + 1)) // 递增延迟
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < MAX_RETRIES) {
                        Log.w(TAG, "请求失败，重试 ${attempt + 1}/$MAX_RETRIES: ${e.message}")
                        kotlinx.coroutines.delay(1000L * (attempt + 1))
                    }
                }
            }

            Result.failure(lastException ?: Exception("Unknown error after retries"))
        }
    }

    /**
     * 执行单次 POST 请求
     */
    private fun <T> doPostRequest(
        endpoint: String,
        body: T
    ): Result<ApiResponse> {
        return try {
            val url = URL("$baseUrl/$endpoint")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
            }

            val jsonBody = gson.toJson(body)
            Log.d(TAG, "POST $endpoint: ${jsonBody.take(200)}...")

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            }

            connection.disconnect()

            Log.d(TAG, "Response $responseCode: $responseBody")

            // 解析响应
            val response = try {
                gson.fromJson(responseBody, ApiResponse::class.java)
            } catch (e: Exception) {
                ApiResponse(success = responseCode in 200..299, error = responseBody)
            }

            when {
                responseCode in 200..299 -> {
                    Result.success(response)
                }
                responseCode == 410 -> {
                    Log.w(TAG, "FCM Token 已过期")
                    Result.failure(TokenExpiredException("Target token is expired"))
                }
                responseCode == 429 -> {
                    Log.w(TAG, "请求频率超限")
                    Result.failure(RateLimitException("Rate limit exceeded"))
                }
                response.code == ErrorCode.TOKEN_NOT_REGISTERED -> {
                    Log.w(TAG, "目标用户未注册 FCM Token")
                    Result.failure(TokenNotRegisteredException("unknown"))
                }
                response.code == ErrorCode.TOKEN_INVALID || response.tokenInvalid -> {
                    Log.w(TAG, "FCM Token 无效或已过期")
                    Result.failure(TokenInvalidException("unknown"))
                }
                response.error?.contains("not a valid FCM registration token") == true -> {
                    Log.w(TAG, "FCM Token 格式无效: ${response.error}")
                    Result.failure(TokenInvalidException("unknown"))
                }
                response.error?.contains("token") == true &&
                (response.error.contains("invalid") || response.error.contains("expired")) -> {
                    Log.w(TAG, "FCM Token 问题: ${response.error}")
                    Result.failure(TokenInvalidException("unknown"))
                }
                else -> {
                    Result.failure(ApiException(responseCode, response.error ?: "Unknown error"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "请求失败: $endpoint", e)
            Result.failure(e)
        }
    }

    // ========================================================================
    // 异常类
    // ========================================================================

    /** Token 已过期异常 */
    class TokenExpiredException(message: String) : Exception(message)

    /** 频率限制异常 */
    class RateLimitException(message: String) : Exception(message)

    /** API 请求异常 */
    class ApiException(val code: Int, message: String) : Exception("HTTP $code: $message")

    /** Token 未注册异常（目标用户未在此设备注册 FCM Token） */
    class TokenNotRegisteredException(val targetUid: String) : Exception("Target user $targetUid has no registered FCM token")

    /** Token 无效异常（FCM Token 已失效，需要重新注册） */
    class TokenInvalidException(val targetUid: String) : Exception("FCM token for user $targetUid is invalid or expired")
}
