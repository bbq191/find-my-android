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
    }

    // 正在请求位置更新的联系人 UID
    private val _requestingLocationFor = MutableStateFlow<String?>(null)
    val requestingLocationFor: StateFlow<String?> = _requestingLocationFor.asStateFlow()

    // 正在连续追踪的联系人 UID
    private val _trackingContactUid = MutableStateFlow<String?>(null)
    val trackingContactUid: StateFlow<String?> = _trackingContactUid.asStateFlow()

    // 错误消息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * 通过 MQTT 发送请求
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
            Log.w(TAG, "当前用户未登录，无法执行请求: $type")
            return
        }

        scope.launch {
            try {
                Log.d(TAG, "执行请求: type=$type, targetUid=$targetUid")

                // 根据需要先上报自己的位置
                if (reportLocationFirst) {
                    reportMyLocationFirst()
                }

                // 通过 MQTT 发送请求
                if (MqttConfig.isConfigured()) {
                    val mqttManager = DeviceRepository.getMqttManager(context)
                    val requestData = mapOf(
                        "requesterUid" to currentUid,
                        "targetUid" to targetUid,
                        "type" to type,
                        "timestamp" to System.currentTimeMillis()
                    ) + additionalData

                    // 发布到目标用户的请求主题
                    val topic = "findmy/requests/$targetUid"
                    val payload = Gson().toJson(requestData)
                    mqttManager.publish(topic, payload)
                    Log.d(TAG, "MQTT 请求已发送: $type")
                } else {
                    Log.w(TAG, "MQTT 未配置，无法发送请求")
                }

                onSuccess?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "请求失败: $type", e)
                _errorMessage.value = "$errorMessagePrefix: ${e.localizedMessage}"
            }
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
        _trackingContactUid.value = null
        sendMqttRequest(
            currentUid = currentUid,
            targetUid = targetUid,
            type = "stop_continuous",
            errorMessagePrefix = "停止追踪失败"
        )
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
        val result = locationReportService.reportCurrentLocation()
        result.fold(
            onSuccess = { Log.d(TAG, "我的位置已上报成功") },
            onFailure = { Log.w(TAG, "上报我的位置失败: ${it.message}") }
        )
    }
}
