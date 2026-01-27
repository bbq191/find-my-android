package me.ikate.findmy.data.model

import com.tencent.tencentmap.mapsdk.maps.model.LatLng

/**
 * 围栏类型 (iOS Find My 风格)
 */
enum class GeofenceType {
    FIXED_LOCATION,  // 固定位置围栏（到达/离开指定地点）
    LEFT_BEHIND      // 离开我身边（联系人远离我的实时位置）
}

/**
 * 电子围栏触发类型
 */
enum class GeofenceTriggerType {
    ENTER,  // 进入时触发（到达）
    EXIT,   // 离开时触发
    BOTH    // 进入和离开都触发（保留兼容）
}

/**
 * 电子围栏数据模型 (iOS Find My 风格)
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
    val isOneTime: Boolean = true,   // 是否一次性（触发后自动删除）- iOS 默认一次性
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // iOS Find My 新增字段
    val geofenceType: GeofenceType = GeofenceType.FIXED_LOCATION,  // 围栏类型
    val address: String = "",        // 位置地址（逆地理编码结果）
    val wasInsideOnCreate: Boolean = false,  // 创建时联系人是否在围栏内（跳过首次触发）
    val ownerLatitude: Double? = null,   // 我的位置纬度（仅 LEFT_BEHIND 使用）
    val ownerLongitude: Double? = null   // 我的位置经度（仅 LEFT_BEHIND 使用）
) {
    /**
     * 是否在进入时通知（到达）
     */
    val notifyOnEnter: Boolean
        get() = triggerType == GeofenceTriggerType.ENTER || triggerType == GeofenceTriggerType.BOTH

    /**
     * 是否在离开时通知
     */
    val notifyOnExit: Boolean
        get() = triggerType == GeofenceTriggerType.EXIT || triggerType == GeofenceTriggerType.BOTH

    /**
     * 是否为"离开我身边"类型
     */
    val isLeftBehind: Boolean
        get() = geofenceType == GeofenceType.LEFT_BEHIND

    /**
     * 获取我的位置（仅 LEFT_BEHIND 类型有效）
     */
    val ownerLocation: LatLng?
        get() = if (ownerLatitude != null && ownerLongitude != null) {
            latLngOf(ownerLatitude, ownerLongitude)
        } else null
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
