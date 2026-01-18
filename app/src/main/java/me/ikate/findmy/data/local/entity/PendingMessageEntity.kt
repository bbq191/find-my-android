package me.ikate.findmy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 待发送消息实体（Room 数据库表）
 * 用于离线时缓存待发送的 MQTT 消息
 */
@Entity(tableName = "pending_messages")
data class PendingMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topic: String,
    val payload: String,  // JSON 格式的消息内容
    val qos: Int = 1,
    val retained: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val status: String = MessageStatus.PENDING.name
) {
    enum class MessageStatus {
        PENDING,    // 待发送
        SENDING,    // 发送中
        SENT,       // 已发送
        FAILED      // 发送失败
    }
}
