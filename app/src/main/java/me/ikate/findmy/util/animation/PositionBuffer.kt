package me.ikate.findmy.util.animation

import com.tencent.tencentmap.mapsdk.maps.model.LatLng

/**
 * 位置缓冲队列（预测缓冲）
 *
 * 用于实现 iOS Find My 级别的极致丝滑效果。
 * 通过维护一个位置点缓冲区，始终播放 buffer[0] → buffer[1] 的动画，
 * 确保 Marker 永不停顿，像在冰面上滑行一样。
 *
 * 工作原理：
 * ```
 * 时间轴: ──────────────────────────────►
 *
 * 实际数据:    A ──────► B ──────► C ──────► D
 *
 * 缓冲区:     [A, B]   [B, C]   [C, D]   [D, ?]
 *
 * 动画播放:    A→B      B→C      C→D      D→D(等待)
 * ```
 *
 * 优点：Marker 永不停顿，动画极其流畅
 * 缺点：引入 1-2 秒视觉延迟
 *
 * @param maxSize 缓冲区最大容量，默认 3 个点
 * @param enabled 是否启用缓冲（可动态切换）
 */
class PositionBuffer(
    private val maxSize: Int = DEFAULT_BUFFER_SIZE,
    var enabled: Boolean = false
) {
    /**
     * 位置点数据
     *
     * @property position 经纬度坐标
     * @property bearing 航向角（度）
     * @property timestamp 时间戳（毫秒）
     * @property accuracy 精度（米），可选
     */
    data class PositionPoint(
        val position: LatLng,
        val bearing: Float,
        val timestamp: Long = System.currentTimeMillis(),
        val accuracy: Float? = null
    )

    /**
     * 动画段数据
     *
     * @property from 起始点
     * @property to 终点
     * @property duration 建议动画时长（毫秒）
     */
    data class AnimationSegment(
        val from: PositionPoint,
        val to: PositionPoint,
        val duration: Long
    )

    private val buffer = ArrayDeque<PositionPoint>(maxSize)
    private var lastDequeuedPoint: PositionPoint? = null

    /**
     * 将新位置点加入缓冲区
     *
     * @param point 新的位置点
     */
    fun enqueue(point: PositionPoint) {
        if (!enabled) {
            // 未启用缓冲时，只保留最新的一个点
            buffer.clear()
            buffer.addLast(point)
            return
        }

        buffer.addLast(point)

        // 超出容量时移除最旧的点
        while (buffer.size > maxSize) {
            buffer.removeFirst()
        }
    }

    /**
     * 添加位置点（便捷方法）
     *
     * @param position 经纬度坐标
     * @param bearing 航向角
     * @param timestamp 时间戳，默认为当前时间
     */
    fun enqueue(position: LatLng, bearing: Float, timestamp: Long = System.currentTimeMillis()) {
        enqueue(PositionPoint(position, bearing, timestamp))
    }

    /**
     * 获取下一个要播放的动画段
     *
     * @return 动画段，如果缓冲区点数不足则返回 null
     */
    fun dequeueAnimationSegment(): AnimationSegment? {
        if (!enabled) {
            // 未启用缓冲时，使用无缓冲模式
            return getDirectSegment()
        }

        // 需要至少 2 个点才能形成动画段
        if (buffer.size < 2) {
            return null
        }

        val from = buffer.first()
        val to = buffer.elementAt(1)

        // 计算建议的动画时长（基于时间戳差）
        val duration = (to.timestamp - from.timestamp).coerceIn(
            DynamicDurationCalculator.MIN_DURATION_MS,
            DynamicDurationCalculator.MAX_DURATION_MS
        )

        // 移除已播放的点
        lastDequeuedPoint = buffer.removeFirst()

        return AnimationSegment(from, to, duration)
    }

    /**
     * 无缓冲模式下获取动画段
     */
    private fun getDirectSegment(): AnimationSegment? {
        val current = buffer.firstOrNull() ?: return null
        val previous = lastDequeuedPoint ?: return null

        val duration = (current.timestamp - previous.timestamp).coerceIn(
            DynamicDurationCalculator.MIN_DURATION_MS,
            DynamicDurationCalculator.MAX_DURATION_MS
        )

        lastDequeuedPoint = current
        return AnimationSegment(previous, current, duration)
    }

    /**
     * 预览下一个动画段（不移除）
     *
     * @return 下一个动画段，如果不可用则返回 null
     */
    fun peekNextSegment(): AnimationSegment? {
        if (!enabled || buffer.size < 2) return null

        val from = buffer.first()
        val to = buffer.elementAt(1)
        val duration = (to.timestamp - from.timestamp).coerceIn(
            DynamicDurationCalculator.MIN_DURATION_MS,
            DynamicDurationCalculator.MAX_DURATION_MS
        )

        return AnimationSegment(from, to, duration)
    }

    /**
     * 获取当前最新的位置点（用于显示当前实际位置）
     *
     * @return 最新的位置点，如果缓冲区为空则返回 null
     */
    fun getLatestPosition(): PositionPoint? = buffer.lastOrNull()

    /**
     * 获取当前正在动画的目标点
     *
     * @return 动画目标点，如果缓冲区为空则返回 null
     */
    fun getCurrentAnimationTarget(): PositionPoint? {
        return if (enabled && buffer.size >= 2) {
            buffer.elementAt(1)
        } else {
            buffer.firstOrNull()
        }
    }

    /**
     * 获取缓冲区大小
     */
    fun size(): Int = buffer.size

    /**
     * 检查缓冲区是否有足够的点进行动画
     */
    fun hasEnoughForAnimation(): Boolean = buffer.size >= 2

    /**
     * 获取缓冲延迟（毫秒）
     *
     * 返回最新点与当前动画目标点之间的时间差，表示视觉延迟
     */
    fun getBufferDelayMs(): Long {
        if (!enabled || buffer.size < 2) return 0L

        val latest = buffer.last()
        val animating = buffer.first()
        return latest.timestamp - animating.timestamp
    }

    /**
     * 清空缓冲区
     */
    fun clear() {
        buffer.clear()
        lastDequeuedPoint = null
    }

    /**
     * 重置缓冲区，但保留最后一个点作为下次动画的起点
     */
    fun resetKeepLast() {
        val last = buffer.lastOrNull()
        buffer.clear()
        if (last != null) {
            lastDequeuedPoint = last
        }
    }

    companion object {
        /** 默认缓冲区大小：3 个点 */
        const val DEFAULT_BUFFER_SIZE = 3

        /** 最小缓冲区大小 */
        const val MIN_BUFFER_SIZE = 2

        /** 最大缓冲区大小 */
        const val MAX_BUFFER_SIZE = 5
    }
}
