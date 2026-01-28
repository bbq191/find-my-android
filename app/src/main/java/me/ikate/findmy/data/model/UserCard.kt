package me.ikate.findmy.data.model

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * 名片数据模型
 * 用于生成和解析二维码中的用户信息
 *
 * 字段名使用简短形式以减小二维码密度：
 * - u: uid
 * - n: nickname
 * - a: avatarUrl
 * - s: status (状态签名)
 * - t: timestamp
 */
@Keep
data class UserCard(
    @SerializedName("u") val uid: String,
    @SerializedName("n") val nickname: String,
    @SerializedName("a") val avatarUrl: String? = null,
    @SerializedName("s") val status: String? = null,
    @SerializedName("t") val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 序列化为 JSON 字符串
     */
    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()

        /**
         * 从 JSON 解析 UserCard
         * @return 解析成功返回 UserCard，失败返回 null
         */
        fun fromJson(json: String): UserCard? = try {
            gson.fromJson(json, UserCard::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
