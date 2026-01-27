package me.ikate.findmy.service

import android.content.Context
import android.util.Log
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import me.ikate.findmy.data.model.Geofence
import me.ikate.findmy.data.model.GeofenceEvent
import me.ikate.findmy.data.model.GeofenceEventType
import me.ikate.findmy.data.remote.mqtt.message.GeofenceSyncAction
import me.ikate.findmy.data.repository.AuthRepository
import me.ikate.findmy.data.repository.DeviceRepository
import me.ikate.findmy.data.repository.GeofenceRepository
import me.ikate.findmy.util.NotificationHelper

/**
 * 围栏事件处理器
 * 统一处理围栏触发事件的通知、存储和同步
 */
class GeofenceEventHandler(private val context: Context) {

    companion object {
        private const val TAG = "GeofenceEventHandler"

        @Volatile
        private var INSTANCE: GeofenceEventHandler? = null

        fun getInstance(context: Context): GeofenceEventHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GeofenceEventHandler(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val geofenceRepository = GeofenceRepository(context)
    private val mqttService by lazy { DeviceRepository.getMqttService(context) }

    // 事件流（供 UI 层订阅）
    private val _eventFlow = MutableSharedFlow<GeofenceEvent>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val eventFlow: Flow<GeofenceEvent> = _eventFlow.asSharedFlow()

    // 获取当前用户 ID
    private fun getCurrentUid(): String = AuthRepository.getUserId(context)

    // 获取当前用户名称
    private fun getCurrentUserName(): String {
        return context.getSharedPreferences("findmy_prefs", Context.MODE_PRIVATE)
            .getString("user_display_name", null) ?: "用户 ${getCurrentUid().take(6)}"
    }

    /**
     * 处理围栏触发事件
     * 当检测到联系人进入/离开围栏时调用
     *
     * @param geofence 触发的围栏
     * @param eventType 事件类型（进入/离开）
     * @param triggerLocation 触发位置
     */
    fun handleGeofenceTrigger(
        geofence: Geofence,
        eventType: GeofenceEventType,
        triggerLocation: LatLng
    ) {
        scope.launch {
            try {
                Log.i(TAG, "========== [围栏触发] ==========")
                Log.i(TAG, "[围栏触发] 联系人: ${geofence.contactName}")
                Log.i(TAG, "[围栏触发] 位置: ${geofence.locationName}")
                Log.i(TAG, "[围栏触发] 类型: $eventType")

                // 1. 记录事件到数据库
                val event = geofenceRepository.recordGeofenceEvent(
                    geofence = geofence,
                    eventType = eventType,
                    triggerLocation = triggerLocation
                )
                Log.d(TAG, "[围栏触发] 事件已记录: ${event.id}")

                // 2. 发送本地通知
                val isEntering = eventType == GeofenceEventType.ENTER || eventType == GeofenceEventType.DWELL
                NotificationHelper.showGeofenceEventNotification(
                    context = context,
                    contactName = geofence.contactName,
                    address = geofence.address.ifBlank { geofence.locationName },
                    isEntering = isEntering,
                    isLeftBehind = geofence.isLeftBehind,
                    radiusMeters = geofence.radiusMeters
                )

                // 3. 通过 MQTT 通知观察者（如果需要）
                // 注意：这里是"被监控者"触发围栏后通知"观察者"
                // 但在当前架构中，围栏检测是在观察者端进行的
                // 所以这里主要是发送到 eventFlow 供 UI 显示

                // 4. 发送到事件流
                _eventFlow.emit(event)

                // 5. 标记事件为已通知
                geofenceRepository.markEventAsNotified(event.id)

                Log.i(TAG, "[围栏触发] ✓ 事件处理完成")
            } catch (e: Exception) {
                Log.e(TAG, "[围栏触发] ✗ 事件处理失败", e)
            }
        }
    }

    /**
     * 通知对方同步围栏配置
     * 当用户 A 为用户 B 设置围栏时，需要通知 B 同步配置
     *
     * @param targetUserId 目标用户 ID（被监控者）
     * @param action 同步动作
     * @param geofenceId 围栏 ID
     */
    fun notifyGeofenceSync(
        targetUserId: String,
        action: GeofenceSyncAction,
        geofenceId: String? = null
    ) {
        scope.launch {
            try {
                mqttService.publishGeofenceSync(
                    targetUserId = targetUserId,
                    senderId = getCurrentUid(),
                    senderName = getCurrentUserName(),
                    action = action,
                    geofenceId = geofenceId
                )
                Log.d(TAG, "围栏同步通知已发送: $targetUserId -> $action")
            } catch (e: Exception) {
                Log.e(TAG, "发送围栏同步通知失败", e)
            }
        }
    }

    /**
     * 处理收到的围栏同步通知
     * 当收到对方的围栏配置变化通知时调用
     */
    fun handleGeofenceSyncNotification(action: GeofenceSyncAction, geofenceId: String?) {
        scope.launch {
            try {
                when (action) {
                    GeofenceSyncAction.SYNC_ALL -> {
                        // 从云端同步所有围栏
                        geofenceRepository.syncFromCloud()
                        Log.d(TAG, "已从云端同步所有围栏")
                    }
                    GeofenceSyncAction.ADDED, GeofenceSyncAction.UPDATED -> {
                        // 从云端同步最新配置
                        geofenceRepository.syncFromCloud()
                        Log.d(TAG, "围栏已更新: $geofenceId")
                    }
                    GeofenceSyncAction.REMOVED -> {
                        // 删除本地围栏
                        if (geofenceId != null) {
                            geofenceRepository.deleteGeofence(geofenceId)
                            Log.d(TAG, "围栏已删除: $geofenceId")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理围栏同步通知失败", e)
            }
        }
    }

    /**
     * 清理资源
     */
    fun destroy() {
        geofenceRepository.destroy()
    }
}
