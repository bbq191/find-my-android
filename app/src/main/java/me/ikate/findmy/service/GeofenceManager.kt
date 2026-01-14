package me.ikate.findmy.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val notifyOnEnter: Boolean,
    val notifyOnExit: Boolean
)

/**
 * 地理围栏管理器
 * 负责添加、移除和管理地理围栏
 */
class GeofenceManager(private val context: Context) {

    companion object {
        private const val TAG = "GeofenceManager"
        private const val PREFS_NAME = "geofences"
        private const val KEY_GEOFENCES = "geofence_list"
        private const val GEOFENCE_EXPIRATION_MS = 24 * 60 * 60 * 1000L // 24小时
    }

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 当前活跃的地理围栏
    private val _activeGeofences = MutableStateFlow<List<GeofenceData>>(emptyList())
    val activeGeofences: StateFlow<List<GeofenceData>> = _activeGeofences.asStateFlow()

    init {
        loadGeofences()
    }

    /**
     * 添加地理围栏
     */
    fun addGeofence(
        contactId: String,
        contactName: String,
        locationName: String,
        center: LatLng,
        radiusMeters: Float,
        notifyOnEnter: Boolean,
        notifyOnExit: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        // 检查权限
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onResult(false, "缺少位置权限")
            return
        }

        val geofenceId = "geofence_${contactId}_${System.currentTimeMillis()}"

        // 构建转换类型
        var transitionTypes = 0
        if (notifyOnEnter) transitionTypes = transitionTypes or Geofence.GEOFENCE_TRANSITION_ENTER
        if (notifyOnExit) transitionTypes = transitionTypes or Geofence.GEOFENCE_TRANSITION_EXIT

        if (transitionTypes == 0) {
            onResult(false, "至少选择一种通知类型")
            return
        }

        val geofence = Geofence.Builder()
            .setRequestId(geofenceId)
            .setCircularRegion(center.latitude, center.longitude, radiusMeters)
            .setExpirationDuration(GEOFENCE_EXPIRATION_MS)
            .setTransitionTypes(transitionTypes)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(geofencingRequest, getGeofencePendingIntent())
            .addOnSuccessListener {
                Log.d(TAG, "地理围栏添加成功: $geofenceId")

                // 保存到本地
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
                saveGeofence(geofenceData)

                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "地理围栏添加失败", e)
                onResult(false, e.localizedMessage)
            }
    }

    /**
     * 移除联系人的所有地理围栏
     */
    fun removeGeofencesForContact(contactId: String, onResult: (Boolean, String?) -> Unit) {
        val geofencesToRemove = _activeGeofences.value.filter { it.contactId == contactId }

        if (geofencesToRemove.isEmpty()) {
            onResult(true, null)
            return
        }

        val ids = geofencesToRemove.map { it.id }

        geofencingClient.removeGeofences(ids)
            .addOnSuccessListener {
                Log.d(TAG, "地理围栏移除成功: $ids")
                removeGeofencesFromStorage(ids)
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "地理围栏移除失败", e)
                onResult(false, e.localizedMessage)
            }
    }

    /**
     * 移除所有地理围栏
     */
    fun removeAllGeofences(onResult: (Boolean, String?) -> Unit) {
        geofencingClient.removeGeofences(getGeofencePendingIntent())
            .addOnSuccessListener {
                Log.d(TAG, "所有地理围栏已移除")
                clearAllGeofencesFromStorage()
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "移除所有地理围栏失败", e)
                onResult(false, e.localizedMessage)
            }
    }

    /**
     * 获取联系人的地理围栏
     */
    fun getGeofenceForContact(contactId: String): GeofenceData? {
        return _activeGeofences.value.find { it.contactId == contactId }
    }

    /**
     * 检查联系人是否有地理围栏
     */
    fun hasGeofenceForContact(contactId: String): Boolean {
        return _activeGeofences.value.any { it.contactId == contactId }
    }

    private fun getGeofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun saveGeofence(geofenceData: GeofenceData) {
        val currentList = _activeGeofences.value.toMutableList()
        // 移除同一联系人的旧围栏
        currentList.removeAll { it.contactId == geofenceData.contactId }
        currentList.add(geofenceData)
        _activeGeofences.value = currentList
        persistGeofences(currentList)
    }

    private fun removeGeofencesFromStorage(ids: List<String>) {
        val currentList = _activeGeofences.value.toMutableList()
        currentList.removeAll { it.id in ids }
        _activeGeofences.value = currentList
        persistGeofences(currentList)
    }

    private fun clearAllGeofencesFromStorage() {
        _activeGeofences.value = emptyList()
        prefs.edit { remove(KEY_GEOFENCES) }
    }

    private fun persistGeofences(geofences: List<GeofenceData>) {
        val jsonArray = JSONArray()
        geofences.forEach { geofence ->
            val json = JSONObject().apply {
                put("id", geofence.id)
                put("contactId", geofence.contactId)
                put("contactName", geofence.contactName)
                put("locationName", geofence.locationName)
                put("latitude", geofence.latitude)
                put("longitude", geofence.longitude)
                put("radiusMeters", geofence.radiusMeters)
                put("notifyOnEnter", geofence.notifyOnEnter)
                put("notifyOnExit", geofence.notifyOnExit)
            }
            jsonArray.put(json)
        }
        prefs.edit { putString(KEY_GEOFENCES, jsonArray.toString()) }
    }

    private fun loadGeofences() {
        val jsonString = prefs.getString(KEY_GEOFENCES, null) ?: return
        try {
            val jsonArray = JSONArray(jsonString)
            val geofences = mutableListOf<GeofenceData>()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                geofences.add(
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
            _activeGeofences.value = geofences
        } catch (e: Exception) {
            Log.e(TAG, "加载地理围栏失败", e)
        }
    }
}
