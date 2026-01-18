package me.ikate.findmy.service

import android.content.Context
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.mapbox.geojson.Point
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import me.ikate.findmy.util.CoordinateConverter
import kotlin.coroutines.resume

/**
 * 高德定位服务封装
 * 提供单次定位和连续定位功能
 *
 * 注意：
 * - 高德定位返回 GCJ-02 坐标，需要转换为 WGS-84 用于 Mapbox
 * - 使用前需要调用 AMapLocationClient.updatePrivacyShow 和 updatePrivacyAgree
 */
class AmapLocationService(private val context: Context) {

    private var locationClient: AMapLocationClient? = null

    /**
     * 定位结果数据类
     */
    data class LocationResult(
        val point: Point,           // WGS-84 坐标（已转换）
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

    companion object {
        // 定位类型常量
        const val LOCATION_TYPE_GPS = 1
        const val LOCATION_TYPE_NETWORK = 2
        const val LOCATION_TYPE_WIFI = 4
        const val LOCATION_TYPE_CELL = 5
        const val LOCATION_TYPE_OFFLINE = 6
        const val LOCATION_TYPE_LAST = 7

        /**
         * 初始化隐私合规（必须在使用定位前调用）
         * @param context 上下文
         * @param isContains 隐私政策是否包含高德说明
         * @param isShow 是否弹窗展示给用户
         */
        fun updatePrivacyShow(context: Context, isContains: Boolean, isShow: Boolean) {
            AMapLocationClient.updatePrivacyShow(context, isContains, isShow)
        }

        /**
         * 更新用户隐私授权状态
         * @param context 上下文
         * @param isAgree 用户是否同意
         */
        fun updatePrivacyAgree(context: Context, isAgree: Boolean) {
            AMapLocationClient.updatePrivacyAgree(context, isAgree)
        }
    }

    /**
     * 获取单次定位
     * 使用高精度模式，定位一次后自动停止
     *
     * @param timeout 超时时间（毫秒），默认 20 秒
     * @return 定位结果
     */
    suspend fun getLocation(timeout: Long = 20000L): LocationResult =
        suspendCancellableCoroutine { continuation ->
            try {
                val client = AMapLocationClient(context)
                locationClient = client

                val option = AMapLocationClientOption().apply {
                    // 高精度模式：同时使用 GPS 和网络定位
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    // 单次定位
                    isOnceLocation = true
                    isOnceLocationLatest = true
                    // 超时时间
                    httpTimeOut = timeout
                    // 返回地址描述
                    isNeedAddress = true
                    // 设置定位间隔（单次定位此参数无效）
                    interval = 2000
                }

                client.setLocationOption(option)

                client.setLocationListener { location ->
                    val result = parseLocation(location)
                    client.stopLocation()
                    client.onDestroy()
                    locationClient = null

                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                client.startLocation()

                continuation.invokeOnCancellation {
                    client.stopLocation()
                    client.onDestroy()
                    locationClient = null
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(
                        LocationResult(
                            point = Point.fromLngLat(0.0, 0.0),
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

    /**
     * 获取连续定位 Flow
     * 按指定间隔持续返回位置更新
     *
     * @param interval 定位间隔（毫秒），默认 2 秒
     * @param mode 定位模式，默认高精度
     * @return 位置更新 Flow
     */
    fun getLocationUpdates(
        interval: Long = 2000L,
        mode: AMapLocationClientOption.AMapLocationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
    ): Flow<LocationResult> = callbackFlow {
        val client = AMapLocationClient(context)

        val option = AMapLocationClientOption().apply {
            locationMode = mode
            isOnceLocation = false
            this.interval = interval
            isNeedAddress = true
            // 设置是否返回方向信息
            isSensorEnable = true
        }

        client.setLocationOption(option)

        val listener = AMapLocationListener { location ->
            val result = parseLocation(location)
            trySend(result)
        }

        client.setLocationListener(listener)
        client.startLocation()

        awaitClose {
            client.stopLocation()
            client.onDestroy()
        }
    }

    /**
     * 解析高德定位结果
     * 将 GCJ-02 坐标转换为 WGS-84
     */
    private fun parseLocation(location: AMapLocation?): LocationResult {
        if (location == null) {
            return LocationResult(
                point = Point.fromLngLat(0.0, 0.0),
                accuracy = 0f,
                bearing = 0f,
                speed = 0f,
                altitude = 0.0,
                address = null,
                locationType = 0,
                errorCode = -1,
                errorInfo = "Location is null"
            )
        }

        if (location.errorCode != 0) {
            return LocationResult(
                point = Point.fromLngLat(0.0, 0.0),
                accuracy = 0f,
                bearing = 0f,
                speed = 0f,
                altitude = 0.0,
                address = null,
                locationType = location.locationType,
                errorCode = location.errorCode,
                errorInfo = location.errorInfo
            )
        }

        // 高德返回 GCJ-02 坐标，转换为 WGS-84 用于 Mapbox
        val wgs84Point = CoordinateConverter.gcj02ToWgs84(
            location.latitude,
            location.longitude
        )

        return LocationResult(
            point = wgs84Point,
            accuracy = location.accuracy,
            bearing = location.bearing,
            speed = location.speed,
            altitude = location.altitude,
            address = location.address,
            locationType = location.locationType,
            errorCode = 0
        )
    }

    /**
     * 停止定位并释放资源
     */
    fun destroy() {
        locationClient?.stopLocation()
        locationClient?.onDestroy()
        locationClient = null
    }
}
