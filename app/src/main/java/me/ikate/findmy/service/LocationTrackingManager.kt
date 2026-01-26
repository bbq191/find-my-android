package me.ikate.findmy.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ikate.findmy.data.remote.mqtt.MqttConfig
import me.ikate.findmy.data.repository.DeviceRepository
import me.ikate.findmy.push.PushWebhookService

/**
 * 位置刷新管理器（简化版）
 * iOS Find My 风格：单击即刷新，无状态机
 *
 * 使用 MQTT 进行位置请求
 */
class LocationTrackingManager(
    private val context: Context,
    private val locationReportService: LocationReportService,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "LocationTrackingManager"
        private const val REFRESH_INDICATOR_DURATION_MS = 5_000L  // 刷新指示器显示时间
        private const val DEBOUNCE_MS = 1_000L  // 防双击误操作
    }

    // 正在刷新位置的联系人 UID 集合（用于显示加载指示器）
    private val _refreshingContacts = MutableStateFlow<Set<String>>(emptySet())
    val refreshingContacts: StateFlow<Set<String>> = _refreshingContacts.asStateFlow()

    // 每个联系人的上次请求时间（用于防抖）
    private val lastRequestTime = mutableMapOf<String, Long>()

    // 错误消息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * 检查指定联系人是否正在刷新
     */
    fun isRefreshing(targetUid: String): Boolean {
        return _refreshingContacts.value.contains(targetUid)
    }

    /**
     * 请求刷新联系人位置（单次请求）
     * iOS Find My 风格：单击即刷新，无追踪状态
     *
     * @param currentUid 当前用户 UID
     * @param targetUid 目标联系人 UID
     */
    fun requestRefresh(currentUid: String?, targetUid: String) {
        Log.i(TAG, "========================================")
        Log.i(TAG, "[刷新位置] 触发刷新")
        Log.i(TAG, "[刷新位置] currentUid: $currentUid")
        Log.i(TAG, "[刷新位置] targetUid: $targetUid")
        Log.i(TAG, "========================================")

        if (currentUid == null) {
            Log.w(TAG, "[刷新位置] 当前用户未登录，无法刷新")
            return
        }

        val now = System.currentTimeMillis()
        val lastTime = lastRequestTime[targetUid] ?: 0L

        // 防抖：防止快速连续点击
        if (now - lastTime < DEBOUNCE_MS) {
            Log.d(TAG, "[刷新位置] 防抖保护：忽略快速连续点击 (间隔=${now - lastTime}ms)")
            return
        }

        // 记录请求时间
        lastRequestTime[targetUid] = now

        // 添加到刷新集合
        addRefreshingContact(targetUid)

        scope.launch {
            try {
                // 先上报自己的位置（互惠原则）
                Log.i(TAG, "[刷新位置] 上报自己的位置...")
                reportMyLocationFirst()

                // 发送单次位置请求
                Log.i(TAG, "[刷新位置] 发送位置请求...")
                sendMqttRequest(
                    currentUid = currentUid,
                    targetUid = targetUid,
                    type = "single"
                )

                // 5秒后自动移除刷新状态
                delay(REFRESH_INDICATOR_DURATION_MS)
                removeRefreshingContact(targetUid)

            } catch (e: Exception) {
                Log.e(TAG, "[刷新位置] 失败: ${e.message}", e)
                removeRefreshingContact(targetUid)
                _errorMessage.value = "刷新失败: ${e.localizedMessage}"
            }
        }
    }

    /**
     * 添加正在刷新的联系人
     */
    private fun addRefreshingContact(targetUid: String) {
        val current = _refreshingContacts.value.toMutableSet()
        current.add(targetUid)
        _refreshingContacts.value = current
    }

    /**
     * 移除正在刷新的联系人
     */
    private fun removeRefreshingContact(targetUid: String) {
        val current = _refreshingContacts.value.toMutableSet()
        current.remove(targetUid)
        _refreshingContacts.value = current
    }

    /**
     * 标记收到位置更新（可立即清除刷新状态）
     */
    fun onLocationReceived(targetUid: String) {
        if (_refreshingContacts.value.contains(targetUid)) {
            Log.d(TAG, "[刷新位置] 收到位置更新，清除刷新状态: $targetUid")
            removeRefreshingContact(targetUid)
        }
    }

    /**
     * 通过 MQTT 发送请求，同时调用推送 Webhook 作为兜底
     */
    private fun sendMqttRequest(
        currentUid: String?,
        targetUid: String,
        type: String,
        additionalData: Map<String, Any> = emptyMap(),
        errorMessagePrefix: String = "请求失败",
        onSuccess: (() -> Unit)? = null
    ) {
        if (currentUid == null) {
            Log.w(TAG, "[位置请求] 当前用户未登录，无法执行请求: $type")
            return
        }

        scope.launch {
            try {
                Log.i(TAG, "========== [位置请求] 开始发送 ==========")
                Log.i(TAG, "[位置请求] 请求类型: $type")
                Log.i(TAG, "[位置请求] 请求者UID: $currentUid")
                Log.i(TAG, "[位置请求] 目标UID: $targetUid")
                Log.i(TAG, "[位置请求] 时间戳: ${System.currentTimeMillis()}")

                // 通过 MQTT 发送请求
                if (MqttConfig.isConfigured()) {
                    val mqttManager = DeviceRepository.getMqttManager(context)
                    val isConnected = mqttManager.isConnected()
                    Log.i(TAG, "[位置请求] MQTT 连接状态: ${if (isConnected) "已连接" else "未连接"}")

                    if (!isConnected) {
                        Log.w(TAG, "[位置请求] MQTT 未连接，尝试重新连接...")
                    }

                    val requestData = mapOf(
                        "requesterUid" to currentUid,
                        "targetUid" to targetUid,
                        "type" to type,
                        "timestamp" to System.currentTimeMillis()
                    ) + additionalData

                    // 发布到目标用户的请求主题
                    val topic = "findmy/requests/$targetUid"
                    val payload = Gson().toJson(requestData)
                    Log.i(TAG, "[位置请求] 发布主题: $topic")
                    Log.i(TAG, "[位置请求] 发布内容: $payload")

                    val publishResult = mqttManager.publish(topic, payload)
                    publishResult.fold(
                        onSuccess = {
                            Log.i(TAG, "[位置请求] MQTT 消息发送成功: $type -> $targetUid")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "[位置请求] MQTT 消息发送失败: ${error.message}", error)
                        }
                    )
                } else {
                    Log.w(TAG, "[位置请求] MQTT 未配置，无法发送请求")
                }

                // 同时通过推送 Webhook 发送（兜底，确保对端 APP 被杀时也能唤醒）
                sendPushWebhook(currentUid, targetUid, type, additionalData)

                onSuccess?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "请求失败: $type", e)
                _errorMessage.value = "$errorMessagePrefix: ${e.localizedMessage}"
            }
        }
    }

    /**
     * 发送推送 Webhook（调用 Cloud Functions 触发 FCM）
     * FCM 作为兜底机制，用于唤醒被系统杀死的 APP
     */
    private suspend fun sendPushWebhook(
        currentUid: String,
        targetUid: String,
        type: String,
        additionalData: Map<String, Any>
    ) {
        if (!PushWebhookService.isConfigured()) {
            Log.d(TAG, "推送 Webhook 未配置，跳过")
            return
        }

        try {
            val result = when (type) {
                "single" -> {
                    PushWebhookService.sendLocationRequest(targetUid, currentUid)
                }
                "play_sound" -> {
                    PushWebhookService.playSound(targetUid, currentUid)
                }
                "stop_sound" -> {
                    PushWebhookService.stopSound(targetUid, currentUid)
                }
                "lost_mode" -> {
                    PushWebhookService.enableLostMode(
                        targetUid = targetUid,
                        requesterId = currentUid,
                        message = additionalData["message"] as? String ?: "",
                        phoneNumber = additionalData["phoneNumber"] as? String ?: "",
                        playSound = additionalData["playSound"] as? Boolean ?: true
                    )
                }
                "disable_lost_mode" -> {
                    PushWebhookService.disableLostMode(targetUid, currentUid)
                }
                else -> {
                    Log.d(TAG, "未知请求类型，跳过推送: $type")
                    Result.success(PushWebhookService.ApiResponse(success = true))
                }
            }

            result.fold(
                onSuccess = { response ->
                    Log.d(TAG, "FCM 推送发送成功: $type (messageId=${response.messageId})")
                },
                onFailure = { error ->
                    when (error) {
                        is PushWebhookService.TokenNotRegisteredException -> {
                            Log.w(TAG, "FCM 推送失败: 目标用户 $targetUid 未注册 Token")
                        }
                        is PushWebhookService.TokenInvalidException -> {
                            Log.w(TAG, "FCM 推送失败: 目标用户 $targetUid 的 Token 已失效")
                        }
                        is PushWebhookService.TokenExpiredException -> {
                            Log.w(TAG, "FCM 推送失败: Token 已过期")
                        }
                        is PushWebhookService.RateLimitException -> {
                            Log.w(TAG, "FCM 推送失败: 请求频率超限")
                        }
                        else -> {
                            Log.w(TAG, "FCM 推送失败: ${error.message}")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "推送 Webhook 异常: ${e.message}")
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 请求目标设备播放声音
     */
    fun requestPlaySound(currentUid: String?, targetUid: String) {
        sendMqttRequest(
            currentUid = currentUid,
            targetUid = targetUid,
            type = "play_sound",
            errorMessagePrefix = "请求播放声音失败"
        )
    }

    /**
     * 请求目标设备停止播放声音
     */
    fun requestStopSound(currentUid: String?, targetUid: String) {
        sendMqttRequest(
            currentUid = currentUid,
            targetUid = targetUid,
            type = "stop_sound",
            errorMessagePrefix = "请求停止播放声音失败"
        )
    }

    /**
     * 启用丢失模式
     */
    fun enableLostMode(
        currentUid: String?,
        targetUid: String,
        message: String,
        phoneNumber: String,
        playSound: Boolean
    ) {
        sendMqttRequest(
            currentUid = currentUid,
            targetUid = targetUid,
            type = "lost_mode",
            additionalData = mapOf(
                "message" to message,
                "phoneNumber" to phoneNumber,
                "playSound" to playSound
            ),
            errorMessagePrefix = "启用丢失模式失败"
        )
    }

    /**
     * 关闭丢失模式
     */
    fun disableLostMode(currentUid: String?, targetUid: String) {
        sendMqttRequest(
            currentUid = currentUid,
            targetUid = targetUid,
            type = "disable_lost_mode",
            errorMessagePrefix = "关闭丢失模式失败"
        )
    }

    /**
     * 先上报自己的位置（互惠原则）
     */
    private suspend fun reportMyLocationFirst() {
        Log.i(TAG, "[上报位置] 开始上报自己的位置...")
        val result = locationReportService.reportCurrentLocation()
        result.fold(
            onSuccess = {
                Log.i(TAG, "[上报位置] 我的位置已上报成功")
            },
            onFailure = {
                Log.w(TAG, "[上报位置] 上报失败: ${it.message}")
            }
        )
    }
}
