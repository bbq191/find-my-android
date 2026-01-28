package me.ikate.findmy.ui.screen.main

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
import com.tencent.tencentmap.mapsdk.maps.TencentMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.latLngOf
import me.ikate.findmy.data.remote.mqtt.MqttConfig
import me.ikate.findmy.data.remote.mqtt.MqttConnectionManager
import me.ikate.findmy.data.repository.AuthRepository
import me.ikate.findmy.data.repository.ContactRepository
import me.ikate.findmy.data.repository.DeviceRepository
import me.ikate.findmy.push.FCMMessageHandler
import me.ikate.findmy.service.ActivityRecognitionManager
import me.ikate.findmy.service.LocationReportService
import me.ikate.findmy.service.SmartLocationConfig
import me.ikate.findmy.service.SmartLocationSyncManager
import me.ikate.findmy.ui.screen.main.components.SheetValue
import me.ikate.findmy.util.TencentMapCameraHelper
import me.ikate.findmy.worker.LocationReportWorker
import me.ikate.findmy.worker.SmartLocationWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 主屏幕 UI 聚合状态
 * 将多个独立的 StateFlow 合并为一个，减少 Compose 重组次数
 *
 * 性能优化：
 * - 原来有 13+ 个独立的 collectAsState()，每个状态变化都可能触发重组
 * - 现在合并为一个 MainUiState，只有相关状态变化时才重组
 */
data class MainUiState(
    val tencentMap: TencentMap? = null,
    val isLocationCentered: Boolean = false,
    val devices: List<Device> = emptyList(),
    val trackingTargetId: String? = null,
    val currentDeviceRealtimeLocation: com.tencent.tencentmap.mapsdk.maps.model.LatLng? = null,
    val currentDeviceBearing: Float = 0f,
    val mqttConnectionState: MqttConnectionManager.ConnectionState = MqttConnectionManager.ConnectionState.Disconnected
)

/**
 * MainViewModel - 主屏幕状态管理
 * 负责管理地图状态、定位状态、设备选中状态等
 */
class MainViewModel(
    application: Application,
    private val authRepository: AuthRepository,
    private val deviceRepository: DeviceRepository,
    private val contactRepository: ContactRepository
) : AndroidViewModel(application) {

    // 地图实例 (腾讯地图)
    private val _tencentMap = MutableStateFlow<TencentMap?>(null)
    val tencentMap: StateFlow<TencentMap?> = _tencentMap.asStateFlow()

    // 腾讯定位服务
    private val tencentLocationService = me.ikate.findmy.service.TencentLocationService(application)

    // 位置上报服务
    private val locationReportService = LocationReportService(application)

    // WorkManager
    private val workManager = WorkManager.getInstance(application)

    // 活动识别管理器（智能位置上报）
    private val activityRecognitionManager = ActivityRecognitionManager(application)

    // 智能位置同步管理器（iOS Find My 风格持续同步）
    private val smartLocationSyncManager = SmartLocationSyncManager.getInstance(application)

    // 智能位置上报是否已启动
    private var isSmartLocationStarted = false

    // 实时位置监听 Job（用于追踪自己时的连续定位）
    private var realtimeLocationJob: Job? = null

    // 当前设备的实时位置（用于地图显示，比数据库更新更快）
    private val _currentDeviceRealtimeLocation = MutableStateFlow<com.tencent.tencentmap.mapsdk.maps.model.LatLng?>(null)
    val currentDeviceRealtimeLocation: StateFlow<com.tencent.tencentmap.mapsdk.maps.model.LatLng?> = _currentDeviceRealtimeLocation.asStateFlow()

    // 当前设备实时朝向
    private val _currentDeviceBearing = MutableStateFlow(0f)
    val currentDeviceBearing: StateFlow<Float> = _currentDeviceBearing.asStateFlow()

    // 定位按钮状态：true = 地图中心在用户位置（实心箭头）, false = 不在（空心箭头）
    private val _isLocationCentered = MutableStateFlow(false)
    val isLocationCentered: StateFlow<Boolean> = _isLocationCentered.asStateFlow()

    // 设备列表（从本地 Room 数据库实时获取，通过 MQTT 同步）
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    // 选中的设备（用于显示详情）
    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice: StateFlow<Device?> = _selectedDevice.asStateFlow()

    // 是否已完成首次自动定位
    private var hasInitialCentered = false

    // 正在追踪的目标 ID（设备 ID 或联系人 ID，null 表示不追踪）
    private val _trackingTargetId = MutableStateFlow<String?>(null)
    val trackingTargetId: StateFlow<String?> = _trackingTargetId.asStateFlow()

    // 是否正在实时追踪当前设备（用于平滑移动地图）- 保留兼容
    private val _isTrackingCurrentDevice = MutableStateFlow(false)
    val isTrackingCurrentDevice: StateFlow<Boolean> = _isTrackingCurrentDevice.asStateFlow()

    // MQTT 连接状态
    private val _mqttConnectionState = MutableStateFlow<MqttConnectionManager.ConnectionState>(
        MqttConnectionManager.ConnectionState.Disconnected
    )
    val mqttConnectionState: StateFlow<MqttConnectionManager.ConnectionState> = _mqttConnectionState.asStateFlow()

    /**
     * 聚合 UI 状态
     * 将多个 StateFlow 合并为一个，供 MainScreen 使用
     * 减少 collectAsState() 调用次数，提升性能
     */
    val uiState: StateFlow<MainUiState> = combine(
        _tencentMap,
        _isLocationCentered,
        _devices,
        _trackingTargetId,
        _currentDeviceRealtimeLocation,
        _currentDeviceBearing,
        _mqttConnectionState
    ) { flows ->
        MainUiState(
            tencentMap = flows[0] as? TencentMap,
            isLocationCentered = flows[1] as Boolean,
            devices = @Suppress("UNCHECKED_CAST") (flows[2] as List<Device>),
            trackingTargetId = flows[3] as? String,
            currentDeviceRealtimeLocation = flows[4] as? com.tencent.tencentmap.mapsdk.maps.model.LatLng,
            currentDeviceBearing = flows[5] as Float,
            mqttConnectionState = flows[6] as MqttConnectionManager.ConnectionState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

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
        // 初始化智能位置同步管理器（iOS Find My 风格持续同步）
        smartLocationSyncManager.initialize()
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
     *
     * 像 iOS Find My 一样：
     * - 有联系人时：用合适的比例显示所有点位（当前设备 + 联系人）
     * - 没有联系人时：用合适的比例显示自己的设备
     *
     * @param hasContacts 是否有联系人好友
     * @param contacts 联系人列表（用于计算边界）
     * @param screenWidthPx 屏幕宽度（像素）
     * @param screenHeightPx 屏幕高度（像素）
     * @param bottomSheetHeightPx 底部面板高度（像素）
     */
    fun attemptInitialLocationCenter(
        hasContacts: Boolean = false,
        contacts: List<me.ikate.findmy.data.model.Contact> = emptyList(),
        screenWidthPx: Int = 0,
        screenHeightPx: Int = 0,
        bottomSheetHeightPx: Int = 0
    ) {
        if (!hasInitialCentered && _tencentMap.value != null) {
            if (hasContacts && contacts.isNotEmpty()) {
                // 有联系人时，缩放显示所有标签（当前设备 + 联系人）
                zoomToShowAllMarkers(contacts, screenWidthPx, screenHeightPx, bottomSheetHeightPx)
            } else {
                // 没有联系人时，用合适的比例显示自己的设备
                zoomToCurrentLocation()
                startTrackingCurrentDevice()
            }
            hasInitialCentered = true
        }
    }

    /**
     * 重置初始化状态
     * 用于在联系人数据加载完成后重新触发缩放
     */
    fun resetInitialCentered() {
        hasInitialCentered = false
    }

    /**
     * 缩放地图以显示所有标签（当前设备 + 联系人）
     * 使用智能缩放，确保点位不贴边，美观显示
     */
    private fun zoomToShowAllMarkers(
        contacts: List<me.ikate.findmy.data.model.Contact>,
        screenWidthPx: Int,
        screenHeightPx: Int,
        bottomSheetHeightPx: Int
    ) {
        val map = _tencentMap.value ?: return

        // 如果有屏幕尺寸信息，使用智能缩放
        if (screenWidthPx > 0 && screenHeightPx > 0) {
            TencentMapCameraHelper.zoomToShowAllSmartly(
                map = map,
                devices = _devices.value,
                contacts = contacts,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                bottomSheetHeightPx = bottomSheetHeightPx,
                isInitial = true
            )
        } else {
            // 降级使用旧方法
            TencentMapCameraHelper.zoomToShowAll(map, _devices.value, contacts)
        }
    }

    /**
     * 缩放地图到当前位置
     * 使用腾讯定位获取当前位置，并以合适的缩放级别显示
     */
    private fun zoomToCurrentLocation() {
        val map = _tencentMap.value ?: return

        viewModelScope.launch {
            val result = tencentLocationService.getLocation(timeout = 10000L)
            if (result.isSuccess) {
                // 立即跳转到当前位置
                TencentMapCameraHelper.jumpToLocation(map, result.latLng, zoom = DEFAULT_SINGLE_DEVICE_ZOOM)
                updateLocationCenteredState(true)
            } else {
                Log.e(TAG, "初始定位失败: ${result.errorInfo}")
            }
        }
    }

    /**
     * 开始追踪指定目标（设备或联系人）
     * 地图会跟随目标位置平滑移动
     *
     * 像 iOS Find My 一样：
     * - 追踪自己时启动实时定位，地图点位实时跟随移动
     * - 追踪他人时依赖 MQTT 位置更新
     *
     * @param targetId 目标 ID（设备 ID 或联系人 ID）
     */
    fun startTracking(targetId: String) {
        _trackingTargetId.value = targetId
        Log.d(TAG, "开始追踪目标: $targetId")

        // 如果追踪的是自己的设备，启动实时定位
        val currentDeviceId = me.ikate.findmy.util.DeviceIdProvider.getDeviceId(getApplication())
        if (targetId == currentDeviceId) {
            startRealtimeLocationUpdates()
        }
    }

    /**
     * 停止追踪
     */
    fun stopTracking() {
        val wasTrackingCurrentDevice = _trackingTargetId.value ==
            me.ikate.findmy.util.DeviceIdProvider.getDeviceId(getApplication())

        _trackingTargetId.value = null
        Log.d(TAG, "停止追踪")

        // 如果之前在追踪自己，停止实时定位以节省电量
        if (wasTrackingCurrentDevice) {
            stopRealtimeLocationUpdates()
        }
    }

    /**
     * 启动实时位置更新
     * 像 iOS Find My 一样，查看自己位置时地图点位实时跟随移动
     */
    // 实时定位：是否已获得过 GPS 定位（获得后不再接受网络定位，防止精度回退跳动）
    private var hasReceivedGps = false
    // 实时定位：上次接受的坐标（用于最小距离过滤）
    private var lastRealtimeLat = Double.NaN
    private var lastRealtimeLng = Double.NaN

    // 实时定位精度门槛（仅接受精度优于此值的位置）
    private val realtimeAccuracyThreshold = 25f
    // 实时定位最小变化距离（米），低于此值不更新 marker，避免 GPS 微跳
    private val realtimeMinDistanceMeters = 3.0

    private fun startRealtimeLocationUpdates() {
        if (realtimeLocationJob?.isActive == true) {
            Log.d(TAG, "实时定位已在运行中")
            return
        }

        // 重置过滤状态
        hasReceivedGps = false
        lastRealtimeLat = Double.NaN
        lastRealtimeLng = Double.NaN

        Log.d(TAG, "启动实时位置更新（像 iOS Find My 一样）")
        realtimeLocationJob = viewModelScope.launch {
            tencentLocationService.getLocationUpdates(
                interval = 2000L // 每2秒更新一次，平衡精度和电量
            ).collect { locationResult ->
                if (locationResult.isSuccess) {
                    val latLng = locationResult.latLng
                    if (latLng.latitude.isNaN() || latLng.longitude.isNaN()) return@collect

                    val isGps = locationResult.locationType ==
                        me.ikate.findmy.service.TencentLocationService.LOCATION_TYPE_GPS

                    // 精度降级过滤：已获得 GPS 后，忽略网络定位（防止 30m 精度回退导致跳动）
                    if (hasReceivedGps && !isGps) return@collect

                    // 精度门槛过滤：丢弃精度过差的位置
                    if (locationResult.accuracy > realtimeAccuracyThreshold && !isGps) return@collect

                    if (isGps) hasReceivedGps = true

                    // 最小距离过滤：GPS 微跳抑制
                    if (!lastRealtimeLat.isNaN() && !lastRealtimeLng.isNaN()) {
                        val dist = me.ikate.findmy.util.DistanceCalculator.calculateDistance(
                            lastRealtimeLat, lastRealtimeLng,
                            latLng.latitude, latLng.longitude
                        )
                        if (dist < realtimeMinDistanceMeters) return@collect
                    }

                    lastRealtimeLat = latLng.latitude
                    lastRealtimeLng = latLng.longitude

                    // 更新实时位置（用于地图显示）
                    _currentDeviceRealtimeLocation.value = latLng
                    _currentDeviceBearing.value = locationResult.bearing
                }
            }
        }
    }

    /**
     * 停止实时位置更新
     */
    private fun stopRealtimeLocationUpdates() {
        realtimeLocationJob?.cancel()
        realtimeLocationJob = null
        _currentDeviceRealtimeLocation.value = null
        Log.d(TAG, "停止实时位置更新")
    }

    /**
     * 开始实时追踪当前设备
     * 地图会跟随设备位置平滑移动
     * 像 iOS Find My 一样，启动实时定位让点位跟随移动
     */
    fun startTrackingCurrentDevice() {
        _isTrackingCurrentDevice.value = true
        val currentDeviceId = me.ikate.findmy.util.DeviceIdProvider.getDeviceId(getApplication())
        _trackingTargetId.value = currentDeviceId
        startRealtimeLocationUpdates()
        Log.d(TAG, "开始实时追踪当前设备")
    }

    /**
     * 停止实时追踪当前设备
     */
    fun stopTrackingCurrentDevice() {
        _isTrackingCurrentDevice.value = false
        stopRealtimeLocationUpdates()
        Log.d(TAG, "停止实时追踪当前设备")
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
            // 连接 MQTT（订阅由 MqttForegroundService 统一管理）
            val result = deviceRepository.connectMqtt()
            result.fold(
                onSuccess = {
                    Log.d(TAG, "MQTT 连接成功")
                    _mqttConnectionState.value = MqttConnectionManager.ConnectionState.Connected
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
     * 设置地图实例 (腾讯地图)
     */
    fun setTencentMap(map: TencentMap) {
        _tencentMap.value = map
    }

    /**
     * 更新定位中心状态
     */
    fun updateLocationCenteredState(isCentered: Boolean) {
        _isLocationCentered.value = isCentered
    }

    /**
     * 点击定位按钮 - 移动地图到用户当前位置
     * 使用腾讯定位获取位置（GCJ-02 坐标，与腾讯地图一致）
     * 注册用户会自动上报位置，匿名用户不上报
     */
    fun onLocationButtonClick() {
        val map = tencentMap.value ?: return

        // 使用腾讯定位获取位置（GCJ-02 坐标）
        viewModelScope.launch {
            val result = tencentLocationService.getLocation(timeout = 10000L)
            if (result.isSuccess) {
                // 立即跳转到当前位置（不使用动画，避免与其他动画冲突）
                TencentMapCameraHelper.jumpToLocation(map, result.latLng, zoom = DEFAULT_SINGLE_DEVICE_ZOOM)
                updateLocationCenteredState(true)

                reportLocationNow()
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
    fun updateSheetValue(@Suppress("UNUSED_PARAMETER") value: SheetValue) {
        // 预留接口，供 MainScreen 调用
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
            Log.i(TAG, "[位置上报] 开始上报当前位置...")
            val result = locationReportService.reportCurrentLocation()
            result.fold(
                onSuccess = {
                    Log.i(TAG, "[位置上报] ✓ 位置上报成功")
                },
                onFailure = { error ->
                    Log.e(TAG, "[位置上报] ✗ 位置上报失败: ${error.message}", error)
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
     *
     * 混合策略：
     * 1. 优先使用 Google Activity Recognition API（更准确）
     * 2. 降级方案：基于加速度传感器实现（不依赖 GMS）
     *
     * Google API 需要 ACTIVITY_RECOGNITION 权限（Android 10+）
     */
    fun startActivityRecognition() {
        if (isSmartLocationStarted) return

        // 设置活动变化回调
        activityRecognitionManager.setActivityChangeCallback { newActivity ->
            Log.d(TAG, "活动识别: ${newActivity.displayName} (${activityRecognitionManager.getStatusDescription()})")

            // 发送活动转换广播（会自动包含来源：google_api 或 sensor）
            me.ikate.findmy.service.ActivityTransitionReceiver.sendActivityTransition(
                context = getApplication(),
                activityType = newActivity,
                transitionType = me.ikate.findmy.service.ActivityTransitionReceiver.ACTIVITY_TRANSITION_ENTER,
                source = when (activityRecognitionManager.getCurrentMode()) {
                    me.ikate.findmy.service.ActivityRecognitionManager.RecognitionMode.GOOGLE_API -> "google_api"
                    me.ikate.findmy.service.ActivityRecognitionManager.RecognitionMode.SENSOR -> "sensor"
                    else -> "unknown"
                }
            )
        }

        activityRecognitionManager.startActivityTransitionUpdates(
            onSuccess = {
                isSmartLocationStarted = true
                val mode = activityRecognitionManager.getCurrentMode()
                val modeDesc = when (mode) {
                    me.ikate.findmy.service.ActivityRecognitionManager.RecognitionMode.GOOGLE_API ->
                        "Google API（更准确）"
                    me.ikate.findmy.service.ActivityRecognitionManager.RecognitionMode.SENSOR ->
                        "传感器模式"
                    else -> "未知模式"
                }
                Log.i(TAG, "智能位置上报已启动 - 活动识别模式: $modeDesc")
            },
            onFailure = { e ->
                Log.w(TAG, "启动活动识别失败: ${e.message}，使用 GPS 速度推断")
                isSmartLocationStarted = true
            }
        )
    }

    /**
     * 重启活动识别
     * 用于权限授予后切换到 Google API 模式
     */
    fun restartActivityRecognition() {
        // 如果已经在使用 Google API，无需重启
        if (activityRecognitionManager.getCurrentMode() ==
            me.ikate.findmy.service.ActivityRecognitionManager.RecognitionMode.GOOGLE_API
        ) {
            Log.d(TAG, "活动识别已在使用 Google API，无需重启")
            return
        }

        // 检查是否有 ACTIVITY_RECOGNITION 权限
        if (!activityRecognitionManager.hasActivityRecognitionPermission()) {
            Log.d(TAG, "未授予 ACTIVITY_RECOGNITION 权限，保持当前模式")
            return
        }

        Log.i(TAG, "ACTIVITY_RECOGNITION 权限已授予，重启活动识别以使用 Google API")

        // 停止当前的活动识别
        activityRecognitionManager.stopActivityTransitionUpdates()
        isSmartLocationStarted = false

        // 重新启动（会自动选择 Google API）
        startActivityRecognition()
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

    // ==================== 智能位置同步（iOS Find My 风格） ====================

    /**
     * 设置选中的联系人
     * 会触发 SmartLocationSyncManager 的高频刷新（每5秒）
     *
     * @param contactUid 联系人 UID，null 表示取消选中
     */
    fun setFocusedContact(contactUid: String?) {
        smartLocationSyncManager.setFocusedContact(contactUid)
    }

    /**
     * 应用进入前台
     * 应在 MainActivity 的 onResume 中调用
     */
    fun onAppForeground() {
        smartLocationSyncManager.onAppForeground()
    }

    /**
     * 应用进入后台
     * 应在 MainActivity 的 onPause 中调用
     */
    fun onAppBackground() {
        smartLocationSyncManager.onAppBackground()
    }

    /**
     * 刷新所有联系人位置
     */
    fun refreshAllContactLocations() {
        smartLocationSyncManager.refreshAllContacts()
    }

    /**
     * 刷新单个联系人位置
     */
    fun refreshContactLocation(targetUid: String) {
        smartLocationSyncManager.refreshContact(targetUid)
    }

    override fun onCleared() {
        super.onCleared()
        // 停止实时位置更新
        stopRealtimeLocationUpdates()
        // 释放腾讯定位资源
        tencentLocationService.destroy()
        locationReportService.destroy()
        // 释放 TencentMap 引用，避免 Activity 重建后旧引用导致内存泄漏
        _tencentMap.value = null
        // 注意：不断开 MQTT 连接，由 MqttForegroundService 管理
        // 这样 APP 退出后仍能保持连接，支持后台功能（刷新、追踪、响铃、丢失模式）
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val SMART_LOCATION_WORK_NAME = "smart_location_periodic"
        private const val LEGACY_LOCATION_WORK_NAME = "location_report"

        /** 单个设备时的默认缩放级别 (18级，街道级别，既精细又能看到周围环境) */
        private const val DEFAULT_SINGLE_DEVICE_ZOOM = 18f
    }
}
