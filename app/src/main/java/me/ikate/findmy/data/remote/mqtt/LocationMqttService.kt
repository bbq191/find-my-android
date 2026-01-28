package me.ikate.findmy.data.remote.mqtt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import me.ikate.findmy.data.local.FindMyDatabase
import me.ikate.findmy.data.local.entity.PendingMessageEntity
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.GeofenceEvent
import me.ikate.findmy.data.model.GeofenceEventType
import me.ikate.findmy.data.model.latLngOf
import me.ikate.findmy.data.remote.mqtt.message.GeofenceEventMessage
import me.ikate.findmy.data.remote.mqtt.message.GeofenceSyncAction
import me.ikate.findmy.data.remote.mqtt.message.GeofenceSyncMessage
import me.ikate.findmy.data.remote.mqtt.message.LocationMessage
import me.ikate.findmy.service.GeofenceEventHandler
import me.ikate.findmy.service.GeofenceForegroundService
import me.ikate.findmy.service.GeofenceManager
import me.ikate.findmy.data.remote.mqtt.message.PresenceMessage
import me.ikate.findmy.data.remote.mqtt.message.RequestMessage
import me.ikate.findmy.data.remote.mqtt.message.SharePauseMessage
import me.ikate.findmy.data.remote.mqtt.message.ShareRequestMessage
import me.ikate.findmy.data.remote.mqtt.message.ShareResponseMessage
import me.ikate.findmy.data.remote.mqtt.message.ShareResponseType
import me.ikate.findmy.domain.communication.CommunicationManager
import me.ikate.findmy.util.DeviceIdProvider

/**
 * 位置 MQTT 服务
 * 处理位置消息的发布和订阅
 */
class LocationMqttService(
    private val context: Context,
    private val mqttManager: MqttConnectionManager
) {
    companion object {
        private const val TAG = "LocationMqttService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = FindMyDatabase.getInstance(context)
    private val pendingMessageDao = database.pendingMessageDao()

    // 围栏管理器（用于检测联系人位置是否触发围栏）
    private val geofenceManager by lazy { GeofenceManager.getInstance(context) }
    private val geofenceEventHandler by lazy { GeofenceEventHandler.getInstance(context) }

    // 消息监听 Job，用于在 destroy 时取消
    private var messageObserverJob: Job? = null

    // 位置更新流（高频，缓冲区较大）
    // 使用 DROP_OLDEST 策略：当缓冲区满时丢弃最旧的消息，保证新消息不阻塞
    private val _locationUpdates = MutableSharedFlow<Device>(
        replay = 0,
        extraBufferCapacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val locationUpdates: Flow<Device> = _locationUpdates.asSharedFlow()

    // 在线状态更新流
    private val _presenceUpdates = MutableSharedFlow<Pair<String, Boolean>>(
        replay = 0,
        extraBufferCapacity = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val presenceUpdates: Flow<Pair<String, Boolean>> = _presenceUpdates.asSharedFlow()

    // 共享邀请请求流（重要消息，使用 SUSPEND 策略确保不丢失）
    private val _shareRequestUpdates = MutableSharedFlow<ShareRequestMessage>(
        replay = 1,  // 保留最新一条，防止订阅者错过
        extraBufferCapacity = 20,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val shareRequestUpdates: Flow<ShareRequestMessage> = _shareRequestUpdates.asSharedFlow()

    // 共享邀请响应流（重要消息）
    private val _shareResponseUpdates = MutableSharedFlow<ShareResponseMessage>(
        replay = 1,
        extraBufferCapacity = 20,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val shareResponseUpdates: Flow<ShareResponseMessage> = _shareResponseUpdates.asSharedFlow()

    // 请求消息流（位置请求、发声请求等）
    private val _requestUpdates = MutableSharedFlow<RequestMessage>(
        replay = 1,
        extraBufferCapacity = 20,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val requestUpdates: Flow<RequestMessage> = _requestUpdates.asSharedFlow()

    // 共享暂停状态更新流
    private val _sharePauseUpdates = MutableSharedFlow<SharePauseMessage>(
        replay = 1,
        extraBufferCapacity = 20,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sharePauseUpdates: Flow<SharePauseMessage> = _sharePauseUpdates.asSharedFlow()

    // 围栏事件更新流（重要通知）
    private val _geofenceEventUpdates = MutableSharedFlow<GeofenceEventMessage>(
        replay = 1,
        extraBufferCapacity = 20,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val geofenceEventUpdates: Flow<GeofenceEventMessage> = _geofenceEventUpdates.asSharedFlow()

    // 围栏同步通知流
    private val _geofenceSyncUpdates = MutableSharedFlow<GeofenceSyncMessage>(
        replay = 1,
        extraBufferCapacity = 20,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val geofenceSyncUpdates: Flow<GeofenceSyncMessage> = _geofenceSyncUpdates.asSharedFlow()

    // 当前订阅的用户 ID 列表
    private val subscribedUsers = mutableSetOf<String>()

    init {
        // 监听 MQTT 消息
        observeMqttMessages()
    }

    /**
     * 监听 MQTT 消息
     */
    private fun observeMqttMessages() {
        messageObserverJob = scope.launch {
            mqttManager.messageFlow.collect { message ->
                handleMessage(message)
            }
        }
    }

    /**
     * 处理收到的消息
     */
    private suspend fun handleMessage(message: MqttConnectionManager.ReceivedMessage) {
        Log.i(TAG, "========== [MQTT消息] 收到消息 ==========")
        Log.i(TAG, "[MQTT消息] 主题: ${message.topic}")
        Log.i(TAG, "[MQTT消息] QoS: ${message.qos}, 保留: ${message.retained}")
        Log.d(TAG, "[MQTT消息] 内容: ${message.payload.take(200)}")

        // 消息去重检查
        try {
            val communicationManager = CommunicationManager.getInstance(context)
            val messageId = communicationManager.generateMessageId(message.topic, message.payload)

            if (communicationManager.isMessageProcessed(messageId)) {
                Log.d(TAG, "[MQTT消息] 消息已处理过，跳过: ${message.topic}")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "[MQTT消息] 消息去重检查失败", e)
        }

        when {
            message.topic.startsWith(MqttConfig.TOPIC_LOCATION_PREFIX) -> {
                Log.i(TAG, "[MQTT消息] → 类型: 位置更新")
                handleLocationMessage(message.payload)
            }
            message.topic.startsWith(MqttConfig.TOPIC_PRESENCE_PREFIX) -> {
                Log.i(TAG, "[MQTT消息] → 类型: 在线状态")
                handlePresenceMessage(message.payload)
            }
            message.topic.startsWith(MqttConfig.TOPIC_SHARE_REQUEST_PREFIX) -> {
                Log.i(TAG, "[MQTT消息] → 类型: 共享邀请")
                handleShareRequestMessage(message.payload)
            }
            message.topic.startsWith(MqttConfig.TOPIC_SHARE_RESPONSE_PREFIX) -> {
                Log.i(TAG, "[MQTT消息] → 类型: 共享响应")
                handleShareResponseMessage(message.payload)
            }
            message.topic.startsWith(MqttConfig.TOPIC_REQUEST_PREFIX) -> {
                Log.i(TAG, "[MQTT消息] → 类型: 位置/声音请求")
                handleRequestMessage(message.payload)
            }
            message.topic.startsWith(MqttConfig.TOPIC_SHARE_PAUSE_PREFIX) -> {
                Log.i(TAG, "[MQTT消息] → 类型: 共享暂停")
                handleSharePauseMessage(message.payload)
            }
            message.topic.startsWith(MqttConfig.TOPIC_GEOFENCE_EVENT_PREFIX) -> {
                Log.i(TAG, "[MQTT消息] → 类型: 围栏事件")
                handleGeofenceEventMessage(message.payload)
            }
            message.topic.startsWith(MqttConfig.TOPIC_GEOFENCE_SYNC_PREFIX) -> {
                Log.i(TAG, "[MQTT消息] → 类型: 围栏同步")
                handleGeofenceSyncMessage(message.payload)
            }
            message.topic.startsWith(MqttConfig.TOPIC_DEBUG_PREFIX) -> {
                Log.i(TAG, "[MQTT消息] → 类型: 调试消息")
                handleDebugMessage(message.payload)
            }
            else -> {
                Log.w(TAG, "[MQTT消息] → 类型: 未知主题")
            }
        }
    }

    /**
     * 处理位置消息
     * 仅处理联系人（他人）的位置更新，忽略自己发布后被 MQTT 回传的消息
     */
    private suspend fun handleLocationMessage(payload: String) {
        val locationMessage = LocationMessage.fromJson(payload)
        if (locationMessage == null) {
            Log.w(TAG, "[位置消息] ✗ 无法解析位置消息: ${payload.take(200)}")
            return
        }

        // 过滤自己的位置消息（自己发布后会被 MQTT 回传，无需处理）
        val myUserId = DeviceIdProvider.getInstance(context).getDeviceId()
        if (locationMessage.userId == myUserId) {
            Log.d(TAG, "[位置消息] 忽略自己的位置回传: ${locationMessage.userId}")
            return
        }

        Log.i(TAG, "========== [位置消息] 收到联系人位置更新 ==========")
        Log.i(TAG, "[位置消息] 用户ID: ${locationMessage.userId}")
        Log.i(TAG, "[位置消息] 设备名: ${locationMessage.deviceName}")
        Log.i(TAG, "[位置消息] 坐标: (${locationMessage.latitude}, ${locationMessage.longitude})")
        Log.i(TAG, "[位置消息] 电量: ${locationMessage.battery}%")
        Log.i(TAG, "[位置消息] 时间戳: ${locationMessage.timestamp}")

        // 步骤1: 保存到设备表
        database.deviceDao().insertOrUpdate(locationMessage.toEntity())

        // 步骤2: 更新联系人表的位置信息（包含电量和设备名）
        database.contactDao().updateLocation(
            targetUserId = locationMessage.userId,
            latitude = locationMessage.latitude,
            longitude = locationMessage.longitude,
            lastUpdateTime = locationMessage.timestamp,
            deviceName = locationMessage.customName ?: locationMessage.deviceName,
            battery = locationMessage.battery
        )

        // 步骤3: 检查围栏触发
        checkGeofenceTrigger(locationMessage.userId, locationMessage.latitude, locationMessage.longitude)

        // 步骤4: 发送到流
        _locationUpdates.emit(locationMessage.toDomain())
        Log.i(TAG, "[位置消息] ✓ 位置更新处理完成: ${locationMessage.userId}")
    }

    /**
     * 处理在线状态消息
     */
    private suspend fun handlePresenceMessage(payload: String) {
        val presenceMessage = PresenceMessage.fromJson(payload)
        if (presenceMessage != null) {
            Log.d(TAG, "收到在线状态更新: ${presenceMessage.deviceId} -> ${presenceMessage.status}")
            val isOnline = presenceMessage.status == me.ikate.findmy.data.remote.mqtt.message.PresenceStatus.ONLINE
            // 更新本地数据库
            if (!isOnline) {
                database.deviceDao().markOffline(presenceMessage.deviceId)
            }
            // 发送到流
            _presenceUpdates.emit(presenceMessage.deviceId to isOnline)
        }
    }

    /**
     * 处理共享邀请请求消息
     */
    private suspend fun handleShareRequestMessage(payload: String) {
        val shareRequest = ShareRequestMessage.fromJson(payload)
        if (shareRequest != null) {
            Log.d(TAG, "收到共享邀请: ${shareRequest.senderName} (${shareRequest.senderId})")
            // 发送到流，让 ViewModel 处理
            _shareRequestUpdates.emit(shareRequest)
        } else {
            Log.w(TAG, "无法解析共享邀请消息: ${payload.take(100)}")
        }
    }

    /**
     * 处理共享邀请响应消息
     */
    private suspend fun handleShareResponseMessage(payload: String) {
        val shareResponse = ShareResponseMessage.fromJson(payload)
        if (shareResponse != null) {
            Log.d(TAG, "收到共享响应: ${shareResponse.responderName} -> ${shareResponse.response}")
            // 发送到流，让 ViewModel 处理
            _shareResponseUpdates.emit(shareResponse)
        } else {
            Log.w(TAG, "无法解析共享响应消息: ${payload.take(100)}")
        }
    }

    /**
     * 处理请求消息（位置请求、发声请求等）
     */
    private suspend fun handleRequestMessage(payload: String) {
        Log.i(TAG, "[请求消息] 开始处理请求...")
        val request = RequestMessage.fromJson(payload)
        if (request != null) {
            Log.i(TAG, "========== [请求消息] 收到请求 ==========")
            Log.i(TAG, "[请求消息] 请求类型: ${request.type}")
            Log.i(TAG, "[请求消息] 请求者UID: ${request.requesterUid}")
            Log.i(TAG, "[请求消息] 目标UID: ${request.targetUid}")
            Log.i(TAG, "[请求消息] 时间戳: ${request.timestamp}")
            if (request.message != null) {
                Log.i(TAG, "[请求消息] 附加消息: ${request.message}")
            }
            // 发送到流，让 ViewModel 处理
            Log.d(TAG, "[请求消息] 发送到 requestUpdates 流...")
            _requestUpdates.emit(request)
            Log.i(TAG, "[请求消息] ✓ 请求已转发到 ViewModel")
        } else {
            Log.w(TAG, "[请求消息] ✗ 无法解析请求消息: ${payload.take(200)}")
        }
    }

    /**
     * 处理共享暂停状态消息（包括过期）
     * 过期是一种特殊的暂停：isExpired=true
     */
    private suspend fun handleSharePauseMessage(payload: String) {
        val pauseMessage = SharePauseMessage.fromJson(payload)
        if (pauseMessage != null) {
            Log.d(TAG, "收到共享暂停状态: ${pauseMessage.senderName} -> isPaused=${pauseMessage.isPaused}, isExpired=${pauseMessage.isExpired}")

            if (pauseMessage.isExpired) {
                // 过期是一种特殊的暂停，更新状态为 EXPIRED
                database.contactDao().updateExpiredStatusByTargetUserId(
                    targetUserId = pauseMessage.senderId
                )
                // 清除位置信息
                database.contactDao().clearLocationByTargetUserId(pauseMessage.senderId)
                Log.d(TAG, "共享已过期，状态更新为 EXPIRED")
            } else {
                // 普通的暂停/恢复
                database.contactDao().updatePauseStatusByTargetUserId(
                    targetUserId = pauseMessage.senderId,
                    isPaused = pauseMessage.isPaused
                )
                // 如果对方暂停了共享，清除该联系人的位置信息
                if (pauseMessage.isPaused) {
                    database.contactDao().clearLocationByTargetUserId(pauseMessage.senderId)
                }
            }

            // 发送到流，让 ViewModel 处理（可用于显示 Toast 等）
            _sharePauseUpdates.emit(pauseMessage)
        } else {
            Log.w(TAG, "无法解析暂停状态消息: ${payload.take(100)}")
        }
    }

    /**
     * 检查联系人位置是否触发围栏 (iOS Find My 风格)
     * 当收到联系人位置更新时调用
     */
    private fun checkGeofenceTrigger(contactUserId: String, latitude: Double, longitude: Double) {
        try {
            // 查找该联系人对应的围栏（通过 targetUserId 关联）
            val geofenceData = geofenceManager.activeGeofences.value.find { geofence ->
                // 围栏的 contactId 实际上存储的是共享记录 ID
                // 需要通过数据库查询获取 targetUserId 匹配
                // 这里简化处理：直接使用 userId 匹配
                geofence.contactId == contactUserId
            }

            if (geofenceData != null) {
                val contactLocation = com.tencent.tencentmap.mapsdk.maps.model.LatLng(latitude, longitude)
                // 获取我的当前位置（用于 LEFT_BEHIND 类型）
                val ownerLocation = GeofenceForegroundService.getOwnerLocation()
                geofenceManager.checkGeofenceForContact(contactUserId, contactLocation, ownerLocation)
                Log.d(TAG, "[围栏检测] 已检查联系人 $contactUserId 的围栏状态, 我的位置: $ownerLocation")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[围栏检测] 检查围栏触发失败", e)
        }
    }

    /**
     * 处理围栏事件消息
     */
    private suspend fun handleGeofenceEventMessage(payload: String) {
        val eventMessage = GeofenceEventMessage.fromJson(payload)
        if (eventMessage != null) {
            Log.i(TAG, "========== [围栏事件] 收到事件 ==========")
            Log.i(TAG, "[围栏事件] 联系人: ${eventMessage.contactName}")
            Log.i(TAG, "[围栏事件] 位置: ${eventMessage.locationName}")
            Log.i(TAG, "[围栏事件] 类型: ${eventMessage.eventType}")
            Log.i(TAG, "[围栏事件] 坐标: (${eventMessage.latitude}, ${eventMessage.longitude})")
            // 发送到流，让 ViewModel 处理
            _geofenceEventUpdates.emit(eventMessage)
        } else {
            Log.w(TAG, "无法解析围栏事件消息: ${payload.take(100)}")
        }
    }

    /**
     * 处理围栏同步消息
     */
    private suspend fun handleGeofenceSyncMessage(payload: String) {
        val syncMessage = GeofenceSyncMessage.fromJson(payload)
        if (syncMessage != null) {
            Log.d(TAG, "收到围栏同步通知: ${syncMessage.senderName} -> ${syncMessage.action}")
            // 发送到流，让 ViewModel 处理同步
            _geofenceSyncUpdates.emit(syncMessage)
        } else {
            Log.w(TAG, "无法解析围栏同步消息: ${payload.take(100)}")
        }
    }

    /**
     * 处理调试消息（来自其他设备的调试反馈）
     */
    private fun handleDebugMessage(payload: String) {
        try {
            val json = com.google.gson.JsonParser.parseString(payload).asJsonObject
            val fromUid = json.get("fromUid")?.asString ?: "unknown"
            val status = json.get("status")?.asString ?: "unknown"
            val message = json.get("message")?.asString ?: ""
            val timestamp = json.get("timestamp")?.asLong ?: 0

            Log.i(TAG, "")
            Log.i(TAG, "╔════════════════════════════════════════════════════════════════╗")
            Log.i(TAG, "║  [远程调试ACK] 收到对端反馈                                     ║")
            Log.i(TAG, "╠════════════════════════════════════════════════════════════════╣")
            Log.i(TAG, "║  来源设备: $fromUid")
            Log.i(TAG, "║  状态: $status")
            Log.i(TAG, "║  消息: $message")
            Log.i(TAG, "║  时间戳: $timestamp")
            Log.i(TAG, "╚════════════════════════════════════════════════════════════════╝")
            Log.i(TAG, "")
        } catch (e: Exception) {
            Log.w(TAG, "[调试消息] 解析失败: ${e.message}")
        }
    }

    /**
     * 订阅调试消息主题
     */
    suspend fun subscribeToDebugTopic(userId: String): Result<Unit> {
        val debugTopic = MqttConfig.getDebugTopic(userId)
        val result = mqttManager.subscribe(debugTopic, qos = 0)
        result.fold(
            onSuccess = { Log.d(TAG, "已订阅调试主题: $userId") },
            onFailure = { Log.e(TAG, "订阅调试主题失败: $userId", it) }
        )
        return result
    }

    /**
     * 发布位置更新
     * @param device 设备信息
     * @return 发布结果
     */
    suspend fun publishLocation(device: Device): Result<Unit> {
        Log.i(TAG, "========== [发布位置] 开始发布 ==========")
        val message = LocationMessage.fromDomain(device)
        val topic = MqttConfig.getLocationTopic(device.ownerId)
        val payload = message.toJson()

        Log.i(TAG, "[发布位置] 所有者ID: ${device.ownerId}")
        Log.i(TAG, "[发布位置] 坐标: (${device.location.latitude}, ${device.location.longitude})")
        Log.i(TAG, "[发布位置] 主题: $topic")
        Log.d(TAG, "[发布位置] 消息: ${payload.take(200)}")

        // 先保存到本地数据库
        Log.d(TAG, "[发布位置] 步骤1: 保存到本地数据库...")
        database.deviceDao().insertOrUpdate(message.toEntity())

        // 尝试发布
        Log.d(TAG, "[发布位置] 步骤2: 发布到 MQTT...")
        val result = mqttManager.publish(
            topic = topic,
            payload = payload,
            qos = 1,
            retained = true // 保留最后一条位置消息
        )

        result.fold(
            onSuccess = {
                Log.i(TAG, "[发布位置] ✓ 位置发布成功")
            },
            onFailure = { error ->
                // 发布失败，加入离线队列
                Log.w(TAG, "[发布位置] ✗ 位置发布失败，加入离线队列: ${error.message}")
                queueMessage(topic, payload, qos = 1, retained = true)
            }
        )

        return result
    }

    /**
     * 发布在线状态
     */
    suspend fun publishPresence(deviceId: String, userId: String, isOnline: Boolean): Result<Unit> {
        val message = if (isOnline) {
            PresenceMessage.online(deviceId, userId)
        } else {
            PresenceMessage.offline(deviceId, userId)
        }
        val topic = MqttConfig.getPresenceTopic(userId)
        val payload = message.toJson()

        return mqttManager.publish(
            topic = topic,
            payload = payload,
            qos = 1,
            retained = true
        )
    }

    /**
     * 发送共享邀请请求
     * @param targetUserId 目标用户 ID
     * @param shareId 共享 ID
     * @param senderId 发送者用户 ID
     * @param senderName 发送者名称
     * @param senderEmail 发送者邮箱
     * @param expireTime 过期时间
     * @return 发送结果
     */
    suspend fun publishShareRequest(
        targetUserId: String,
        shareId: String,
        senderId: String,
        senderName: String,
        senderEmail: String? = null,
        expireTime: Long? = null
    ): Result<Unit> {
        val message = ShareRequestMessage(
            shareId = shareId,
            senderId = senderId,
            senderName = senderName,
            senderEmail = senderEmail,
            expireTime = expireTime
        )
        val topic = MqttConfig.getShareRequestTopic(targetUserId)
        val payload = message.toJson()

        Log.d(TAG, "发送共享邀请到: $targetUserId")

        val result = mqttManager.publish(
            topic = topic,
            payload = payload,
            qos = 1,
            retained = false
        )

        if (result.isFailure) {
            Log.w(TAG, "共享邀请发送失败，加入离线队列")
            queueMessage(topic, payload, qos = 1, retained = false)
        }

        return result
    }

    /**
     * 发送共享邀请响应（接受/拒绝）
     * @param targetUserId 目标用户 ID（邀请发起者）
     * @param shareId 共享 ID
     * @param responderId 响应者用户 ID
     * @param responderName 响应者名称
     * @param accepted 是否接受
     * @return 发送结果
     */
    suspend fun publishShareResponse(
        targetUserId: String,
        shareId: String,
        responderId: String,
        responderName: String,
        accepted: Boolean
    ): Result<Unit> {
        val message = if (accepted) {
            ShareResponseMessage.accepted(shareId, responderId, responderName)
        } else {
            ShareResponseMessage.rejected(shareId, responderId, responderName)
        }
        val topic = MqttConfig.getShareResponseTopic(targetUserId)
        val payload = message.toJson()

        Log.d(TAG, "发送共享响应到: $targetUserId, accepted=$accepted")

        val result = mqttManager.publish(
            topic = topic,
            payload = payload,
            qos = 1,
            retained = false
        )

        if (result.isFailure) {
            Log.w(TAG, "共享响应发送失败，加入离线队列")
            queueMessage(topic, payload, qos = 1, retained = false)
        }

        return result
    }

    /**
     * 发送共享移除通知（通知对方你已被移出联系人列表）
     * @param targetUserId 目标用户 ID
     * @param shareId 共享 ID
     * @param responderId 响应者用户 ID（移除者）
     * @param responderName 响应者名称
     * @return 发送结果
     */
    suspend fun publishShareRemove(
        targetUserId: String,
        shareId: String,
        responderId: String,
        responderName: String
    ): Result<Unit> {
        val message = ShareResponseMessage.removed(shareId, responderId, responderName)
        val topic = MqttConfig.getShareResponseTopic(targetUserId)
        val payload = message.toJson()

        Log.d(TAG, "发送移除通知到: $targetUserId")

        val result = mqttManager.publish(
            topic = topic,
            payload = payload,
            qos = 1,
            retained = false
        )

        if (result.isFailure) {
            Log.w(TAG, "移除通知发送失败，加入离线队列")
            queueMessage(topic, payload, qos = 1, retained = false)
        }

        return result
    }

    /**
     * 订阅所有系统主题（使用通配符合并，节省 EMQX Serverless 订阅配额）
     *
     * EMQX Cloud Serverless 限制每个客户端最多 10 个订阅。
     * 使用通配符将 6 个系统主题合并为 3 个：
     *   - findmy/share/+/{uid}     → 覆盖 share/request + share/response + share/pause
     *   - findmy/requests/{uid}    → 位置请求（无法通配，路径层级不同）
     *   - findmy/geofence/+/{uid}  → 覆盖 geofence/events + geofence/sync
     * 剩余 7 个配额可用于联系人位置订阅。
     *
     * @param userId 当前用户 ID
     */
    suspend fun subscribeToSystemTopics(userId: String): Result<Unit> {
        val shareWildcard = MqttConfig.getShareWildcardTopic(userId)
        val requestTopic = MqttConfig.getRequestTopic(userId)
        val geofenceWildcard = MqttConfig.getGeofenceWildcardTopic(userId)

        Log.i(TAG, "[系统订阅] 开始订阅系统主题（通配符模式）...")
        Log.i(TAG, "[系统订阅] 1. 共享通配符: $shareWildcard")
        Log.i(TAG, "[系统订阅] 2. 请求主题: $requestTopic")
        Log.i(TAG, "[系统订阅] 3. 围栏通配符: $geofenceWildcard")

        val shareResult = mqttManager.subscribe(shareWildcard, qos = 1)
        if (shareResult.isFailure) {
            Log.e(TAG, "[系统订阅] 共享通配符订阅失败: ${shareResult.exceptionOrNull()?.message}")
        }

        val requestResult = mqttManager.subscribe(requestTopic, qos = 1)
        if (requestResult.isFailure) {
            Log.e(TAG, "[系统订阅] 请求主题订阅失败: ${requestResult.exceptionOrNull()?.message}")
        }

        val geofenceResult = mqttManager.subscribe(geofenceWildcard, qos = 1)
        if (geofenceResult.isFailure) {
            Log.e(TAG, "[系统订阅] 围栏通配符订阅失败: ${geofenceResult.exceptionOrNull()?.message}")
        }

        val allSuccess = shareResult.isSuccess && requestResult.isSuccess && geofenceResult.isSuccess
        Log.i(TAG, "[系统订阅] 完成: share=${shareResult.isSuccess}, request=${requestResult.isSuccess}, geofence=${geofenceResult.isSuccess}")

        return if (allSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("部分系统主题订阅失败"))
        }
    }

    /**
     * 发送共享暂停状态通知
     * @param targetUserId 目标用户 ID
     * @param senderId 发送者用户 ID
     * @param senderName 发送者名称
     * @param isPaused 是否暂停
     * @return 发送结果
     */
    suspend fun publishSharePause(
        targetUserId: String,
        senderId: String,
        senderName: String,
        isPaused: Boolean
    ): Result<Unit> {
        val message = if (isPaused) {
            SharePauseMessage.paused(senderId, senderName)
        } else {
            SharePauseMessage.resumed(senderId, senderName)
        }
        val topic = MqttConfig.getSharePauseTopic(targetUserId)
        val payload = message.toJson()

        Log.d(TAG, "发送共享暂停状态到: $targetUserId, isPaused=$isPaused")

        val result = mqttManager.publish(
            topic = topic,
            payload = payload,
            qos = 1,
            retained = false
        )

        if (result.isFailure) {
            Log.w(TAG, "共享暂停状态发送失败，加入离线队列")
            queueMessage(topic, payload, qos = 1, retained = false)
        }

        return result
    }

    /**
     * 发送共享过期通知（过期是一种特殊的暂停）
     * @param targetUserId 目标用户 ID
     * @param senderId 发送者用户 ID
     * @param senderName 发送者名称
     * @return 发送结果
     */
    suspend fun publishShareExpired(
        targetUserId: String,
        senderId: String,
        senderName: String
    ): Result<Unit> {
        val message = SharePauseMessage.expired(senderId, senderName)
        val topic = MqttConfig.getSharePauseTopic(targetUserId)
        val payload = message.toJson()

        Log.d(TAG, "发送共享过期通知到: $targetUserId")

        val result = mqttManager.publish(
            topic = topic,
            payload = payload,
            qos = 1,
            retained = false
        )

        if (result.isFailure) {
            Log.w(TAG, "共享过期通知发送失败，加入离线队列")
            queueMessage(topic, payload, qos = 1, retained = false)
        }

        return result
    }

    /**
     * 订阅用户的位置更新
     * @param userId 要订阅的用户 ID
     */
    suspend fun subscribeToUser(userId: String): Result<Unit> {
        if (subscribedUsers.contains(userId)) {
            Log.d(TAG, "[订阅用户] 已在本地订阅列表中: $userId，跳过")
            return Result.success(Unit)
        }

        // 只订阅 location 主题（节省 EMQX 订阅配额，每个主题占 1 个配额）
        // presence 不再订阅：在线状态可通过 location 更新时间推断
        val locationTopic = MqttConfig.getLocationTopic(userId)

        Log.i(TAG, "[订阅用户] 开始订阅: $userId")
        Log.i(TAG, "[订阅用户] 位置主题: $locationTopic")

        for (attempt in 1..3) {
            val result = mqttManager.subscribe(locationTopic, qos = 1)
            if (result.isSuccess) {
                subscribedUsers.add(userId)
                Log.i(TAG, "[订阅用户] ✓ 订阅成功: $userId (第${attempt}次, 当前订阅数: ${subscribedUsers.size})")
                return Result.success(Unit)
            } else {
                Log.w(TAG, "[订阅用户] ✗ 订阅失败: $userId (第${attempt}次): ${result.exceptionOrNull()?.message}")
                if (attempt < 3) kotlinx.coroutines.delay(500L * attempt)
            }
        }

        Log.e(TAG, "[订阅用户] ✗ 订阅最终失败: $userId (已重试3次)")
        return Result.failure(Exception("位置主题订阅失败"))
    }

    /**
     * 清除已订阅用户缓存
     * 用于 MQTT 重连后强制重新订阅（因为 broker 端订阅已丢失）
     */
    fun clearSubscribedUsers() {
        val count = subscribedUsers.size
        subscribedUsers.clear()
        Log.i(TAG, "[订阅缓存] 已清除订阅缓存，原有 $count 个用户")
    }

    /**
     * 取消订阅用户
     */
    suspend fun unsubscribeFromUser(userId: String): Result<Unit> {
        val locationTopic = MqttConfig.getLocationTopic(userId)

        mqttManager.unsubscribe(locationTopic)
        subscribedUsers.remove(userId)

        Log.d(TAG, "已取消订阅用户: $userId")
        return Result.success(Unit)
    }

    /**
     * 订阅多个用户
     */
    suspend fun subscribeToUsers(userIds: List<String>) {
        userIds.forEach { userId ->
            subscribeToUser(userId)
        }
    }

    /**
     * 将消息加入离线队列
     */
    private suspend fun queueMessage(
        topic: String,
        payload: String,
        qos: Int = 1,
        retained: Boolean = false
    ) {
        val entity = PendingMessageEntity(
            topic = topic,
            payload = payload,
            qos = qos,
            retained = retained
        )
        pendingMessageDao.insert(entity)
        Log.d(TAG, "消息已加入离线队列: $topic")
    }

    /**
     * 发送离线队列中的消息
     */
    suspend fun flushPendingMessages(): Int {
        val pendingMessages = pendingMessageDao.getPendingMessages()
        var sentCount = 0

        for (message in pendingMessages) {
            pendingMessageDao.markSending(message.id)

            val result = mqttManager.publish(
                topic = message.topic,
                payload = message.payload,
                qos = message.qos,
                retained = message.retained
            )

            if (result.isSuccess) {
                pendingMessageDao.markSent(message.id)
                sentCount++
            } else {
                pendingMessageDao.markFailedAndRetry(message.id)
            }
        }

        // 清理已发送的消息
        pendingMessageDao.deleteSentMessages()

        // 只在有消息处理时才输出日志
        if (pendingMessages.isNotEmpty()) {
            Log.d(TAG, "离线队列处理完成: 发送 $sentCount/${pendingMessages.size} 条消息")
        }
        return sentCount
    }

    /**
     * 获取离线消息数量
     */
    suspend fun getPendingMessageCount(): Int {
        return pendingMessageDao.getPendingCount()
    }

    // ==================== 围栏相关方法 ====================

    /**
     * 发布围栏触发事件
     * 当联系人进入/离开围栏时，通知观察者
     *
     * @param targetUserId 观察者用户 ID
     * @param event 围栏事件
     * @return 发送结果
     */
    suspend fun publishGeofenceEvent(
        targetUserId: String,
        event: GeofenceEvent
    ): Result<Unit> {
        val message = GeofenceEventMessage.fromDomain(event)
        val topic = MqttConfig.getGeofenceEventTopic(targetUserId)
        val payload = message.toJson()

        Log.d(TAG, "发送围栏事件到: $targetUserId, 类型: ${event.eventType}")

        val result = mqttManager.publish(
            topic = topic,
            payload = payload,
            qos = 1,
            retained = false
        )

        if (result.isFailure) {
            Log.w(TAG, "围栏事件发送失败，加入离线队列")
            queueMessage(topic, payload, qos = 1, retained = false)
        }

        return result
    }

    /**
     * 发布围栏同步通知
     * 当围栏配置变化时，通知相关用户同步
     *
     * @param targetUserId 目标用户 ID
     * @param senderId 发送者用户 ID
     * @param senderName 发送者名称
     * @param action 同步动作
     * @param geofenceId 围栏 ID（可选）
     * @return 发送结果
     */
    suspend fun publishGeofenceSync(
        targetUserId: String,
        senderId: String,
        senderName: String,
        action: GeofenceSyncAction,
        geofenceId: String? = null
    ): Result<Unit> {
        val message = GeofenceSyncMessage(
            senderId = senderId,
            senderName = senderName,
            action = action,
            geofenceId = geofenceId
        )
        val topic = MqttConfig.getGeofenceSyncTopic(targetUserId)
        val payload = message.toJson()

        Log.d(TAG, "发送围栏同步通知到: $targetUserId, 动作: $action")

        val result = mqttManager.publish(
            topic = topic,
            payload = payload,
            qos = 1,
            retained = false
        )

        if (result.isFailure) {
            Log.w(TAG, "围栏同步通知发送失败，加入离线队列")
            queueMessage(topic, payload, qos = 1, retained = false)
        }

        return result
    }

    /**
     * 发布 FCM Token 到 MQTT 服务器
     * 服务器端可据此向设备发送 FCM 推送
     *
     * @param userId 用户 ID
     * @param fcmToken FCM Token
     */
    suspend fun publishFcmToken(userId: String, fcmToken: String): Result<Unit> {
        val topic = MqttConfig.getFcmTokenTopic(userId)
        val payload = """{"userId":"$userId","fcmToken":"$fcmToken","timestamp":${System.currentTimeMillis()}}"""

        Log.d(TAG, "发布 FCM Token: $userId")

        val result = mqttManager.publish(
            topic = topic,
            payload = payload,
            qos = 1,
            retained = true  // 保留最新 Token
        )

        if (result.isFailure) {
            Log.w(TAG, "FCM Token 发布失败，加入离线队列")
            queueMessage(topic, payload, qos = 1, retained = true)
        }

        return result
    }

    /**
     * 销毁服务，释放资源
     * 取消协程作用域和消息监听
     */
    fun destroy() {
        Log.d(TAG, "销毁 LocationMqttService")
        messageObserverJob?.cancel()
        messageObserverJob = null
        scope.cancel()
        subscribedUsers.clear()
    }
}
