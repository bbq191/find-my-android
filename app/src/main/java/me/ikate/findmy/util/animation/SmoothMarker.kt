package me.ikate.findmy.util.animation

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.util.Log
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import com.tencent.tencentmap.mapsdk.maps.model.Marker

/**
 * 平滑移动 Marker 封装类
 *
 * 封装腾讯地图 Marker，实现 iOS Find My 级别的丝滑移动效果。
 * 核心功能：
 * - 坐标线性插值（平滑移动）
 * - 航向角最短路径插值（防止绕远路旋转）
 * - 动态时长计算（根据数据间隔自动调整）
 * - 低速航向角过滤（防止定位漂移导致原地转圈）
 * - 可选预测缓冲（极致丝滑，但有延迟）
 *
 * 使用方式：
 * ```kotlin
 * val smoothMarker = SmoothMarker(marker)
 * smoothMarker.animateTo(newLatLng, newBearing) // 自动计算时长
 * // 或
 * smoothMarker.animateTo(newLatLng, newBearing, 2000L) // 指定时长
 * ```
 *
 * @param marker 腾讯地图 Marker 实例
 * @param bearingThresholdMeters 航向角更新阈值（米），移动距离小于此值时不更新航向角
 * @param enableBuffer 是否启用预测缓冲（会引入 1-2 秒延迟）
 */
class SmoothMarker(
    private val marker: Marker,
    private val bearingThresholdMeters: Float = DEFAULT_BEARING_THRESHOLD_METERS,
    enableBuffer: Boolean = false,
    private val rotateMarker: Boolean = false  // 是否旋转 Marker 图标（默认不旋转，iOS Find My 风格）
) {
    /** 坐标插值器 */
    private val latLngEvaluator = LatLngEvaluator()

    /** 动态时长计算器 */
    private val durationCalculator = DynamicDurationCalculator()

    /** 位置缓冲区（可选） */
    private val positionBuffer = PositionBuffer(enabled = enableBuffer)

    /** 当前动画实例 */
    private var currentAnimator: ValueAnimator? = null

    /** 上一个位置 */
    private var lastPosition: LatLng = marker.position

    /** 上一个航向角 */
    private var lastBearing: Float = marker.rotation

    /** 当前动画中的位置（用于外部获取实时位置） */
    private var animatedPosition: LatLng = marker.position

    /** 动画监听器 */
    private var animationListener: AnimationListener? = null

    /**
     * 动画事件监听接口
     */
    interface AnimationListener {
        /** 动画开始 */
        fun onAnimationStart(from: LatLng, to: LatLng) {}

        /** 动画更新（每帧调用） */
        fun onAnimationUpdate(position: LatLng, bearing: Float, fraction: Float) {}

        /** 动画结束 */
        fun onAnimationEnd(position: LatLng) {}

        /** 动画取消 */
        fun onAnimationCancel() {}
    }

    /**
     * 平滑移动到新位置（自动计算动画时长）
     *
     * @param newLatLng 目标坐标
     * @param newBearing 目标航向角（度）
     * @param onUpdate 每帧更新回调，可用于同步更新其他 UI 元素
     */
    fun animateTo(
        newLatLng: LatLng,
        newBearing: Float,
        onUpdate: ((LatLng, Float) -> Unit)? = null
    ) {
        val duration = durationCalculator.calculate()
        animateTo(newLatLng, newBearing, duration, onUpdate)
    }

    /**
     * 平滑移动到新位置（指定动画时长）
     *
     * @param newLatLng 目标坐标
     * @param newBearing 目标航向角（度）
     * @param durationMs 动画时长（毫秒）
     * @param onUpdate 每帧更新回调
     */
    fun animateTo(
        newLatLng: LatLng,
        newBearing: Float,
        durationMs: Long,
        onUpdate: ((LatLng, Float) -> Unit)? = null
    ) {
        // 如果启用了缓冲，先入队
        if (positionBuffer.enabled) {
            positionBuffer.enqueue(newLatLng, newBearing)
            playBufferedAnimation(onUpdate)
            return
        }

        // 无缓冲模式：直接动画
        playAnimation(lastPosition, newLatLng, lastBearing, newBearing, durationMs, onUpdate)

        // 更新状态
        lastPosition = newLatLng
        lastBearing = newBearing
    }

    /**
     * 播放缓冲动画
     */
    private fun playBufferedAnimation(onUpdate: ((LatLng, Float) -> Unit)?) {
        val segment = positionBuffer.dequeueAnimationSegment() ?: return

        playAnimation(
            segment.from.position,
            segment.to.position,
            segment.from.bearing,
            segment.to.bearing,
            segment.duration,
            onUpdate
        )
    }

    /**
     * 执行动画
     */
    private fun playAnimation(
        fromLatLng: LatLng,
        toLatLng: LatLng,
        fromBearing: Float,
        toBearing: Float,
        durationMs: Long,
        onUpdate: ((LatLng, Float) -> Unit)?
    ) {
        // 1. 取消正在进行的动画（防止鬼畜）
        currentAnimator?.cancel()

        // 2. 计算移动距离，决定是否更新航向角
        val distance = calculateDistance(fromLatLng, toLatLng)
        val shouldUpdateBearing = BearingEvaluator.shouldUpdateBearing(distance, bearingThresholdMeters)

        // 3. 确定最终的航向角
        val effectiveToBearing = if (shouldUpdateBearing) {
            BearingEvaluator.normalizeAngle(toBearing)
        } else {
            lastBearing // 低速时保持原航向角
        }

        val effectiveFromBearing = BearingEvaluator.normalizeAngle(fromBearing)

        // 4. 通知动画开始
        animationListener?.onAnimationStart(fromLatLng, toLatLng)

        // 5. 创建动画
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()

            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float

                // 计算插值坐标
                val interpolatedPosition = latLngEvaluator.evaluate(fraction, fromLatLng, toLatLng)

                // 计算插值航向角（最短路径）
                val interpolatedBearing = BearingEvaluator.evaluate(
                    fraction,
                    effectiveFromBearing,
                    effectiveToBearing
                )

                // 更新 Marker 位置
                marker.position = interpolatedPosition
                // 仅当启用旋转时才更新 Marker 角度（iOS Find My 风格：图标不旋转）
                if (rotateMarker) {
                    marker.rotation = interpolatedBearing
                }

                // 保存当前动画位置
                animatedPosition = interpolatedPosition

                // 回调
                onUpdate?.invoke(interpolatedPosition, interpolatedBearing)
                animationListener?.onAnimationUpdate(interpolatedPosition, interpolatedBearing, fraction)
            }

            // 动画结束时的处理
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    animationListener?.onAnimationEnd(toLatLng)

                    // 如果缓冲区还有数据，继续播放下一段
                    if (positionBuffer.enabled && positionBuffer.hasEnoughForAnimation()) {
                        playBufferedAnimation(onUpdate)
                    }
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    animationListener?.onAnimationCancel()
                }

                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }

        animator.start()
        currentAnimator = animator
    }

    /**
     * 取消当前动画
     */
    fun cancel() {
        currentAnimator?.cancel()
        currentAnimator = null
    }

    /**
     * 立即设置位置（无动画）
     *
     * @param latLng 目标坐标
     * @param bearing 航向角，默认不更新
     */
    fun setPosition(latLng: LatLng, bearing: Float? = null) {
        cancel()

        marker.position = latLng
        lastPosition = latLng
        animatedPosition = latLng

        bearing?.let {
            val normalizedBearing = BearingEvaluator.normalizeAngle(it)
            if (rotateMarker) {
                marker.rotation = normalizedBearing
            }
            lastBearing = normalizedBearing
        }

        // 重置计算器，下次动画重新计算时长
        durationCalculator.reset()
    }

    /**
     * 获取当前动画中的位置
     *
     * @return 如果正在动画，返回插值位置；否则返回 Marker 实际位置
     */
    fun getAnimatedPosition(): LatLng = animatedPosition

    /**
     * 获取 Marker 的最终目标位置
     */
    fun getLastPosition(): LatLng = lastPosition

    /**
     * 获取当前航向角
     */
    fun getCurrentBearing(): Float = lastBearing

    /**
     * 判断是否正在动画
     */
    fun isAnimating(): Boolean = currentAnimator?.isRunning == true

    /**
     * 设置动画监听器
     */
    fun setAnimationListener(listener: AnimationListener?) {
        animationListener = listener
    }

    /**
     * 启用/禁用预测缓冲
     *
     * @param enable 是否启用
     */
    fun setBufferEnabled(enable: Boolean) {
        if (positionBuffer.enabled != enable) {
            positionBuffer.enabled = enable
            if (!enable) {
                positionBuffer.clear()
            }
        }
    }

    /**
     * 获取缓冲延迟（毫秒）
     *
     * @return 当前缓冲造成的视觉延迟，未启用缓冲时返回 0
     */
    fun getBufferDelayMs(): Long = positionBuffer.getBufferDelayMs()

    /**
     * 重置状态
     *
     * 在停止追踪或切换目标时调用，清除历史数据
     */
    fun reset() {
        cancel()
        durationCalculator.reset()
        positionBuffer.clear()
    }

    /**
     * 获取底层 Marker 实例
     */
    fun getMarker(): Marker = marker

    /**
     * 计算两点之间的距离（米）
     */
    private fun calculateDistance(from: LatLng, to: LatLng): Double {
        val earthRadius = 6371009.0
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLng = Math.toRadians(to.longitude - from.longitude)

        val a = kotlin.math.sin(deltaLat / 2) * kotlin.math.sin(deltaLat / 2) +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                kotlin.math.sin(deltaLng / 2) * kotlin.math.sin(deltaLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return earthRadius * c
    }

    companion object {
        /** 默认航向角更新阈值：5米 */
        const val DEFAULT_BEARING_THRESHOLD_METERS = 5f
    }
}
