package me.ikate.findmy.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.ikate.findmy.service.LocationReportService

/**
 * 位置上报 Worker
 * 定期执行位置上报任务
 */
class LocationReportWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val locationReportService = LocationReportService(applicationContext)
            val result = locationReportService.reportCurrentLocation()

            if (result.isSuccess) {
                android.util.Log.d("LocationReportWorker", "位置上报成功")
                Result.success()
            } else {
                android.util.Log.e("LocationReportWorker", "位置上报失败: ${result.exceptionOrNull()}")
                Result.retry()
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationReportWorker", "Worker执行失败", e)
            Result.failure()
        }
    }
}
