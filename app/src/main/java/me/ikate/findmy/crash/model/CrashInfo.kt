package me.ikate.findmy.crash.model

import com.google.gson.annotations.SerializedName

/**
 * 崩溃信息数据类
 * 包含完整的崩溃上下文信息，用于问题排查
 */
data class CrashInfo(
    // 基础信息
    @SerializedName("crash_id")
    val crashId: String,

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("type")
    val type: CrashType,

    @SerializedName("uploaded")
    var uploaded: Boolean = false,

    @SerializedName("upload_attempts")
    var uploadAttempts: Int = 0,

    // 异常信息
    @SerializedName("exception")
    val exception: ExceptionInfo,

    // 设备信息
    @SerializedName("device")
    val device: DeviceInfo,

    // 内存状态
    @SerializedName("memory")
    val memory: MemoryInfo,

    // 存储状态
    @SerializedName("storage")
    val storage: StorageInfo,

    // 应用状态
    @SerializedName("app")
    val app: AppInfo,

    // 网络状态
    @SerializedName("network")
    val network: NetworkInfo
)

/**
 * 崩溃类型
 */
enum class CrashType {
    @SerializedName("CRASH")
    CRASH,

    @SerializedName("ANR")
    ANR
}

/**
 * 异常信息
 */
data class ExceptionInfo(
    @SerializedName("class_name")
    val className: String,

    @SerializedName("message")
    val message: String?,

    @SerializedName("stack_trace")
    val stackTrace: String,

    @SerializedName("cause_chain")
    val causeChain: List<String>,

    @SerializedName("thread_name")
    val threadName: String
)

/**
 * 设备信息
 */
data class DeviceInfo(
    @SerializedName("model")
    val model: String,

    @SerializedName("manufacturer")
    val manufacturer: String,

    @SerializedName("brand")
    val brand: String,

    @SerializedName("android_version")
    val androidVersion: String,

    @SerializedName("sdk_version")
    val sdkVersion: Int,

    @SerializedName("one_ui_version")
    val oneUiVersion: String?,

    @SerializedName("kernel_version")
    val kernelVersion: String,

    @SerializedName("build_fingerprint")
    val buildFingerprint: String,

    @SerializedName("device_id")
    val deviceId: String
)

/**
 * 内存信息
 */
data class MemoryInfo(
    @SerializedName("total_memory_mb")
    val totalMemoryMb: Long,

    @SerializedName("available_memory_mb")
    val availableMemoryMb: Long,

    @SerializedName("java_heap_max_mb")
    val javaHeapMaxMb: Long,

    @SerializedName("java_heap_used_mb")
    val javaHeapUsedMb: Long,

    @SerializedName("is_low_memory")
    val isLowMemory: Boolean
)

/**
 * 存储信息
 */
data class StorageInfo(
    @SerializedName("internal_free_mb")
    val internalFreeMb: Long,

    @SerializedName("external_free_mb")
    val externalFreeMb: Long
)

/**
 * 应用信息
 */
data class AppInfo(
    @SerializedName("version_name")
    val versionName: String,

    @SerializedName("version_code")
    val versionCode: Int,

    @SerializedName("process_name")
    val processName: String,

    @SerializedName("is_foreground")
    val isForeground: Boolean,

    @SerializedName("current_activity")
    val currentActivity: String?,

    @SerializedName("user_id")
    val userId: String?
)

/**
 * 网络信息
 */
data class NetworkInfo(
    @SerializedName("type")
    val type: String,

    @SerializedName("is_connected")
    val isConnected: Boolean
)
