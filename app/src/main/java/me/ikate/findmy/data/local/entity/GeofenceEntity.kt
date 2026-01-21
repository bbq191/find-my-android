package me.ikate.findmy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.ikate.findmy.data.model.Geofence
import me.ikate.findmy.data.model.GeofenceTriggerType
import me.ikate.findmy.data.model.latLngOf

/**
 * 电子围栏实体（Room 数据库表）
 * 用于本地持久化围栏配置
 *
 * 注意：坐标使用 GCJ-02 (腾讯坐标系)
 */
@Entity(
    tableName = "geofences",
    indices = [
        Index(value = ["contactId"], unique = true)  // 每个联系人只能有一个围栏
    ]
)
data class GeofenceEntity(
    @PrimaryKey
    val id: String,
    val contactId: String,           // 被监控的联系人ID
    val contactName: String,         // 联系人名称
    val locationName: String,        // 位置名称
    val latitude: Double,            // GCJ-02 坐标
    val longitude: Double,           // GCJ-02 坐标
    val radiusMeters: Float,         // 围栏半径（米）
    val triggerType: String = GeofenceTriggerType.ENTER.name,
    val isActive: Boolean = true,
    val isOneTime: Boolean = false,  // 是否一次性触发
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {

    /**
     * 转换为领域模型
     */
    fun toDomain(): Geofence = Geofence(
        id = id,
        contactId = contactId,
        contactName = contactName,
        locationName = locationName,
        center = latLngOf(latitude, longitude),
        radiusMeters = radiusMeters,
        triggerType = try {
            GeofenceTriggerType.valueOf(triggerType)
        } catch (e: Exception) {
            GeofenceTriggerType.ENTER
        },
        isActive = isActive,
        isOneTime = isOneTime,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        /**
         * 从领域模型创建实体
         */
        fun fromDomain(geofence: Geofence): GeofenceEntity = GeofenceEntity(
            id = geofence.id,
            contactId = geofence.contactId,
            contactName = geofence.contactName,
            locationName = geofence.locationName,
            latitude = geofence.center.latitude,
            longitude = geofence.center.longitude,
            radiusMeters = geofence.radiusMeters,
            triggerType = geofence.triggerType.name,
            isActive = geofence.isActive,
            isOneTime = geofence.isOneTime,
            createdAt = geofence.createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }
}
