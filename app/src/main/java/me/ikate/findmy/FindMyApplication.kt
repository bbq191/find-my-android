package me.ikate.findmy

import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.util.Log
import com.tencent.map.geolocation.TencentLocationManager
import com.tencent.tencentmap.mapsdk.maps.TencentMapInitializer
import me.ikate.findmy.crash.CrashMonitor
import me.ikate.findmy.crash.CrashUploadWorker
import me.ikate.findmy.di.allModules
import me.ikate.findmy.domain.communication.CommunicationManager
import me.ikate.findmy.push.FCMManager
import me.ikate.findmy.service.GeofenceServiceController
import me.ikate.findmy.service.MqttForegroundService
import me.ikate.findmy.service.TencentLocationService
import me.ikate.findmy.util.PrivacyManager
import me.ikate.findmy.util.SecurePreferences
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * FindMy 应用入口
 * 负责全局初始化
 *
 * 单例生命周期由 Koin 统一管理，避免手动 instance 模式导致的内存泄漏
 */
class FindMyApplication : Application() {

    companion object {
        private const val TAG = "FindMyApplication"
    }

    // Koin 注入的单例（由 Koin 管理生命周期）
    private val communicationManager: CommunicationManager by inject()
    private val geofenceServiceController: GeofenceServiceController by inject()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FindMyApplication 启动")

        // StrictMode 初始化（仅 Debug 构建，检测主线程违规和资源泄漏）
        initStrictMode()

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

        // 初始化通讯管理器（通过 Koin 注入，由 Koin 管理生命周期）
        // 注意：initialize() 内部有防重复调用保护
        initCommunicationManager()

        // 初始化围栏服务智能开关控制器（通过 Koin 注入，由 Koin 管理生命周期）
        // 根据数据库中是否有激活围栏，自动决定是否启动围栏监控前台服务
        initGeofenceServiceController()
    }

    /**
     * 初始化通讯管理器
     *
     * 功能：
     * - 统一管理 MQTT + FCM 通讯
     * - 智能重连（指数退避 + 网络感知）
     * - 离线消息队列管理
     * - 消息去重
     *
     * 生命周期由 Koin 管理，Application 销毁时自动释放资源
     */
    private fun initCommunicationManager() {
        try {
            // communicationManager 由 Koin 注入，调用 initialize() 启动内部功能
            // initialize() 内部有防重复调用保护
            communicationManager.initialize()
            Log.d(TAG, "通讯管理器初始化成功（由 Koin 管理生命周期）")
        } catch (e: Exception) {
            Log.e(TAG, "通讯管理器初始化失败", e)
        }
    }

    /**
     * 初始化围栏服务智能开关控制器
     *
     * 智能开关策略：
     * - 有激活围栏 → 启动 GeofenceForegroundService（高频位置监控，IMPORTANCE_LOW 通知）
     * - 无激活围栏 → 停止前台服务，切换到低功耗模式（FCM + WorkManager 心跳）
     *
     * 生命周期由 Koin 管理，Application 销毁时自动释放资源
     */
    private fun initGeofenceServiceController() {
        try {
            // geofenceServiceController 由 Koin 注入，调用 initialize() 启动内部功能
            geofenceServiceController.initialize()
            Log.d(TAG, "围栏服务智能开关控制器初始化成功（由 Koin 管理生命周期）")
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

    /**
     * 初始化 StrictMode（仅 Debug 构建）
     *
     * 功能：
     * - 检测主线程上的网络访问（最重要，会导致 ANR）
     * - 检测资源泄漏（未关闭的 Cursor、未释放的 SQLite 对象等）
     * - 检测 Activity 泄漏
     *
     * 注意：
     * - 仅在 Debug 构建中启用，Release 构建自动跳过
     * - 磁盘读写检测已禁用（第三方 SDK 如腾讯地图会产生大量误报）
     * - 明文网络检测已禁用（腾讯 SDK 内部通信会触发误报）
     * - 违规行为会记录到 Logcat（tag: StrictMode）
     * - 配合 LeakCanary 使用效果更佳
     */
    private fun initStrictMode() {
        if (!BuildConfig.DEBUG) {
            Log.d(TAG, "Release 构建，跳过 StrictMode 初始化")
            return
        }

        Log.d(TAG, "初始化 StrictMode（Debug 模式）")

        // 线程策略：仅检测最关键的违规（网络访问会导致 ANR）
        // 注意：磁盘读写检测已禁用，因为：
        // 1. 腾讯地图 SDK 初始化会触发大量磁盘读取
        // 2. SharedPreferences 首次加载会触发磁盘读取
        // 3. Firebase SDK 内部也有磁盘操作
        // 这些都是第三方库的正常行为，我们无法控制
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectNetwork()             // 检测网络访问（最重要！会导致 ANR）
                .detectCustomSlowCalls()     // 检测自定义慢调用
                // .detectDiskReads()        // 禁用：第三方 SDK 误报过多
                // .detectDiskWrites()       // 禁用：第三方 SDK 误报过多
                .penaltyLog()                // 记录到 Logcat
                .build()
        )

        // VM 策略：检测内存泄漏和资源泄漏（这些是真正需要关注的问题）
        // 注意：明文网络检测已禁用，因为腾讯 SDK 内部使用明文通信
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()      // 检测未关闭的 SQLite 对象
                .detectLeakedClosableObjects()     // 检测未关闭的 Closeable 对象
                .detectActivityLeaks()             // 检测 Activity 泄漏
                .detectLeakedRegistrationObjects() // 检测未注销的 BroadcastReceiver 等
                .detectFileUriExposure()           // 检测 file:// URI 暴露
                // .detectCleartextNetwork()       // 禁用：腾讯 SDK 内部明文通信
                .penaltyLog()                      // 记录到 Logcat
                .build()
        )

        Log.d(TAG, "StrictMode 初始化完成（精简模式：仅检测网络访问和资源泄漏）")
    }
}
