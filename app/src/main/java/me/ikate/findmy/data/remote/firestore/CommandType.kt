package me.ikate.findmy.data.remote.firestore

/**
 * 远程指令类型
 * 对应 Firestore commands 集合中的 type 字段
 */
enum class CommandType(val value: String) {
    /** 播放查找提示音 */
    RING("RING"),

    /** 停止播放提示音 */
    STOP_RING("STOP_RING"),

    /** 单次精准定位 */
    LOCATE("LOCATE"),

    /** 启用丢失模式 */
    LOCK("LOCK"),

    /** 关闭丢失模式 */
    UNLOCK("UNLOCK"),

    /** 开始实时追踪 */
    START_TRACKING("START_TRACKING"),

    /** 停止实时追踪 */
    STOP_TRACKING("STOP_TRACKING");

    companion object {
        fun fromValue(value: String): CommandType? {
            return entries.find { it.value == value }
        }
    }
}

/**
 * 指令执行状态
 */
enum class CommandStatus(val value: String) {
    /** 待执行 */
    PENDING("PENDING"),

    /** 执行中 */
    EXECUTING("EXECUTING"),

    /** 已执行 */
    EXECUTED("EXECUTED"),

    /** 执行失败 */
    FAILED("FAILED");

    companion object {
        fun fromValue(value: String): CommandStatus? {
            return entries.find { it.value == value }
        }
    }
}
