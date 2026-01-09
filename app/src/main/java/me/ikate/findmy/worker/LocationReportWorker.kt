package me.ikate.findmy.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.Priority
import me.ikate.findmy.service.LocationReportService

/**
 * 位置上报 Worker
 * 支持两种模式：
 * 1. 定期上报模式（默认）：使用均衡省电的定位优先级
 * 2. 加急单次模式：收到 FCM 位置请求时，使用高精度定位立即上报
 */
class LocationReportWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // 检查是否为单次加急任务
            val isOneShot = inputData.getBoolean("isOneShot", false)
            val requesterUid = inputData.getString("requesterUid")

            // 根据模式选择定位优先级
            val priority = if (isOneShot) {
                android.util.Log.d(
                    "LocationReportWorker",
                    "执行加急单次定位任务，请求者: $requesterUid"
                )
                Priority.PRIORITY_HIGH_ACCURACY // 高精度，快速响应
            } else {
                android.util.Log.d("LocationReportWorker", "执行定期位置上报")
                Priority.PRIORITY_BALANCED_POWER_ACCURACY // 均衡省电
            }

            val locationReportService = LocationReportService(applicationContext)
            val result = locationReportService.reportCurrentLocation(priority)

            if (result.isSuccess) {
                android.util.Log.d("LocationReportWorker", "位置上报成功 (isOneShot=$isOneShot)")
                Result.success()
            } else {
                android.util.Log.e(
                    "LocationReportWorker",
                    "位置上报失败: ${result.exceptionOrNull()}"
                )
                // 加急任务失败不重试，定期任务可以重试
                if (isOneShot) Result.failure() else Result.retry()
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationReportWorker", "Worker执行失败", e)
            Result.failure()
        }
    }
}
