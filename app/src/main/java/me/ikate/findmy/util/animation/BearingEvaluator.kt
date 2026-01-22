package me.ikate.findmy.util.animation

/**
 * 航向角插值器
 *
 * 解决角度旋转时绕远路的问题。例如从 350° 转到 10°，
 * 直接插值会顺时针转 340°，而实际应该逆时针转 20°。
 *
 * 该类使用最短路径算法，确保旋转动画始终走最短的弧。
 */
object BearingEvaluator {

    /**
     * 计算两个角度之间的最短路径插值
     *
     * @param fraction 插值进度 (0.0 ~ 1.0)
     * @param startBearing 起始角度（度）
     * @param endBearing 结束角度（度）
     * @return 插值后的角度（0 ~ 360°）
     */
    fun evaluate(fraction: Float, startBearing: Float, endBearing: Float): Float {
        var diff = endBearing - startBearing

        // 归一化到 [-180, 180] 区间，确保走最短路径
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360

        val result = startBearing + diff * fraction

        // 归一化结果到 [0, 360) 区间
        return normalizeAngle(result)
    }

    /**
     * 将角度归一化到 [0, 360) 区间
     *
     * @param angle 输入角度
     * @return 归一化后的角度
     */
    fun normalizeAngle(angle: Float): Float {
        var normalized = angle % 360
        if (normalized < 0) normalized += 360
        return normalized
    }

    /**
     * 计算两个角度之间的最小差值（带符号）
     *
     * @param from 起始角度
     * @param to 目标角度
     * @return 最小差值（-180 ~ 180）
     */
    fun shortestRotation(from: Float, to: Float): Float {
        var diff = to - from
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360
        return diff
    }

    /**
     * 判断是否需要更新航向角
     *
     * 当移动距离很小时（如等红灯、定位漂移），不应该更新航向角，
     * 否则会导致 Marker 原地转圈。
     *
     * @param distanceMeters 移动距离（米）
     * @param thresholdMeters 阈值（米），默认 5 米
     * @return 是否应该更新航向角
     */
    fun shouldUpdateBearing(distanceMeters: Double, thresholdMeters: Float = 5f): Boolean {
        return distanceMeters >= thresholdMeters
    }

    /**
     * 计算两点之间的航向角
     *
     * @param fromLat 起点纬度
     * @param fromLng 起点经度
     * @param toLat 终点纬度
     * @param toLng 终点经度
     * @return 航向角（0 ~ 360°，正北为 0°）
     */
    fun calculateBearing(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): Float {
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val deltaLng = Math.toRadians(toLng - fromLng)

        val x = kotlin.math.sin(deltaLng) * kotlin.math.cos(lat2)
        val y = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
                kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(deltaLng)

        val bearing = Math.toDegrees(kotlin.math.atan2(x, y)).toFloat()
        return normalizeAngle(bearing)
    }
}
