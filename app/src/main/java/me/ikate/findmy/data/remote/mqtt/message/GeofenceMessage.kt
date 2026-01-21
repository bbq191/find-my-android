package me.ikate.findmy.data.remote.mqtt.message

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import me.ikate.findmy.data.model.GeofenceEvent
import me.ikate.findmy.data.model.GeofenceEventType

/**
 * 围栏触发事件消息
 * 用于通知观察者联系人已进入/离开围栏
 */
data class GeofenceEventMessage(
    @SerializedName("type")
    val type: String = MessageType.GEOFENCE_EVENT.value,

    @SerializedName("eventId")
    val eventId: Long,

    @SerializedName("geofenceId")
    val geofenceId: String,

    @SerializedName("contactId")
    val contactId: String,

    @SerializedName("contactName")
    val contactName: String,

    @SerializedName("locationName")
    val locationName: String,

    @SerializedName("eventType")
    val eventType: String,  // ENTER / EXIT / DWELL

    @SerializedName("lat")
    val latitude: Double,

    @SerializedName("lng")
    val longitude: Double,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = gson.toJson(this)

    /**
     * 转换为领域模型
     */
    fun toDomain(): GeofenceEvent = GeofenceEvent(
        id = eventId,
        geofenceId = geofenceId,
        contactId = contactId,
        contactName = contactName,
        locationName = locationName,
        eventType = try {
            GeofenceEventType.valueOf(eventType)
        } catch (e: Exception) {
            GeofenceEventType.ENTER
        },
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
        isNotified = false
    )

    companion object {
        private val gson = Gson()

        fun fromJson(json: String): GeofenceEventMessage? {
            return try {
                gson.fromJson(json, GeofenceEventMessage::class.java)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 从领域模型创建消息
         */
        fun fromDomain(event: GeofenceEvent): GeofenceEventMessage = GeofenceEventMessage(
            eventId = event.id,
            geofenceId = event.geofenceId,
            contactId = event.contactId,
            contactName = event.contactName,
            locationName = event.locationName,
            eventType = event.eventType.name,
            latitude = event.latitude,
            longitude = event.longitude,
            timestamp = event.timestamp
        )

        /**
         * 创建进入事件消息
         */
        fun enter(
            eventId: Long,
            geofenceId: String,
            contactId: String,
            contactName: String,
            locationName: String,
            latitude: Double,
            longitude: Double
        ): GeofenceEventMessage = GeofenceEventMessage(
            eventId = eventId,
            geofenceId = geofenceId,
            contactId = contactId,
            contactName = contactName,
            locationName = locationName,
            eventType = GeofenceEventType.ENTER.name,
            latitude = latitude,
            longitude = longitude
        )

        /**
         * 创建离开事件消息
         */
        fun exit(
            eventId: Long,
            geofenceId: String,
            contactId: String,
            contactName: String,
            locationName: String,
            latitude: Double,
            longitude: Double
        ): GeofenceEventMessage = GeofenceEventMessage(
            eventId = eventId,
            geofenceId = geofenceId,
            contactId = contactId,
            contactName = contactName,
            locationName = locationName,
            eventType = GeofenceEventType.EXIT.name,
            latitude = latitude,
            longitude = longitude
        )
    }
}

/**
 * 围栏同步消息
 * 用于通知对方同步围栏配置
 */
data class GeofenceSyncMessage(
    @SerializedName("type")
    val type: String = MessageType.GEOFENCE_SYNC.value,

    @SerializedName("senderId")
    val senderId: String,

    @SerializedName("senderName")
    val senderName: String,

    @SerializedName("action")
    val action: GeofenceSyncAction,

    @SerializedName("geofenceId")
    val geofenceId: String? = null,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()

        fun fromJson(json: String): GeofenceSyncMessage? {
            return try {
                gson.fromJson(json, GeofenceSyncMessage::class.java)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 创建同步全部围栏的通知
         */
        fun syncAll(senderId: String, senderName: String): GeofenceSyncMessage {
            return GeofenceSyncMessage(
                senderId = senderId,
                senderName = senderName,
                action = GeofenceSyncAction.SYNC_ALL
            )
        }

        /**
         * 创建新增围栏的通知
         */
        fun added(senderId: String, senderName: String, geofenceId: String): GeofenceSyncMessage {
            return GeofenceSyncMessage(
                senderId = senderId,
                senderName = senderName,
                action = GeofenceSyncAction.ADDED,
                geofenceId = geofenceId
            )
        }

        /**
         * 创建删除围栏的通知
         */
        fun removed(senderId: String, senderName: String, geofenceId: String): GeofenceSyncMessage {
            return GeofenceSyncMessage(
                senderId = senderId,
                senderName = senderName,
                action = GeofenceSyncAction.REMOVED,
                geofenceId = geofenceId
            )
        }
    }
}

/**
 * 围栏同步动作类型
 */
enum class GeofenceSyncAction {
    @SerializedName("sync_all")
    SYNC_ALL,       // 同步所有围栏

    @SerializedName("added")
    ADDED,          // 新增围栏

    @SerializedName("updated")
    UPDATED,        // 更新围栏

    @SerializedName("removed")
    REMOVED         // 删除围栏
}
