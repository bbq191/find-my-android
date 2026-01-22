package me.ikate.findmy.util.animation

import android.animation.TypeEvaluator
import com.tencent.tencentmap.mapsdk.maps.model.LatLng

/**
 * 坐标线性插值器
 *
 * 用于在两个经纬度坐标之间进行平滑插值，实现 Marker 的丝滑移动效果。
 * 使用线性插值公式: Result = Start + (End - Start) * fraction
 *
 * 在 120Hz 屏幕上，每秒会调用 120 次 evaluate()，实现极其流畅的动画。
 */
class LatLngEvaluator : TypeEvaluator<LatLng> {

    /**
     * 计算两个坐标之间的中间点
     *
     * @param fraction 插值进度 (0.0 ~ 1.0)
     * @param startValue 起始坐标
     * @param endValue 结束坐标
     * @return 插值后的中间坐标
     */
    override fun evaluate(fraction: Float, startValue: LatLng, endValue: LatLng): LatLng {
        val lat = startValue.latitude + (endValue.latitude - startValue.latitude) * fraction
        val lng = startValue.longitude + (endValue.longitude - startValue.longitude) * fraction
        return LatLng(lat, lng)
    }
}
