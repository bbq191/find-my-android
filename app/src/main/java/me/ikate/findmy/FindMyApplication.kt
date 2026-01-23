package me.ikate.findmy

import android.app.Application
import android.util.Log
import com.tencent.map.geolocation.TencentLocationManager
import com.tencent.tencentmap.mapsdk.maps.TencentMapInitializer
import me.ikate.findmy.crash.CrashMonitor
import me.ikate.findmy.crash.CrashUploadWorker
import me.ikate.findmy.di.allModules
import me.ikate.findmy.push.FCMManager
import me.ikate.findmy.service.GeofenceServiceController
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

        // 崩溃监控初始化（必须最先执行，捕获后续初始化中的崩溃）
        initCrashMonitor()

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

        // 初始化围栏服务智能开关控制器
        // 根据数据库中是否有激活围栏，自动决定是否启动围栏监控前台服务
        initGeofenceServiceController()
    }

    /**
     * 初始化围栏服务智能开关控制器
     *
     * 智能开关策略：
     * - 有激活围栏 → 启动 GeofenceForegroundService（高频位置监控，IMPORTANCE_LOW 通知）
     * - 无激活围栏 → 停止前台服务，切换到低功耗模式（FCM + WorkManager 心跳）
     */
    private fun initGeofenceServiceController() {
        try {
            GeofenceServiceController.getInstance(this).initialize()
            Log.d(TAG, "围栏服务智能开关控制器初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "围栏服务智能开关控制器初始化失败", e)
        }
    }

    /**
     * 初始化崩溃监控系统
     * 必须最先执行，以捕获后续初始化中的崩溃
     */
    private fun initCrashMonitor() {
        try {
            CrashMonitor.init(this)

            // 检查是否处于崩溃循环
            if (CrashMonitor.isInCrashLoop()) {
                Log.w(TAG, "应用处于崩溃循环，部分功能可能被禁用")
                // TODO: 可以在这里跳过某些可能导致崩溃的初始化
            }

            // 检查是否有待上传的崩溃日志，调度上传任务
            val pendingCount = CrashMonitor.getPendingLogCount()
            if (pendingCount > 0) {
                Log.d(TAG, "发现 $pendingCount 条待上传的崩溃日志，调度上传任务")
                CrashUploadWorker.enqueue(this)
            }

            Log.d(TAG, "崩溃监控系统初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "崩溃监控系统初始化失败", e)
        }
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
