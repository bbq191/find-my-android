package me.ikate.findmy.service

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.DeviceType
import me.ikate.findmy.data.repository.AuthRepository
import me.ikate.findmy.data.repository.DeviceRepository
import me.ikate.findmy.util.DeviceIdProvider

/**
 * 位置上报服务
 * 使用腾讯定位 SDK 获取当前设备位置，通过 MQTT 同步到服务器
 *
 * 注意：
 * - 腾讯定位返回 GCJ-02 坐标，与腾讯地图一致，无需坐标转换
 * - 使用前需要确保已调用 PrivacyManager.initPrivacy() 初始化隐私合规
 * - 数据存储在本地 Room 数据库，通过 MQTT 实时同步
 */
class LocationReportService(private val context: Context) {

    private val tencentLocationService = TencentLocationService(context)
    private val deviceRepository = DeviceRepository(context)

    /**
     * 获取当前设备ID
     * 使用 Android ID 作为设备唯一标识
     * 委托给 DeviceIdProvider 统一管理
     */
    private fun getDeviceId(): String {
        return DeviceIdProvider.getDeviceId(context)
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
     * 使用腾讯定位 SDK 获取高精度位置，保存到本地并通过 MQTT 同步
     *
     * @param timeout 定位超时时间（毫秒），默认 20 秒
     * @return 上报结果，包含设备信息
     */
    suspend fun reportCurrentLocation(timeout: Long = 20000L): Result<Device> {
        android.util.Log.i(TAG, "========== [位置上报服务] 开始上报 ==========")
        return try {
            // 使用腾讯定位获取位置（GCJ-02 坐标）
            android.util.Log.i(TAG, "[位置上报服务] 步骤1: 获取当前位置...")
            val locationResult = tencentLocationService.getLocation(timeout)

            if (!locationResult.isSuccess) {
                val errorMsg = "定位失败: ${locationResult.errorInfo} (错误码: ${locationResult.errorCode})"
                android.util.Log.e(TAG, "[位置上报服务] ✗ $errorMsg")
                return Result.failure(Exception(errorMsg))
            }

            val latLng = locationResult.latLng
            if (latLng.latitude.isNaN() || latLng.longitude.isNaN()) {
                android.util.Log.e(TAG, "[位置上报服务] ✗ 坐标无效")
                return Result.failure(Exception("无法获取位置信息，请确保已开启定位服务且信号良好"))
            }

            android.util.Log.i(TAG, "[位置上报服务] ✓ 定位成功")
            android.util.Log.i(TAG, "[位置上报服务] 坐标: (${latLng.latitude}, ${latLng.longitude})")
            android.util.Log.i(TAG, "[位置上报服务] 定位类型: ${getLocationTypeName(locationResult.locationType)}")
            android.util.Log.i(TAG, "[位置上报服务] 精度: ${locationResult.accuracy}m")

            val currentUserId = AuthRepository.getUserId(context)
            android.util.Log.i(TAG, "[位置上报服务] 步骤2: 构建设备对象...")
            android.util.Log.i(TAG, "[位置上报服务] 用户ID: $currentUserId")
            android.util.Log.i(TAG, "[位置上报服务] 设备ID: ${getDeviceId()}")

            // 创建设备对象（GCJ-02 坐标，与腾讯地图一致）
            val device = Device(
                id = getDeviceId(),
                name = getDeviceName(),
                ownerId = currentUserId,
                location = latLng,
                battery = getBatteryLevel(),
                lastUpdateTime = System.currentTimeMillis(),
                isOnline = true,
                deviceType = getDeviceType(),
                customName = getCustomDeviceName(),
                bearing = locationResult.bearing,
                speed = locationResult.speed // GPS速度用于智能活动识别
            )

            // 保存到本地数据库并通过 MQTT 同步
            android.util.Log.i(TAG, "[位置上报服务] 步骤3: 保存并同步到 MQTT...")
            deviceRepository.saveDevice(device)

            android.util.Log.i(TAG, "[位置上报服务] ✓ 位置上报成功")
            android.util.Log.i(TAG, "[位置上报服务] 设备名: ${device.name}")
            android.util.Log.i(TAG, "[位置上报服务] 电量: ${device.battery}%")

            Result.success(device)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "[位置上报服务] ✗ 位置上报失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "LocationReportService"
    }

    /**
     * 获取定位类型名称
     */
    private fun getLocationTypeName(type: Int): String {
        return when (type) {
            TencentLocationService.LOCATION_TYPE_GPS -> "GPS"
            TencentLocationService.LOCATION_TYPE_NETWORK -> "网络"
            TencentLocationService.LOCATION_TYPE_WIFI -> "WiFi"
            TencentLocationService.LOCATION_TYPE_CELL -> "基站"
            TencentLocationService.LOCATION_TYPE_OFFLINE -> "离线"
            TencentLocationService.LOCATION_TYPE_LAST -> "缓存"
            else -> "未知($type)"
        }
    }

    /**
     * 释放资源
     */
    fun destroy() {
        tencentLocationService.destroy()
    }
}
