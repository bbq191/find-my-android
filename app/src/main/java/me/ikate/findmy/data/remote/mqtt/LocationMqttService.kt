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
            else -> {
                Log.w(TAG, "[MQTT消息] → 类型: 未知主题")
            }
        }
    }

    /**
     * 处理位置消息
     */
    private suspend fun handleLocationMessage(payload: String) {
        Log.i(TAG, "[位置消息] 开始处理位置更新...")
        val locationMessage = LocationMessage.fromJson(payload)
        if (locationMessage != null) {
            Log.i(TAG, "========== [位置消息] 收到位置更新 ==========")
            Log.i(TAG, "[位置消息] 用户ID: ${locationMessage.userId}")
            Log.i(TAG, "[位置消息] 设备名: ${locationMessage.deviceName}")
            Log.i(TAG, "[位置消息] 坐标: (${locationMessage.latitude}, ${locationMessage.longitude})")
            Log.i(TAG, "[位置消息] 精度: ${locationMessage.accuracy}m")
            Log.i(TAG, "[位置消息] 电量: ${locationMessage.battery}%")
            Log.i(TAG, "[位置消息] 时间戳: ${locationMessage.timestamp}")
            Log.i(TAG, "[位置消息] 坐标系: ${locationMessage.coordType}")

            // 保存到设备表
            Log.d(TAG, "[位置消息] 步骤1: 保存到设备表...")
            database.deviceDao().insertOrUpdate(locationMessage.toEntity())

            // 同时更新联系人表的位置信息（包含电量和设备名）
            Log.d(TAG, "[位置消息] 步骤2: 更新联系人表位置信息...")
            database.contactDao().updateLocation(
                targetUserId = locationMessage.userId,
                latitude = locationMessage.latitude,
                longitude = locationMessage.longitude,
                lastUpdateTime = locationMessage.timestamp,
                deviceName = locationMessage.customName ?: locationMessage.deviceName,
                battery = locationMessage.battery
            )

            // 步骤3: 检查围栏触发
            Log.d(TAG, "[位置消息] 步骤3: 检查围栏触发...")
            checkGeofenceTrigger(locationMessage.userId, locationMessage.latitude, locationMessage.longitude)

            // 步骤4: 发送到流
            Log.d(TAG, "[位置消息] 步骤4: 发送到 locationUpdates 流...")
            _locationUpdates.emit(locationMessage.toDomain())
            Log.i(TAG, "[位置消息] ✓ 位置更新处理完成")
        } else {
            Log.w(TAG, "[位置消息] ✗ 无法解析位置消息: ${payload.take(200)}")
        }
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
     * 订阅当前用户的共享主题（接收邀请和响应）
     * @param userId 当前用户 ID
     */
    suspend fun subscribeToShareTopics(userId: String): Result<Unit> {
        val requestTopic = MqttConfig.getShareRequestTopic(userId)
        val responseTopic = MqttConfig.getShareResponseTopic(userId)

        val requestResult = mqttManager.subscribe(requestTopic, qos = 1)
        val responseResult = mqttManager.subscribe(responseTopic, qos = 1)

        return if (requestResult.isSuccess && responseResult.isSuccess) {
            Log.d(TAG, "已订阅共享主题: $userId")
            Result.success(Unit)
        } else {
            Log.e(TAG, "订阅共享主题失败")
            Result.failure(Exception("订阅共享主题失败"))
        }
    }

    /**
     * 订阅当前用户的请求主题（接收位置请求、发声请求等）
     * @param userId 当前用户 ID
     */
    suspend fun subscribeToRequestTopic(userId: String): Result<Unit> {
        val requestTopic = MqttConfig.getRequestTopic(userId)
        val result = mqttManager.subscribe(requestTopic, qos = 1)

        return if (result.isSuccess) {
            Log.d(TAG, "已订阅请求主题: $userId")
            Result.success(Unit)
        } else {
            Log.e(TAG, "订阅请求主题失败")
            Result.failure(Exception("订阅请求主题失败"))
        }
    }

    /**
     * 订阅当前用户的共享暂停状态主题
     * @param userId 当前用户 ID
     */
    suspend fun subscribeToSharePauseTopic(userId: String): Result<Unit> {
        val pauseTopic = MqttConfig.getSharePauseTopic(userId)
        val result = mqttManager.subscribe(pauseTopic, qos = 1)

        return if (result.isSuccess) {
            Log.d(TAG, "已订阅暂停状态主题: $userId")
            Result.success(Unit)
        } else {
            Log.e(TAG, "订阅暂停状态主题失败")
            Result.failure(Exception("订阅暂停状态主题失败"))
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
            return Result.success(Unit)
        }

        val locationTopic = MqttConfig.getLocationTopic(userId)
        val presenceTopic = MqttConfig.getPresenceTopic(userId)

        val locationResult = mqttManager.subscribe(locationTopic, qos = 1)
        val presenceResult = mqttManager.subscribe(presenceTopic, qos = 1)

        return if (locationResult.isSuccess && presenceResult.isSuccess) {
            subscribedUsers.add(userId)
            Log.d(TAG, "已订阅用户: $userId")
            Result.success(Unit)
        } else {
            Result.failure(Exception("订阅失败"))
        }
    }

    /**
     * 取消订阅用户
     */
    suspend fun unsubscribeFromUser(userId: String): Result<Unit> {
        val locationTopic = MqttConfig.getLocationTopic(userId)
        val presenceTopic = MqttConfig.getPresenceTopic(userId)

        mqttManager.unsubscribe(locationTopic)
        mqttManager.unsubscribe(presenceTopic)
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
     * 订阅当前用户的围栏事件主题
     * @param userId 当前用户 ID
     */
    suspend fun subscribeToGeofenceEventTopic(userId: String): Result<Unit> {
        val eventTopic = MqttConfig.getGeofenceEventTopic(userId)
        val result = mqttManager.subscribe(eventTopic, qos = 1)

        return if (result.isSuccess) {
            Log.d(TAG, "已订阅围栏事件主题: $userId")
            Result.success(Unit)
        } else {
            Log.e(TAG, "订阅围栏事件主题失败")
            Result.failure(Exception("订阅围栏事件主题失败"))
        }
    }

    /**
     * 订阅当前用户的围栏同步主题
     * @param userId 当前用户 ID
     */
    suspend fun subscribeToGeofenceSyncTopic(userId: String): Result<Unit> {
        val syncTopic = MqttConfig.getGeofenceSyncTopic(userId)
        val result = mqttManager.subscribe(syncTopic, qos = 1)

        return if (result.isSuccess) {
            Log.d(TAG, "已订阅围栏同步主题: $userId")
            Result.success(Unit)
        } else {
            Log.e(TAG, "订阅围栏同步主题失败")
            Result.failure(Exception("订阅围栏同步主题失败"))
        }
    }

    /**
     * 订阅所有围栏相关主题
     * @param userId 当前用户 ID
     */
    suspend fun subscribeToGeofenceTopics(userId: String): Result<Unit> {
        val eventResult = subscribeToGeofenceEventTopic(userId)
        val syncResult = subscribeToGeofenceSyncTopic(userId)

        return if (eventResult.isSuccess && syncResult.isSuccess) {
            Log.d(TAG, "已订阅所有围栏主题: $userId")
            Result.success(Unit)
        } else {
            Log.e(TAG, "订阅围栏主题失败")
            Result.failure(Exception("订阅围栏主题失败"))
        }
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
