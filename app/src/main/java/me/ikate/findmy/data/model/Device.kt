package me.ikate.findmy.data.model

import com.mapbox.geojson.Point

data class Device(
    val id: String,
    val name: String, // 设备名称（型号，如 "Xiaomi Mi 11"）
    val ownerId: String, // 设备所有者 UID
    val location: Point,
    val battery: Int = 100,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true,
    val deviceType: DeviceType = DeviceType.PHONE,
    val customName: String? = null, // 设备自定义名称（用户给设备起的昵称，如 "我的手机"）
    val bearing: Float = 0f, // GPS方向角度（0-360度，0为北）
    val speed: Float = 0f, // GPS速度（米/秒），用于智能活动识别
    val sharedWith: List<String> = emptyList() // 共享给哪些用户的 UID 列表
)

/**
 * 设备类型枚举
 */
enum class DeviceType {
    PHONE,      // 手机
    TABLET,     // 平板
    WATCH,      // 手表
    AIRTAG,     // AirTag
    OTHER       // 其他
}
