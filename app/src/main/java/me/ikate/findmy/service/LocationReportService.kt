package me.ikate.findmy.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.tasks.await
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.DeviceType
import me.ikate.findmy.data.repository.DeviceRepository

/**
 * 位置上报服务
 * 负责获取当前设备位置和状态，上报到 Firebase
 */
class LocationReportService(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val deviceRepository = DeviceRepository()

    /**
     * 获取当前设备ID
     * 使用 Android ID 作为设备唯一标识
     */
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
     * 获取用户显示名称（从通讯录vCard中获取）
     */
    private fun getOwnerName(): String? {
        return try {
            // 从系统通讯录获取用户自己的名称
            val cursor = context.contentResolver.query(
                android.provider.ContactsContract.Profile.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.Profile.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.ContactsContract.Profile.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        return it.getString(nameIndex)
                    }
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.w("LocationReportService", "无法获取用户名称", e)
            null
        }
    }

    /**
     * 获取用户头像URL（从通讯录vCard中获取）
     */
    private fun getOwnerAvatarUrl(): String? {
        return try {
            val cursor = context.contentResolver.query(
                android.provider.ContactsContract.Profile.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.Profile.PHOTO_URI),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val photoIndex = it.getColumnIndex(android.provider.ContactsContract.Profile.PHOTO_URI)
                    if (photoIndex >= 0) {
                        return it.getString(photoIndex)
                    }
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.w("LocationReportService", "无法获取用户头像", e)
            null
        }
    }

    /**
     * 获取当前位置并上报到 Firebase
     * 注意：需要已授予定位权限
     *
     * @return 上报是否成功
     */
    @SuppressLint("MissingPermission")
    suspend fun reportCurrentLocation(): Result<Device> {
        return try {
            // 获取当前位置
            val location = fusedLocationClient.lastLocation.await()
                ?: throw Exception("无法获取位置信息，请确保已开启定位服务")

            // 获取GPS方向（bearing）
            val bearing = if (location.hasBearing()) location.bearing else 0f

            // 创建设备对象（使用 WGS-84 坐标，DeviceRepository 会自动转换为 GCJ-02）
            val device = Device(
                id = getDeviceId(),
                name = getDeviceName(), // 设备型号
                location = LatLng(location.latitude, location.longitude),
                battery = getBatteryLevel(),
                lastUpdateTime = System.currentTimeMillis(),
                isOnline = true,
                deviceType = getDeviceType(),
                ownerName = getOwnerName(), // 机主名称
                customName = getCustomDeviceName(), // 设备自定义名称
                bearing = bearing,
                avatarUrl = getOwnerAvatarUrl()
            )

            // 保存到 Firebase
            deviceRepository.saveDevice(device)

            android.util.Log.d("LocationReportService", "位置上报成功: ${device.name} at (${location.latitude}, ${location.longitude})")
            Result.success(device)
        } catch (e: Exception) {
            android.util.Log.e("LocationReportService", "位置上报失败", e)
            Result.failure(e)
        }
    }

    /**
     * 标记设备为离线
     */
    suspend fun markDeviceOffline() {
        try {
            // 获取最后已知位置
            val location = fusedLocationClient.lastLocation.await()
            val lastLocation = if (location != null) {
                LatLng(location.latitude, location.longitude)
            } else {
                LatLng(0.0, 0.0) // 如果没有位置信息，使用默认值
            }

            val device = Device(
                id = getDeviceId(),
                name = getDeviceName(),
                location = lastLocation,
                battery = getBatteryLevel(),
                lastUpdateTime = System.currentTimeMillis(),
                isOnline = false,
                deviceType = getDeviceType()
            )

            deviceRepository.saveDevice(device)
            android.util.Log.d("LocationReportService", "设备已标记为离线")
        } catch (e: Exception) {
            android.util.Log.e("LocationReportService", "标记离线失败", e)
        }
    }
}
