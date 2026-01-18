package me.ikate.findmy.data.remote.mqtt.message

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 在线状态消息
 * 用于设备上下线通知
 */
data class PresenceMessage(
    @SerializedName("type")
    val type: String = MessageType.PRESENCE_UPDATE.value,

    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("userId")
    val userId: String,

    @SerializedName("status")
    val status: PresenceStatus,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 转换为 JSON
     */
    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()

        /**
         * 从 JSON 解析
         */
        fun fromJson(json: String): PresenceMessage? {
            return try {
                gson.fromJson(json, PresenceMessage::class.java)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 创建上线消息
         */
        fun online(deviceId: String, userId: String): PresenceMessage = PresenceMessage(
            deviceId = deviceId,
            userId = userId,
            status = PresenceStatus.ONLINE
        )

        /**
         * 创建下线消息
         */
        fun offline(deviceId: String, userId: String): PresenceMessage = PresenceMessage(
            deviceId = deviceId,
            userId = userId,
            status = PresenceStatus.OFFLINE
        )
    }
}

/**
 * 在线状态枚举
 */
enum class PresenceStatus {
    @SerializedName("online")
    ONLINE,

    @SerializedName("offline")
    OFFLINE,

    @SerializedName("away")
    AWAY
}
