package me.ikate.findmy.util

import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 距离计算工具
 * 使用 Haversine 公式计算两个经纬度之间的距离
 */
object DistanceCalculator {

    private const val EARTH_RADIUS_KM = 6371.0 // 地球半径（公里）

    /**
     * 计算两个经纬度之间的距离
     * @param from 起点坐标
     * @param to 终点坐标
     * @return 距离（米）
     */
    fun calculateDistance(from: LatLng, to: LatLng): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lat2 = Math.toRadians(to.latitude)
        val lon2 = Math.toRadians(to.longitude)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        val distanceKm = EARTH_RADIUS_KM * c
        return distanceKm * 1000 // 转换为米
    }

    /**
     * 格式化距离为可读文本
     * @param distanceInMeters 距离（米）
     * @return 格式化的距离文本，例如 "150米"、"1.2公里"
     */
    fun formatDistance(distanceInMeters: Double): String {
        return when {
            distanceInMeters < 1000 -> {
                "${distanceInMeters.toInt()}米"
            }
            distanceInMeters < 10000 -> {
                // 小于10公里，保留1位小数
                String.format("%.1f公里", distanceInMeters / 1000)
            }
            else -> {
                // 大于10公里，保留整数
                "${(distanceInMeters / 1000).toInt()}公里"
            }
        }
    }

    /**
     * 计算并格式化距离
     * @param from 起点坐标
     * @param to 终点坐标
     * @return 格式化的距离文本
     */
    fun calculateAndFormatDistance(from: LatLng?, to: LatLng?): String? {
        if (from == null || to == null) return null
        val distance = calculateDistance(from, to)
        return formatDistance(distance)
    }
}
