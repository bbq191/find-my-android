package me.ikate.findmy.service

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 位置追踪管理器
 * 负责处理位置请求、连续追踪的启动/停止
 */
class LocationTrackingManager(
    private val firestore: FirebaseFirestore,
    private val locationReportService: LocationReportService,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "LocationTrackingManager"
        private const val COLLECTION_LOCATION_REQUESTS = "locationRequests"
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

    // ====================================================================
    // 通用请求执行器
    // ====================================================================

    /**
     * 执行 Firebase 请求的通用方法
     *
     * @param currentUid 当前用户的 UID（必须非空）
     * @param targetUid 目标用户的 UID
     * @param type 请求类型
     * @param additionalData 额外数据字段
     * @param errorMessagePrefix 错误消息前缀
     * @param onSuccess 成功回调（可选）
     */
    private fun executeRequest(
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

                // 构建请求数据
                val requestData = hashMapOf<String, Any>(
                    "requesterUid" to currentUid,
                    "targetUid" to targetUid,
                    "type" to type,
                    "timestamp" to System.currentTimeMillis(),
                    "status" to "pending"
                ).apply {
                    putAll(additionalData)
                }

                firestore.collection(COLLECTION_LOCATION_REQUESTS)
                    .add(requestData)
                    .await()

                Log.d(TAG, "请求已创建: $type")
                onSuccess?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "请求失败: $type", e)
                _errorMessage.value = "$errorMessagePrefix: ${e.localizedMessage}"
            }
        }
    }

    // ====================================================================
    // 位置请求
    // ====================================================================

    /**
     * 请求联系人的实时位置更新
     *
     * @param currentUid 当前用户的 UID
     * @param targetUid 目标用户的 UID
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

                // 创建位置请求记录
                val requestData = hashMapOf(
                    "requesterUid" to currentUid,
                    "targetUid" to targetUid,
                    "type" to "single",
                    "timestamp" to System.currentTimeMillis(),
                    "status" to "pending"
                )

                firestore.collection(COLLECTION_LOCATION_REQUESTS)
                    .add(requestData)
                    .await()

                Log.d(TAG, "位置请求已创建")

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
     *
     * @param currentUid 当前用户的 UID
     * @param targetUid 目标联系人的 UID
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

                // 创建连续追踪请求
                val trackingData = hashMapOf(
                    "requesterUid" to currentUid,
                    "targetUid" to targetUid,
                    "type" to "continuous",
                    "timestamp" to System.currentTimeMillis(),
                    "status" to "pending"
                )

                firestore.collection(COLLECTION_LOCATION_REQUESTS)
                    .add(trackingData)
                    .await()

                Log.d(TAG, "连续追踪请求已创建")

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
        executeRequest(
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
        executeRequest(
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
        executeRequest(
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
        executeRequest(
            currentUid = currentUid,
            targetUid = targetUid,
            type = "enable_lost_mode",
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
        executeRequest(
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
        val result = locationReportService.reportCurrentLocation()
        result.fold(
            onSuccess = { Log.d(TAG, "我的位置已上报成功") },
            onFailure = { Log.w(TAG, "上报我的位置失败: ${it.message}") }
        )
    }
}
