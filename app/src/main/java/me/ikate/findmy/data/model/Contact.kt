package me.ikate.findmy.data.model

import com.mapbox.geojson.Point

/**
 * 联系人数据模型(扩展版)
 * 包含共享关系和位置信息
 */
data class Contact(
    val id: String,              // 对应 LocationShare.id (共享记录ID)
    val email: String,
    val name: String,
    val avatarUrl: String? = null,

    // 共享关系信息
    val shareStatus: ShareStatus = ShareStatus.PENDING,
    val shareDirection: ShareDirection,  // 我分享给他 / 他分享给我
    val expireTime: Long? = null,
    val targetUserId: String? = null, // 目标用户的 UID (用于发送位置请求)

    // 位置信息(仅当对方分享给我且已接受时有效)
    val location: Point? = null,
    val lastUpdateTime: Long? = null,
    val isLocationAvailable: Boolean = false,  // 位置是否可用
    val isPaused: Boolean = false, // 是否已暂停共享
    val deviceName: String? = null, // 设备名称/型号
    val battery: Int? = null, // 设备电量
//    val speed: Float? = null // 移动速度 (m/s)
)

/**
 * 共享方向
 */
enum class ShareDirection {
    I_SHARE_TO_THEM,   // 我分享给他们
    THEY_SHARE_TO_ME,  // 他们分享给我
    MUTUAL             // 双向共享
}
