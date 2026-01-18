package me.ikate.findmy.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

    companion object {
        private const val TAG = "LocationReportWorker"
    }

    override suspend fun doWork(): Result {
        android.util.Log.d(TAG, "🚀 Worker开始执行，线程: ${Thread.currentThread().name}")

        return try {
            // 检查是否为单次加急任务
            val isOneShot = inputData.getBoolean("isOneShot", false)
            val requesterUid = inputData.getString("requesterUid")

            // 根据模式选择定位超时
            val timeout = if (isOneShot) {
                android.util.Log.d(TAG, "执行加急单次定位任务，请求者: $requesterUid")
                sendDebugNotification("Worker执行中", "正在获取高精度位置...")
                10000L // 加急任务：10秒超时
            } else {
                android.util.Log.d(TAG, "执行定期位置上报")
                20000L // 定期任务：20秒超时
            }

            val startTime = System.currentTimeMillis()
            val locationReportService = LocationReportService(applicationContext)
            val result = locationReportService.reportCurrentLocation(timeout)
            val duration = System.currentTimeMillis() - startTime

            if (result.isSuccess) {
                val device = result.getOrNull()
                android.util.Log.d(
                    TAG,
                    "✅ 位置上报成功 (耗时: ${duration}ms, isOneShot=$isOneShot, 位置: ${device?.location})"
                )
                if (isOneShot) {
                    sendDebugNotification(
                        "位置上报成功",
                        "耗时: ${duration}ms\n位置已上报"
                    )
                }
                Result.success()
            } else {
                val error = result.exceptionOrNull()
                android.util.Log.e(TAG, "❌ 位置上报失败: $error")
                if (isOneShot) {
                    sendDebugNotification(
                        "位置上报失败",
                        "错误: ${error?.message ?: "未知错误"}"
                    )
                }
                // 加急任务失败不重试，定期任务可以重试
                if (isOneShot) Result.failure() else Result.retry()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Worker执行失败", e)
            sendDebugNotification("Worker异常", "错误: ${e.message}")
            Result.failure()
        }
    }

    /**
     * 🔍 调试工具：发送调试通知
     */
    private fun sendDebugNotification(title: String, message: String) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 创建调试通知渠道
        val debugChannelId = "debug_channel"
        val debugChannel = NotificationChannel(
            debugChannelId,
            "调试通知",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(debugChannel)

        val notification = NotificationCompat.Builder(applicationContext, debugChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔍 $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
