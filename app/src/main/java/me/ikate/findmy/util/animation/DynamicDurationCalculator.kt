package me.ikate.findmy.util.animation

/**
 * 动态动画时长计算器
 *
 * 根据数据到达的实际时间间隔动态计算动画时长，解决以下问题：
 * - 数据来得快：上个动画还没播完就被打断 → 卡顿
 * - 数据来得慢：动画播完了新数据还没来 → 停顿
 *
 * 使用方式：
 * 1. 每次收到新位置数据时调用 calculate()
 * 2. 将返回的时长传递给动画
 *
 * @param minDurationMs 最小动画时长（毫秒），防止过快导致抖动，默认 500ms
 * @param maxDurationMs 最大动画时长（毫秒），防止断网后动画过长，默认 10000ms
 * @param defaultDurationMs 断网恢复时的默认时长（毫秒），默认 2000ms
 */
class DynamicDurationCalculator(
    private val minDurationMs: Long = MIN_DURATION_MS,
    private val maxDurationMs: Long = MAX_DURATION_MS,
    private val defaultDurationMs: Long = DEFAULT_DURATION_MS
) {
    private var lastUpdateTimeMs: Long = 0L

    /**
     * 计算动态动画时长
     *
     * @param currentTimeMs 当前时间戳（毫秒），默认使用系统时间
     * @return 建议的动画时长（毫秒）
     */
    fun calculate(currentTimeMs: Long = System.currentTimeMillis()): Long {
        val isFirstCall = lastUpdateTimeMs == 0L
        val timeDiff = if (isFirstCall) {
            // 首次调用，使用快速首次动画时长（500ms 而非 2000ms，避免延迟感）
            FIRST_ANIM_DURATION_MS
        } else {
            currentTimeMs - lastUpdateTimeMs
        }

        lastUpdateTimeMs = currentTimeMs

        // 首次调用直接返回首次动画时长
        if (isFirstCall) {
            return FIRST_ANIM_DURATION_MS
        }

        // 边界检查
        return when {
            timeDiff > maxDurationMs -> defaultDurationMs  // 断网恢复，使用默认值
            timeDiff < minDurationMs -> minDurationMs      // 数据太快，使用最小值
            else -> timeDiff                                // 正常情况，跟随实际间隔
        }
    }

    /**
     * 重置计算器状态
     *
     * 在追踪停止或重新开始时调用，避免使用错误的时间差
     */
    fun reset() {
        lastUpdateTimeMs = 0L
    }

    /**
     * 获取上次更新时间
     *
     * @return 上次更新的时间戳（毫秒），如果从未更新则返回 0
     */
    fun getLastUpdateTime(): Long = lastUpdateTimeMs

    /**
     * 判断是否已初始化（已接收过至少一次数据）
     */
    fun isInitialized(): Boolean = lastUpdateTimeMs != 0L

    companion object {
        /** 最小动画时长：500ms，防止数据过快导致抖动 */
        const val MIN_DURATION_MS = 500L

        /** 最大动画时长：10秒，超过此值认为是断网 */
        const val MAX_DURATION_MS = 10_000L

        /** 默认动画时长：2秒，断网恢复时使用 */
        const val DEFAULT_DURATION_MS = 2_000L

        /** 首次动画时长：500ms，快速显示首个位置，避免 2 秒延迟感 */
        const val FIRST_ANIM_DURATION_MS = 500L

        /**
         * 根据设备类型获取优化后的默认时长
         *
         * @param isHighRefreshRate 是否支持高刷新率（如 120Hz）
         * @return 优化后的默认时长
         */
        fun getOptimizedDefaultDuration(isHighRefreshRate: Boolean): Long {
            return if (isHighRefreshRate) 1500L else 2000L
        }
    }
}
