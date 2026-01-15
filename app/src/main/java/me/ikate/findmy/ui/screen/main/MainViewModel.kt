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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.repository.AuthRepository
import me.ikate.findmy.data.repository.DeviceRepository
import me.ikate.findmy.service.ActivityRecognitionManager
import me.ikate.findmy.service.LocationReportService
import me.ikate.findmy.service.SmartLocationConfig
import me.ikate.findmy.ui.screen.main.components.SheetValue
import me.ikate.findmy.util.CoordinateConverter
import me.ikate.findmy.worker.LocationReportWorker
import me.ikate.findmy.worker.SmartLocationWorker
import java.util.concurrent.TimeUnit

/**
 * MainViewModel - 主屏幕状态管理
 * 负责管理地图状态、定位状态、设备选中状态等
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // 地图实例
    private val _googleMap = MutableStateFlow<GoogleMap?>(null)
    val googleMap: StateFlow<GoogleMap?> = _googleMap.asStateFlow()

    // 定位服务客户端
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    // 认证仓库
    private val authRepository = AuthRepository(application.applicationContext)

    // 设备数据仓库
    private val deviceRepository = DeviceRepository()

    // 位置上报服务
    private val locationReportService = LocationReportService(application)

    // WorkManager
    private val workManager = WorkManager.getInstance(application)

    // 活动识别管理器（智能位置上报）
    private val activityRecognitionManager = ActivityRecognitionManager(application)

    // 智能位置上报是否已启动
    private var isSmartLocationStarted = false

    // 智能位置上报模式状态（用于 UI 观察）
    private val _isSmartLocationEnabled = MutableStateFlow(
        SmartLocationConfig.isSmartModeEnabled(application)
    )
    val isSmartLocationEnabled: StateFlow<Boolean> = _isSmartLocationEnabled.asStateFlow()

    // 定位按钮状态：true = 地图中心在用户位置（实心箭头）, false = 不在（空心箭头）
    private val _isLocationCentered = MutableStateFlow(false)
    val isLocationCentered: StateFlow<Boolean> = _isLocationCentered.asStateFlow()

    // 设备列表（从 Firebase 实时获取）
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    // 选中的设备（用于显示详情）
    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice: StateFlow<Device?> = _selectedDevice.asStateFlow()

    // 底部面板状态
    private val _sheetValue = MutableStateFlow(SheetValue.Collapsed)

    // 是否已完成首次自动定位
    private var hasInitialCentered = false

    init {
        // 确保用户已登录（自动匿名登录）
        ensureUserAuthenticated()
        // 启动时开始监听设备列表变化
        observeDevices()
        // 立即上报一次位置，以便在地图上显示当前设备
        reportLocationNow()
        // 初始化位置上报（智能模式或传统模式）
        initLocationReporting()
    }

    /**
     * 确保用户已通过 Firebase 认证
     * 如果未登录，则使用设备 ID 自动登录（确保同设备 UID 不变）
     */
    private fun ensureUserAuthenticated() {
        viewModelScope.launch {
            if (!authRepository.isSignedIn()) {
                Log.d(TAG, "用户未登录，开始设备登录")
                authRepository.signInWithDeviceId().fold(
                    onSuccess = { user ->
                        Log.d(TAG, "设备登录成功: ${user.uid}")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "设备登录失败", error)
                    }
                )
            } else {
                Log.d(TAG, "用户已登录: ${authRepository.getCurrentUserId()}")
            }
        }
    }

    /**
     * 尝试首次自动定位
     * 需在获取权限且地图加载完成后调用
     */
    fun attemptInitialLocationCenter() {
        if (!hasInitialCentered && _googleMap.value != null) {
            onLocationButtonClick()
            hasInitialCentered = true
        }
    }

    /**
     * 检查当前用户是否为匿名用户
     */
    private fun isAnonymousUser(): Boolean {
        return FirebaseAuth.getInstance().currentUser?.isAnonymous ?: true
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
     * 设置地图实例
     */
    fun setGoogleMap(map: GoogleMap) {
        _googleMap.value = map
    }

    /**
     * 更新定位中心状态
     */
    fun updateLocationCenteredState(isCentered: Boolean) {
        _isLocationCentered.value = isCentered
    }

    /**
     * 点击定位按钮 - 移动地图到用户当前位置
     * 注意：在中国大陆地区，GPS坐标（WGS-84）会自动转换为GCJ-02以匹配Google Maps底图
     * 注册用户会自动上报位置，匿名用户不上报
     */
    @SuppressLint("MissingPermission")
    fun onLocationButtonClick() {
        googleMap.value?.let { map ->
            // 使用 FusedLocationProviderClient 获取位置，替代已废弃的 map.myLocation
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    // GPS获取的是WGS-84坐标，需要转换为GCJ-02以匹配Google Maps在中国的底图
                    val wgsCoord = LatLng(location.latitude, location.longitude)
                    val gcjCoord =
                        CoordinateConverter.wgs84ToGcj02(wgsCoord.latitude, wgsCoord.longitude)

                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(gcjCoord, 15f)
                    )
                    updateLocationCenteredState(true)

                    // 只有注册用户点击重定位时才上报位置，匿名用户不上报
                    if (!isAnonymousUser()) {
                        reportLocationNow()
                    }
                }
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
     * 根据设置选择智能模式或传统定期模式
     */
    private fun initLocationReporting() {
        if (_isSmartLocationEnabled.value) {
            // 智能模式：取消传统定期任务，启动智能上报
            workManager.cancelUniqueWork(LEGACY_LOCATION_WORK_NAME)
            initSmartLocationReporting()
        } else {
            // 传统模式：使用固定频率上报
            startLegacyPeriodicLocationReport()
        }
    }

    /**
     * 启动传统定期位置上报（作为智能模式的备选）
     * 注册用户：每15分钟上报一次
     * 匿名用户：每100分钟上报一次
     */
    private fun startLegacyPeriodicLocationReport() {
        // 根据用户类型设置不同的上报频率
        val intervalMinutes = if (isAnonymousUser()) 100L else 15L

        val locationReportRequest = PeriodicWorkRequestBuilder<LocationReportWorker>(
            repeatInterval = intervalMinutes,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            LEGACY_LOCATION_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // 如果已存在则保留
            locationReportRequest
        )

        Log.d(TAG, "已启动传统定期位置上报 (间隔: ${intervalMinutes}分钟)")
    }

    /**
     * 立即上报当前位置
     */
    @SuppressLint("MissingPermission")
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
     * 会自动检查权限并启动活动识别监听
     */
    private fun initSmartLocationReporting() {
        if (isSmartLocationStarted) return

        // 检查智能模式是否启用
        if (!SmartLocationConfig.isSmartModeEnabled(getApplication())) {
            Log.d(TAG, "智能位置模式未启用")
            return
        }

        // 如果有活动识别权限，启动活动转换监听
        if (activityRecognitionManager.hasPermission()) {
            startActivityRecognition()
        } else {
            Log.d(TAG, "缺少活动识别权限，使用基础定期上报模式")
        }
    }

    /**
     * 启动活动识别监听
     * 需要在获得 ACTIVITY_RECOGNITION 权限后调用
     */
    fun startActivityRecognition() {
        if (isSmartLocationStarted) return

        activityRecognitionManager.startActivityTransitionUpdates(
            onSuccess = {
                isSmartLocationStarted = true
                Log.d(TAG, "智能位置上报已启动 - 活动识别监听中")
                // 根据当前活动状态安排首次智能上报
                scheduleSmartLocationReport()
            },
            onFailure = { e ->
                Log.e(TAG, "启动活动识别失败: ${e.message}")
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

    /**
     * 设置智能位置上报模式
     */
    fun setSmartModeEnabled(enabled: Boolean) {
        SmartLocationConfig.setSmartModeEnabled(getApplication(), enabled)
        _isSmartLocationEnabled.value = enabled

        if (enabled) {
            // 切换到智能模式：取消传统定期任务
            workManager.cancelUniqueWork(LEGACY_LOCATION_WORK_NAME)
            initSmartLocationReporting()
        } else {
            // 切换到传统模式：停止活动识别，取消智能上报任务
            stopActivityRecognition()
            workManager.cancelUniqueWork(SMART_LOCATION_WORK_NAME)
            // 启动传统定期上报
            startLegacyPeriodicLocationReport()
        }
        Log.d(TAG, "位置上报模式: ${if (enabled) "智能模式" else "传统模式"}")
    }

    /**
     * 检查是否有活动识别权限
     */
    fun hasActivityRecognitionPermission(): Boolean {
        return activityRecognitionManager.hasPermission()
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val SMART_LOCATION_WORK_NAME = "smart_location_periodic"
        private const val LEGACY_LOCATION_WORK_NAME = "location_report"
    }
}
