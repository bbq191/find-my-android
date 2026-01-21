package me.ikate.findmy.data.remote.mqtt.message

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 共享邀请请求消息
 * 用于发送和接收位置共享邀请
 */
data class ShareRequestMessage(
    @SerializedName("type")
    val type: String = MessageType.SHARE_REQUEST.value,

    @SerializedName("shareId")
    val shareId: String,

    @SerializedName("senderId")
    val senderId: String,

    @SerializedName("senderName")
    val senderName: String,

    @SerializedName("senderEmail")
    val senderEmail: String? = null,

    @SerializedName("expireTime")
    val expireTime: Long? = null,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()

        fun fromJson(json: String): ShareRequestMessage? {
            return try {
                gson.fromJson(json, ShareRequestMessage::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 共享邀请响应消息
 * 用于通知对方邀请已被接受/拒绝
 */
data class ShareResponseMessage(
    @SerializedName("type")
    val type: String = MessageType.SHARE_RESPONSE.value,

    @SerializedName("shareId")
    val shareId: String,

    @SerializedName("responderId")
    val responderId: String,

    @SerializedName("responderName")
    val responderName: String,

    @SerializedName("response")
    val response: ShareResponseType,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()

        fun fromJson(json: String): ShareResponseMessage? {
            return try {
                gson.fromJson(json, ShareResponseMessage::class.java)
            } catch (e: Exception) {
                null
            }
        }

        fun accepted(shareId: String, responderId: String, responderName: String): ShareResponseMessage {
            return ShareResponseMessage(
                shareId = shareId,
                responderId = responderId,
                responderName = responderName,
                response = ShareResponseType.ACCEPTED
            )
        }

        fun rejected(shareId: String, responderId: String, responderName: String): ShareResponseMessage {
            return ShareResponseMessage(
                shareId = shareId,
                responderId = responderId,
                responderName = responderName,
                response = ShareResponseType.REJECTED
            )
        }

        /**
         * 创建移除响应（通知对方你已被移出联系人列表）
         */
        fun removed(shareId: String, responderId: String, responderName: String): ShareResponseMessage {
            return ShareResponseMessage(
                shareId = shareId,
                responderId = responderId,
                responderName = responderName,
                response = ShareResponseType.REMOVED
            )
        }
    }
}

/**
 * 共享响应类型
 */
enum class ShareResponseType {
    @SerializedName("accepted")
    ACCEPTED,

    @SerializedName("rejected")
    REJECTED,

    @SerializedName("removed")
    REMOVED  // 联系人被移除
}

/**
 * 共享暂停状态消息
 * 用于通知对方共享已暂停/恢复/过期
 *
 * 过期是一种特殊的暂停：
 * - isPaused = true, isExpired = true 表示共享已过期
 * - 过期后任一方都可以恢复共享
 */
data class SharePauseMessage(
    @SerializedName("type")
    val type: String = MessageType.SHARE_PAUSE.value,

    @SerializedName("senderId")
    val senderId: String,

    @SerializedName("senderName")
    val senderName: String,

    @SerializedName("isPaused")
    val isPaused: Boolean,

    @SerializedName("isExpired")
    val isExpired: Boolean = false,  // 是否因过期而暂停

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()

        fun fromJson(json: String): SharePauseMessage? {
            return try {
                gson.fromJson(json, SharePauseMessage::class.java)
            } catch (e: Exception) {
                null
            }
        }

        fun paused(senderId: String, senderName: String): SharePauseMessage {
            return SharePauseMessage(
                senderId = senderId,
                senderName = senderName,
                isPaused = true,
                isExpired = false
            )
        }

        fun resumed(senderId: String, senderName: String): SharePauseMessage {
            return SharePauseMessage(
                senderId = senderId,
                senderName = senderName,
                isPaused = false,
                isExpired = false
            )
        }

        /**
         * 创建过期消息（过期是一种特殊的暂停）
         */
        fun expired(senderId: String, senderName: String): SharePauseMessage {
            return SharePauseMessage(
                senderId = senderId,
                senderName = senderName,
                isPaused = true,
                isExpired = true
            )
        }
    }
}
