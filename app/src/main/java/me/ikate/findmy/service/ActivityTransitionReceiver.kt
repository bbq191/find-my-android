package me.ikate.findmy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.ikate.findmy.worker.SmartLocationWorker

/**
 * 活动转换广播接收器
 *
 * 处理来自 ActivityRecognitionManager 的活动状态变化事件
 * 支持基于传感器的活动识别（不依赖 Google Play Services）
 *
 * 触发场景：
 * 1. 传感器检测到活动状态变化
 * 2. GPS 速度推断的活动变化
 * 3. WiFi 环境变化暗示的位置移动
 */
class ActivityTransitionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ActivityTransition"

        // 广播 Action
        const val ACTION_ACTIVITY_TRANSITION = "me.ikate.findmy.ACTIVITY_TRANSITION"

        // 活动转换类型常量
        const val ACTIVITY_TRANSITION_ENTER = 0
        const val ACTIVITY_TRANSITION_EXIT = 1

        // Intent extras
        const val EXTRA_ACTIVITY_TYPE = "activity_type"
        const val EXTRA_TRANSITION_TYPE = "transition_type"
        const val EXTRA_GPS_SPEED = "gps_speed"
        const val EXTRA_TRIGGER_SOURCE = "trigger_source"

        // 触发来源
        const val SOURCE_SENSOR = "sensor"
        const val SOURCE_GPS_SPEED = "gps_speed"
        const val SOURCE_WIFI_CHANGE = "wifi_change"

        /**
         * 发送活动转换广播
         */
        fun sendActivityTransition(
            context: Context,
            activityType: SmartLocationConfig.ActivityType,
            transitionType: Int,
            source: String = SOURCE_SENSOR,
            gpsSpeed: Float = -1f
        ) {
            val intent = Intent(ACTION_ACTIVITY_TRANSITION).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_ACTIVITY_TYPE, activityType.name)
                putExtra(EXTRA_TRANSITION_TYPE, transitionType)
                putExtra(EXTRA_TRIGGER_SOURCE, source)
                if (gpsSpeed >= 0) {
                    putExtra(EXTRA_GPS_SPEED, gpsSpeed)
                }
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ACTIVITY_TRANSITION) {
            Log.d(TAG, "收到非活动转换广播，已忽略")
            return
        }

        val activityTypeName = intent.getStringExtra(EXTRA_ACTIVITY_TYPE)
        val transitionType = intent.getIntExtra(EXTRA_TRANSITION_TYPE, -1)
        val triggerSource = intent.getStringExtra(EXTRA_TRIGGER_SOURCE) ?: SOURCE_SENSOR
        val gpsSpeed = intent.getFloatExtra(EXTRA_GPS_SPEED, -1f)

        if (activityTypeName == null || transitionType < 0) {
            Log.d(TAG, "收到格式不完整的活动转换事件，已忽略")
            return
        }

        val activityType = try {
            SmartLocationConfig.ActivityType.valueOf(activityTypeName)
        } catch (e: Exception) {
            SmartLocationConfig.ActivityType.UNKNOWN
        }

        Log.d(
            TAG,
            "活动转换: ${activityType.displayName}, " +
                    "转换类型: ${getTransitionName(transitionType)}, " +
                    "来源: $triggerSource" +
                    if (gpsSpeed >= 0) ", GPS速度: ${gpsSpeed}m/s" else ""
        )

        // 只在进入新活动状态时处理
        if (transitionType == ACTIVITY_TRANSITION_ENTER) {
            handleActivityEnter(context, activityType, triggerSource, gpsSpeed)
        } else if (transitionType == ACTIVITY_TRANSITION_EXIT) {
            handleActivityExit(context, activityType)
        }
    }

    /**
     * 处理进入新活动状态
     */
    private fun handleActivityEnter(
        context: Context,
        activityType: SmartLocationConfig.ActivityType,
        triggerSource: String,
        gpsSpeed: Float
    ) {
        val previousActivity = SmartLocationConfig.getLastActivity(context)

        // 保存当前活动状态
        SmartLocationConfig.saveLastActivity(context, activityType)

        // 更新静止状态追踪
        updateStationaryTracking(context, activityType)

        // 检查是否需要触发位置上报
        val shouldTriggerReport = shouldTriggerLocationReport(
            context,
            previousActivity,
            activityType,
            triggerSource
        )

        if (shouldTriggerReport) {
            triggerSmartLocationCheck(context, activityType, gpsSpeed)
        }
    }

    /**
     * 处理退出活动状态
     */
    private fun handleActivityExit(
        context: Context,
        activityType: SmartLocationConfig.ActivityType
    ) {
        // 退出静止状态时，重置静止计数
        if (activityType == SmartLocationConfig.ActivityType.STILL) {
            SmartLocationConfig.resetStillState(context)
        }
    }

    /**
     * 更新静止状态追踪
     */
    private fun updateStationaryTracking(
        context: Context,
        activityType: SmartLocationConfig.ActivityType
    ) {
        if (activityType == SmartLocationConfig.ActivityType.STILL) {
            SmartLocationConfig.recordStillState(context)
        } else {
            SmartLocationConfig.resetStillState(context)
        }
    }

    /**
     * 判断是否应该触发位置上报
     */
    private fun shouldTriggerLocationReport(
        context: Context,
        previousActivity: SmartLocationConfig.ActivityType,
        currentActivity: SmartLocationConfig.ActivityType,
        triggerSource: String
    ): Boolean {
        // 检查最小上报间隔
        if (!SmartLocationConfig.canReportNow(context)) {
            Log.d(TAG, "距离上次上报时间太短，跳过触发")
            return false
        }

        // WiFi 环境变化触发
        if (triggerSource == SOURCE_WIFI_CHANGE) {
            Log.d(TAG, "WiFi 环境变化，触发上报检查")
            return true
        }

        // 静止状态不主动触发
        if (currentActivity == SmartLocationConfig.ActivityType.STILL) {
            return false
        }

        // 从静止转为移动状态，触发上报
        if (previousActivity == SmartLocationConfig.ActivityType.STILL &&
            currentActivity != SmartLocationConfig.ActivityType.STILL &&
            currentActivity != SmartLocationConfig.ActivityType.UNKNOWN
        ) {
            Log.d(TAG, "从静止转为移动(${currentActivity.displayName})，触发上报")
            return true
        }

        // 活动类型显著变化（例如从步行变为驾车）
        if (isSignificantActivityChange(previousActivity, currentActivity)) {
            Log.d(TAG, "活动类型显著变化(${previousActivity.displayName} -> ${currentActivity.displayName})，触发上报")
            return true
        }

        return false
    }

    /**
     * 判断是否为显著的活动类型变化
     */
    private fun isSignificantActivityChange(
        previous: SmartLocationConfig.ActivityType,
        current: SmartLocationConfig.ActivityType
    ): Boolean {
        // 定义活动类型的"速度等级"
        fun activitySpeedLevel(type: SmartLocationConfig.ActivityType): Int {
            return when (type) {
                SmartLocationConfig.ActivityType.STILL -> 0
                SmartLocationConfig.ActivityType.WALKING -> 1
                SmartLocationConfig.ActivityType.RUNNING -> 2
                SmartLocationConfig.ActivityType.ON_BICYCLE -> 3
                SmartLocationConfig.ActivityType.IN_VEHICLE -> 4
                SmartLocationConfig.ActivityType.UNKNOWN -> -1
            }
        }

        val previousLevel = activitySpeedLevel(previous)
        val currentLevel = activitySpeedLevel(current)

        // 等级差超过1视为显著变化
        return kotlin.math.abs(previousLevel - currentLevel) > 1
    }

    /**
     * 触发智能位置检查
     */
    private fun triggerSmartLocationCheck(
        context: Context,
        activityType: SmartLocationConfig.ActivityType,
        gpsSpeed: Float
    ) {
        Log.d(TAG, "触发智能位置上报: ${activityType.displayName}")

        val inputDataBuilder = workDataOf(
            SmartLocationWorker.KEY_TRIGGER_REASON to "activity_change",
            SmartLocationWorker.KEY_ACTIVITY_TYPE to activityType.name
        )

        val inputData = if (gpsSpeed >= 0) {
            workDataOf(
                SmartLocationWorker.KEY_TRIGGER_REASON to "activity_change",
                SmartLocationWorker.KEY_ACTIVITY_TYPE to activityType.name,
                SmartLocationWorker.KEY_GPS_SPEED to gpsSpeed
            )
        } else {
            inputDataBuilder
        }

        val workRequest = OneTimeWorkRequestBuilder<SmartLocationWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "smart_location_activity_trigger",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun getTransitionName(transitionType: Int): String {
        return when (transitionType) {
            ACTIVITY_TRANSITION_ENTER -> "进入"
            ACTIVITY_TRANSITION_EXIT -> "退出"
            else -> "未知"
        }
    }
}
