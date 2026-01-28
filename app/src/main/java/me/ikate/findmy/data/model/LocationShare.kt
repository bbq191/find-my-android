package me.ikate.findmy.data.model

/**
 * 共享状态枚举
 */
enum class ShareStatus {
    PENDING,   // 待接受
    ACCEPTED,  // 已接受
    EXPIRED,   // 已过期
    REJECTED,  // 已拒绝
    REMOVED    // 已被移除（对方主动移除了你）
}
