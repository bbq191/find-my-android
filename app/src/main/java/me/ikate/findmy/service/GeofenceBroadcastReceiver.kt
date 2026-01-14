package me.ikate.findmy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import me.ikate.findmy.util.NotificationHelper

/**
 * 地理围栏广播接收器
 * 处理地理围栏进入/离开事件
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent 为空")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e(TAG, "地理围栏错误: $errorMessage")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT
        ) {
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            if (triggeringGeofences.isNullOrEmpty()) {
                Log.w(TAG, "没有触发的地理围栏")
                return
            }

            // 获取地理围栏管理器来查找详细信息
            val geofenceManager = GeofenceManager(context)

            for (geofence in triggeringGeofences) {
                val geofenceId = geofence.requestId
                Log.d(TAG, "地理围栏触发: $geofenceId, 类型: $geofenceTransition")

                // 从存储中查找围栏详情
                val geofenceData = geofenceManager.activeGeofences.value.find { it.id == geofenceId }

                if (geofenceData != null) {
                    sendGeofenceNotification(
                        context = context,
                        contactName = geofenceData.contactName,
                        locationName = geofenceData.locationName,
                        isEntering = geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER
                    )
                } else {
                    // 找不到详情，使用通用通知
                    sendGeofenceNotification(
                        context = context,
                        contactName = "联系人",
                        locationName = "指定区域",
                        isEntering = geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER
                    )
                }
            }
        } else {
            Log.w(TAG, "未知的地理围栏转换类型: $geofenceTransition")
        }
    }

    private fun sendGeofenceNotification(
        context: Context,
        contactName: String,
        locationName: String,
        isEntering: Boolean
    ) {
        val title = if (isEntering) {
            "$contactName 进入了 $locationName"
        } else {
            "$contactName 离开了 $locationName"
        }

        val message = if (isEntering) {
            "$contactName 刚刚到达 $locationName 附近"
        } else {
            "$contactName 刚刚离开了 $locationName"
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
