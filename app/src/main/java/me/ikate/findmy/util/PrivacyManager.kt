package me.ikate.findmy.util

import android.content.Context
import android.content.SharedPreferences
import com.tencent.tencentmap.mapsdk.maps.TencentMapInitializer
import me.ikate.findmy.service.TencentLocationService

/**
 * 隐私合规管理器
 * 管理用户隐私授权状态，用于满足腾讯等 SDK 的合规要求
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
     * 同时初始化腾讯等 SDK 的隐私接口
     */
    fun setPrivacyAgreed(context: Context, agreed: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_PRIVACY_AGREED, agreed)
            .putLong(KEY_PRIVACY_SHOW_TIME, System.currentTimeMillis())
            .apply()

        // 更新腾讯 SDK 隐私状态
        updateTencentPrivacy(context, agreed)
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

        // 定位 SDK 隐私合规
        TencentLocationService.updatePrivacyShow(context, true, true)
        TencentLocationService.updatePrivacyAgree(context, agreed)

        // 地图 SDK 隐私合规（必须在使用地图 API 之前设置）
        TencentMapInitializer.setAgreePrivacy(agreed)

        return !agreed
    }

    /**
     * 更新腾讯 SDK 隐私状态
     */
    private fun updateTencentPrivacy(context: Context, agreed: Boolean) {
        // 定位 SDK
        TencentLocationService.updatePrivacyShow(context, true, true)
        TencentLocationService.updatePrivacyAgree(context, agreed)

        // 地图 SDK
        TencentMapInitializer.setAgreePrivacy(agreed)
    }

    /**
     * 获取隐私政策展示时间
     */
    fun getPrivacyShowTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_PRIVACY_SHOW_TIME, 0)
    }
}
