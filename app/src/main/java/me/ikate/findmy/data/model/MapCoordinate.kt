package me.ikate.findmy.data.model

import com.tencent.tencentmap.mapsdk.maps.model.LatLng

/**
 * 坐标扩展函数
 * 提供腾讯地图 LatLng 的便捷操作
 *
 * 腾讯地图使用 GCJ-02 坐标系（火星坐标）
 * 与腾讯定位 SDK 输出的坐标一致，无需转换
 */

/**
 * 从纬度和经度创建 LatLng
 * @param latitude 纬度
 * @param longitude 经度
 * @return 腾讯 LatLng
 */
fun latLngOf(latitude: Double, longitude: Double): LatLng =
    LatLng(latitude, longitude)

/**
 * 检查 LatLng 是否有效（非 NaN 且非零）
 */
fun LatLng.isValid(): Boolean =
    !latitude.isNaN() && !longitude.isNaN() && (latitude != 0.0 || longitude != 0.0)

/**
 * 空 LatLng 常量（用于默认值）
 */
val EMPTY_LATLNG: LatLng = LatLng(0.0, 0.0)
