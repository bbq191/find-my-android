package me.ikate.findmy.util

import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import me.ikate.findmy.data.model.latLngOf
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 坐标系工具类
 *
 * 支持的坐标系：
 * - WGS-84: GPS 原始坐标（iOS 设备、国际标准）
 * - GCJ-02: 火星坐标（腾讯、高德地图使用）
 *
 * 腾讯定位 SDK 和腾讯地图 SDK 都使用 GCJ-02 坐标系
 * 从其他设备（如 iOS）接收的位置通常是 WGS-84，需要转换
 */
object CoordinateConverter {

    // 椭球参数
    private const val A = 6378245.0          // 长半轴
    private const val EE = 0.00669342162296594323  // 偏心率平方

    /**
     * 判断坐标是否在中国大陆范围内
     * 只有在中国大陆范围内才需要进行坐标转换
     *
     * @param lat 纬度
     * @param lng 经度
     * @return true 表示在中国大陆范围内
     */
    fun isInChina(lat: Double, lng: Double): Boolean {
        // 粗略判断，不包括港澳台
        return lng > 73.66 && lng < 135.05 && lat > 3.86 && lat < 53.55
    }

    /**
     * WGS-84 转 GCJ-02（火星坐标）
     * 用于将 GPS 原始坐标或 iOS 设备坐标转换为腾讯地图坐标
     *
     * @param wgsLat WGS-84 纬度
     * @param wgsLng WGS-84 经度
     * @return GCJ-02 坐标
     */
    fun wgs84ToGcj02(wgsLat: Double, wgsLng: Double): LatLng {
        if (wgsLat.isNaN() || wgsLng.isNaN()) {
            return latLngOf(0.0, 0.0)
        }

        // 不在中国范围内，不需要转换
        if (!isInChina(wgsLat, wgsLng)) {
            return latLngOf(wgsLat, wgsLng)
        }

        var dLat = transformLat(wgsLng - 105.0, wgsLat - 35.0)
        var dLng = transformLng(wgsLng - 105.0, wgsLat - 35.0)

        val radLat = wgsLat / 180.0 * Math.PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)

        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * Math.PI)

        val gcjLat = wgsLat + dLat
        val gcjLng = wgsLng + dLng

        return latLngOf(gcjLat, gcjLng)
    }

    /**
     * GCJ-02 转 WGS-84
     * 用于将腾讯坐标转换为 GPS 原始坐标（如需要上传到国际服务）
     *
     * @param gcjLat GCJ-02 纬度
     * @param gcjLng GCJ-02 经度
     * @return WGS-84 坐标
     */
    fun gcj02ToWgs84(gcjLat: Double, gcjLng: Double): LatLng {
        if (gcjLat.isNaN() || gcjLng.isNaN()) {
            return latLngOf(0.0, 0.0)
        }

        // 不在中国范围内，不需要转换
        if (!isInChina(gcjLat, gcjLng)) {
            return latLngOf(gcjLat, gcjLng)
        }

        var dLat = transformLat(gcjLng - 105.0, gcjLat - 35.0)
        var dLng = transformLng(gcjLng - 105.0, gcjLat - 35.0)

        val radLat = gcjLat / 180.0 * Math.PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)

        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * Math.PI)

        val wgsLat = gcjLat - dLat
        val wgsLng = gcjLng - dLng

        return latLngOf(wgsLat, wgsLng)
    }

    /**
     * 直接返回坐标（无需转换）
     * 用于腾讯定位输出（已经是 GCJ-02）
     *
     * @param lat 纬度
     * @param lng 经度
     * @return LatLng 坐标
     */
    fun toMapCoordinate(lat: Double, lng: Double): LatLng {
        if (lat.isNaN() || lng.isNaN()) {
            return latLngOf(0.0, 0.0)
        }
        return latLngOf(lat, lng)
    }

    /**
     * 智能转换：根据坐标系类型自动转换为 GCJ-02
     *
     * @param lat 纬度
     * @param lng 经度
     * @param isWgs84 是否为 WGS-84 坐标，true 则转换，false 则直接使用
     * @return GCJ-02 坐标
     */
    fun toGcj02(lat: Double, lng: Double, isWgs84: Boolean): LatLng {
        return if (isWgs84) {
            wgs84ToGcj02(lat, lng)
        } else {
            latLngOf(lat, lng)
        }
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * Math.PI) + 320.0 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }
}
