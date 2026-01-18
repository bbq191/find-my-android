package me.ikate.findmy.data.remote.mqtt.message

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 请求消息（位置请求、发声请求等）
 */
data class RequestMessage(
    @SerializedName("requesterUid")
    val requesterUid: String,

    @SerializedName("targetUid")
    val targetUid: String,

    @SerializedName("type")
    val type: String,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    // 可选的额外数据
    @SerializedName("message")
    val message: String? = null,

    @SerializedName("phoneNumber")
    val phoneNumber: String? = null,

    @SerializedName("playSound")
    val playSound: Boolean = false
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()

        // 请求类型常量
        const val TYPE_SINGLE = "single"           // 单次位置请求
        const val TYPE_CONTINUOUS = "continuous"   // 持续追踪
        const val TYPE_PLAY_SOUND = "play_sound"   // 播放声音
        const val TYPE_STOP_SOUND = "stop_sound"   // 停止声音
        const val TYPE_LOST_MODE = "lost_mode"     // 丢失模式
        const val TYPE_DISABLE_LOST_MODE = "disable_lost_mode"  // 关闭丢失模式

        fun fromJson(json: String): RequestMessage? {
            return try {
                gson.fromJson(json, RequestMessage::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
