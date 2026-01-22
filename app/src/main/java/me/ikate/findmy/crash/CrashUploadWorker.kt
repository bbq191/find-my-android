package me.ikate.findmy.crash

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import me.ikate.findmy.crash.model.CrashInfo
import java.util.concurrent.TimeUnit

/**
 * 崩溃日志上传 Worker
 *
 * 使用 WorkManager 确保崩溃日志可靠上传：
 * - 需要网络连接
 * - 指数退避重试
 * - 上传成功后删除本地日志
 */
class CrashUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CrashUploadWorker"
        private const val WORK_NAME = "crash_upload_work"
        private const val COLLECTION_NAME = "crash_reports"
        private const val MAX_UPLOAD_ATTEMPTS = 3

        /**
         * 调度上传任务
         * 使用 KEEP 策略，避免重复调度
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<CrashUploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )

            Log.d(TAG, "崩溃日志上传任务已调度")
        }

        /**
         * 取消上传任务
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "崩溃日志上传任务已取消")
        }
    }

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override suspend fun doWork(): Result {
        Log.d(TAG, "开始上传崩溃日志...")

        val storage = CrashLogStorage(applicationContext)
        val pendingLogs = storage.getPendingLogs()

        if (pendingLogs.isEmpty()) {
            Log.d(TAG, "没有待上传的崩溃日志")
            return Result.success()
        }

        Log.d(TAG, "发现 ${pendingLogs.size} 条待上传的崩溃日志")

        var successCount = 0
        var failCount = 0

        for (file in pendingLogs) {
            val crashInfo = storage.readCrashInfo(file)
            if (crashInfo == null) {
                Log.w(TAG, "无法读取崩溃日志: ${file.name}，跳过")
                continue
            }

            // 检查上传尝试次数
            if (crashInfo.uploadAttempts >= MAX_UPLOAD_ATTEMPTS) {
                Log.w(TAG, "崩溃日志 ${crashInfo.crashId} 上传尝试次数已达上限，删除")
                storage.delete(crashInfo.crashId)
                continue
            }

            // 尝试上传
            val uploaded = uploadToFirestore(crashInfo)
            if (uploaded) {
                // 上传成功，删除本地文件
                storage.delete(crashInfo.crashId)
                successCount++
                Log.d(TAG, "崩溃日志上传成功: ${crashInfo.crashId}")
            } else {
                // 上传失败，增加尝试次数
                storage.incrementUploadAttempts(crashInfo.crashId)
                failCount++
                Log.w(TAG, "崩溃日志上传失败: ${crashInfo.crashId}")
            }
        }

        Log.d(TAG, "上传完成: 成功 $successCount，失败 $failCount")

        return if (failCount > 0) {
            // 有失败的，稍后重试
            Result.retry()
        } else {
            Result.success()
        }
    }

    /**
     * 上传崩溃信息到 Firestore
     */
    private suspend fun uploadToFirestore(crashInfo: CrashInfo): Boolean {
        return try {
            val data = crashInfoToMap(crashInfo)

            firestore.collection(COLLECTION_NAME)
                .document(crashInfo.crashId)
                .set(data)
                .await()

            true
        } catch (e: Exception) {
            Log.e(TAG, "上传到 Firestore 失败", e)
            false
        }
    }

    /**
     * 将 CrashInfo 转换为 Map
     */
    private fun crashInfoToMap(crashInfo: CrashInfo): Map<String, Any?> {
        return mapOf(
            "crash_id" to crashInfo.crashId,
            "timestamp" to crashInfo.timestamp,
            "type" to crashInfo.type.name,
            "exception" to mapOf(
                "class_name" to crashInfo.exception.className,
                "message" to crashInfo.exception.message,
                "stack_trace" to crashInfo.exception.stackTrace,
                "cause_chain" to crashInfo.exception.causeChain,
                "thread_name" to crashInfo.exception.threadName
            ),
            "device" to mapOf(
                "model" to crashInfo.device.model,
                "manufacturer" to crashInfo.device.manufacturer,
                "brand" to crashInfo.device.brand,
                "android_version" to crashInfo.device.androidVersion,
                "sdk_version" to crashInfo.device.sdkVersion,
                "one_ui_version" to crashInfo.device.oneUiVersion,
                "kernel_version" to crashInfo.device.kernelVersion,
                "build_fingerprint" to crashInfo.device.buildFingerprint,
                "device_id" to crashInfo.device.deviceId
            ),
            "memory" to mapOf(
                "total_memory_mb" to crashInfo.memory.totalMemoryMb,
                "available_memory_mb" to crashInfo.memory.availableMemoryMb,
                "java_heap_max_mb" to crashInfo.memory.javaHeapMaxMb,
                "java_heap_used_mb" to crashInfo.memory.javaHeapUsedMb,
                "is_low_memory" to crashInfo.memory.isLowMemory
            ),
            "storage" to mapOf(
                "internal_free_mb" to crashInfo.storage.internalFreeMb,
                "external_free_mb" to crashInfo.storage.externalFreeMb
            ),
            "app" to mapOf(
                "version_name" to crashInfo.app.versionName,
                "version_code" to crashInfo.app.versionCode,
                "process_name" to crashInfo.app.processName,
                "is_foreground" to crashInfo.app.isForeground,
                "current_activity" to crashInfo.app.currentActivity,
                "user_id" to crashInfo.app.userId
            ),
            "network" to mapOf(
                "type" to crashInfo.network.type,
                "is_connected" to crashInfo.network.isConnected
            )
        )
    }
}
