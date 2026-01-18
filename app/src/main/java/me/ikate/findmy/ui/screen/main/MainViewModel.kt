package me.ikate.findmy.ui.screen.main

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mapbox.maps.MapboxMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.pointOf
import me.ikate.findmy.data.remote.mqtt.LocationMqttService
import me.ikate.findmy.data.remote.mqtt.MqttConfig
import me.ikate.findmy.data.remote.mqtt.MqttConnectionManager
import me.ikate.findmy.data.remote.mqtt.message.RequestMessage
import me.ikate.findmy.data.repository.AuthRepository
import me.ikate.findmy.data.repository.ContactRepository
import me.ikate.findmy.data.repository.DeviceRepository
import me.ikate.findmy.push.GeTuiMessageHandler
import me.ikate.findmy.service.ActivityRecognitionManager
import me.ikate.findmy.service.LocationReportService
import me.ikate.findmy.service.SmartLocationConfig
import me.ikate.findmy.ui.screen.main.components.SheetValue
import me.ikate.findmy.util.MapCameraHelper
import me.ikate.findmy.worker.LocationReportWorker
import me.ikate.findmy.worker.SmartLocationWorker
import java.util.concurrent.TimeUnit

/**
 * MainViewModel - 主屏幕状态管理
 * 负责管理地图状态、定位状态、设备选中状态等
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // 地图实例 (Mapbox)
    private val _mapboxMap = MutableStateFlow<MapboxMap?>(null)
    val mapboxMap: StateFlow<MapboxMap?> = _mapboxMap.asStateFlow()

    // 高德定位服务
    private val amapLocationService = me.ikate.findmy.service.AmapLocationService(application)

    // 认证仓库
    private val authRepository = AuthRepository(application.applicationContext)

    // 设备数据仓库
    private val deviceRepository = DeviceRepository(application.applicationContext)

    // 联系人仓库（用于检查共享状态）
    private val contactRepository = ContactRepository(application.applicationContext)

    // 位置上报服务
    private val locationReportService = LocationReportService(application)

    // WorkManager
    private val workManager = WorkManager.getInstance(application)

    // 活动识别管理器（智能位置上报）
    private val activityRecognitionManager = ActivityRecognitionManager(application)

    // 智能位置上报是否已启动
    private var isSmartLocationStarted = false

    // 定位按钮状态：true = 地图中心在用户位置（实心箭头）, false = 不在（空心箭头）
    private val _isLocationCentered = MutableStateFlow(false)
    val isLocationCentered: StateFlow<Boolean> = _isLocationCentered.asStateFlow()

    // 设备列表（从本地 Room 数据库实时获取，通过 MQTT 同步）
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    // 选中的设备（用于显示详情）
    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice: StateFlow<Device?> = _selectedDevice.asStateFlow()

    // 底部面板状态
    private val _sheetValue = MutableStateFlow(SheetValue.Collapsed)

    // 是否已完成首次自动定位
    private var hasInitialCentered = false

    // MQTT 连接状态
    private val _mqttConnectionState = MutableStateFlow<MqttConnectionManager.ConnectionState>(
        MqttConnectionManager.ConnectionState.Disconnected
    )
    val mqttConnectionState: StateFlow<MqttConnectionManager.ConnectionState> = _mqttConnectionState.asStateFlow()

    init {
        // 确保用户已登录（自动匿名登录）
        ensureUserAuthenticated()
        // 初始化 MQTT 连接
        initMqttConnection()
        // 启动时开始监听设备列表变化
        observeDevices()
        // 立即上报一次位置，以便在地图上显示当前设备
        reportLocationNow()
        // 初始化位置上报（智能模式或传统模式）
        initLocationReporting()
    }

    /**
     * 确保用户已就绪
     * 使用 Android ID 作为用户标识，无需认证流程
     */
    private fun ensureUserAuthenticated() {
        viewModelScope.launch {
            authRepository.signIn().fold(
                onSuccess = { userId ->
                    Log.d(TAG, "用户已就绪: $userId")
                },
                onFailure = { error ->
                    Log.e(TAG, "无法获取用户ID", error)
                }
            )
        }
    }

    /**
     * 尝试首次自动定位
     * 需在获取权限且地图加载完成后调用
     */
    fun attemptInitialLocationCenter() {
        if (!hasInitialCentered && _mapboxMap.value != null) {
            onLocationButtonClick()
            hasInitialCentered = true
        }
    }

    /**
     * 检查当前用户是否为匿名用户
     * 简化实现：使用 Android ID 时不存在匿名用户概念，始终返回 false
     */
    private fun isAnonymousUser(): Boolean {
        return false
    }

    /**
     * 初始化 MQTT 连接
     */
    private fun initMqttConnection() {
        if (!MqttConfig.isConfigured()) {
            Log.d(TAG, "MQTT 未配置，跳过连接")
            return
        }

        viewModelScope.launch {
            // 连接 MQTT
            val result = deviceRepository.connectMqtt()
            result.fold(
                onSuccess = {
                    Log.d(TAG, "MQTT 连接成功")
                    _mqttConnectionState.value = MqttConnectionManager.ConnectionState.Connected
                    // 订阅当前用户的位置主题和共享主题
                    val userId = authRepository.getCurrentUserId()
                    if (userId != null) {
                        deviceRepository.subscribeToUser(userId)
                        // 订阅共享主题（接收邀请和响应）和请求主题
                        val mqttService = DeviceRepository.getMqttService(getApplication())
                        viewModelScope.launch {
                            // 订阅共享主题
                            val shareResult = mqttService.subscribeToShareTopics(userId)
                            if (shareResult.isSuccess) {
                                Log.d(TAG, "已订阅共享主题")
                            } else {
                                Log.e(TAG, "订阅共享主题失败")
                            }
                            // 订阅请求主题（位置请求、发声请求等）
                            val requestResult = mqttService.subscribeToRequestTopic(userId)
                            if (requestResult.isSuccess) {
                                Log.d(TAG, "已订阅请求主题")
                                // 开始监听请求
                                observeRequests(mqttService)
                            } else {
                                Log.e(TAG, "订阅请求主题失败")
                            }
                            // 订阅共享暂停状态主题
                            val pauseResult = mqttService.subscribeToSharePauseTopic(userId)
                            if (pauseResult.isSuccess) {
                                Log.d(TAG, "已订阅暂停状态主题")
                            } else {
                                Log.e(TAG, "订阅暂停状态主题失败")
                            }
                            // 刷新离线消息队列
                            val sentCount = mqttService.flushPendingMessages()
                            if (sentCount > 0) {
                                Log.d(TAG, "已发送 $sentCount 条离线消息")
                            }
                        }
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "MQTT 连接失败", error)
                    _mqttConnectionState.value = MqttConnectionManager.ConnectionState.Error(
                        error.message ?: "连接失败",
                        error
                    )
                }
            )

            // 监听 MQTT 连接状态变化
            val mqttManager = DeviceRepository.getMqttManager(getApplication())
            mqttManager.connectionState.collect { state ->
                _mqttConnectionState.value = state
            }
        }
    }

    /**
     * 监听设备列表变化
     */
    private fun observeDevices() {
        viewModelScope.launch {
            deviceRepository.observeDevices().collect { deviceList ->
                Log.d(TAG, "收到设备列表更新，数量: ${deviceList.size}")
                deviceList.forEach { device ->
                    Log.d(TAG, "  - ${device.name} (id=${device.id})")
                }
                _devices.value = deviceList
            }
        }
    }

    /**
     * 监听 MQTT 请求消息（位置请求、发声请求等）
     */
    private fun observeRequests(mqttService: LocationMqttService) {
        viewModelScope.launch {
            mqttService.requestUpdates.collect { request ->
                Log.d(TAG, "收到请求: type=${request.type}, from=${request.requesterUid}")
                handleRequest(request)
            }
        }
    }

    /**
     * 处理收到的请求
     * 在响应请求前检查共享状态：
     * 1. 请求者必须在联系人列表中
     * 2. 共享状态必须是 ACCEPTED
     * 3. 我没有暂停与该用户的共享
     */
    private fun handleRequest(request: RequestMessage) {
        viewModelScope.launch {
            // 检查是否应该响应该请求
            val shouldRespond = contactRepository.shouldRespondToRequest(request.requesterUid)
            if (!shouldRespond) {
                Log.d(TAG, "忽略来自 ${request.requesterUid} 的请求: 共享已暂停或无效")
                return@launch
            }

            when (request.type) {
                RequestMessage.TYPE_SINGLE -> {
                    // 单次位置请求 - 立即上报位置
                    Log.d(TAG, "收到位置请求，正在上报位置...")
                    reportLocationNow()
                }
                RequestMessage.TYPE_CONTINUOUS -> {
                    // 持续追踪请求 - 触发连续上报
                    Log.d(TAG, "收到持续追踪请求，开始连续上报...")
                    reportLocationNow()
                }
                RequestMessage.TYPE_PLAY_SOUND -> {
                    // 播放声音请求 - 直接调用 SoundPlaybackService
                    Log.d(TAG, "收到播放声音请求，来自: ${request.requesterUid}")
                    me.ikate.findmy.service.SoundPlaybackService.startPlaying(
                        context = getApplication(),
                        requesterUid = request.requesterUid
                    )
                }
                RequestMessage.TYPE_STOP_SOUND -> {
                    // 停止声音 - 直接调用 SoundPlaybackService
                    Log.d(TAG, "收到停止声音请求")
                    me.ikate.findmy.service.SoundPlaybackService.stopPlaying(getApplication())
                }
                RequestMessage.TYPE_LOST_MODE -> {
                    // 丢失模式 - 直接调用 LostModeService
                    Log.d(TAG, "收到丢失模式请求: message=${request.message}, phone=${request.phoneNumber}, playSound=${request.playSound}")
                    me.ikate.findmy.service.LostModeService.enable(
                        context = getApplication(),
                        message = request.message ?: "此设备已丢失",
                        phoneNumber = request.phoneNumber ?: "",
                        playSound = request.playSound,
                        requesterUid = request.requesterUid
                    )
                }
                RequestMessage.TYPE_DISABLE_LOST_MODE -> {
                    // 关闭丢失模式
                    Log.d(TAG, "收到关闭丢失模式请求")
                    me.ikate.findmy.service.LostModeService.disable(getApplication())
                }
                else -> {
                    Log.w(TAG, "未知请求类型: ${request.type}")
                }
            }
        }
    }

    /**
     * 设置地图实例 (Mapbox)
     */
    fun setMapboxMap(map: MapboxMap) {
        _mapboxMap.value = map
    }

    /**
     * 更新定位中心状态
     */
    fun updateLocationCenteredState(isCentered: Boolean) {
        _isLocationCentered.value = isCentered
    }

    /**
     * 点击定位按钮 - 移动地图到用户当前位置
     * 使用高德定位获取位置，内部已转换为 WGS-84
     * 注册用户会自动上报位置，匿名用户不上报
     */
    fun onLocationButtonClick() {
        val map = mapboxMap.value ?: return

        // 使用高德定位获取位置（内部已转换为 WGS-84）
        viewModelScope.launch {
            val result = amapLocationService.getLocation(timeout = 10000L)
            if (result.isSuccess) {
                MapCameraHelper.animateToLocation(map, result.point, zoom = 15.0)
                updateLocationCenteredState(true)

                // 只有注册用户点击重定位时才上报位置，匿名用户不上报
                if (!isAnonymousUser()) {
                    reportLocationNow()
                }
            } else {
                Log.e(TAG, "定位失败: ${result.errorInfo}")
            }
        }
    }

    /**
     * 取消选中设备
     */
    fun clearSelectedDevice() {
        _selectedDevice.value = null
    }

    /**
     * 更新底部面板状态
     */
    fun updateSheetValue(value: SheetValue) {
        _sheetValue.value = value
    }

    /**
     * 初始化位置上报
     * 始终使用智能模式（基于传感器的活动识别）
     */
    private fun initLocationReporting() {
        // 取消传统定期任务，启动智能上报
        workManager.cancelUniqueWork(LEGACY_LOCATION_WORK_NAME)
        initSmartLocationReporting()
    }

    /**
     * 立即上报当前位置
     */
    fun reportLocationNow() {
        viewModelScope.launch {
            val result = locationReportService.reportCurrentLocation()
            result.fold(
                onSuccess = {
                    Log.d(TAG, "位置上报成功")
                },
                onFailure = { error ->
                    Log.e(TAG, "位置上报失败", error)
                }
            )
        }
    }

    // ==================== 智能位置上报 ====================

    /**
     * 初始化智能位置上报
     * 启动基于传感器的活动识别监听
     */
    private fun initSmartLocationReporting() {
        if (isSmartLocationStarted) return

        // 启动基于传感器的活动识别
        startActivityRecognition()

        // 启动 WiFi 环境变化监测
        startWifiMonitoring()

        // 安排智能定期上报
        scheduleSmartLocationReport()
    }

    /**
     * 启动 WiFi 环境变化监测
     * 当 WiFi BSSID 变化时触发位置检查
     */
    private fun startWifiMonitoring() {
        viewModelScope.launch {
            // 保存当前 WiFi BSSID 作为初始值
            val currentBssid = SmartLocationConfig.getCurrentWifiBssid(getApplication())
            SmartLocationConfig.saveLastWifiBssid(getApplication(), currentBssid)
            Log.d(TAG, "WiFi 环境监测已启动，当前 BSSID: ${currentBssid ?: "未连接"}")
        }
    }

    /**
     * 检查 WiFi 环境变化并触发上报
     * 应在定期检查或网络状态变化时调用
     */
    fun checkWifiEnvironmentChange() {
        val context = getApplication<Application>()
        if (SmartLocationConfig.hasWifiEnvironmentChanged(context)) {
            Log.d(TAG, "检测到 WiFi 环境变化，触发位置检查")

            // 发送 WiFi 变化触发的活动转换广播
            me.ikate.findmy.service.ActivityTransitionReceiver.sendActivityTransition(
                context = context,
                activityType = SmartLocationConfig.getLastActivity(context),
                transitionType = me.ikate.findmy.service.ActivityTransitionReceiver.ACTIVITY_TRANSITION_ENTER,
                source = me.ikate.findmy.service.ActivityTransitionReceiver.SOURCE_WIFI_CHANGE
            )

            // 更新保存的 WiFi BSSID
            val currentBssid = SmartLocationConfig.getCurrentWifiBssid(context)
            SmartLocationConfig.saveLastWifiBssid(context, currentBssid)
        }
    }

    /**
     * 启动活动识别监听
     * 基于传感器的活动识别，不依赖 Google Play Services
     */
    fun startActivityRecognition() {
        if (isSmartLocationStarted) return

        // 检查传感器是否可用
        if (!activityRecognitionManager.isSensorAvailable()) {
            Log.w(TAG, "加速度传感器不可用，使用 GPS 速度推断活动类型")
            isSmartLocationStarted = true
            return
        }

        // 设置活动变化回调
        activityRecognitionManager.setActivityChangeCallback { newActivity ->
            Log.d(TAG, "活动识别: ${newActivity.displayName}")

            // 发送活动转换广播
            me.ikate.findmy.service.ActivityTransitionReceiver.sendActivityTransition(
                context = getApplication(),
                activityType = newActivity,
                transitionType = me.ikate.findmy.service.ActivityTransitionReceiver.ACTIVITY_TRANSITION_ENTER,
                source = me.ikate.findmy.service.ActivityTransitionReceiver.SOURCE_SENSOR
            )
        }

        activityRecognitionManager.startActivityTransitionUpdates(
            onSuccess = {
                isSmartLocationStarted = true
                Log.d(TAG, "智能位置上报已启动 - 传感器活动识别监听中")
            },
            onFailure = { e ->
                Log.w(TAG, "启动传感器活动识别失败: ${e.message}，使用 GPS 速度推断")
                isSmartLocationStarted = true
            }
        )
    }

    /**
     * 停止活动识别监听
     */
    fun stopActivityRecognition() {
        activityRecognitionManager.stopActivityTransitionUpdates(
            onSuccess = {
                isSmartLocationStarted = false
                Log.d(TAG, "活动识别监听已停止")
            }
        )
    }

    /**
     * 安排智能位置上报任务
     * 根据当前活动状态和电量动态调整上报频率
     */
    private fun scheduleSmartLocationReport() {
        val context = getApplication<Application>()
        val activityType = SmartLocationConfig.getLastActivity(context)
        val intervalMinutes = SmartLocationConfig.calculateSmartInterval(context, activityType)

        Log.d(TAG, "安排智能位置上报 - 活动: ${activityType.displayName}, 间隔: ${intervalMinutes}分钟")

        // 使用 PeriodicWork 进行定期智能上报
        val smartWorkRequest = PeriodicWorkRequestBuilder<SmartLocationWorker>(
            repeatInterval = intervalMinutes,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        ).setInputData(
            workDataOf(
                SmartLocationWorker.KEY_TRIGGER_REASON to "periodic"
            )
        ).build()

        workManager.enqueueUniquePeriodicWork(
            SMART_LOCATION_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // 如果间隔变化则更新
            smartWorkRequest
        )
    }

    /**
     * 触发一次立即的智能位置上报
     */
    fun triggerSmartLocationReport(forceReport: Boolean = false) {
        val context = getApplication<Application>()
        val activityType = SmartLocationConfig.getLastActivity(context)

        val workRequest = OneTimeWorkRequestBuilder<SmartLocationWorker>()
            .setInputData(
                workDataOf(
                    SmartLocationWorker.KEY_TRIGGER_REASON to "manual",
                    SmartLocationWorker.KEY_ACTIVITY_TYPE to activityType.name,
                    SmartLocationWorker.KEY_FORCE_REPORT to forceReport
                )
            )
            .build()

        workManager.enqueueUniqueWork(
            "smart_location_immediate",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        Log.d(TAG, "触发立即智能位置上报 (强制: $forceReport)")
    }

    override fun onCleared() {
        super.onCleared()
        // 释放高德定位资源
        amapLocationService.destroy()
        locationReportService.destroy()
        // 注意：不断开 MQTT 连接，由 MqttForegroundService 管理
        // 这样 APP 退出后仍能保持连接，支持后台功能（刷新、追踪、响铃、丢失模式）
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val SMART_LOCATION_WORK_NAME = "smart_location_periodic"
        private const val LEGACY_LOCATION_WORK_NAME = "location_report"
    }
}
