package me.ikate.findmy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.amap.api.fence.GeoFence
import com.amap.api.fence.GeoFenceClient
import me.ikate.findmy.util.NotificationHelper
import org.json.JSONArray

/**
 * 地理围栏广播接收器
 *
 * 接收高德 GeoFence SDK 的围栏触发事件
 * 使用状态记录确保进出事件只通知一次
 *
 * 注意：此类直接从 SharedPreferences 读取围栏配置，
 * 不创建 GeofenceManager 实例，避免重复注册围栏导致循环触发
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"
        private const val PREFS_NAME = "geofence_notification_state"
        private const val KEY_PREFIX = "last_state_"

        // 围栏配置存储（与 GeofenceManager 共享）
        private const val GEOFENCE_PREFS_NAME = "geofences"
        private const val KEY_GEOFENCES = "geofence_list"

        // 围栏事件类型常量（与高德 SDK 保持一致）
        const val GEOFENCE_TRANSITION_ENTER = GeoFenceClient.GEOFENCE_IN
        const val GEOFENCE_TRANSITION_EXIT = GeoFenceClient.GEOFENCE_OUT
        const val GEOFENCE_TRANSITION_DWELL = GeoFenceClient.GEOFENCE_STAYED

        // 通知状态常量
        private const val STATE_UNKNOWN = -1
        private const val STATE_INSIDE = 1
        private const val STATE_OUTSIDE = 0
    }

    /**
     * 围栏配置数据（轻量级，仅用于通知）
     */
    private data class GeofenceConfig(
        val id: String,
        val contactId: String,
        val contactName: String,
        val locationName: String,
        val notifyOnEnter: Boolean,
        val notifyOnExit: Boolean
    )

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "收到围栏广播: ${intent.action}")

        // 检查是否为围栏触发事件
        if (intent.action != GeofenceManager.ACTION_GEOFENCE_TRIGGERED) {
            Log.d(TAG, "非围栏触发事件，忽略")
            return
        }

        // 从 Bundle 中获取围栏信息
        val bundle = intent.extras
        if (bundle == null) {
            Log.w(TAG, "Bundle 为空")
            return
        }

        // 获取围栏状态
        val status = bundle.getInt(GeoFence.BUNDLE_KEY_FENCESTATUS, -1)
        val customId = bundle.getString(GeoFence.BUNDLE_KEY_CUSTOMID)
        val fenceId = bundle.getString(GeoFence.BUNDLE_KEY_FENCEID)

        Log.d(TAG, "围栏事件: status=$status, customId=$customId, fenceId=$fenceId")

        if (customId == null) {
            Log.w(TAG, "customId 为空，无法识别围栏")
            return
        }

        // 直接从 SharedPreferences 读取围栏配置（不创建 GeofenceManager 避免循环）
        val geofenceConfig = getGeofenceConfigById(context, customId)

        if (geofenceConfig == null) {
            Log.w(TAG, "未找到围栏配置: $customId")
            return
        }

        // 获取上次通知状态
        val lastState = getLastNotificationState(context, customId)
        val newState = when (status) {
            GeoFenceClient.GEOFENCE_IN -> STATE_INSIDE
            GeoFenceClient.GEOFENCE_OUT -> STATE_OUTSIDE
            else -> STATE_UNKNOWN
        }

        // 根据状态和配置决定是否发送通知
        when (status) {
            GeoFenceClient.GEOFENCE_IN -> {
                Log.d(TAG, "${geofenceConfig.contactName} 进入了 ${geofenceConfig.locationName}, 上次状态: $lastState")
                // 只有状态变化时才通知（从外部进入，或首次检测）
                if (geofenceConfig.notifyOnEnter && lastState != STATE_INSIDE) {
                    sendGeofenceNotification(
                        context = context,
                        contactName = geofenceConfig.contactName,
                        locationName = geofenceConfig.locationName,
                        isEntering = true
                    )
                    saveNotificationState(context, customId, STATE_INSIDE)
                } else if (lastState == STATE_INSIDE) {
                    Log.d(TAG, "已经在围栏内，跳过重复通知")
                }
            }
            GeoFenceClient.GEOFENCE_OUT -> {
                Log.d(TAG, "${geofenceConfig.contactName} 离开了 ${geofenceConfig.locationName}, 上次状态: $lastState")
                // 只有状态变化时才通知（从内部离开，或首次检测）
                if (geofenceConfig.notifyOnExit && lastState != STATE_OUTSIDE) {
                    sendGeofenceNotification(
                        context = context,
                        contactName = geofenceConfig.contactName,
                        locationName = geofenceConfig.locationName,
                        isEntering = false
                    )
                    saveNotificationState(context, customId, STATE_OUTSIDE)
                } else if (lastState == STATE_OUTSIDE) {
                    Log.d(TAG, "已经在围栏外，跳过重复通知")
                }
            }
            GeoFenceClient.GEOFENCE_STAYED -> {
                Log.d(TAG, "${geofenceConfig.contactName} 停留在 ${geofenceConfig.locationName}")
                // 停留事件不发通知，但更新状态为在内部
                if (lastState != STATE_INSIDE) {
                    saveNotificationState(context, customId, STATE_INSIDE)
                }
            }
            else -> {
                Log.w(TAG, "未知的围栏状态: $status")
            }
        }
    }

    /**
     * 从 SharedPreferences 直接读取围栏配置
     * 避免创建 GeofenceManager 导致围栏重新注册
     */
    private fun getGeofenceConfigById(context: Context, geofenceId: String): GeofenceConfig? {
        val prefs = context.getSharedPreferences(GEOFENCE_PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_GEOFENCES, null) ?: return null

        return try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                if (json.getString("id") == geofenceId) {
                    return GeofenceConfig(
                        id = json.getString("id"),
                        contactId = json.getString("contactId"),
                        contactName = json.getString("contactName"),
                        locationName = json.getString("locationName"),
                        notifyOnEnter = json.getBoolean("notifyOnEnter"),
                        notifyOnExit = json.getBoolean("notifyOnExit")
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "读取围栏配置失败", e)
            null
        }
    }

    /**
     * 获取上次通知状态
     */
    private fun getLastNotificationState(context: Context, geofenceId: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_PREFIX + geofenceId, STATE_UNKNOWN)
    }

    /**
     * 保存通知状态
     */
    private fun saveNotificationState(context: Context, geofenceId: String, state: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putInt(KEY_PREFIX + geofenceId, state) }
        Log.d(TAG, "保存围栏状态: $geofenceId -> $state")
    }

    private fun sendGeofenceNotification(
        context: Context,
        contactName: String,
        locationName: String,
        isEntering: Boolean
    ) {
        val title = if (isEntering) {
            "$contactName 到达了 $locationName"
        } else {
            "$contactName 离开了 $locationName"
        }

        val message = if (isEntering) {
            "$contactName 刚刚进入 $locationName 区域"
        } else {
            "$contactName 刚刚离开 $locationName 区域"
        }

        NotificationHelper.showGeofenceNotification(
            context = context,
            title = title,
            message = message,
            isEntering = isEntering
        )

        Log.d(TAG, "地理围栏通知已发送: $title")
    }
}
