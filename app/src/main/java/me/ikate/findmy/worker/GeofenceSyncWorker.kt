package me.ikate.findmy.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import me.ikate.findmy.data.repository.GeofenceRepository

/**
 * 围栏同步 Worker
 * 用于从云端同步围栏配置
 *
 * 触发场景：
 * 1. 收到 FCM 围栏同步通知
 * 2. 应用启动时
 * 3. 网络恢复时
 */
class GeofenceSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "GeofenceSyncWorker"
        private const val WORK_NAME = "geofence_sync_work"

        /**
         * 调度一次性同步任务
         */
        fun enqueue(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<GeofenceSyncWorker>()
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE, // 替换之前的任务
                    workRequest
                )

            Log.d(TAG, "围栏同步任务已调度")
        }

        /**
         * 取消同步任务
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "围栏同步任务已取消")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "========== [围栏同步] 开始同步 ==========")

        return try {
            val repository = GeofenceRepository(applicationContext)

            // 从云端同步围栏配置
            val syncResult = repository.syncFromCloud()

            syncResult.fold(
                onSuccess = { count ->
                    Log.i(TAG, "[围栏同步] ✓ 同步成功，共 $count 个围栏")
                    Result.success()
                },
                onFailure = { error ->
                    Log.e(TAG, "[围栏同步] ✗ 同步失败", error)
                    // 如果是网络问题，允许重试
                    if (runAttemptCount < 3) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "[围栏同步] ✗ 同步异常", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
