package me.ikate.findmy.push

import android.content.Context
import android.util.Log
import com.igexin.sdk.PushManager
import com.igexin.sdk.Tag
import me.ikate.findmy.data.repository.AuthRepository

/**
 * 个推推送管理器
 * 负责初始化、用户绑定、标签管理等
 */
object GeTuiManager {

    private const val TAG = "GeTuiManager"
    private var isInitialized = false

    /**
     * 预初始化个推推送（隐私合规）
     * 应在 Application.onCreate() 中调用
     * 此方法不会获取设备信息，仅读取配置
     */
    fun preInit(context: Context) {
        if (!GeTuiConfig.isConfigured()) {
            Log.w(TAG, "个推推送未配置 App ID，跳过预初始化")
            return
        }

        try {
            PushManager.getInstance().preInit(context)
            Log.d(TAG, "个推推送预初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "个推推送预初始化失败", e)
        }
    }

    /**
     * 初始化个推推送（在用户同意隐私政策后调用）
     * 此方法会注册 CID 并启动推送服务
     */
    fun init(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "个推推送已初始化")
            return
        }

        if (!GeTuiConfig.isConfigured()) {
            Log.w(TAG, "个推推送未配置 App ID，跳过初始化")
            return
        }

        try {
            // 初始化个推 SDK（会启动推送服务并注册 CID）
            PushManager.getInstance().initialize(context)

            isInitialized = true
            Log.d(TAG, "个推推送初始化成功")

            // 获取并打印 Client ID
            val clientId = PushManager.getInstance().getClientid(context)
            if (!clientId.isNullOrBlank()) {
                Log.d(TAG, "个推 Client ID: $clientId")
                GeTuiMessageHandler.saveClientId(context, clientId)
            }

        } catch (e: Exception) {
            Log.e(TAG, "个推推送初始化失败", e)
        }
    }

    /**
     * 绑定用户别名
     * 使用 Android ID 作为别名，便于定向推送
     */
    fun bindUser(context: Context) {
        if (!isInitialized) {
            Log.w(TAG, "个推未初始化，无法绑定用户")
            return
        }

        try {
            val userId = AuthRepository.getUserId(context)
            // 个推使用 bindAlias 绑定别名
            PushManager.getInstance().bindAlias(context, userId)
            Log.d(TAG, "用户别名绑定请求已发送: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "用户别名绑定失败", e)
        }
    }

    /**
     * 解绑用户别名
     */
    fun unbindUser(context: Context) {
        if (!isInitialized) return

        try {
            val userId = AuthRepository.getUserId(context)
            PushManager.getInstance().unBindAlias(context, userId, true)
            Log.d(TAG, "用户别名已解绑: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "用户别名解绑失败", e)
        }
    }

    /**
     * 停止推送服务
     */
    fun stopPush(context: Context) {
        try {
            PushManager.getInstance().turnOffPush(context)
            Log.d(TAG, "推送服务已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止推送服务失败", e)
        }
    }

    /**
     * 恢复推送服务
     */
    fun resumePush(context: Context) {
        try {
            PushManager.getInstance().turnOnPush(context)
            Log.d(TAG, "推送服务已恢复")
        } catch (e: Exception) {
            Log.e(TAG, "恢复推送服务失败", e)
        }
    }

    /**
     * 检查推送是否已停止
     */
    fun isPushStopped(context: Context): Boolean {
        return try {
            !PushManager.getInstance().isPushTurnedOn(context)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取 Client ID
     */
    fun getClientId(context: Context): String {
        return try {
            PushManager.getInstance().getClientid(context) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 设置标签（用于分组推送）
     */
    fun setTags(context: Context, tags: Array<String>) {
        try {
            val tagArray = tags.map { Tag().apply { name = it } }.toTypedArray()
            PushManager.getInstance().setTag(context, tagArray, "findmy_tags")
            Log.d(TAG, "标签设置请求已发送: ${tags.contentToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "标签设置失败", e)
        }
    }

    /**
     * 设置静默时间（在此时间段内不弹出通知）
     * @param beginHour 开始小时 (0-23)
     * @param duration 持续时间（小时）
     */
    fun setSilentTime(context: Context, beginHour: Int, duration: Int) {
        try {
            PushManager.getInstance().setSilentTime(context, beginHour, duration)
            Log.d(TAG, "静默时间设置成功: 从 $beginHour 点开始，持续 $duration 小时")
        } catch (e: Exception) {
            Log.e(TAG, "静默时间设置失败", e)
        }
    }

    /**
     * 设置角标数量（仅部分厂商支持）
     */
    fun setBadgeNum(context: Context, num: Int) {
        try {
            PushManager.getInstance().setBadgeNum(context, num)
        } catch (e: Exception) {
            Log.e(TAG, "设置角标失败", e)
        }
    }

    /**
     * 清除角标
     */
    fun clearBadge(context: Context) {
        setBadgeNum(context, 0)
    }

    /**
     * 获取 SDK 版本
     */
    fun getVersion(context: Context): String {
        return try {
            PushManager.getInstance().getVersion(context) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 检查通知权限是否开启
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return try {
            PushManager.getInstance().areNotificationsEnabled(context)
        } catch (e: Exception) {
            false
        }
    }
}
