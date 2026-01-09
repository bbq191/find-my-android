package me.ikate.findmy.ui.screen.main

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
import me.ikate.findmy.service.LocationReportService
import me.ikate.findmy.ui.screen.main.components.SheetValue
import me.ikate.findmy.util.CoordinateConverter
import me.ikate.findmy.worker.LocationReportWorker
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

    // 定位按钮状态：true = 地图中心在用户位置（实心箭头）, false = 不在（空心箭头）
    private val _isLocationCentered = MutableStateFlow(false)
    val isLocationCentered: StateFlow<Boolean> = _isLocationCentered.asStateFlow()

    // 设备列表（从 Firebase 实时获取）
    private val _devices = MutableStateFlow<List<Device>>(emptyList())

    // 选中的设备（用于显示详情）
    private val _selectedDevice = MutableStateFlow<Device?>(null)

    // 底部面板状态
    private val _sheetValue = MutableStateFlow(SheetValue.Collapsed)

    // 是否已完成首次自动定位
    private var hasInitialCentered = false

    init {
        // 确保用户已登录（自动匿名登录）
        ensureUserAuthenticated()
        // 启动时开始监听设备列表变化
        observeDevices()
        // 启动定期位置上报（根据用户类型调整频率）
        startPeriodicLocationReport()
        // 立即上报一次位置，以便在地图上显示当前设备
        reportLocationNow()
    }

    /**
     * 确保用户已通过 Firebase 认证
     * 如果未登录，则使用设备 ID 自动登录（确保同设备 UID 不变）
     */
    private fun ensureUserAuthenticated() {
        viewModelScope.launch {
            if (!authRepository.isSignedIn()) {
                android.util.Log.d("MainViewModel", "用户未登录，开始设备登录")
                authRepository.signInWithDeviceId().fold(
                    onSuccess = { user ->
                        android.util.Log.d("MainViewModel", "设备登录成功: ${user.uid}")
                    },
                    onFailure = { error ->
                        android.util.Log.e("MainViewModel", "设备登录失败", error)
                    }
                )
            } else {
                android.util.Log.d("MainViewModel", "用户已登录: ${authRepository.getCurrentUserId()}")
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
     * 启动定期位置上报
     * 注册用户：每15分钟上报一次
     * 匿名用户：每100分钟上报一次
     */
    private fun startPeriodicLocationReport() {
        // 根据用户类型设置不同的上报频率
        val intervalMinutes = if (isAnonymousUser()) 100L else 15L

        val locationReportRequest = PeriodicWorkRequestBuilder<LocationReportWorker>(
            repeatInterval = intervalMinutes,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            "location_report",
            ExistingPeriodicWorkPolicy.KEEP, // 如果已存在则保留
            locationReportRequest
        )

        android.util.Log.d("MainViewModel", "已启动定期位置上报任务 (间隔: ${intervalMinutes}分钟)")
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
                    android.util.Log.d("MainViewModel", "位置上报成功")
                },
                onFailure = { error ->
                    android.util.Log.e("MainViewModel", "位置上报失败", error)
                }
            )
        }
    }

}
