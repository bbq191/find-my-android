package me.ikate.findmy.data.model

import com.google.android.gms.maps.model.LatLng

data class Device(
    val id: String,
    val name: String, // 设备名称（型号，如 "Xiaomi Mi 11"）
    val location: LatLng,
    val avatarUrl: String? = null,
    val battery: Int = 100,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true,
    val deviceType: DeviceType = DeviceType.PHONE,
    val ownerName: String? = null, // 机主名称（从通讯录获取）
    val customName: String? = null, // 设备自定义名称（用户给设备起的昵称，如 "我的手机"）
    val bearing: Float = 0f // GPS方向角度（0-360度，0为北）
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
