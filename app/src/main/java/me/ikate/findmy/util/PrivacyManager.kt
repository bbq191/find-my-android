package me.ikate.findmy.util

import android.content.Context
import android.content.SharedPreferences
import me.ikate.findmy.service.AmapLocationService

/**
 * 隐私合规管理器
 * 管理用户隐私授权状态，用于满足高德等 SDK 的合规要求
 */
object PrivacyManager {

    private const val PREFS_NAME = "privacy_prefs"
    private const val KEY_PRIVACY_AGREED = "privacy_agreed"
    private const val KEY_PRIVACY_SHOW_TIME = "privacy_show_time"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 检查用户是否已同意隐私政策
     */
    fun isPrivacyAgreed(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PRIVACY_AGREED, false)
    }

    /**
     * 记录用户同意隐私政策
     * 同时初始化高德等 SDK 的隐私接口
     */
    fun setPrivacyAgreed(context: Context, agreed: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_PRIVACY_AGREED, agreed)
            .putLong(KEY_PRIVACY_SHOW_TIME, System.currentTimeMillis())
            .apply()

        // 更新高德 SDK 隐私状态
        updateAmapPrivacy(context, agreed)
    }

    /**
     * 初始化 SDK 隐私合规
     * 应在 Application.onCreate 或首次使用定位前调用
     *
     * @param context 上下文
     * @param showPrivacyDialog 是否需要显示隐私政策弹窗（未同意时返回 true）
     * @return 是否需要显示隐私政策弹窗
     */
    fun initPrivacy(context: Context): Boolean {
        val agreed = isPrivacyAgreed(context)

        // 无论是否同意，都需要调用这两个方法
        // isContains: 隐私政策是否包含高德说明
        // isShow: 是否弹窗展示给用户
        AmapLocationService.updatePrivacyShow(context, true, true)
        AmapLocationService.updatePrivacyAgree(context, agreed)

        return !agreed
    }

    /**
     * 更新高德 SDK 隐私状态
     */
    private fun updateAmapPrivacy(context: Context, agreed: Boolean) {
        AmapLocationService.updatePrivacyShow(context, true, true)
        AmapLocationService.updatePrivacyAgree(context, agreed)
    }

    /**
     * 获取隐私政策展示时间
     */
    fun getPrivacyShowTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_PRIVACY_SHOW_TIME, 0)
    }
}
