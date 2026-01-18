package me.ikate.findmy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.DeviceType
import me.ikate.findmy.data.model.pointOf

/**
 * 设备实体（Room 数据库表）
 * 用于本地持久化设备信息
 */
@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val ownerId: String,
    val latitude: Double,
    val longitude: Double,
    val battery: Int = 100,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true,
    val deviceType: String = DeviceType.PHONE.name,
    val customName: String? = null,
    val bearing: Float = 0f,
    val sharedWith: String = "" // 逗号分隔的 UID 列表
) {

    /**
     * 转换为领域模型
     */
    fun toDomain(): Device = Device(
        id = id,
        name = name,
        ownerId = ownerId,
        location = pointOf(latitude, longitude),
        battery = battery,
        lastUpdateTime = lastUpdateTime,
        isOnline = isOnline,
        deviceType = try {
            DeviceType.valueOf(deviceType)
        } catch (e: IllegalArgumentException) {
            DeviceType.OTHER
        },
        customName = customName,
        bearing = bearing,
        sharedWith = if (sharedWith.isBlank()) emptyList() else sharedWith.split(",")
    )

    companion object {
        /**
         * 从领域模型创建实体
         */
        fun fromDomain(device: Device): DeviceEntity = DeviceEntity(
            id = device.id,
            name = device.name,
            ownerId = device.ownerId,
            latitude = device.location.latitude(),
            longitude = device.location.longitude(),
            battery = device.battery,
            lastUpdateTime = device.lastUpdateTime,
            isOnline = device.isOnline,
            deviceType = device.deviceType.name,
            customName = device.customName,
            bearing = device.bearing,
            sharedWith = device.sharedWith.joinToString(",")
        )
    }
}
