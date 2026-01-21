package me.ikate.findmy.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.ikate.findmy.domain.location.SmartLocator
import me.ikate.findmy.service.LocationReportService
import me.ikate.findmy.service.SmartLocationConfig

/**
 * 智能位置上报 Worker
 *
 * 对标 iOS Find My 的智能上报策略：
 * - 综合评估活动状态、电量、WiFi环境、静止时长
 * - 动态调整上报频率和定位超时
 * - 支持 GPS 速度校准活动类型
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
        const val KEY_GPS_SPEED = "gps_speed"
    }

    override suspend fun doWork(): Result {
        val triggerReason = inputData.getString(KEY_TRIGGER_REASON) ?: "periodic"
        val activityTypeName = inputData.getString(KEY_ACTIVITY_TYPE)
        val forceReport = inputData.getBoolean(KEY_FORCE_REPORT, false)
        val gpsSpeed = inputData.getFloat(KEY_GPS_SPEED, -1f)

        Log.d(TAG, "智能位置上报开始 - 触发原因: $triggerReason, 活动类型: $activityTypeName, GPS速度: $gpsSpeed")

        // 如果智能模式未启用，直接上报
        if (!SmartLocationConfig.isSmartModeEnabled(applicationContext)) {
            Log.d(TAG, "智能模式未启用，执行普通上报")
            return reportLocation(20000L)
        }

        // 获取当前活动类型
        val activityType = determineActivityType(activityTypeName, gpsSpeed)

        // 获取电池电量
        val batteryLevel = SmartLocationConfig.getBatteryLevel(applicationContext)

        Log.d(TAG, "当前状态 - 活动: ${activityType.displayName}, 电量: $batteryLevel%")

        // 使用综合决策评估
        val decision = SmartLocationConfig.evaluateReportDecision(
            applicationContext,
            triggerReason,
            activityType
        )

        Log.d(TAG, "决策结果 - 应上报: ${decision.shouldReport}, 原因: ${decision.reason}, 建议间隔: ${decision.suggestedInterval}分钟")

        // 强制上报优先
        if (forceReport) {
            Log.d(TAG, "强制上报模式")
            return reportLocation(10000L)
        }

        // 根据决策判断是否上报
        if (!decision.shouldReport) {
            Log.d(TAG, "根据智能策略，本次跳过上报: ${decision.reason}")
            return Result.success()
        }

        // 检查最小上报间隔
        if (!SmartLocationConfig.canReportNow(applicationContext) && triggerReason == "periodic") {
            Log.d(TAG, "距离上次上报时间太短，跳过")
            return Result.success()
        }

        // 根据活动类型和电量选择定位超时
        val timeout = calculateTimeout(activityType, batteryLevel)

        return reportLocation(timeout)
    }

    /**
     * 确定当前活动类型
     * 优先使用传入的活动类型，其次使用 GPS 速度推断，最后使用保存的状态
     */
    private fun determineActivityType(
        activityTypeName: String?,
        gpsSpeed: Float
    ): SmartLocationConfig.ActivityType {
        // 1. 尝试使用传入的活动类型
        if (activityTypeName != null) {
            try {
                return SmartLocationConfig.ActivityType.valueOf(activityTypeName)
            } catch (e: Exception) {
                Log.w(TAG, "无效的活动类型名称: $activityTypeName")
            }
        }

        // 2. 尝试使用 GPS 速度推断
        if (gpsSpeed >= 0) {
            val inferredActivity = SmartLocationConfig.inferActivityFromSpeed(gpsSpeed)
            if (inferredActivity != SmartLocationConfig.ActivityType.UNKNOWN) {
                Log.d(TAG, "使用 GPS 速度推断活动类型: ${inferredActivity.displayName}")
                return inferredActivity
            }
        }

        // 3. 使用保存的活动状态
        return SmartLocationConfig.getLastActivity(applicationContext)
    }

    /**
     * 根据活动类型和电量计算定位超时
     */
    private fun calculateTimeout(
        activityType: SmartLocationConfig.ActivityType,
        batteryLevel: Int
    ): Long {
        val baseTimeout = when (activityType) {
            SmartLocationConfig.ActivityType.IN_VEHICLE,
            SmartLocationConfig.ActivityType.RUNNING,
            SmartLocationConfig.ActivityType.ON_BICYCLE -> {
                // 高速移动时使用较短超时，确保快速响应
                10000L
            }
            SmartLocationConfig.ActivityType.WALKING -> {
                // 步行时使用正常超时
                15000L
            }
            SmartLocationConfig.ActivityType.STILL -> {
                // 静止时可以使用较长超时，允许更精确定位
                25000L
            }
            SmartLocationConfig.ActivityType.UNKNOWN -> {
                20000L
            }
        }

        // 低电量时延长超时，降低定位精度以节省电量
        return if (batteryLevel <= SmartLocationConfig.LOW_BATTERY_THRESHOLD) {
            minOf(baseTimeout + 10000L, 35000L)
        } else {
            baseTimeout
        }
    }

    /**
     * 执行位置上报
     */
    private suspend fun reportLocation(timeout: Long): Result {
        val triggerReason = inputData.getString(KEY_TRIGGER_REASON) ?: "periodic"

        return try {
            val locationReportService = LocationReportService(applicationContext)
            val result = locationReportService.reportCurrentLocation(timeout)

            if (result.isSuccess) {
                val device = result.getOrNull()
                device?.location?.let { location ->
                    // 保存上报位置用于后续比较
                    SmartLocationConfig.saveLastLocation(
                        applicationContext,
                        location.latitude,
                        location.longitude
                    )

                    // 保存当前 WiFi BSSID
                    val currentBssid = SmartLocationConfig.getCurrentWifiBssid(applicationContext)
                    SmartLocationConfig.saveLastWifiBssid(applicationContext, currentBssid)

                    // 通知 SmartLocator 上报成功
                    try {
                        val smartLocator = SmartLocator.getInstance(applicationContext)
                        val reason = parseTriggerReason(triggerReason)
                        smartLocator.recordReportSuccess(location.latitude, location.longitude, reason)
                    } catch (e: Exception) {
                        Log.w(TAG, "通知 SmartLocator 失败", e)
                    }
                }

                // 如果设备提供了速度信息，保存用于活动推断
                device?.speed?.let { speed ->
                    if (speed >= 0) {
                        SmartLocationConfig.saveLastSpeed(applicationContext, speed)

                        // 使用 SmartLocator 进行 GPS 速度校准
                        try {
                            val smartLocator = SmartLocator.getInstance(applicationContext)
                            smartLocator.calibrateWithGpsSpeed(speed)
                        } catch (e: Exception) {
                            Log.w(TAG, "GPS 速度校准失败", e)
                        }

                        // 使用速度更新活动类型
                        val inferredActivity = SmartLocationConfig.inferActivityFromSpeed(speed)
                        if (inferredActivity != SmartLocationConfig.ActivityType.UNKNOWN) {
                            val currentActivity = SmartLocationConfig.getLastActivity(applicationContext)
                            // 仅在活动类型差异较大时更新
                            if (shouldUpdateActivity(currentActivity, inferredActivity)) {
                                SmartLocationConfig.saveLastActivity(applicationContext, inferredActivity)
                                updateStillState(inferredActivity)
                            }
                        }
                    }
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

    /**
     * 解析触发原因
     */
    private fun parseTriggerReason(reason: String): SmartLocator.TriggerReason {
        return try {
            SmartLocator.TriggerReason.valueOf(reason.uppercase())
        } catch (e: Exception) {
            SmartLocator.TriggerReason.PERIODIC
        }
    }

    /**
     * 判断是否应该更新活动类型
     * 避免频繁切换
     */
    private fun shouldUpdateActivity(
        current: SmartLocationConfig.ActivityType,
        inferred: SmartLocationConfig.ActivityType
    ): Boolean {
        // 相同类型不需要更新
        if (current == inferred) return false

        // 静止和步行之间的切换需要更明确的证据
        if ((current == SmartLocationConfig.ActivityType.STILL &&
             inferred == SmartLocationConfig.ActivityType.WALKING) ||
            (current == SmartLocationConfig.ActivityType.WALKING &&
             inferred == SmartLocationConfig.ActivityType.STILL)) {
            return true // GPS 速度判断较为可靠
        }

        // 其他情况都更新
        return true
    }

    /**
     * 更新静止状态追踪
     */
    private fun updateStillState(activityType: SmartLocationConfig.ActivityType) {
        if (activityType == SmartLocationConfig.ActivityType.STILL) {
            SmartLocationConfig.recordStillState(applicationContext)
        } else {
            SmartLocationConfig.resetStillState(applicationContext)
        }
    }
}
