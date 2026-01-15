package me.ikate.findmy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import me.ikate.findmy.worker.SmartLocationWorker

/**
 * 活动转换广播接收器
 * 接收用户活动状态变化（静止、步行、跑步、驾车等）并触发智能位置上报
 */
class ActivityTransitionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ActivityTransition"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent) ?: return

            for (event in result.transitionEvents) {
                val activityType = SmartLocationConfig.fromDetectedActivity(event.activityType)
                val transitionType = event.transitionType

                Log.d(TAG, "活动转换: ${activityType.displayName}, 转换类型: ${getTransitionName(transitionType)}")

                // 只在进入新活动状态时处理
                if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                    // 保存当前活动状态
                    SmartLocationConfig.saveLastActivity(context, activityType)

                    // 触发智能位置上报检查
                    triggerSmartLocationCheck(context, activityType)
                }
            }
        }
    }

    /**
     * 触发智能位置检查
     */
    private fun triggerSmartLocationCheck(
        context: Context,
        activityType: SmartLocationConfig.ActivityType
    ) {
        // 如果是从静止转为移动状态，立即上报一次位置
        val shouldImmediateReport = activityType != SmartLocationConfig.ActivityType.STILL &&
                activityType != SmartLocationConfig.ActivityType.UNKNOWN

        if (shouldImmediateReport && SmartLocationConfig.canReportNow(context)) {
            Log.d(TAG, "活动状态变为: ${activityType.displayName}，触发位置上报")

            val workRequest = OneTimeWorkRequestBuilder<SmartLocationWorker>()
                .setInputData(
                    workDataOf(
                        SmartLocationWorker.KEY_TRIGGER_REASON to "activity_change",
                        SmartLocationWorker.KEY_ACTIVITY_TYPE to activityType.name
                    )
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "smart_location_activity_trigger",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    private fun getTransitionName(transitionType: Int): String {
        return when (transitionType) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "进入"
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "退出"
            else -> "未知"
        }
    }
}
