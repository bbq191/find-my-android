package me.ikate.findmy.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import me.ikate.findmy.service.LocationReportService

/**
 * 短时实时模式 Worker (60秒高频位置上报)
 *
 * 工作原理：
 * 1. 当用户A持续查看用户B时触发
 * 2. 在60秒内每隔7秒上报一次高精度位置（共约8次）
 * 3. 60秒超时后自动停止，无需手动干预
 * 4. 使用高精度定位 (HIGH_ACCURACY) 确保位置准确
 *
 * 电量消耗：
 * - 60秒内最多8次GPS定位
 * - 自动停止，不会持续耗电
 * - 比完整的实时模式（前台服务）更省电
 */
class ContinuousLocationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ContinuousLocationWorker"
        private const val CHANNEL_ID = "continuous_tracking_channel"
        private const val NOTIFICATION_ID = 1001

        // 配置参数
        private const val TRACKING_DURATION_MS = 60_000L // 60秒
        private const val UPDATE_INTERVAL_MS = 7_000L // 7秒间隔（60/7≈8次更新）
    }

    private val locationReportService = LocationReportService(applicationContext)

    override suspend fun doWork(): Result {
        val requesterUid = inputData.getString("requesterUid") ?: "unknown"
        Log.d(TAG, "🎯 开始短时实时追踪，请求者: $requesterUid")

        return try {
            // 设置前台通知（提升优先级，防止被系统杀掉）
            setForeground(createForegroundInfo())

            val endTime = System.currentTimeMillis() + TRACKING_DURATION_MS
            var updateCount = 0

            try {
                // 第一次立即上报
                reportLocation(++updateCount)

                // 循环上报，直到60秒超时
                while (System.currentTimeMillis() < endTime) {
                    // 检查任务是否被取消（用户主动停止）
                    if (isStopped) {
                        Log.d(TAG, "⏹️ 追踪被手动停止")
                        break
                    }

                    delay(UPDATE_INTERVAL_MS)

                    // 再次检查是否超时（防止延迟导致超时）
                    if (System.currentTimeMillis() >= endTime) {
                        Log.d(TAG, "⏱️ 追踪时间到，自动停止")
                        break
                    }

                    reportLocation(++updateCount)
                }

                Log.d(TAG, "✅ 短时实时追踪完成，共上报 $updateCount 次位置")
                sendDebugNotification(
                    "实时追踪结束",
                    "已完成 ${updateCount} 次位置更新"
                )

                Result.success()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消（WorkManager 调用 stop）
                Log.d(TAG, "⏹️ 追踪任务被取消，共上报 $updateCount 次位置")
                sendDebugNotification(
                    "实时追踪已停止",
                    "任务被取消，已上报 ${updateCount} 次"
                )
                // 重新抛出 CancellationException，让协程正确处理取消
                throw e
            } finally {
                // 无论正常结束还是被取消，都执行清理工作
                Log.d(TAG, "🧹 执行清理工作（共上报了 $updateCount 次位置）")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 重新抛出，确保协程取消传播
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ 短时实时追踪失败", e)
            sendDebugNotification(
                "实时追踪异常",
                "错误: ${e.message}"
            )
            Result.failure()
        }
    }

    /**
     * 上报一次位置
     */
    private suspend fun reportLocation(updateCount: Int) {
        val startTime = System.currentTimeMillis()
        val result = locationReportService.reportCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY
        )
        val duration = System.currentTimeMillis() - startTime

        if (result.isSuccess) {
            val device = result.getOrNull()
            Log.d(
                TAG,
                "📍 第 $updateCount 次位置上报成功 (耗时: ${duration}ms, 位置: ${device?.location})"
            )
        } else {
            Log.e(TAG, "❌ 第 $updateCount 次位置上报失败: ${result.exceptionOrNull()}")
        }
    }

    /**
     * 创建前台通知信息（提升Worker优先级）
     */
    private fun createForegroundInfo(): ForegroundInfo {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 创建通知渠道
        val channel = NotificationChannel(
            CHANNEL_ID,
            "实时位置追踪",
            NotificationManager.IMPORTANCE_LOW // 低重要性，不打扰用户
        )
        channel.description = "正在连续更新位置（60秒）"
        notificationManager.createNotificationChannel(channel)

        // 创建通知
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("正在共享位置")
            .setContentText("实时更新中（约60秒）")
            .setOngoing(true) // 持续通知，不可滑动删除
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    /**
     * 🔍 调试工具：发送调试通知
     */
    private fun sendDebugNotification(title: String, message: String) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
