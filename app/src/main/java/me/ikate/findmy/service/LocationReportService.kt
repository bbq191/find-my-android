package me.ikate.findmy.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.DeviceType
import me.ikate.findmy.data.repository.AuthRepository
import me.ikate.findmy.data.repository.DeviceRepository

/**
 * 位置上报服务
 * 使用高德定位 SDK 获取当前设备位置，通过 MQTT 同步到服务器
 *
 * 注意：
 * - 高德定位返回 GCJ-02 坐标，AmapLocationService 内部已转换为 WGS-84
 * - 使用前需要确保已调用 PrivacyManager.initPrivacy() 初始化隐私合规
 * - 数据存储在本地 Room 数据库，通过 MQTT 实时同步
 */
class LocationReportService(private val context: Context) {

    private val amapLocationService = AmapLocationService(context)
    private val deviceRepository = DeviceRepository(context)

    /**
     * 获取当前设备ID
     * 使用 Android ID 作为设备唯一标识
     */
    @SuppressLint("HardwareIds")
    private fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }

    /**
     * 获取设备名称（型号）
     */
    private fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    /**
     * 获取设备自定义名称
     * 尝试从系统设置中获取设备名称
     */
    private fun getCustomDeviceName(): String? {
        return try {
            // 尝试获取蓝牙设备名称或系统设置的设备名称
            android.provider.Settings.Global.getString(
                context.contentResolver,
                "device_name"
            ) ?: android.provider.Settings.Secure.getString(
                context.contentResolver,
                "bluetooth_name"
            )
        } catch (e: Exception) {
            android.util.Log.w("LocationReportService", "无法获取设备自定义名称", e)
            null
        }
    }

    /**
     * 获取设备类型
     */
    private fun getDeviceType(): DeviceType {
        return when {
            // 简单判断，可以根据需要扩展
            Build.DEVICE.contains("tablet", ignoreCase = true) -> DeviceType.TABLET
            else -> DeviceType.PHONE
        }
    }

    /**
     * 获取电池电量
     */
    private fun getBatteryLevel(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    /**
     * 获取当前位置并上报
     * 使用高德定位 SDK 获取高精度位置，保存到本地并通过 MQTT 同步
     *
     * @param timeout 定位超时时间（毫秒），默认 20 秒
     * @return 上报结果，包含设备信息
     */
    suspend fun reportCurrentLocation(timeout: Long = 20000L): Result<Device> {
        return try {
            // 使用高德定位获取位置（内部已转换为 WGS-84）
            val locationResult = amapLocationService.getLocation(timeout)

            if (!locationResult.isSuccess) {
                val errorMsg = "定位失败: ${locationResult.errorInfo} (错误码: ${locationResult.errorCode})"
                android.util.Log.e("LocationReportService", errorMsg)
                return Result.failure(Exception(errorMsg))
            }

            val point = locationResult.point
            if (point.latitude().isNaN() || point.longitude().isNaN()) {
                return Result.failure(Exception("无法获取位置信息，请确保已开启定位服务且信号良好"))
            }

            val currentUserId = AuthRepository.getUserId(context)

            android.util.Log.d(
                "LocationReportService",
                "🔐 当前用户ID: $currentUserId, 设备ID: ${getDeviceId()}"
            )

            // 创建设备对象（坐标已是 WGS-84，Mapbox 直接使用）
            val device = Device(
                id = getDeviceId(),
                name = getDeviceName(),
                ownerId = currentUserId,
                location = point,
                battery = getBatteryLevel(),
                lastUpdateTime = System.currentTimeMillis(),
                isOnline = true,
                deviceType = getDeviceType(),
                customName = getCustomDeviceName(),
                bearing = locationResult.bearing,
                speed = locationResult.speed // GPS速度用于智能活动识别
            )

            // 保存到本地数据库并通过 MQTT 同步
            deviceRepository.saveDevice(device)

            android.util.Log.d(
                "LocationReportService",
                "✅ 位置上报成功: ${device.name} (ownerId=$currentUserId) at (${point.latitude()}, ${point.longitude()})"
            )
            android.util.Log.d(
                "LocationReportService",
                "📍 定位类型: ${getLocationTypeName(locationResult.locationType)}, 精度: ${locationResult.accuracy}m"
            )

            Result.success(device)
        } catch (e: Exception) {
            android.util.Log.e("LocationReportService", "位置上报失败", e)
            Result.failure(e)
        }
    }

    /**
     * 获取定位类型名称
     */
    private fun getLocationTypeName(type: Int): String {
        return when (type) {
            AmapLocationService.LOCATION_TYPE_GPS -> "GPS"
            AmapLocationService.LOCATION_TYPE_NETWORK -> "网络"
            AmapLocationService.LOCATION_TYPE_WIFI -> "WiFi"
            AmapLocationService.LOCATION_TYPE_CELL -> "基站"
            AmapLocationService.LOCATION_TYPE_OFFLINE -> "离线"
            AmapLocationService.LOCATION_TYPE_LAST -> "缓存"
            else -> "未知($type)"
        }
    }

    /**
     * 释放资源
     */
    fun destroy() {
        amapLocationService.destroy()
    }
}
