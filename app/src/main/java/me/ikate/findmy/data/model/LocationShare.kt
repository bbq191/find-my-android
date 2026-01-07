package me.ikate.findmy.data.model

/**
 * 位置共享关系数据模型
 * Firestore 集合: location_shares
 */
data class LocationShare(
    val id: String = "",
    val fromUid: String,
    val toUid: String? = null,
    val status: ShareStatus = ShareStatus.PENDING,
    val expireTime: Long? = null,  // 过期时间 (null 代表永久)
    val createdAt: Long = System.currentTimeMillis(),
    val acceptedAt: Long? = null,
    
    // 别名信息 (接收者给发送者起的备注)
    val receiverAliasName: String? = null,
    val receiverAliasAvatar: String? = null,

    // 别名信息 (发送者给接收者起的备注)
    val senderAliasName: String? = null,
    val senderAliasAvatar: String? = null,

    // 是否暂停共享
    val isPaused: Boolean = false
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
