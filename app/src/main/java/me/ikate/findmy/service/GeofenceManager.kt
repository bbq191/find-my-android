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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject
import me.ikate.findmy.data.model.GeofenceType
import me.ikate.findmy.util.DistanceCalculator

/**
 * 地理围栏配置数据类 (iOS Find My 风格)
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
    val notifyOnExit: Boolean,
    // iOS Find My 新增字段
    val geofenceType: GeofenceType = GeofenceType.FIXED_LOCATION,
    val address: String = "",
    val wasInsideOnCreate: Boolean = false,  // 创建时联系人是否在围栏内（跳过首次触发）
    val ownerLatitude: Double? = null,  // 我的位置纬度（仅 LEFT_BEHIND 使用）
    val ownerLongitude: Double? = null, // 我的位置经度（仅 LEFT_BEHIND 使用）
    val isOneTime: Boolean = true       // 是否一次性触发
)

/**
 * 地理围栏管理器（单例）
 *
 * 使用基于位置的距离检测实现电子围栏功能
 * 注意：
 * - 输入坐标为 GCJ-02（腾讯坐标系）
 * - 通过定期检查联系人位置与围栏中心的距离来判断进入/离开
 * - 使用 applicationContext 防止 Activity 泄漏
 * - 必须使用 getInstance() 获取单例，确保 UI 和服务共享同一实例
 */
class GeofenceManager private constructor(context: Context) {

    companion object {
        private const val TAG = "GeofenceManager"
        private const val PREFS_NAME = "geofences"
        private const val KEY_GEOFENCES = "geofence_list"
        private const val NOTIFICATION_STATE_PREFS = "geofence_notification_state"
        private const val STATE_KEY_PREFIX = "last_state_"
        private const val READY_TO_TRIGGER_PREFIX = "ready_to_trigger_"  // 跳过首次后准备触发

        // 广播 Action
        const val ACTION_GEOFENCE_TRIGGERED = "me.ikate.findmy.GEOFENCE_TRIGGERED"

        // 围栏事件类型常量
        const val GEOFENCE_TRANSITION_ENTER = 1
        const val GEOFENCE_TRANSITION_EXIT = 2
        const val GEOFENCE_TRANSITION_DWELL = 4

        // 单例实例
        @Volatile
        private var instance: GeofenceManager? = null

        /**
         * 获取单例实例
         * 确保 UI 层和服务层使用同一个实例
         */
        fun getInstance(context: Context): GeofenceManager {
            return instance ?: synchronized(this) {
                instance ?: GeofenceManager(context.applicationContext).also {
                    instance = it
                    Log.d(TAG, "GeofenceManager 单例实例创建")
                }
            }
        }
    }

    // 使用 applicationContext 防止 Activity/Fragment 泄漏
    private val context: Context = context.applicationContext

    private val prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val statePrefs = this.context.getSharedPreferences(NOTIFICATION_STATE_PREFS, Context.MODE_PRIVATE)

    // 当前激活的围栏
    private val _activeGeofences = MutableStateFlow<List<GeofenceData>>(emptyList())
    val activeGeofences: StateFlow<List<GeofenceData>> = _activeGeofences.asStateFlow()

    // 上次检测到的围栏状态 (geofenceId -> isInside)
    // 使用 ConcurrentHashMap 保证多协程并发访问的线程安全
    private val lastKnownStates = ConcurrentHashMap<String, Boolean>()

    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        loadGeofences()
        loadLastKnownStates()
    }

    /**
     * 添加地理围栏 (iOS Find My 风格)
     *
     * @param contactId 联系人ID
     * @param contactName 联系人名称
     * @param locationName 位置名称
     * @param center GCJ-02 坐标（腾讯坐标系）
     * @param radiusMeters 围栏半径（米）
     * @param notifyOnEnter 进入时通知（到达）
     * @param notifyOnExit 离开时通知
     * @param geofenceType 围栏类型（FIXED_LOCATION / LEFT_BEHIND）
     * @param address 位置地址（逆地理编码）
     * @param wasInsideOnCreate 创建时联系人是否在围栏内
     * @param ownerLocation 我的位置（仅 LEFT_BEHIND 使用）
     * @param isOneTime 是否一次性触发
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
        geofenceType: GeofenceType = GeofenceType.FIXED_LOCATION,
        address: String = "",
        wasInsideOnCreate: Boolean = false,
        ownerLocation: LatLng? = null,
        isOneTime: Boolean = true,
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
            notifyOnExit = notifyOnExit,
            geofenceType = geofenceType,
            address = address,
            wasInsideOnCreate = wasInsideOnCreate,
            ownerLatitude = ownerLocation?.latitude,
            ownerLongitude = ownerLocation?.longitude,
            isOneTime = isOneTime
        )

        // 添加到列表
        val currentList = _activeGeofences.value.toMutableList()
        currentList.add(geofenceData)
        _activeGeofences.value = currentList
        saveGeofences()

        Log.d(TAG, "围栏创建成功: $geofenceId, 类型: $geofenceType, " +
                "位置: (${center.latitude}, ${center.longitude}), 半径: ${radiusMeters}m, " +
                "创建时在内: $wasInsideOnCreate, 一次性: $isOneTime")
        onSuccess()
    }

    /**
     * 检查联系人位置是否触发围栏事件 (iOS Find My 风格)
     * 应在联系人位置更新时调用
     *
     * @param contactId 联系人ID
     * @param contactLocation 联系人当前位置 (GCJ-02)
     * @param ownerLocation 我的当前位置 (GCJ-02)，用于 LEFT_BEHIND 类型
     */
    fun checkGeofenceForContact(
        contactId: String,
        contactLocation: LatLng,
        ownerLocation: LatLng? = null
    ) {
        val geofence = _activeGeofences.value.find { it.contactId == contactId } ?: return

        // 确定围栏中心：LEFT_BEHIND 使用我的实时位置，FIXED_LOCATION 使用配置的位置
        val centerLat: Double
        val centerLon: Double

        if (geofence.geofenceType == GeofenceType.LEFT_BEHIND) {
            // 离开我身边：使用我的实时位置
            if (ownerLocation != null) {
                centerLat = ownerLocation.latitude
                centerLon = ownerLocation.longitude
            } else if (geofence.ownerLatitude != null && geofence.ownerLongitude != null) {
                // 回退到创建时的我的位置
                centerLat = geofence.ownerLatitude
                centerLon = geofence.ownerLongitude
            } else {
                Log.w(TAG, "LEFT_BEHIND 围栏缺少我的位置，跳过检查")
                return
            }
        } else {
            // 固定位置围栏
            centerLat = geofence.latitude
            centerLon = geofence.longitude
        }

        val distance = DistanceCalculator.calculateDistance(
            centerLat, centerLon,
            contactLocation.latitude, contactLocation.longitude
        )

        val isInside = distance <= geofence.radiusMeters
        val wasInside = lastKnownStates[geofence.id]
        val isReadyToTrigger = isReadyToTrigger(geofence.id)

        Log.d(TAG, "检查围栏: ${geofence.locationName}, 类型: ${geofence.geofenceType}, " +
                "距离: ${distance.toInt()}m, 半径: ${geofence.radiusMeters.toInt()}m, " +
                "内部: $isInside, 之前: $wasInside, 创建时在内: ${geofence.wasInsideOnCreate}, 准备触发: $isReadyToTrigger")

        // 第一次检测，初始化状态
        if (wasInside == null) {
            lastKnownStates[geofence.id] = isInside
            saveLastKnownStates()

            // iOS Find My 风格：跳过首次触发
            // 如果创建时联系人在围栏内，且触发类型是"离开"
            // 则需要等联系人先进入再离开才触发（或当前已在内，等离开）
            if (geofence.wasInsideOnCreate && geofence.notifyOnExit && !geofence.notifyOnEnter) {
                Log.d(TAG, "跳过首次触发：联系人创建围栏时已在围栏内，等待状态变化")
                // 不标记为 ready，等待离开后再进入的完整周期
            }
            return
        }

        // 检测状态变化
        if (isInside != wasInside) {
            lastKnownStates[geofence.id] = isInside
            saveLastKnownStates()

            // iOS Find My 跳过首次触发逻辑
            if (geofence.wasInsideOnCreate && !isReadyToTrigger) {
                if (!isInside && geofence.notifyOnExit) {
                    // 联系人首次离开围栏，标记为准备触发
                    // 下次进入后的离开才触发通知
                    markAsReadyToTrigger(geofence.id)
                    Log.d(TAG, "首次离开，标记为准备触发状态: ${geofence.id}")
                    return
                }
                if (isInside && geofence.notifyOnEnter) {
                    // 联系人首次进入（创建时在外面），正常触发
                    // 但如果 wasInsideOnCreate 为 true，说明创建时在里面，不应触发
                    Log.d(TAG, "跳过首次进入触发: ${geofence.id}")
                    return
                }
            }

            if (isInside && geofence.notifyOnEnter) {
                // 进入围栏
                Log.d(TAG, "联系人 ${geofence.contactName} 进入围栏: ${geofence.locationName}")
                sendGeofenceEvent(geofence, GEOFENCE_TRANSITION_ENTER, distance)
            } else if (!isInside && geofence.notifyOnExit) {
                // 离开围栏
                if (geofence.geofenceType == GeofenceType.LEFT_BEHIND) {
                    Log.d(TAG, "联系人 ${geofence.contactName} 离开我身边: 距离 ${distance.toInt()}m")
                } else {
                    Log.d(TAG, "联系人 ${geofence.contactName} 离开围栏: ${geofence.locationName}")
                }
                sendGeofenceEvent(geofence, GEOFENCE_TRANSITION_EXIT, distance)
            }
        }
    }

    /**
     * 检查围栏是否已准备好触发（跳过首次后）
     */
    private fun isReadyToTrigger(geofenceId: String): Boolean {
        return statePrefs.getBoolean(READY_TO_TRIGGER_PREFIX + geofenceId, false)
    }

    /**
     * 标记围栏为准备触发状态
     */
    private fun markAsReadyToTrigger(geofenceId: String) {
        statePrefs.edit { putBoolean(READY_TO_TRIGGER_PREFIX + geofenceId, true) }
    }

    /**
     * 清除围栏的准备触发状态
     */
    private fun clearReadyToTrigger(geofenceId: String) {
        statePrefs.edit { remove(READY_TO_TRIGGER_PREFIX + geofenceId) }
    }

    /**
     * 发送围栏事件广播 (iOS Find My 风格)
     */
    private fun sendGeofenceEvent(geofence: GeofenceData, transitionType: Int, distance: Double = 0.0) {
        val intent = Intent(ACTION_GEOFENCE_TRIGGERED).apply {
            setPackage(context.packageName)
            putExtra("fence_id", geofence.id)
            putExtra("contact_id", geofence.contactId)
            putExtra("contact_name", geofence.contactName)
            putExtra("location_name", geofence.locationName)
            putExtra("transition_type", transitionType)
            // iOS Find My 新增字段
            putExtra("geofence_type", geofence.geofenceType.name)
            putExtra("address", geofence.address)
            putExtra("distance", distance)
            putExtra("is_one_time", geofence.isOneTime)
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
            clearReadyToTrigger(geofenceToRemove.id)  // 清除准备触发状态
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
     * 保存围栏配置到本地 (iOS Find My 风格)
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
                // iOS Find My 新增字段
                put("geofenceType", geofence.geofenceType.name)
                put("address", geofence.address)
                put("wasInsideOnCreate", geofence.wasInsideOnCreate)
                put("isOneTime", geofence.isOneTime)
                geofence.ownerLatitude?.let { put("ownerLatitude", it) }
                geofence.ownerLongitude?.let { put("ownerLongitude", it) }
            }
            jsonArray.put(json)
        }
        prefs.edit { putString(KEY_GEOFENCES, jsonArray.toString()) }
    }

    /**
     * 从本地加载围栏配置 (iOS Find My 风格)
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
                        notifyOnExit = json.getBoolean("notifyOnExit"),
                        // iOS Find My 新增字段（兼容旧数据）
                        geofenceType = try {
                            GeofenceType.valueOf(json.optString("geofenceType", "FIXED_LOCATION"))
                        } catch (e: Exception) {
                            GeofenceType.FIXED_LOCATION
                        },
                        address = json.optString("address", ""),
                        wasInsideOnCreate = json.optBoolean("wasInsideOnCreate", false),
                        isOneTime = json.optBoolean("isOneTime", true),
                        ownerLatitude = if (json.has("ownerLatitude")) json.getDouble("ownerLatitude") else null,
                        ownerLongitude = if (json.has("ownerLongitude")) json.getDouble("ownerLongitude") else null
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
     * 释放资源
     * 注意：必须取消 CoroutineScope，防止内存泄漏
     */
    fun destroy() {
        scope.cancel()
        Log.d(TAG, "围栏管理器已销毁")
    }
}
