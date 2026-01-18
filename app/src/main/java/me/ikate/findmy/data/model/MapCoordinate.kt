package me.ikate.findmy.data.model

import com.mapbox.geojson.Point

/**
 * 坐标扩展函数
 * 提供 Mapbox Point 的便捷操作
 *
 * 注意：Mapbox Point 的构造顺序是 (longitude, latitude)
 *       与 Google LatLng(latitude, longitude) 相反
 */

/**
 * 从纬度和经度创建 Point
 * @param latitude 纬度
 * @param longitude 经度
 * @return Mapbox Point
 */
fun pointOf(latitude: Double, longitude: Double): Point =
    Point.fromLngLat(longitude, latitude)

/**
 * Point 扩展属性：获取纬度
 */
val Point.latitude: Double
    get() = latitude()

/**
 * Point 扩展属性：获取经度
 */
val Point.longitude: Double
    get() = longitude()

/**
 * 检查 Point 是否有效（非 NaN）
 */
fun Point.isValid(): Boolean =
    !latitude().isNaN() && !longitude().isNaN()

/**
 * 空 Point 常量（用于默认值）
 */
val EMPTY_POINT: Point = Point.fromLngLat(0.0, 0.0)
