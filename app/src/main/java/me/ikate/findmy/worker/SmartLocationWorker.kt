package me.ikate.findmy.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.Priority
import me.ikate.findmy.service.LocationReportService
import me.ikate.findmy.service.SmartLocationConfig

/**
 * 智能位置上报 Worker
 * 根据用户活动状态、电量、位置变化等因素智能决定是否上报位置
 */
class SmartLocationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "SmartLocationWorker"

        // Input data keys
        const val KEY_TRIGGER_REASON = "trigger_reason"
        const val KEY_ACTIVITY_TYPE = "activity_type"
        const val KEY_FORCE_REPORT = "force_report"
    }

    override suspend fun doWork(): Result {
        val triggerReason = inputData.getString(KEY_TRIGGER_REASON) ?: "periodic"
        val activityTypeName = inputData.getString(KEY_ACTIVITY_TYPE)
        val forceReport = inputData.getBoolean(KEY_FORCE_REPORT, false)

        Log.d(TAG, "智能位置上报开始 - 触发原因: $triggerReason, 活动类型: $activityTypeName")

        // 如果智能模式未启用，直接上报
        if (!SmartLocationConfig.isSmartModeEnabled(applicationContext)) {
            Log.d(TAG, "智能模式未启用，执行普通上报")
            return reportLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        }

        // 获取当前活动类型
        val activityType = if (activityTypeName != null) {
            try {
                SmartLocationConfig.ActivityType.valueOf(activityTypeName)
            } catch (e: Exception) {
                SmartLocationConfig.getLastActivity(applicationContext)
            }
        } else {
            SmartLocationConfig.getLastActivity(applicationContext)
        }

        // 获取电池电量
        val batteryLevel = SmartLocationConfig.getBatteryLevel(applicationContext)

        Log.d(TAG, "当前状态 - 活动: ${activityType.displayName}, 电量: $batteryLevel%")

        // 决定是否上报
        val shouldReport = forceReport || shouldReportBasedOnContext(triggerReason, activityType, batteryLevel)

        if (!shouldReport) {
            Log.d(TAG, "根据智能策略，本次跳过上报")
            return Result.success()
        }

        // 根据活动类型选择定位优先级
        val priority = when (activityType) {
            SmartLocationConfig.ActivityType.IN_VEHICLE,
            SmartLocationConfig.ActivityType.RUNNING,
            SmartLocationConfig.ActivityType.ON_BICYCLE -> {
                // 高速移动时使用高精度定位
                Priority.PRIORITY_HIGH_ACCURACY
            }
            SmartLocationConfig.ActivityType.WALKING -> {
                // 步行时使用均衡模式
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }
            else -> {
                // 静止或未知时使用低功耗模式
                if (batteryLevel <= SmartLocationConfig.LOW_BATTERY_THRESHOLD) {
                    Priority.PRIORITY_LOW_POWER
                } else {
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY
                }
            }
        }

        return reportLocation(priority)
    }

    /**
     * 根据上下文判断是否应该上报
     */
    private fun shouldReportBasedOnContext(
        triggerReason: String,
        activityType: SmartLocationConfig.ActivityType,
        batteryLevel: Int
    ): Boolean {
        // 检查最小上报间隔
        if (!SmartLocationConfig.canReportNow(applicationContext)) {
            Log.d(TAG, "距离上次上报时间太短，跳过")
            return false
        }

        // 低电量 + 静止状态 -> 降低上报频率
        if (batteryLevel <= SmartLocationConfig.LOW_BATTERY_THRESHOLD &&
            activityType == SmartLocationConfig.ActivityType.STILL) {
            Log.d(TAG, "低电量且静止状态，跳过本次上报")
            return false
        }

        // 活动变化触发时，总是上报
        if (triggerReason == "activity_change") {
            return true
        }

        // 显著位置变化触发时，总是上报
        if (triggerReason == "significant_change") {
            return true
        }

        // 定期上报时，检查活动状态
        if (triggerReason == "periodic") {
            // 静止状态且非首次上报，可能跳过
            if (activityType == SmartLocationConfig.ActivityType.STILL && batteryLevel < 50) {
                // 50%以下电量时，静止状态有50%概率跳过
                if (Math.random() < 0.5) {
                    Log.d(TAG, "静止状态且电量较低，随机跳过")
                    return false
                }
            }
        }

        return true
    }

    /**
     * 执行位置上报
     */
    private suspend fun reportLocation(priority: Int): Result {
        return try {
            val locationReportService = LocationReportService(applicationContext)
            val result = locationReportService.reportCurrentLocation(priority)

            if (result.isSuccess) {
                val device = result.getOrNull()
                device?.location?.let { location ->
                    // 保存上报位置用于后续比较
                    SmartLocationConfig.saveLastLocation(
                        applicationContext,
                        location.latitude,
                        location.longitude
                    )
                }
                Log.d(TAG, "智能位置上报成功: ${device?.location}")
                Result.success()
            } else {
                Log.e(TAG, "智能位置上报失败: ${result.exceptionOrNull()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "智能位置上报异常", e)
            Result.failure()
        }
    }
}
