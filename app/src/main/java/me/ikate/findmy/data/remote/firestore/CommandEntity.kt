package me.ikate.findmy.data.remote.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * Firestore 远程指令实体
 *
 * 对应集合路径: users/{uid}/devices/{deviceId}/commands/{cmdId}
 *
 * 数据流:
 * 1. 控制端写入 PENDING 状态的指令
 * 2. 设备端通过 FCM 唤醒后读取 PENDING 指令
 * 3. 设备执行指令并更新状态为 EXECUTED/FAILED
 * 4. 控制端监听状态变化获取执行结果
 */
data class CommandEntity(
    /** 指令唯一 ID (Firestore 文档 ID) */
    @DocumentId
    val cmdId: String = "",

    /** 指令类型 */
    @PropertyName("type")
    val type: String = "",

    /** 执行状态 */
    @PropertyName("status")
    val status: String = CommandStatus.PENDING.value,

    /** 指令参数 */
    @PropertyName("params")
    val params: CommandParams? = null,

    /** 创建时间 */
    @PropertyName("created_at")
    @ServerTimestamp
    val createdAt: Timestamp? = null,

    /** 执行时间 */
    @PropertyName("executed_at")
    val executedAt: Timestamp? = null,

    /** 设备响应数据 */
    @PropertyName("device_response")
    val deviceResponse: Map<String, Any>? = null,

    /** 请求者 UID */
    @PropertyName("requester_uid")
    val requesterUid: String? = null
) {
    /** 获取指令类型枚举 */
    fun getCommandType(): CommandType? = CommandType.fromValue(type)

    /** 获取执行状态枚举 */
    fun getCommandStatus(): CommandStatus? = CommandStatus.fromValue(status)

    /** 是否待执行 */
    fun isPending(): Boolean = status == CommandStatus.PENDING.value

    /** 无参构造函数 (Firestore 反序列化需要) */
    constructor() : this("")
}

/**
 * 指令参数
 * 不同类型的指令使用不同的参数
 */
data class CommandParams(
    /** 丢失模式消息 */
    @PropertyName("message")
    val message: String? = null,

    /** 联系电话 */
    @PropertyName("phone_number")
    val phoneNumber: String? = null,

    /** 是否播放声音 */
    @PropertyName("play_sound")
    val playSound: Boolean = true,

    /** 追踪时长(秒) */
    @PropertyName("duration_seconds")
    val durationSeconds: Int? = null
) {
    /** 无参构造函数 (Firestore 反序列化需要) */
    constructor() : this(null)
}

/**
 * 设备状态实体
 *
 * 对应文档路径: users/{uid}/devices/{deviceId}
 * 字段: device_status (作为子文档或字段)
 */
data class DeviceStatusEntity(
    /** 最后位置 */
    @PropertyName("last_location")
    val lastLocation: LocationData? = null,

    /** 丢失模式状态 */
    @PropertyName("lost_mode")
    val lostMode: LostModeData? = null,

    /** 最后响铃信息 */
    @PropertyName("last_ring")
    val lastRing: RingData? = null,

    /** 设备是否在线 */
    @PropertyName("online")
    val online: Boolean = false,

    /** 最后在线时间 */
    @PropertyName("last_seen")
    @ServerTimestamp
    val lastSeen: Timestamp? = null
) {
    constructor() : this(null)
}

/**
 * 位置数据
 */
data class LocationData(
    @PropertyName("latitude")
    val latitude: Double = 0.0,

    @PropertyName("longitude")
    val longitude: Double = 0.0,

    @PropertyName("accuracy")
    val accuracy: Float = 0f,

    @PropertyName("timestamp")
    val timestamp: Timestamp? = null,

    @PropertyName("address")
    val address: String? = null
) {
    constructor() : this(0.0)
}

/**
 * 丢失模式数据
 */
data class LostModeData(
    @PropertyName("enabled")
    val enabled: Boolean = false,

    @PropertyName("message")
    val message: String? = null,

    @PropertyName("phone_number")
    val phoneNumber: String? = null,

    @PropertyName("enabled_at")
    val enabledAt: Timestamp? = null
) {
    constructor() : this(false)
}

/**
 * 响铃数据
 */
data class RingData(
    @PropertyName("triggered_at")
    val triggeredAt: Timestamp? = null,

    @PropertyName("stopped_at")
    val stoppedAt: Timestamp? = null
) {
    constructor() : this(null)
}
