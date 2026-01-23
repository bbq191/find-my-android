package me.ikate.findmy.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.tencent.map.geolocation.TencentLocation
import com.tencent.map.geolocation.TencentLocationListener
import com.tencent.map.geolocation.TencentLocationManager
import com.tencent.map.geolocation.TencentLocationRequest
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import me.ikate.findmy.data.model.latLngOf
import me.ikate.findmy.util.DeviceOptimizationConfig
import kotlin.coroutines.resume

/**
 * 腾讯定位服务封装
 * 提供单次定位和连续定位功能
 *
 * 注意：
 * - 腾讯定位返回 GCJ-02 坐标，腾讯地图同样使用 GCJ-02，无需转换
 * - 使用前需要调用 TencentLocationManager.setUserAgreePrivacy 进行隐私合规
 */
class TencentLocationService(private val context: Context) {

    companion object {
        private const val TAG = "TencentLocationService"

        // 定位类型常量
        const val LOCATION_TYPE_GPS = 1
        const val LOCATION_TYPE_NETWORK = 2
        const val LOCATION_TYPE_WIFI = 4
        const val LOCATION_TYPE_CELL = 5
        const val LOCATION_TYPE_OFFLINE = 6
        const val LOCATION_TYPE_LAST = 7

        // 错误码常量
        const val ERROR_PERMISSION_DENIED = -100

        /**
         * 初始化隐私合规（必须在使用定位前调用）
         * @param context 上下文
         * @param isContains 隐私政策是否包含腾讯说明（保持接口兼容）
         * @param isShow 是否弹窗展示给用户（保持接口兼容）
         */
        fun updatePrivacyShow(context: Context, isContains: Boolean, isShow: Boolean) {
            // 腾讯 SDK 使用 setUserAgreePrivacy，此方法保持接口兼容
            Log.d(TAG, "Privacy show: isContains=$isContains, isShow=$isShow")
        }

        /**
         * 更新用户隐私授权状态
         * @param context 上下文
         * @param isAgree 用户是否同意
         */
        fun updatePrivacyAgree(context: Context, isAgree: Boolean) {
            // 腾讯定位 SDK 8.x 隐私合规设置
            // 注意：setUserAgreePrivacy 是静态方法，必须直接调用
            try {
                TencentLocationManager.setUserAgreePrivacy(isAgree)
                Log.d(TAG, "setUserAgreePrivacy($isAgree) 调用成功")
            } catch (e: Exception) {
                Log.e(TAG, "设置定位隐私合规失败", e)
            }
        }
    }

    private var locationManager: TencentLocationManager? = null

    /**
     * 检查是否有定位权限
     */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 定位结果数据类
     */
    data class LocationResult(
        val latLng: LatLng,         // GCJ-02 坐标（腾讯定位原始坐标）
        val accuracy: Float,        // 精度（米）
        val bearing: Float,         // 方向（0-360）
        val speed: Float,           // 速度（米/秒）
        val altitude: Double,       // 海拔（米）
        val address: String?,       // 地址描述
        val locationType: Int,      // 定位类型
        val errorCode: Int = 0,     // 错误码（0 表示成功）
        val errorInfo: String? = null // 错误信息
    ) {
        val isSuccess: Boolean get() = errorCode == 0
    }

    /**
     * 获取单次定位
     * GPS 优先模式：优先等待 GPS 定位，超时后使用最佳可用位置
     *
     * @param timeout 超时时间（毫秒），默认 20 秒
     * @param gpsWaitTime GPS 等待时间（毫秒），默认 5 秒，在此期间优先等待 GPS
     * @return 定位结果
     */
    suspend fun getLocation(
        timeout: Long = DeviceOptimizationConfig.getGpsConfig().timeoutMs,
        gpsWaitTime: Long = DeviceOptimizationConfig.getGpsConfig().gpsWaitTimeMs
    ): LocationResult =
        suspendCancellableCoroutine { continuation ->
            // 先检查权限
            if (!hasLocationPermission()) {
                Log.w(TAG, "缺少定位权限，无法获取位置")
                continuation.resume(
                    LocationResult(
                        latLng = latLngOf(0.0, 0.0),
                        accuracy = 0f,
                        bearing = 0f,
                        speed = 0f,
                        altitude = 0.0,
                        address = null,
                        locationType = 0,
                        errorCode = ERROR_PERMISSION_DENIED,
                        errorInfo = "缺少定位权限"
                    )
                )
                return@suspendCancellableCoroutine
            }

            val mainHandler = Handler(Looper.getMainLooper())

            // 必须在主线程执行定位请求
            mainHandler.post {
                try {
                    // 获取定位管理器实例（Key 通过 AndroidManifest.xml 中的 meta-data 配置）
                    val manager = TencentLocationManager.getInstance(context)
                    locationManager = manager

                    // 保存最佳位置（用于 GPS 超时后的回退）
                    var bestLocation: LocationResult? = null
                    var isCompleted = false

                    val request = TencentLocationRequest.create().apply {
                        // 使用名称级别（比 POI 级别更稳定）
                        requestLevel = TencentLocationRequest.REQUEST_LEVEL_NAME
                        // 允许 GPS
                        isAllowGPS = true
                        // 设置较短的定位间隔以快速获取多个位置
                        interval = 1000
                    }

                    val listener = object : TencentLocationListener {
                        override fun onLocationChanged(
                            location: TencentLocation?,
                            error: Int,
                            reason: String?
                        ) {
                            if (isCompleted) return

                            val result = parseLocation(location, error, reason)

                            if (!result.isSuccess) {
                                Log.w(TAG, "[GPS优先] 定位失败: ${result.errorInfo}")
                                return
                            }

                            Log.d(TAG, "[GPS优先] 收到位置: 类型=${getLocationTypeName(result.locationType)}, 精度=${result.accuracy}m")

                            // GPS 定位：立即返回
                            if (result.locationType == LOCATION_TYPE_GPS) {
                                Log.i(TAG, "[GPS优先] ✓ 获取到 GPS 定位，立即返回")
                                isCompleted = true
                                manager.removeUpdates(this)
                                locationManager = null
                                if (continuation.isActive) {
                                    continuation.resume(result)
                                }
                                return
                            }

                            // 网络定位：保存最佳位置，继续等待 GPS
                            if (bestLocation == null || result.accuracy < bestLocation!!.accuracy) {
                                bestLocation = result
                                Log.d(TAG, "[GPS优先] 更新最佳网络位置，精度=${result.accuracy}m")
                            }
                        }

                        override fun onStatusUpdate(name: String?, status: Int, desc: String?) {
                            // 状态更新（静默）
                        }
                    }

                    // GPS 等待超时：返回最佳可用位置
                    val gpsTimeoutRunnable = Runnable {
                        if (isCompleted) return@Runnable

                        isCompleted = true
                        manager.removeUpdates(listener)
                        locationManager = null

                        if (continuation.isActive) {
                            val finalLocation = bestLocation
                            if (finalLocation != null) {
                                Log.i(TAG, "[GPS优先] GPS 超时，使用最佳网络位置，精度=${finalLocation.accuracy}m")
                                continuation.resume(finalLocation)
                            } else {
                                Log.w(TAG, "[GPS优先] GPS 超时且无可用位置")
                                continuation.resume(
                                    LocationResult(
                                        latLng = latLngOf(0.0, 0.0),
                                        accuracy = 0f,
                                        bearing = 0f,
                                        speed = 0f,
                                        altitude = 0.0,
                                        address = null,
                                        locationType = 0,
                                        errorCode = -2,
                                        errorInfo = "GPS 超时且无可用位置"
                                    )
                                )
                            }
                        }
                    }

                    // 使用 requestLocationUpdates 替代 requestSingleFreshLocation
                    // 因为 requestSingleFreshLocation 在某些情况下返回错误码 4
                    val result = manager.requestLocationUpdates(
                        request,
                        listener,
                        Looper.getMainLooper()
                    )

                    Log.d(TAG, "requestLocationUpdates result: $result")

                    if (result != 0) {
                        // 定位请求失败时，安全地移除监听器（避免 NPE）
                        try {
                            manager.removeUpdates(listener)
                        } catch (e: Exception) {
                            Log.w(TAG, "removeUpdates 失败（可忽略）: ${e.message}")
                        }
                        if (continuation.isActive) {
                            continuation.resume(
                                LocationResult(
                                    latLng = latLngOf(0.0, 0.0),
                                    accuracy = 0f,
                                    bearing = 0f,
                                    speed = 0f,
                                    altitude = 0.0,
                                    address = null,
                                    locationType = 0,
                                    errorCode = result,
                                    errorInfo = "Failed to start location request (code: $result)"
                                )
                            )
                        }
                    } else {
                        // 启动 GPS 等待超时
                        mainHandler.postDelayed(gpsTimeoutRunnable, gpsWaitTime)
                    }

                    continuation.invokeOnCancellation {
                        mainHandler.removeCallbacks(gpsTimeoutRunnable)
                        mainHandler.post {
                            if (!isCompleted) {
                                isCompleted = true
                                manager.removeUpdates(listener)
                                locationManager = null
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "getLocation error", e)
                    if (continuation.isActive) {
                        continuation.resume(
                            LocationResult(
                                latLng = latLngOf(0.0, 0.0),
                                accuracy = 0f,
                                bearing = 0f,
                                speed = 0f,
                                altitude = 0.0,
                                address = null,
                                locationType = 0,
                                errorCode = -1,
                                errorInfo = e.message
                            )
                        )
                    }
                }
            }
        }

    /**
     * 获取定位类型名称（内部使用）
     */
    private fun getLocationTypeName(type: Int): String {
        return when (type) {
            LOCATION_TYPE_GPS -> "GPS"
            LOCATION_TYPE_NETWORK -> "网络"
            LOCATION_TYPE_WIFI -> "WiFi"
            LOCATION_TYPE_CELL -> "基站"
            LOCATION_TYPE_OFFLINE -> "离线"
            LOCATION_TYPE_LAST -> "缓存"
            else -> "未知($type)"
        }
    }

    /**
     * 获取连续定位 Flow
     * 按指定间隔持续返回位置更新
     *
     * @param interval 定位间隔（毫秒），默认 2 秒
     * @param highAccuracy 是否使用高精度模式，默认 true
     * @return 位置更新 Flow
     */
    fun getLocationUpdates(
        interval: Long = 2000L,
        highAccuracy: Boolean = true
    ): Flow<LocationResult> = callbackFlow {
        val manager = TencentLocationManager.getInstance(context)

        val request = TencentLocationRequest.create().apply {
            requestLevel = if (highAccuracy) {
                TencentLocationRequest.REQUEST_LEVEL_ADMIN_AREA
            } else {
                TencentLocationRequest.REQUEST_LEVEL_NAME
            }
            isAllowGPS = highAccuracy
            this.interval = interval
        }

        val listener = object : TencentLocationListener {
            override fun onLocationChanged(
                location: TencentLocation?,
                error: Int,
                reason: String?
            ) {
                val result = parseLocation(location, error, reason)
                trySend(result)
            }

            override fun onStatusUpdate(name: String?, status: Int, desc: String?) {
                // 状态更新（静默）
            }
        }

        manager.requestLocationUpdates(request, listener, Looper.getMainLooper())

        awaitClose {
            manager.removeUpdates(listener)
        }
    }

    /**
     * 解析腾讯定位结果
     * 腾讯定位返回 GCJ-02 坐标，与腾讯地图一致，无需转换
     */
    private fun parseLocation(
        location: TencentLocation?,
        error: Int,
        reason: String?
    ): LocationResult {
        if (location == null || error != TencentLocation.ERROR_OK) {
            return LocationResult(
                latLng = latLngOf(0.0, 0.0),
                accuracy = 0f,
                bearing = 0f,
                speed = 0f,
                altitude = 0.0,
                address = null,
                locationType = 0,
                errorCode = error,
                errorInfo = reason ?: "Location is null"
            )
        }

        // 腾讯定位直接返回 GCJ-02 坐标，与腾讯地图一致
        return LocationResult(
            latLng = latLngOf(location.latitude, location.longitude),
            accuracy = location.accuracy,
            bearing = location.bearing,
            speed = location.speed,
            altitude = location.altitude,
            address = location.address,
            locationType = mapLocationType(location.provider),
            errorCode = 0
        )
    }

    /**
     * 映射定位类型
     */
    private fun mapLocationType(provider: String?): Int {
        return when (provider) {
            TencentLocation.GPS_PROVIDER -> LOCATION_TYPE_GPS
            TencentLocation.NETWORK_PROVIDER -> LOCATION_TYPE_NETWORK
            else -> LOCATION_TYPE_NETWORK
        }
    }

    /**
     * 停止定位并释放资源
     */
    fun destroy() {
        locationManager = null
        Log.d(TAG, "TencentLocationService destroyed")
    }
}
