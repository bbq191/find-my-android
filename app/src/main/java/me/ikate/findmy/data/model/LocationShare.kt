package me.ikate.findmy.data.model

/**
 * 位置共享关系数据模型
 * Firestore 集合: location_shares
 */
data class LocationShare(
    val id: String = "",
    val fromUid: String,
    val toEmail: String,
    val toUid: String? = null,
    val status: ShareStatus = ShareStatus.PENDING,
    val expireTime: Long? = null,  // 过期时间 (null 代表永久)
    val createdAt: Long = System.currentTimeMillis(),
    val acceptedAt: Long? = null
)

/**
 * 共享状态枚举
 */
enum class ShareStatus {
    PENDING,   // 待接受
    ACCEPTED,  // 已接受
    EXPIRED,   // 已过期
    REJECTED   // 已拒绝
}
