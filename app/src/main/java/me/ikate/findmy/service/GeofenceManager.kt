package me.ikate.findmy.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 地理围栏配置数据类
 */
data class GeofenceData(
    val id: String,
    val contactId: String,
    val contactName: String,
    val locationName: String,
    val latitude: Double,  // GCJ-02 坐标（腾讯坐标系）
    val longitude: Double,
    val radiusMeters: Float,
    val notifyOnEnter: Boolean,
    val notifyOnExit: Boolean
)

/**
 * 地理围栏管理器
 *
 * 使用基于位置的距离检测实现电子围栏功能
 * 注意：
 * - 输入坐标为 GCJ-02（腾讯坐标系）
 * - 通过定期检查联系人位置与围栏中心的距离来判断进入/离开
 */
class GeofenceManager(private val context: Context) {

    companion object {
        private const val TAG = "GeofenceManager"
        private const val PREFS_NAME = "geofences"
        private const val KEY_GEOFENCES = "geofence_list"
        private const val NOTIFICATION_STATE_PREFS = "geofence_notification_state"
        private const val STATE_KEY_PREFIX = "last_state_"
        private const val EARTH_RADIUS_METERS = 6371000.0

        // 广播 Action
        const val ACTION_GEOFENCE_TRIGGERED = "me.ikate.findmy.GEOFENCE_TRIGGERED"

        // 围栏事件类型常量
        const val GEOFENCE_TRANSITION_ENTER = 1
        const val GEOFENCE_TRANSITION_EXIT = 2
        const val GEOFENCE_TRANSITION_DWELL = 4
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val statePrefs = context.getSharedPreferences(NOTIFICATION_STATE_PREFS, Context.MODE_PRIVATE)

    // 当前激活的围栏
    private val _activeGeofences = MutableStateFlow<List<GeofenceData>>(emptyList())
    val activeGeofences: StateFlow<List<GeofenceData>> = _activeGeofences.asStateFlow()

    // 上次检测到的围栏状态 (geofenceId -> isInside)
    private val lastKnownStates = mutableMapOf<String, Boolean>()

    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        loadGeofences()
        loadLastKnownStates()
    }

    /**
     * 添加地理围栏
     *
     * @param contactId 联系人ID
     * @param contactName 联系人名称
     * @param locationName 位置名称
     * @param center GCJ-02 坐标（腾讯坐标系）
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
        center: LatLng,
        radiusMeters: Float,
        notifyOnEnter: Boolean,
        notifyOnExit: Boolean,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        // 生成唯一ID
        val geofenceId = "geofence_${contactId}_${System.currentTimeMillis()}"

        // 先移除该联系人的旧围栏
        removeGeofenceInternal(contactId)

        // 保存围栏数据（GCJ-02 坐标）
        val geofenceData = GeofenceData(
            id = geofenceId,
            contactId = contactId,
            contactName = contactName,
            locationName = locationName,
            latitude = center.latitude,
            longitude = center.longitude,
            radiusMeters = radiusMeters,
            notifyOnEnter = notifyOnEnter,
            notifyOnExit = notifyOnExit
        )

        // 添加到列表
        val currentList = _activeGeofences.value.toMutableList()
        currentList.add(geofenceData)
        _activeGeofences.value = currentList
        saveGeofences()

        Log.d(TAG, "围栏创建成功: $geofenceId, 位置: (${center.latitude}, ${center.longitude}), 半径: ${radiusMeters}m")
        onSuccess()
    }

    /**
     * 检查联系人位置是否触发围栏事件
     * 应在联系人位置更新时调用
     *
     * @param contactId 联系人ID
     * @param contactLocation 联系人当前位置 (GCJ-02)
     */
    fun checkGeofenceForContact(contactId: String, contactLocation: LatLng) {
        val geofence = _activeGeofences.value.find { it.contactId == contactId } ?: return

        val distance = calculateDistance(
            geofence.latitude, geofence.longitude,
            contactLocation.latitude, contactLocation.longitude
        )

        val isInside = distance <= geofence.radiusMeters
        val wasInside = lastKnownStates[geofence.id]

        Log.d(TAG, "检查围栏: ${geofence.locationName}, 距离: ${distance.toInt()}m, 半径: ${geofence.radiusMeters.toInt()}m, 内部: $isInside, 之前: $wasInside")

        // 第一次检测，初始化状态
        if (wasInside == null) {
            lastKnownStates[geofence.id] = isInside
            saveLastKnownStates()
            return
        }

        // 检测状态变化
        if (isInside != wasInside) {
            lastKnownStates[geofence.id] = isInside
            saveLastKnownStates()

            if (isInside && geofence.notifyOnEnter) {
                // 进入围栏
                Log.d(TAG, "联系人 ${geofence.contactName} 进入围栏: ${geofence.locationName}")
                sendGeofenceEvent(geofence, GEOFENCE_TRANSITION_ENTER)
            } else if (!isInside && geofence.notifyOnExit) {
                // 离开围栏
                Log.d(TAG, "联系人 ${geofence.contactName} 离开围栏: ${geofence.locationName}")
                sendGeofenceEvent(geofence, GEOFENCE_TRANSITION_EXIT)
            }
        }
    }

    /**
     * 发送围栏事件广播
     */
    private fun sendGeofenceEvent(geofence: GeofenceData, transitionType: Int) {
        val intent = Intent(ACTION_GEOFENCE_TRIGGERED).apply {
            setPackage(context.packageName)
            putExtra("fence_id", geofence.id)
            putExtra("contact_id", geofence.contactId)
            putExtra("contact_name", geofence.contactName)
            putExtra("location_name", geofence.locationName)
            putExtra("transition_type", transitionType)
        }
        context.sendBroadcast(intent)
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
            // 清除状态记录
            lastKnownStates.remove(geofenceToRemove.id)
            clearNotificationState(geofenceToRemove.id)
            Log.d(TAG, "已移除围栏: ${geofenceToRemove.id}")
        }

        // 更新列表
        val currentList = _activeGeofences.value.toMutableList()
        currentList.removeAll { it.contactId == contactId }
        _activeGeofences.value = currentList
        saveGeofences()
        saveLastKnownStates()
    }

    /**
     * 移除所有地理围栏
     */
    fun removeAllGeofences(onComplete: () -> Unit = {}) {
        lastKnownStates.clear()
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
        statePrefs.edit { remove(STATE_KEY_PREFIX + geofenceId) }
        Log.d(TAG, "已清除围栏通知状态: $geofenceId")
    }

    /**
     * 清除所有围栏的通知状态记录
     */
    private fun clearAllNotificationStates() {
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
     * 保存上次已知状态
     */
    private fun saveLastKnownStates() {
        val json = JSONObject()
        lastKnownStates.forEach { (id, isInside) ->
            json.put(id, isInside)
        }
        statePrefs.edit { putString("last_known_states", json.toString()) }
    }

    /**
     * 加载上次已知状态
     */
    private fun loadLastKnownStates() {
        val jsonString = statePrefs.getString("last_known_states", null) ?: return
        try {
            val json = JSONObject(jsonString)
            json.keys().forEach { key ->
                lastKnownStates[key] = json.getBoolean(key)
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载上次已知状态失败", e)
        }
    }

    /**
     * 计算两点之间的距离（米）
     * 使用 Haversine 公式
     */
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * 释放资源
     */
    fun destroy() {
        Log.d(TAG, "围栏管理器已销毁")
    }
}
