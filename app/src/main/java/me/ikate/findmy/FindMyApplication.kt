package me.ikate.findmy

import android.app.Application
import android.util.Log
import com.tencent.map.geolocation.TencentLocationManager
import com.tencent.tencentmap.mapsdk.maps.TencentMapInitializer
import me.ikate.findmy.di.allModules
import me.ikate.findmy.push.FCMManager
import me.ikate.findmy.service.MqttForegroundService
import me.ikate.findmy.service.TencentLocationService
import me.ikate.findmy.util.PrivacyManager
import me.ikate.findmy.util.SecurePreferences
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * FindMy 应用入口
 * 负责全局初始化
 */
class FindMyApplication : Application() {

    companion object {
        private const val TAG = "FindMyApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FindMyApplication 启动")

        // 初始化加密存储（在 Koin 之前，确保 ViewModel 创建时不会遇到问题）
        SecurePreferences.init(this)

        // 腾讯 SDK 隐私合规初始化（必须在使用任何腾讯 API 之前调用）
        initTencentPrivacy()

        // 初始化 Koin 依赖注入
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@FindMyApplication)
            modules(allModules)
        }

        // 获取并上报 FCM Token（初始化 FCM）
        initFcm()

        // 启动 MQTT 前台服务（保持后台连接）
        // 后台唤醒依赖 FCM 高优先级消息（Android 12+ 唯一可靠方式）
        MqttForegroundService.start(this)
    }

    /**
     * 初始化腾讯 SDK 隐私合规
     * 必须在使用任何腾讯 API（地图、定位）之前调用
     */
    private fun initTencentPrivacy() {
        val agreed = PrivacyManager.isPrivacyAgreed(this)

        // 定位 SDK 隐私合规
        TencentLocationService.updatePrivacyShow(this, true, true)
        TencentLocationService.updatePrivacyAgree(this, agreed)

        // 地图 SDK 隐私合规
        TencentMapInitializer.setAgreePrivacy(agreed)

        // 初始化地图 SDK
        try {
            TencentMapInitializer.setAgreePrivacy(agreed)
            Log.d(TAG, "腾讯地图 SDK 初始化成功，API Key: ${BuildConfig.TENCENT_MAP_KEY.take(8)}...")
        } catch (e: Exception) {
            Log.e(TAG, "腾讯地图 SDK 初始化失败", e)
        }

        Log.d(TAG, "腾讯 SDK 隐私合规已初始化，agreed=$agreed")
    }

    /**
     * 初始化 FCM 推送
     * 获取 Token 并保存，等待 MQTT 连接后上报
     */
    private fun initFcm() {
        FCMManager.getTokenAsync { token ->
            if (token != null) {
                Log.d(TAG, "FCM Token 获取成功")
                FCMManager.saveToken(this, token)
                // Token 会在 MQTT 连接成功后上报
            } else {
                Log.w(TAG, "FCM Token 获取失败")
            }
        }
    }
}
