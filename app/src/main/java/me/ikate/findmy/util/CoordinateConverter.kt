package me.ikate.findmy.util

import com.mapbox.geojson.Point
import me.ikate.findmy.data.model.pointOf
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 坐标系转换工具类
 * 用于解决中国大陆地区GPS坐标（WGS-84）与地图坐标（GCJ-02）之间的偏移问题
 *
 * 背景：
 * - WGS-84：全球标准GPS坐标系，GPS设备获取的原始坐标，Mapbox 使用此坐标系
 * - GCJ-02：中国国家测绘局加密坐标系（火星坐标系），高德地图使用此坐标系
 *
 * Mapbox 使用 WGS-84 坐标系，不需要转换
 * 但如果使用高德定位 SDK（输出 GCJ-02），则需要转换为 WGS-84
 *
 * 参考：
 * - https://github.com/googollee/eviltransform
 */
object CoordinateConverter {

    private const val PI = 3.14159265358979324
    private const val A = 6378245.0 // 长半轴
    private const val EE = 0.00669342162296594323 // 偏心率平方

    /**
     * 判断坐标是否在中国大陆范围内
     * 只有在中国大陆范围内才需要进行坐标转换
     *
     * @param lat 纬度
     * @param lng 经度
     * @return true表示在中国大陆范围内
     */
    fun isInChina(lat: Double, lng: Double): Boolean {
        // 粗略判断，不包括港澳台
        return lng > 73.66 && lng < 135.05 && lat > 3.86 && lat < 53.55
    }

    /**
     * WGS-84 转 GCJ-02
     * 将GPS原始坐标转换为高德/Google Maps中国大陆地图坐标
     *
     * @param wgsLat WGS-84纬度
     * @param wgsLng WGS-84经度
     * @return GCJ-02坐标 (Point)
     */
    fun wgs84ToGcj02(wgsLat: Double, wgsLng: Double): Point {
        if (wgsLat.isNaN() || wgsLng.isNaN()) {
            return pointOf(0.0, 0.0)
        }

        if (!isInChina(wgsLat, wgsLng)) {
            // 不在中国大陆范围内，无需转换
            return pointOf(wgsLat, wgsLng)
        }

        var dLat = transformLat(wgsLng - 105.0, wgsLat - 35.0)
        var dLng = transformLng(wgsLng - 105.0, wgsLat - 35.0)
        val radLat = wgsLat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)

        val mgLat = wgsLat + dLat
        val mgLng = wgsLng + dLng

        return pointOf(mgLat, mgLng)
    }

    /**
     * GCJ-02 转 WGS-84
     * 将高德定位坐标转换为 Mapbox 使用的 WGS-84 坐标
     * 使用迭代法提高精度
     *
     * @param gcjLat GCJ-02纬度
     * @param gcjLng GCJ-02经度
     * @return WGS-84坐标 (Point)
     */
    fun gcj02ToWgs84(gcjLat: Double, gcjLng: Double): Point {
        if (gcjLat.isNaN() || gcjLng.isNaN()) {
            return pointOf(0.0, 0.0)
        }

        if (!isInChina(gcjLat, gcjLng)) {
            // 不在中国大陆范围内，无需转换
            return pointOf(gcjLat, gcjLng)
        }

        // 使用迭代法反算 WGS-84 坐标
        var wgsLat = gcjLat
        var wgsLng = gcjLng

        repeat(3) { // 迭代3次提高精度
            val gcj = wgs84ToGcj02(wgsLat, wgsLng)
            wgsLat += gcjLat - gcj.latitude()
            wgsLng += gcjLng - gcj.longitude()
        }

        return pointOf(wgsLat, wgsLng)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
