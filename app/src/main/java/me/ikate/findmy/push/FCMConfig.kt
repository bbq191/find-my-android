package me.ikate.findmy.push

/**
 * FCM 推送配置
 * 定义消息类型和命令常量
 */
object FCMConfig {

    // ==================== 推送消息类型 ====================

    /** 位置请求推送 */
    const val MSG_TYPE_LOCATION_REQUEST = "location_request"

    /** 设备控制推送 (响铃、丢失模式等) */
    const val MSG_TYPE_DEVICE_COMMAND = "device_command"

    /** 分享请求推送 */
    const val MSG_TYPE_SHARE_REQUEST = "share_request"

    /** 分享接受推送 */
    const val MSG_TYPE_SHARE_ACCEPTED = "share_accepted"

    /** 同步指令推送 (双通道模式: FCM 唤醒 + Firestore 指令) */
    const val MSG_TYPE_SYNC_COMMANDS = "sync_commands"

    /** 围栏触发事件推送 */
    const val MSG_TYPE_GEOFENCE_EVENT = "geofence_event"

    /** 围栏配置同步推送 (通知对方同步围栏设置) */
    const val MSG_TYPE_GEOFENCE_SYNC = "geofence_sync"

    // ==================== 设备命令类型 ====================

    /** 播放声音 */
    const val COMMAND_PLAY_SOUND = "play_sound"

    /** 停止声音 */
    const val COMMAND_STOP_SOUND = "stop_sound"

    /** 丢失模式 */
    const val COMMAND_LOST_MODE = "lost_mode"

    /** 立即上报位置 */
    const val COMMAND_REPORT_LOCATION = "report_location"

    // ==================== SharedPreferences ====================

    /** SharedPreferences 名称 */
    const val PREFS_NAME = "fcm_prefs"

    /** FCM Token 存储键 */
    const val KEY_FCM_TOKEN = "fcm_token"
}
