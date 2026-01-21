package me.ikate.findmy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import me.ikate.findmy.data.model.GeofenceEvent
import me.ikate.findmy.data.model.GeofenceEventType

/**
 * 围栏事件日志实体（Room 数据库表）
 * 记录联系人进入/离开围栏的历史事件
 *
 * 注意：坐标使用 GCJ-02 (腾讯坐标系)
 */
@Entity(
    tableName = "geofence_events",
    indices = [
        Index(value = ["geofenceId"]),
        Index(value = ["contactId"]),
        Index(value = ["timestamp"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = GeofenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["geofenceId"],
            onDelete = ForeignKey.CASCADE  // 围栏删除时级联删除事件
        )
    ]
)
data class GeofenceEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val geofenceId: String,          // 关联的围栏ID
    val contactId: String,           // 触发的联系人ID
    val contactName: String,         // 联系人名称
    val locationName: String,        // 位置名称
    val eventType: String,           // ENTER / EXIT / DWELL
    val latitude: Double,            // 触发时的位置 (GCJ-02)
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val isNotified: Boolean = false  // 是否已发送通知
) {

    /**
     * 转换为领域模型
     */
    fun toDomain(): GeofenceEvent = GeofenceEvent(
        id = id,
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
        isNotified = isNotified
    )

    companion object {
        /**
         * 从领域模型创建实体
         */
        fun fromDomain(event: GeofenceEvent): GeofenceEventEntity = GeofenceEventEntity(
            id = event.id,
            geofenceId = event.geofenceId,
            contactId = event.contactId,
            contactName = event.contactName,
            locationName = event.locationName,
            eventType = event.eventType.name,
            latitude = event.latitude,
            longitude = event.longitude,
            timestamp = event.timestamp,
            isNotified = event.isNotified
        )
    }
}
