package me.ikate.findmy.push

import me.ikate.findmy.BuildConfig

/**
 * 个推推送配置
 */
object GeTuiConfig {

    /** 个推 App ID */
    val appId: String = BuildConfig.GETUI_APP_ID

    /** 个推 App Key */
    val appKey: String = BuildConfig.GETUI_APP_KEY

    /** 是否已配置 */
    fun isConfigured(): Boolean {
        return appId.isNotBlank() && appId != "your_getui_app_id"
    }

    // ==================== 推送消息类型 ====================

    /** 位置请求推送 */
    const val MSG_TYPE_LOCATION_REQUEST = "location_request"

    /** 设备控制推送 (响铃、丢失模式等) */
    const val MSG_TYPE_DEVICE_COMMAND = "device_command"

    /** 分享请求推送 */
    const val MSG_TYPE_SHARE_REQUEST = "share_request"

    /** 分享接受推送 */
    const val MSG_TYPE_SHARE_ACCEPTED = "share_accepted"

    // ==================== 设备命令类型 ====================

    /** 播放声音 */
    const val COMMAND_PLAY_SOUND = "play_sound"

    /** 停止声音 */
    const val COMMAND_STOP_SOUND = "stop_sound"

    /** 丢失模式 */
    const val COMMAND_LOST_MODE = "lost_mode"

    /** 立即上报位置 */
    const val COMMAND_REPORT_LOCATION = "report_location"
}
