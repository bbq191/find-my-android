package me.ikate.findmy.data.model

import com.tencent.tencentmap.mapsdk.maps.model.LatLng

/**
 * 电子围栏触发类型
 */
enum class GeofenceTriggerType {
    ENTER,  // 进入时触发
    EXIT,   // 离开时触发
    BOTH    // 进入和离开都触发
}

/**
 * 电子围栏数据模型
 * 用于监控联系人进入或离开指定区域
 */
data class Geofence(
    val id: String,
    val contactId: String,           // 被监控的联系人ID
    val contactName: String,         // 联系人名称（冗余存储便于显示）
    val locationName: String,        // 位置名称，如"家"、"公司"
    val center: LatLng,              // 围栏中心点 (GCJ-02 坐标)
    val radiusMeters: Float,         // 围栏半径（米），建议最小200米
    val triggerType: GeofenceTriggerType = GeofenceTriggerType.ENTER,
    val isActive: Boolean = true,    // 是否激活
    val isOneTime: Boolean = false,  // 是否一次性（触发后自动删除）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 是否在进入时通知
     */
    val notifyOnEnter: Boolean
        get() = triggerType == GeofenceTriggerType.ENTER || triggerType == GeofenceTriggerType.BOTH

    /**
     * 是否在离开时通知
     */
    val notifyOnExit: Boolean
        get() = triggerType == GeofenceTriggerType.EXIT || triggerType == GeofenceTriggerType.BOTH
}

/**
 * 围栏事件类型
 */
enum class GeofenceEventType {
    ENTER,  // 进入围栏
    EXIT,   // 离开围栏
    DWELL   // 在围栏内停留
}

/**
 * 围栏触发事件
 * 记录联系人进入/离开围栏的历史
 */
data class GeofenceEvent(
    val id: Long = 0,
    val geofenceId: String,          // 关联的围栏ID
    val contactId: String,           // 触发的联系人ID
    val contactName: String,         // 联系人名称
    val locationName: String,        // 位置名称
    val eventType: GeofenceEventType,
    val latitude: Double,            // 触发时的位置 (GCJ-02)
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val isNotified: Boolean = false  // 是否已发送通知
)
