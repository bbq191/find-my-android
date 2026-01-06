package me.ikate.findmy.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.DeviceType
import me.ikate.findmy.util.CoordinateConverter
import com.google.firebase.auth.FirebaseAuth

/**
 * 设备数据仓库
 * 封装 Firestore 数据访问，提供设备列表查询和实时监听
 *
 * Firestore 数据结构：
 * devices/{deviceId}
 *   - name: String
 *   - ownerId: String
 *   - location: GeoPoint
 *   - battery: Number (0-100)
 *   - lastUpdateTime: Timestamp
 *   - avatarUrl: String (可选)
 *   - isOnline: Boolean
 *   - deviceType: String ("PHONE", "TABLET", "WATCH", "AIRTAG", "OTHER")
 */
class DeviceRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val devicesCollection = firestore.collection("devices")

    /**
     * 实时监听设备列表变化
     * 返回 Flow，自动订阅 Firestore 快照更新
     * 自动使用当前登录用户的 ID 进行查询
     */
    fun observeDevices(): Flow<List<Device>> = callbackFlow {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserId == null) {
            android.util.Log.w("DeviceRepository", "用户未登录，返回空列表")
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val query = devicesCollection.whereEqualTo("ownerId", currentUserId)

        val listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("DeviceRepository", "监听设备列表失败", error)
                // 发送空列表而不是关闭Flow，避免应用崩溃
                trySend(emptyList())
                return@addSnapshotListener
            }

            snapshot?.let {
                val devices = it.documents.mapNotNull { doc ->
                    try {
                        val location = doc.getGeoPoint("location")
                        // Firebase中存储的是WGS-84坐标（GPS原始坐标）
                        // 需要转换为GCJ-02以匹配Google Maps在中国的底图
                        val wgsLat = location?.latitude ?: 0.0
                        val wgsLng = location?.longitude ?: 0.0
                        val gcjLocation = CoordinateConverter.wgs84ToGcj02(wgsLat, wgsLng)

                        Device(
                            id = doc.id,
                            name = doc.getString("name") ?: "未知设备",
                            location = gcjLocation,
                            avatarUrl = doc.getString("avatarUrl"),
                            battery = doc.getLong("battery")?.toInt() ?: 100,
                            lastUpdateTime = doc.getTimestamp("lastUpdateTime")?.toDate()?.time
                                ?: System.currentTimeMillis(),
                            isOnline = doc.getBoolean("isOnline") ?: false,
                            deviceType = DeviceType.valueOf(
                                doc.getString("deviceType") ?: "OTHER"
                            ),
                            ownerName = doc.getString("ownerName"),
                            customName = doc.getString("customName"),
                            bearing = doc.getDouble("bearing")?.toFloat() ?: 0f
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("DeviceRepository", "解析设备数据失败: ${doc.id}", e)
                        null
                    }
                }
                trySend(devices)
            }
        }

        awaitClose { listenerRegistration.remove() }
    }

    /**
     * 添加或更新设备
     * 自动添加当前用户 ID 作为 ownerId
     *
     * @param device 设备对象
     */
    suspend fun saveDevice(device: Device) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            android.util.Log.e("DeviceRepository", "用户未登录，无法保存设备")
            return
        }

        val deviceData = hashMapOf(
            "name" to device.name,
            "location" to GeoPoint(device.location.latitude, device.location.longitude),
            "battery" to device.battery,
            "lastUpdateTime" to com.google.firebase.Timestamp.now(),
            "avatarUrl" to device.avatarUrl,
            "isOnline" to device.isOnline,
            "deviceType" to device.deviceType.name,
            "ownerId" to currentUserId,
            "ownerName" to device.ownerName,
            "customName" to device.customName,
            "bearing" to device.bearing
        )

        devicesCollection.document(device.id)
            .set(deviceData)
            .addOnSuccessListener {
                android.util.Log.d("DeviceRepository", "设备保存成功: ${device.id}")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("DeviceRepository", "设备保存失败: ${device.id}", e)
            }
    }

    /**
     * 删除设备
     *
     * @param deviceId 设备 ID
     */
    suspend fun deleteDevice(deviceId: String) {
        devicesCollection.document(deviceId)
            .delete()
            .addOnSuccessListener {
                android.util.Log.d("DeviceRepository", "设备删除成功: $deviceId")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("DeviceRepository", "设备删除失败: $deviceId", e)
            }
    }
}
