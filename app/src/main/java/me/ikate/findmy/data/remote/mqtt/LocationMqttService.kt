package me.ikate.findmy.data.remote.mqtt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import me.ikate.findmy.data.local.FindMyDatabase
import me.ikate.findmy.data.local.entity.PendingMessageEntity
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.remote.mqtt.message.LocationMessage
import me.ikate.findmy.data.remote.mqtt.message.PresenceMessage
import me.ikate.findmy.data.remote.mqtt.message.RequestMessage
import me.ikate.findmy.data.remote.mqtt.message.SharePauseMessage
import me.ikate.findmy.data.remote.mqtt.message.ShareRequestMessage
import me.ikate.findmy.data.remote.mqtt.message.ShareResponseMessage
import me.ikate.findmy.data.remote.mqtt.message.ShareResponseType

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

    // 位置更新流
    private val _locationUpdates = MutableSharedFlow<Device>(
        replay = 0,
        extraBufferCapacity = 50
    )
    val locationUpdates: Flow<Device> = _locationUpdates.asSharedFlow()

    // 在线状态更新流
    private val _presenceUpdates = MutableSharedFlow<Pair<String, Boolean>>(
        replay = 0,
        extraBufferCapacity = 50
    )
    val presenceUpdates: Flow<Pair<String, Boolean>> = _presenceUpdates.asSharedFlow()

    // 共享邀请请求流
    private val _shareRequestUpdates = MutableSharedFlow<ShareRequestMessage>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val shareRequestUpdates: Flow<ShareRequestMessage> = _shareRequestUpdates.asSharedFlow()

    // 共享邀请响应流
    private val _shareResponseUpdates = MutableSharedFlow<ShareResponseMessage>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val shareResponseUpdates: Flow<ShareResponseMessage> = _shareResponseUpdates.asSharedFlow()

    // 请求消息流（位置请求、发声请求等）
    private val _requestUpdates = MutableSharedFlow<RequestMessage>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val requestUpdates: Flow<RequestMessage> = _requestUpdates.asSharedFlow()

    // 共享暂停状态更新流
    private val _sharePauseUpdates = MutableSharedFlow<SharePauseMessage>(
        replay = 0,
        extraBufferCapacity = 20
    )
    val sharePauseUpdates: Flow<SharePauseMessage> = _sharePauseUpdates.asSharedFlow()

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
        scope.launch {
            mqttManager.messageFlow.collect { message ->
                handleMessage(message)
            }
        }
    }

    /**
     * 处理收到的消息
     */
    private suspend fun handleMessage(message: MqttConnectionManager.ReceivedMessage) {
        when {
            message.topic.startsWith(MqttConfig.TOPIC_LOCATION_PREFIX) -> {
                handleLocationMessage(message.payload)
            }
            message.topic.startsWith(MqttConfig.TOPIC_PRESENCE_PREFIX) -> {
                handlePresenceMessage(message.payload)
            }
            message.topic.startsWith(MqttConfig.TOPIC_SHARE_REQUEST_PREFIX) -> {
                handleShareRequestMessage(message.payload)
            }
            message.topic.startsWith(MqttConfig.TOPIC_SHARE_RESPONSE_PREFIX) -> {
                handleShareResponseMessage(message.payload)
            }
            message.topic.startsWith(MqttConfig.TOPIC_REQUEST_PREFIX) -> {
                handleRequestMessage(message.payload)
            }
            message.topic.startsWith(MqttConfig.TOPIC_SHARE_PAUSE_PREFIX) -> {
                handleSharePauseMessage(message.payload)
            }
        }
    }

    /**
     * 处理位置消息
     */
    private suspend fun handleLocationMessage(payload: String) {
        val locationMessage = LocationMessage.fromJson(payload)
        if (locationMessage != null) {
            Log.d(TAG, "收到位置更新: ${locationMessage.deviceName} (用户: ${locationMessage.userId}, 电量: ${locationMessage.battery}%)")
            // 保存到设备表
            database.deviceDao().insertOrUpdate(locationMessage.toEntity())
            // 同时更新联系人表的位置信息（包含电量和设备名）
            database.contactDao().updateLocation(
                targetUserId = locationMessage.userId,
                latitude = locationMessage.latitude,
                longitude = locationMessage.longitude,
                lastUpdateTime = locationMessage.timestamp,
                deviceName = locationMessage.customName ?: locationMessage.deviceName,
                battery = locationMessage.battery
            )
            // 发送到流
            _locationUpdates.emit(locationMessage.toDomain())
        } else {
            Log.w(TAG, "无法解析位置消息: ${payload.take(100)}")
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
        val request = RequestMessage.fromJson(payload)
        if (request != null) {
            Log.d(TAG, "收到请求: type=${request.type}, from=${request.requesterUid}")
            // 发送到流，让 ViewModel 处理
            _requestUpdates.emit(request)
        } else {
            Log.w(TAG, "无法解析请求消息: ${payload.take(100)}")
        }
    }

    /**
     * 处理共享暂停状态消息
     */
    private suspend fun handleSharePauseMessage(payload: String) {
        val pauseMessage = SharePauseMessage.fromJson(payload)
        if (pauseMessage != null) {
            Log.d(TAG, "收到共享暂停状态: ${pauseMessage.senderName} -> isPaused=${pauseMessage.isPaused}")
            // 更新联系人表的暂停状态
            database.contactDao().updatePauseStatusByTargetUserId(
                targetUserId = pauseMessage.senderId,
                isPaused = pauseMessage.isPaused
            )
            // 如果对方暂停了共享，清除该联系人的位置信息
            if (pauseMessage.isPaused) {
                database.contactDao().clearLocationByTargetUserId(pauseMessage.senderId)
            }
            // 发送到流，让 ViewModel 处理（可用于显示 Toast 等）
            _sharePauseUpdates.emit(pauseMessage)
        } else {
            Log.w(TAG, "无法解析暂停状态消息: ${payload.take(100)}")
        }
    }

    /**
     * 发布位置更新
     * @param device 设备信息
     * @return 发布结果
     */
    suspend fun publishLocation(device: Device): Result<Unit> {
        val message = LocationMessage.fromDomain(device)
        val topic = MqttConfig.getLocationTopic(device.ownerId)
        val payload = message.toJson()

        // 先保存到本地数据库
        database.deviceDao().insertOrUpdate(message.toEntity())

        // 尝试发布
        val result = mqttManager.publish(
            topic = topic,
            payload = payload,
            qos = 1,
            retained = true // 保留最后一条位置消息
        )

        if (result.isFailure) {
            // 发布失败，加入离线队列
            Log.w(TAG, "位置发布失败，加入离线队列")
            queueMessage(topic, payload, qos = 1, retained = true)
        }

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

        Log.d(TAG, "离线队列处理完成: 发送 $sentCount/${pendingMessages.size} 条消息")
        return sentCount
    }

    /**
     * 获取离线消息数量
     */
    suspend fun getPendingMessageCount(): Int {
        return pendingMessageDao.getPendingCount()
    }
}
