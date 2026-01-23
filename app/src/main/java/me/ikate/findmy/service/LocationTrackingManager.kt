package me.ikate.findmy.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ikate.findmy.data.remote.mqtt.MqttConfig
import me.ikate.findmy.data.repository.DeviceRepository
import me.ikate.findmy.push.PushWebhookService

/**
 * 追踪状态枚举
 * 用于表示联系人位置追踪的详细状态
 */
enum class TrackingState {
    /** 空闲，未追踪 */
    IDLE,
    /** 等待中，启动追踪等待连接（黄色） */
    WAITING,
    /** 已连接，追踪中（蓝色旋转动画） */
    CONNECTED,
    /** 追踪成功，位置已刷新（绿色），短暂显示后回到 IDLE */
    SUCCESS,
    /** 追踪失败，连接不成功（灰色） */
    FAILED
}

/**
 * 位置追踪管理器
 * 使用 MQTT 进行位置请求和追踪（替代 Firestore）
 */
class LocationTrackingManager(
    private val context: Context,
    private val locationReportService: LocationReportService,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "LocationTrackingManager"
        private const val REQUEST_TIMEOUT_MS = 10_000L
        private const val TRACKING_DURATION_MS = 60_000L
        private const val SUCCESS_DISPLAY_MS = 2_000L  // 成功状态显示时间
    }

    // 正在请求位置更新的联系人 UID
    private val _requestingLocationFor = MutableStateFlow<String?>(null)
    val requestingLocationFor: StateFlow<String?> = _requestingLocationFor.asStateFlow()

    // 正在连续追踪的联系人 UID
    private val _trackingContactUid = MutableStateFlow<String?>(null)
    val trackingContactUid: StateFlow<String?> = _trackingContactUid.asStateFlow()

    // 追踪状态 Map: targetUid -> TrackingState
    private val _trackingStates = MutableStateFlow<Map<String, TrackingState>>(emptyMap())
    val trackingStates: StateFlow<Map<String, TrackingState>> = _trackingStates.asStateFlow()

    // 追踪任务 Map: targetUid -> Job（用于取消追踪）
    private val trackingJobs = mutableMapOf<String, Job>()

    // 错误消息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * 获取指定联系人的追踪状态
     */
    fun getTrackingState(targetUid: String): TrackingState {
        return _trackingStates.value[targetUid] ?: TrackingState.IDLE
    }

    /**
     * 更新追踪状态
     */
    private fun updateTrackingState(targetUid: String, state: TrackingState) {
        val currentStates = _trackingStates.value.toMutableMap()
        if (state == TrackingState.IDLE) {
            currentStates.remove(targetUid)
        } else {
            currentStates[targetUid] = state
        }
        _trackingStates.value = currentStates
        Log.d(TAG, "[追踪状态] $targetUid -> $state")
    }

    /**
     * 标记追踪成功
     * 当收到位置更新时调用，显示成功状态后自动回到 IDLE
     */
    fun markTrackingSuccess(targetUid: String) {
        scope.launch {
            updateTrackingState(targetUid, TrackingState.SUCCESS)
            delay(SUCCESS_DISPLAY_MS)
            // 如果还是 SUCCESS 状态才重置（可能已被其他操作改变）
            if (_trackingStates.value[targetUid] == TrackingState.SUCCESS) {
                updateTrackingState(targetUid, TrackingState.IDLE)
            }
        }
    }

    /**
     * 标记追踪失败
     * 当连接失败或超时时调用，显示失败状态后自动回到 IDLE
     */
    fun markTrackingFailed(targetUid: String) {
        scope.launch {
            updateTrackingState(targetUid, TrackingState.FAILED)
            delay(SUCCESS_DISPLAY_MS)
            // 如果还是 FAILED 状态才重置（可能已被其他操作改变）
            if (_trackingStates.value[targetUid] == TrackingState.FAILED) {
                updateTrackingState(targetUid, TrackingState.IDLE)
            }
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
        reportLocationFirst: Boolean = false,
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

                // 根据需要先上报自己的位置
                if (reportLocationFirst) {
                    Log.d(TAG, "[位置请求] 先上报自己的位置...")
                    reportMyLocationFirst()
                }

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
                            Log.i(TAG, "[位置请求] ✓ MQTT 消息发送成功: $type -> $targetUid")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "[位置请求] ✗ MQTT 消息发送失败: ${error.message}", error)
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
                "continuous" -> {
                    PushWebhookService.startContinuousTracking(targetUid, currentUid)
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
                            Log.w(TAG, "FCM 推送失败: 目标用户 $targetUid 未注册 Token（可能未安装应用或未登录）")
                        }
                        is PushWebhookService.TokenInvalidException -> {
                            Log.w(TAG, "FCM 推送失败: 目标用户 $targetUid 的 Token 已失效（需要对方重新打开应用）")
                        }
                        is PushWebhookService.TokenExpiredException -> {
                            Log.w(TAG, "FCM 推送失败: Token 已过期")
                        }
                        is PushWebhookService.RateLimitException -> {
                            Log.w(TAG, "FCM 推送失败: 请求频率超限，稍后重试")
                        }
                        else -> {
                            Log.w(TAG, "FCM 推送失败: ${error.message}")
                        }
                    }
                    // FCM 推送失败不影响主流程（MQTT 是主通道）
                }
            )
        } catch (e: Exception) {
            // 推送失败不影响主流程
            Log.w(TAG, "推送 Webhook 异常: ${e.message}")
        }
    }

    /**
     * 请求联系人的实时位置更新
     */
    fun requestLocationUpdate(currentUid: String?, targetUid: String) {
        if (currentUid == null) {
            Log.w(TAG, "当前用户未登录，无法请求位置更新")
            return
        }

        scope.launch {
            try {
                _requestingLocationFor.value = targetUid
                Log.d(TAG, "请求位置更新: targetUid=$targetUid")

                // 先上报自己的最新位置
                reportMyLocationFirst()

                // 通过 MQTT 发送位置请求
                sendMqttRequest(
                    currentUid = currentUid,
                    targetUid = targetUid,
                    type = "single",
                    reportLocationFirst = false
                )

                // 超时后清除加载状态
                delay(REQUEST_TIMEOUT_MS)
                if (_requestingLocationFor.value == targetUid) {
                    _requestingLocationFor.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "请求位置更新失败", e)
                _requestingLocationFor.value = null
                _errorMessage.value = "请求失败: ${e.localizedMessage}"
            }
        }
    }

    /**
     * 开始短时实时追踪（60秒高频位置更新）
     */
    fun startContinuousTracking(currentUid: String?, targetUid: String) {
        if (currentUid == null) {
            Log.w(TAG, "当前用户未登录，无法开始追踪")
            return
        }

        scope.launch {
            try {
                _trackingContactUid.value = targetUid
                Log.d(TAG, "开始连续追踪: targetUid=$targetUid")

                // 先上报自己的位置
                reportMyLocationFirst()

                // 通过 MQTT 发送连续追踪请求
                sendMqttRequest(
                    currentUid = currentUid,
                    targetUid = targetUid,
                    type = "continuous",
                    reportLocationFirst = false
                )

                // 60秒后自动清除追踪状态
                delay(TRACKING_DURATION_MS)
                if (_trackingContactUid.value == targetUid) {
                    _trackingContactUid.value = null
                    Log.d(TAG, "连续追踪已自动结束（60秒超时）")
                }
            } catch (e: Exception) {
                Log.e(TAG, "开始连续追踪失败", e)
                _trackingContactUid.value = null
                _errorMessage.value = "启动追踪失败: ${e.localizedMessage}"
            }
        }
    }

    /**
     * 停止连续追踪
     */
    fun stopContinuousTracking(currentUid: String?, targetUid: String) {
        // 取消追踪任务
        trackingJobs[targetUid]?.cancel()
        trackingJobs.remove(targetUid)

        // 清除状态
        _trackingContactUid.value = null
        _requestingLocationFor.value = null
        updateTrackingState(targetUid, TrackingState.IDLE)

        sendMqttRequest(
            currentUid = currentUid,
            targetUid = targetUid,
            type = "stop_continuous",
            errorMessagePrefix = "停止追踪失败"
        )
    }

    /**
     * 刷新并追踪（点击头像触发）
     * 先刷新一次位置，然后开始 60 秒的连续追踪
     *
     * 状态变化: WAITING(黄色) → CONNECTED(蓝色) → SUCCESS(绿色) → IDLE
     */
    fun refreshAndTrack(currentUid: String?, targetUid: String) {
        Log.i(TAG, "========================================")
        Log.i(TAG, "[刷新追踪] 触发点击事件")
        Log.i(TAG, "[刷新追踪] currentUid: $currentUid")
        Log.i(TAG, "[刷新追踪] targetUid: $targetUid")
        Log.i(TAG, "========================================")

        if (currentUid == null) {
            Log.w(TAG, "[刷新追踪] 当前用户未登录，无法刷新并追踪")
            return
        }

        // 如果已经在追踪这个联系人，则停止追踪
        val currentState = getTrackingState(targetUid)
        if (currentState != TrackingState.IDLE && currentState != TrackingState.SUCCESS) {
            Log.d(TAG, "[刷新追踪] 已在追踪中，停止追踪: targetUid=$targetUid")
            stopContinuousTracking(currentUid, targetUid)
            return
        }

        // 取消之前的追踪任务（如果有）
        trackingJobs[targetUid]?.cancel()

        val job = scope.launch {
            try {
                // 步骤1: 设置等待状态（黄色）
                updateTrackingState(targetUid, TrackingState.WAITING)
                _requestingLocationFor.value = targetUid
                Log.i(TAG, "[刷新追踪] 步骤1: 状态 -> WAITING (黄色)")

                // 先上报自己的最新位置
                Log.i(TAG, "[刷新追踪] 步骤2: 上报自己的位置（互惠原则）")
                reportMyLocationFirst()

                // 发送单次位置请求（立即刷新）
                Log.i(TAG, "[刷新追踪] 步骤3: 发送单次位置请求 (type=single)")
                sendMqttRequest(
                    currentUid = currentUid,
                    targetUid = targetUid,
                    type = "single",
                    reportLocationFirst = false
                )

                // 短暂延迟后进入连接状态
                Log.i(TAG, "[刷新追踪] 步骤4: 等待500ms后进入连接状态...")
                delay(500L)

                // 步骤5: 设置连接状态（蓝色）
                updateTrackingState(targetUid, TrackingState.CONNECTED)
                _requestingLocationFor.value = null
                _trackingContactUid.value = targetUid
                Log.i(TAG, "[刷新追踪] 步骤5: 状态 -> CONNECTED (蓝色)")

                // 发送连续追踪请求
                Log.i(TAG, "[刷新追踪] 步骤6: 发送连续追踪请求 (type=continuous)")
                sendMqttRequest(
                    currentUid = currentUid,
                    targetUid = targetUid,
                    type = "continuous",
                    reportLocationFirst = false
                )

                Log.i(TAG, "[刷新追踪] 步骤7: 等待位置响应中... (60秒超时)")

                // 60秒后自动清除追踪状态
                delay(TRACKING_DURATION_MS)
                if (_trackingContactUid.value == targetUid) {
                    _trackingContactUid.value = null
                    // 超时未收到响应，标记为失败
                    val currentState = _trackingStates.value[targetUid]
                    if (currentState == TrackingState.CONNECTED || currentState == TrackingState.WAITING) {
                        Log.i(TAG, "[刷新追踪] 步骤8: 追踪超时，标记失败 -> FAILED")
                        markTrackingFailed(targetUid)
                    } else {
                        updateTrackingState(targetUid, TrackingState.IDLE)
                        Log.i(TAG, "[刷新追踪] 步骤8: 追踪已结束 -> IDLE")
                    }
                }
            } catch (e: CancellationException) {
                // 协程取消是正常行为（用户主动停止追踪），不视为错误
                Log.d(TAG, "[刷新追踪] 追踪已取消")
                throw e  // 重新抛出，保持协程取消传播
            } catch (e: Exception) {
                Log.e(TAG, "[刷新追踪] ✗ 失败: ${e.message}", e)
                _requestingLocationFor.value = null
                _trackingContactUid.value = null
                updateTrackingState(targetUid, TrackingState.IDLE)
                _errorMessage.value = "刷新追踪失败: ${e.localizedMessage}"
            }
        }

        trackingJobs[targetUid] = job
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
            type = "lost_mode",  // 使用 RequestMessage.TYPE_LOST_MODE 常量值
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
            type = "disable_lost_mode",  // 使用 RequestMessage.TYPE_DISABLE_LOST_MODE 常量值
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
                Log.i(TAG, "[上报位置] ✓ 我的位置已上报成功")
            },
            onFailure = {
                Log.w(TAG, "[上报位置] ✗ 上报失败: ${it.message}")
            }
        )
    }
}
