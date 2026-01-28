package me.ikate.findmy.data.remote.mqtt

import me.ikate.findmy.BuildConfig

/**
 * MQTT 连接配置
 * 从 BuildConfig 读取 EMQX Cloud 配置
 */
object MqttConfig {

    /** MQTT Broker URL (ssl://xxx.emqxsl.cn:8883) */
    val brokerUrl: String = BuildConfig.MQTT_BROKER_URL

    /** MQTT 用户名 */
    val username: String = BuildConfig.MQTT_USERNAME

    /** MQTT 密码 */
    val password: String = BuildConfig.MQTT_PASSWORD

    /** 连接超时时间（秒） */
    const val CONNECTION_TIMEOUT = 30

    /** 保活间隔（秒） */
    const val KEEP_ALIVE_INTERVAL = 60

    /** 最大重连延迟（秒） */
    const val MAX_RECONNECT_DELAY = 128

    /** 最大重连次数（达到后停止重连，避免无限重试） */
    const val MAX_RECONNECT_ATTEMPTS = 5

    /**
     * 会话版本号
     * 当订阅拓扑发生变化时递增此值，触发 clean session 连接以清除 EMQX 端的旧持久会话
     * v1: 初始版本（6 个独立系统订阅）
     * v2: 通配符合并（3 个系统订阅 + N 个联系人位置订阅）
     * v3-v4: purge 逻辑移至 MqttConnectionManager.connect() 内部，确保在任何 connect 前执行
     */
    const val SESSION_VERSION = 5

    /** SharedPreferences Key: 已同步的会话版本号 */
    const val PREF_SESSION_VERSION = "mqtt_session_version"

    /** 消息保留时间 - QoS 1/2 消息的离线保留（秒）*/
    const val MESSAGE_EXPIRY_INTERVAL = 3600L // 1 小时

    /** 是否清除会话（false = 持久会话，支持离线消息） */
    const val CLEAN_SESSION = false

    // ==================== 主题定义 ====================

    /** 位置更新主题前缀 */
    const val TOPIC_LOCATION_PREFIX = "findmy/location/"

    /** 在线状态主题前缀 */
    const val TOPIC_PRESENCE_PREFIX = "findmy/presence/"

    /** 设备控制主题前缀 */
    const val TOPIC_COMMAND_PREFIX = "findmy/command/"

    /** 共享组主题前缀 */
    const val TOPIC_GROUP_PREFIX = "findmy/group/"

    /** 共享请求主题前缀 (用于发送邀请) */
    const val TOPIC_SHARE_REQUEST_PREFIX = "findmy/share/request/"

    /** 共享响应主题前缀 (用于接受/拒绝邀请) */
    const val TOPIC_SHARE_RESPONSE_PREFIX = "findmy/share/response/"

    /** 请求主题前缀 (用于位置请求、发声等) */
    const val TOPIC_REQUEST_PREFIX = "findmy/requests/"

    /** 共享暂停状态主题前缀 (用于通知暂停/恢复共享) */
    const val TOPIC_SHARE_PAUSE_PREFIX = "findmy/share/pause/"

    /** FCM Token 主题前缀 (用于上报 FCM Token 到服务器) */
    const val TOPIC_FCM_TOKEN_PREFIX = "findmy/fcm_token/"

    /** 围栏事件主题前缀 (用于通知围栏触发事件) */
    const val TOPIC_GEOFENCE_EVENT_PREFIX = "findmy/geofence/events/"

    /** 围栏同步主题前缀 (用于围栏配置同步) */
    const val TOPIC_GEOFENCE_SYNC_PREFIX = "findmy/geofence/sync/"

    /** 调试消息主题前缀 (用于远程调试) */
    const val TOPIC_DEBUG_PREFIX = "findmy/debug/"

    /**
     * 获取用户的位置主题
     * @param userId 用户 ID
     * @return 位置主题，如 "findmy/location/uid123"
     */
    fun getLocationTopic(userId: String): String = "$TOPIC_LOCATION_PREFIX$userId"

    /**
     * 获取用户的在线状态主题
     * @param userId 用户 ID
     * @return 在线状态主题，如 "findmy/presence/uid123"
     */
    fun getPresenceTopic(userId: String): String = "$TOPIC_PRESENCE_PREFIX$userId"

    /**
     * 获取设备的控制命令主题
     * @param deviceId 设备 ID
     * @return 控制命令主题，如 "findmy/command/device123"
     */
    fun getCommandTopic(deviceId: String): String = "$TOPIC_COMMAND_PREFIX$deviceId"

    /**
     * 获取共享组的主题
     * @param groupId 组 ID
     * @return 共享组主题，如 "findmy/group/group123"
     */
    fun getGroupTopic(groupId: String): String = "$TOPIC_GROUP_PREFIX$groupId"

    /**
     * 获取用户的共享请求接收主题
     * 用户订阅此主题来接收别人发来的邀请
     * @param userId 用户 ID
     * @return 共享请求主题，如 "findmy/share/request/uid123"
     */
    fun getShareRequestTopic(userId: String): String = "$TOPIC_SHARE_REQUEST_PREFIX$userId"

    /**
     * 获取用户的共享响应接收主题
     * 用户订阅此主题来接收邀请的响应（接受/拒绝）
     * @param userId 用户 ID
     * @return 共享响应主题，如 "findmy/share/response/uid123"
     */
    fun getShareResponseTopic(userId: String): String = "$TOPIC_SHARE_RESPONSE_PREFIX$userId"

    /**
     * 获取用户的请求接收主题
     * 用户订阅此主题来接收位置请求、发声请求等
     * @param userId 用户 ID
     * @return 请求主题，如 "findmy/requests/uid123"
     */
    fun getRequestTopic(userId: String): String = "$TOPIC_REQUEST_PREFIX$userId"

    /**
     * 获取用户的共享暂停状态接收主题
     * 用户订阅此主题来接收联系人的暂停/恢复通知
     * @param userId 用户 ID
     * @return 暂停状态主题，如 "findmy/share/pause/uid123"
     */
    fun getSharePauseTopic(userId: String): String = "$TOPIC_SHARE_PAUSE_PREFIX$userId"

    /**
     * 获取用户的 FCM Token 上报主题
     * 用于将设备的 FCM Token 上报到服务器
     * @param userId 用户 ID
     * @return FCM Token 主题，如 "findmy/fcm_token/uid123"
     */
    fun getFcmTokenTopic(userId: String): String = "$TOPIC_FCM_TOKEN_PREFIX$userId"

    /**
     * 获取用户的围栏事件接收主题
     * 用户订阅此主题来接收联系人的围栏触发事件
     * @param userId 用户 ID
     * @return 围栏事件主题，如 "findmy/geofence/events/uid123"
     */
    fun getGeofenceEventTopic(userId: String): String = "$TOPIC_GEOFENCE_EVENT_PREFIX$userId"

    /**
     * 获取用户的围栏同步接收主题
     * 用户订阅此主题来接收围栏配置同步通知
     * @param userId 用户 ID
     * @return 围栏同步主题，如 "findmy/geofence/sync/uid123"
     */
    fun getGeofenceSyncTopic(userId: String): String = "$TOPIC_GEOFENCE_SYNC_PREFIX$userId"

    /**
     * 获取用户的调试消息接收主题
     * 用户订阅此主题来接收来自其他设备的调试反馈
     * @param userId 用户 ID
     * @return 调试消息主题，如 "findmy/debug/uid123"
     */
    fun getDebugTopic(userId: String): String = "$TOPIC_DEBUG_PREFIX$userId"

    // ==================== 通配符主题（节省 EMQX Serverless 订阅配额，上限 10 个/客户端） ====================

    /**
     * 获取用户的共享通配符主题
     * 覆盖 share/request、share/response、share/pause 三个子主题
     * @param userId 用户 ID
     * @return 通配符主题，如 "findmy/share/+/uid123"
     */
    fun getShareWildcardTopic(userId: String): String = "findmy/share/+/$userId"

    /**
     * 获取用户的围栏通配符主题
     * 覆盖 geofence/events、geofence/sync 两个子主题
     * @param userId 用户 ID
     * @return 通配符主题，如 "findmy/geofence/+/uid123"
     */
    fun getGeofenceWildcardTopic(userId: String): String = "findmy/geofence/+/$userId"

    /**
     * 生成客户端 ID
     * @param userId 用户 ID
     * @param deviceId 设备 ID
     * @return 唯一客户端 ID
     */
    fun generateClientId(userId: String, deviceId: String): String {
        return "findmy_${userId}_$deviceId"
    }

    /**
     * 检查配置是否有效
     */
    fun isConfigured(): Boolean {
        return brokerUrl.isNotBlank() &&
               username.isNotBlank() &&
               password.isNotBlank() &&
               brokerUrl != "ssl://your-broker.emqxsl.cn:8883"
    }
}
