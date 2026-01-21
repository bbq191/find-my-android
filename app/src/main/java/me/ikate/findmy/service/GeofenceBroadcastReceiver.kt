package me.ikate.findmy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import me.ikate.findmy.domain.location.SmartLocator
import me.ikate.findmy.util.NotificationHelper

/**
 * 地理围栏广播接收器
 *
 * 接收 GeofenceManager 发送的围栏触发事件
 * 显示通知并触发位置上报
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"

        // 自身位置围栏ID（与 SmartLocator 保持一致）
        private const val SELF_GEOFENCE_ID = "self_location_fence"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "收到围栏广播: ${intent.action}")

        // 检查是否为围栏触发事件
        if (intent.action != GeofenceManager.ACTION_GEOFENCE_TRIGGERED) {
            Log.d(TAG, "非围栏触发事件，忽略")
            return
        }

        val fenceId = intent.getStringExtra("fence_id") ?: return
        val contactId = intent.getStringExtra("contact_id") ?: return
        val contactName = intent.getStringExtra("contact_name") ?: "联系人"
        val locationName = intent.getStringExtra("location_name") ?: "位置"
        val transitionType = intent.getIntExtra("transition_type", -1)

        Log.d(TAG, "围栏事件: fenceId=$fenceId, contactId=$contactId, transition=$transitionType")

        when (transitionType) {
            GeofenceManager.GEOFENCE_TRANSITION_ENTER -> {
                Log.d(TAG, "$contactName 进入围栏: $locationName")

                // 发送通知
                sendGeofenceNotification(
                    context = context,
                    contactName = contactName,
                    locationName = locationName,
                    isEntering = true
                )

                // 触发位置上报（自身围栏进入）
                if (fenceId == SELF_GEOFENCE_ID) {
                    triggerLocationReport(context, SmartLocator.TriggerReason.GEOFENCE_ENTER)
                }
            }
            GeofenceManager.GEOFENCE_TRANSITION_EXIT -> {
                Log.d(TAG, "$contactName 离开围栏: $locationName")

                // 发送通知
                sendGeofenceNotification(
                    context = context,
                    contactName = contactName,
                    locationName = locationName,
                    isEntering = false
                )

                // 触发位置上报（自身围栏离开 - 说明位置变化）
                if (fenceId == SELF_GEOFENCE_ID) {
                    triggerLocationReport(context, SmartLocator.TriggerReason.GEOFENCE_EXIT)
                }
            }
            else -> {
                Log.w(TAG, "未知的围栏事件类型: $transitionType")
            }
        }
    }

    /**
     * 触发位置上报
     */
    private fun triggerLocationReport(context: Context, reason: SmartLocator.TriggerReason) {
        try {
            val smartLocator = SmartLocator.getInstance(context)
            smartLocator.triggerLocationReport(reason)
            Log.d(TAG, "已触发位置上报: ${reason.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "触发位置上报失败", e)
        }
    }

    /**
     * 发送围栏通知
     */
    private fun sendGeofenceNotification(
        context: Context,
        contactName: String,
        locationName: String,
        isEntering: Boolean
    ) {
        val title = if (isEntering) "$contactName 进入围栏" else "$contactName 离开围栏"
        val message = if (isEntering) "$contactName 已进入 $locationName" else "$contactName 已离开 $locationName"

        NotificationHelper.showGeofenceNotification(
            context = context,
            title = title,
            message = message,
            isEntering = isEntering
        )

        Log.d(TAG, "地理围栏通知已发送: $contactName ${if (isEntering) "进入" else "离开"} $locationName")
    }
}
