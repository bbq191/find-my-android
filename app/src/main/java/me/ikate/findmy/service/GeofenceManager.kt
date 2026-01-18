package me.ikate.findmy.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.amap.api.fence.GeoFence
import com.amap.api.fence.GeoFenceClient
import com.amap.api.fence.GeoFenceListener
import com.amap.api.location.DPoint
import com.mapbox.geojson.Point
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.ikate.findmy.util.CoordinateConverter
import org.json.JSONArray
import org.json.JSONObject

/**
 * 地理围栏配置数据类
 */
data class GeofenceData(
    val id: String,
    val contactId: String,
    val contactName: String,
    val locationName: String,
    val latitude: Double,  // WGS-84 坐标（用于 Mapbox 显示）
    val longitude: Double,
    val radiusMeters: Float,
    val notifyOnEnter: Boolean,
    val notifyOnExit: Boolean
)

/**
 * 地理围栏管理器
 *
 * 使用高德 GeoFence SDK 实现电子围栏功能
 * 注意：
 * - 输入坐标为 WGS-84（Mapbox 坐标系）
 * - 内部会转换为 GCJ-02（高德坐标系）进行围栏注册
 */
class GeofenceManager(private val context: Context) {

    companion object {
        private const val TAG = "GeofenceManager"
        private const val PREFS_NAME = "geofences"
        private const val KEY_GEOFENCES = "geofence_list"
        private const val NOTIFICATION_STATE_PREFS = "geofence_notification_state"
        private const val STATE_KEY_PREFIX = "last_state_"

        // 广播 Action
        const val ACTION_GEOFENCE_TRIGGERED = "me.ikate.findmy.GEOFENCE_TRIGGERED"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 高德围栏客户端
    private var geoFenceClient: GeoFenceClient? = null

    // 当前激活的围栏
    private val _activeGeofences = MutableStateFlow<List<GeofenceData>>(emptyList())
    val activeGeofences: StateFlow<List<GeofenceData>> = _activeGeofences.asStateFlow()

    // 围栏创建中的回调映射
    private val pendingCallbacks = mutableMapOf<String, Pair<() -> Unit, (String) -> Unit>>()

    // 已创建的围栏对象映射（用于移除）
    private val createdFences = mutableMapOf<String, GeoFence>()

    init {
        loadGeofences()
        initGeoFenceClient()
    }

    /**
     * 初始化高德围栏客户端
     */
    private fun initGeoFenceClient() {
        try {
            geoFenceClient = GeoFenceClient(context).apply {
                // 设置围栏触发动作：进入、离开、停留
                setActivateAction(
                    GeoFenceClient.GEOFENCE_IN or
                    GeoFenceClient.GEOFENCE_OUT or
                    GeoFenceClient.GEOFENCE_STAYED
                )

                // 创建 PendingIntent 用于接收围栏触发事件
                createPendingIntent(ACTION_GEOFENCE_TRIGGERED)

                // 设置围栏创建监听器
                setGeoFenceListener(object : GeoFenceListener {
                    override fun onGeoFenceCreateFinished(
                        geoFenceList: List<GeoFence>?,
                        errorCode: Int,
                        customId: String?
                    ) {
                        if (errorCode == GeoFence.ADDGEOFENCE_SUCCESS) {
                            Log.d(TAG, "围栏创建成功: $customId, 数量: ${geoFenceList?.size}")
                            // 保存创建的围栏对象（用于后续移除）
                            geoFenceList?.forEach { fence ->
                                fence.customId?.let { id ->
                                    createdFences[id] = fence
                                }
                            }
                            customId?.let { id ->
                                pendingCallbacks.remove(id)?.first?.invoke()
                            }
                        } else {
                            Log.e(TAG, "围栏创建失败: $customId, 错误码: $errorCode")
                            customId?.let { id ->
                                pendingCallbacks.remove(id)?.second?.invoke(
                                    getErrorMessage(errorCode)
                                )
                            }
                        }
                    }
                })
            }
            Log.d(TAG, "高德围栏客户端初始化成功")

            // 重新注册已保存的围栏
            reRegisterGeofences()
        } catch (e: Exception) {
            Log.e(TAG, "高德围栏客户端初始化失败", e)
        }
    }

    /**
     * 添加地理围栏
     *
     * @param contactId 联系人ID
     * @param contactName 联系人名称
     * @param locationName 位置名称
     * @param center WGS-84 坐标（Mapbox）
     * @param radiusMeters 围栏半径（米）
     * @param notifyOnEnter 进入时通知
     * @param notifyOnExit 离开时通知
     * @param onSuccess 成功回调
     * @param onFailure 失败回调
     */
    fun addGeofence(
        contactId: String,
        contactName: String,
        locationName: String,
        center: Point,
        radiusMeters: Float,
        notifyOnEnter: Boolean,
        notifyOnExit: Boolean,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val client = geoFenceClient
        if (client == null) {
            onFailure("围栏服务未初始化")
            return
        }

        // 生成唯一ID
        val geofenceId = "geofence_${contactId}_${System.currentTimeMillis()}"

        // 先移除该联系人的旧围栏
        removeGeofenceInternal(contactId)

        // 保存围栏数据（使用 WGS-84 坐标）
        val geofenceData = GeofenceData(
            id = geofenceId,
            contactId = contactId,
            contactName = contactName,
            locationName = locationName,
            latitude = center.latitude(),
            longitude = center.longitude(),
            radiusMeters = radiusMeters,
            notifyOnEnter = notifyOnEnter,
            notifyOnExit = notifyOnExit
        )

        // 添加到列表
        val currentList = _activeGeofences.value.toMutableList()
        currentList.add(geofenceData)
        _activeGeofences.value = currentList
        saveGeofences()

        // 转换坐标：WGS-84 → GCJ-02
        val gcj02Point = CoordinateConverter.wgs84ToGcj02(
            center.latitude(),
            center.longitude()
        )

        // 创建高德围栏中心点
        val amapCenter = DPoint(gcj02Point.latitude(), gcj02Point.longitude())

        // 注册回调
        pendingCallbacks[geofenceId] = Pair(onSuccess, onFailure)

        // 添加圆形围栏
        try {
            client.addGeoFence(
                amapCenter,
                radiusMeters,
                geofenceId  // customId 用于标识围栏
            )
            Log.d(TAG, "正在创建围栏: $geofenceId, 中心: ${gcj02Point.latitude()}, ${gcj02Point.longitude()}, 半径: $radiusMeters")
        } catch (e: Exception) {
            Log.e(TAG, "添加围栏异常", e)
            pendingCallbacks.remove(geofenceId)
            onFailure("添加围栏失败: ${e.message}")
        }
    }

    /**
     * 移除指定联系人的地理围栏
     */
    fun removeGeofence(contactId: String, onComplete: () -> Unit = {}) {
        removeGeofenceInternal(contactId)
        onComplete()
    }

    private fun removeGeofenceInternal(contactId: String) {
        val geofenceToRemove = _activeGeofences.value.find { it.contactId == contactId }
        if (geofenceToRemove != null) {
            // 从高德客户端移除（使用保存的 GeoFence 对象）
            val fence = createdFences.remove(geofenceToRemove.id)
            if (fence != null) {
                geoFenceClient?.removeGeoFence(fence)
                Log.d(TAG, "已移除围栏: ${geofenceToRemove.id}")
            } else {
                Log.w(TAG, "未找到围栏对象，可能尚未创建完成: ${geofenceToRemove.id}")
            }
            // 清除该围栏的通知状态记录
            clearNotificationState(geofenceToRemove.id)
        }

        // 更新列表
        val currentList = _activeGeofences.value.toMutableList()
        currentList.removeAll { it.contactId == contactId }
        _activeGeofences.value = currentList
        saveGeofences()
    }

    /**
     * 移除所有地理围栏
     */
    fun removeAllGeofences(onComplete: () -> Unit = {}) {
        geoFenceClient?.removeGeoFence()
        createdFences.clear()
        _activeGeofences.value = emptyList()
        saveGeofences()
        clearAllNotificationStates()
        Log.d(TAG, "已移除所有围栏")
        onComplete()
    }

    /**
     * 清除指定围栏的通知状态记录
     */
    private fun clearNotificationState(geofenceId: String) {
        val statePrefs = context.getSharedPreferences(NOTIFICATION_STATE_PREFS, Context.MODE_PRIVATE)
        statePrefs.edit { remove(STATE_KEY_PREFIX + geofenceId) }
        Log.d(TAG, "已清除围栏通知状态: $geofenceId")
    }

    /**
     * 清除所有围栏的通知状态记录
     */
    private fun clearAllNotificationStates() {
        val statePrefs = context.getSharedPreferences(NOTIFICATION_STATE_PREFS, Context.MODE_PRIVATE)
        statePrefs.edit { clear() }
        Log.d(TAG, "已清除所有围栏通知状态")
    }

    /**
     * 获取指定联系人的围栏配置
     */
    fun getGeofenceForContact(contactId: String): GeofenceData? {
        return _activeGeofences.value.find { it.contactId == contactId }
    }

    /**
     * 根据围栏ID获取围栏数据
     */
    fun getGeofenceById(geofenceId: String): GeofenceData? {
        return _activeGeofences.value.find { it.id == geofenceId }
    }

    /**
     * 重新注册已保存的围栏
     */
    private fun reRegisterGeofences() {
        val client = geoFenceClient ?: return
        val geofences = _activeGeofences.value

        if (geofences.isEmpty()) {
            Log.d(TAG, "没有需要重新注册的围栏")
            return
        }

        Log.d(TAG, "正在重新注册 ${geofences.size} 个围栏")

        geofences.forEach { geofence ->
            // 转换坐标
            val gcj02Point = CoordinateConverter.wgs84ToGcj02(
                geofence.latitude,
                geofence.longitude
            )
            val amapCenter = DPoint(gcj02Point.latitude(), gcj02Point.longitude())

            try {
                client.addGeoFence(
                    amapCenter,
                    geofence.radiusMeters,
                    geofence.id
                )
            } catch (e: Exception) {
                Log.e(TAG, "重新注册围栏失败: ${geofence.id}", e)
            }
        }
    }

    /**
     * 保存围栏配置到本地
     */
    private fun saveGeofences() {
        val jsonArray = JSONArray()
        _activeGeofences.value.forEach { geofence ->
            val json = JSONObject().apply {
                put("id", geofence.id)
                put("contactId", geofence.contactId)
                put("contactName", geofence.contactName)
                put("locationName", geofence.locationName)
                put("latitude", geofence.latitude)
                put("longitude", geofence.longitude)
                put("radiusMeters", geofence.radiusMeters.toDouble())
                put("notifyOnEnter", geofence.notifyOnEnter)
                put("notifyOnExit", geofence.notifyOnExit)
            }
            jsonArray.put(json)
        }
        prefs.edit { putString(KEY_GEOFENCES, jsonArray.toString()) }
    }

    /**
     * 从本地加载围栏配置
     */
    private fun loadGeofences() {
        val jsonString = prefs.getString(KEY_GEOFENCES, null) ?: return
        try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<GeofenceData>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                list.add(
                    GeofenceData(
                        id = json.getString("id"),
                        contactId = json.getString("contactId"),
                        contactName = json.getString("contactName"),
                        locationName = json.getString("locationName"),
                        latitude = json.getDouble("latitude"),
                        longitude = json.getDouble("longitude"),
                        radiusMeters = json.getDouble("radiusMeters").toFloat(),
                        notifyOnEnter = json.getBoolean("notifyOnEnter"),
                        notifyOnExit = json.getBoolean("notifyOnExit")
                    )
                )
            }
            _activeGeofences.value = list
            Log.d(TAG, "加载了 ${list.size} 个围栏配置")
        } catch (e: Exception) {
            Log.e(TAG, "加载围栏配置失败", e)
        }
    }

    /**
     * 获取错误信息
     */
    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            GeoFence.ERROR_CODE_INVALID_PARAMETER -> "参数无效"
            GeoFence.ERROR_CODE_FAILURE_CONNECTION -> "网络连接失败"
            GeoFence.ERROR_CODE_FAILURE_AUTH -> "认证失败，请检查高德 API Key"
            GeoFence.ERROR_CODE_FAILURE_PARSER -> "解析失败"
            GeoFence.ERROR_CODE_UNKNOWN -> "未知错误"
            else -> "错误码: $errorCode"
        }
    }

    /**
     * 释放资源
     */
    fun destroy() {
        geoFenceClient?.removeGeoFence()
        geoFenceClient = null
        pendingCallbacks.clear()
        createdFences.clear()
        Log.d(TAG, "围栏管理器已销毁")
    }
}
