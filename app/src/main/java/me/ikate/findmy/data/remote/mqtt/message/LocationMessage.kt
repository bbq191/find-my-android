package me.ikate.findmy.data.remote.mqtt.message

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import me.ikate.findmy.data.local.entity.DeviceEntity
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.DeviceType
import me.ikate.findmy.data.model.pointOf

/**
 * 位置更新消息
 * MQTT 消息协议定义
 */
data class LocationMessage(
    @SerializedName("type")
    val type: String = MessageType.LOCATION_UPDATE.value,

    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("userId")
    val userId: String,

    @SerializedName("deviceName")
    val deviceName: String,

    @SerializedName("customName")
    val customName: String? = null,

    @SerializedName("lat")
    val latitude: Double,

    @SerializedName("lng")
    val longitude: Double,

    @SerializedName("bearing")
    val bearing: Float = 0f,

    @SerializedName("accuracy")
    val accuracy: Float = 0f,

    @SerializedName("battery")
    val battery: Int = 100,

    @SerializedName("deviceType")
    val deviceType: String = DeviceType.PHONE.name,

    @SerializedName("isOnline")
    val isOnline: Boolean = true,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("sharedWith")
    val sharedWith: List<String> = emptyList()
) {
    /**
     * 转换为 JSON 字符串
     */
    fun toJson(): String = gson.toJson(this)

    /**
     * 转换为领域模型
     */
    fun toDomain(): Device = Device(
        id = deviceId,
        name = deviceName,
        ownerId = userId,
        location = pointOf(latitude, longitude),
        battery = battery,
        lastUpdateTime = timestamp,
        isOnline = isOnline,
        deviceType = try {
            DeviceType.valueOf(deviceType)
        } catch (e: IllegalArgumentException) {
            DeviceType.OTHER
        },
        customName = customName,
        bearing = bearing,
        sharedWith = sharedWith
    )

    /**
     * 转换为 Room 实体
     */
    fun toEntity(): DeviceEntity = DeviceEntity(
        id = deviceId,
        name = deviceName,
        ownerId = userId,
        latitude = latitude,
        longitude = longitude,
        battery = battery,
        lastUpdateTime = timestamp,
        isOnline = isOnline,
        deviceType = deviceType,
        customName = customName,
        bearing = bearing,
        sharedWith = sharedWith.joinToString(",")
    )

    companion object {
        private val gson = Gson()

        /**
         * 从 JSON 解析
         */
        fun fromJson(json: String): LocationMessage? {
            return try {
                gson.fromJson(json, LocationMessage::class.java)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 从领域模型创建
         */
        fun fromDomain(device: Device): LocationMessage = LocationMessage(
            deviceId = device.id,
            userId = device.ownerId,
            deviceName = device.name,
            customName = device.customName,
            latitude = device.location.latitude(),
            longitude = device.location.longitude(),
            bearing = device.bearing,
            battery = device.battery,
            deviceType = device.deviceType.name,
            isOnline = device.isOnline,
            timestamp = device.lastUpdateTime,
            sharedWith = device.sharedWith
        )
    }
}

/**
 * 消息类型枚举
 */
enum class MessageType(val value: String) {
    LOCATION_UPDATE("location_update"),
    PRESENCE_UPDATE("presence_update"),
    DEVICE_COMMAND("device_command"),
    SHARE_REQUEST("share_request"),
    SHARE_RESPONSE("share_response"),
    SHARE_PAUSE("share_pause")  // 暂停/恢复共享状态通知
}
