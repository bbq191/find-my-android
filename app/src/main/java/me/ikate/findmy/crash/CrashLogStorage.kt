package me.ikate.findmy.crash

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import me.ikate.findmy.crash.model.CrashInfo
import me.ikate.findmy.crash.model.CrashType
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * 崩溃日志存储管理
 *
 * 存储策略：
 * - 路径: /Android/data/{pkg}/files/crash/
 * - 单文件最大: 500KB
 * - 目录总大小限制: 10MB
 * - 文件保留天数: 7 天
 */
class CrashLogStorage(context: Context) {

    companion object {
        private const val TAG = "CrashLogStorage"
        private const val CRASH_DIR = "crash"
        private const val MAX_SINGLE_FILE_SIZE = 500 * 1024L // 500KB
        private const val MAX_TOTAL_SIZE = 10 * 1024 * 1024L // 10MB
        private const val MAX_FILE_AGE_DAYS = 7
        private const val FILE_PREFIX_CRASH = "crash-"
        private const val FILE_PREFIX_ANR = "anr-"
        private const val FILE_EXTENSION = ".json"
    }

    private val crashDir: File = File(context.getExternalFilesDir(null), CRASH_DIR).apply {
        if (!exists()) mkdirs()
    }

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    /**
     * 保存崩溃信息到本地文件
     *
     * @param crashInfo 崩溃信息
     * @return 是否保存成功
     */
    @Synchronized
    fun save(crashInfo: CrashInfo): Boolean {
        return try {
            // 先执行清理
            cleanup()

            // 生成文件名
            val prefix = when (crashInfo.type) {
                CrashType.CRASH -> FILE_PREFIX_CRASH
                CrashType.ANR -> FILE_PREFIX_ANR
            }
            val fileName = "$prefix${crashInfo.timestamp}$FILE_EXTENSION"
            val file = File(crashDir, fileName)

            // 序列化并检查大小
            val json = gson.toJson(crashInfo)
            if (json.toByteArray().size > MAX_SINGLE_FILE_SIZE) {
                // 如果超过大小限制，截断堆栈信息
                val truncatedInfo = crashInfo.copy(
                    exception = crashInfo.exception.copy(
                        stackTrace = crashInfo.exception.stackTrace.take(10000) +
                                "\n... truncated due to size limit"
                    )
                )
                FileWriter(file).use { it.write(gson.toJson(truncatedInfo)) }
            } else {
                FileWriter(file).use { it.write(json) }
            }

            Log.d(TAG, "崩溃日志已保存: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存崩溃日志失败", e)
            false
        }
    }

    /**
     * 获取待上传的日志文件列表
     * 返回未标记为已上传的日志
     */
    fun getPendingLogs(): List<File> {
        return crashDir.listFiles { file ->
            file.isFile &&
                    file.extension == "json" &&
                    (file.name.startsWith(FILE_PREFIX_CRASH) || file.name.startsWith(FILE_PREFIX_ANR))
        }?.filter { file ->
            // 读取文件检查 uploaded 字段
            try {
                val crashInfo = readCrashInfo(file)
                crashInfo?.uploaded != true
            } catch (e: Exception) {
                true // 读取失败的文件也需要处理
            }
        }?.sortedBy { it.lastModified() } ?: emptyList()
    }

    /**
     * 获取所有日志文件
     */
    fun getAllLogs(): List<File> {
        return crashDir.listFiles { file ->
            file.isFile &&
                    file.extension == "json" &&
                    (file.name.startsWith(FILE_PREFIX_CRASH) || file.name.startsWith(FILE_PREFIX_ANR))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * 读取崩溃信息
     */
    fun readCrashInfo(file: File): CrashInfo? {
        return try {
            FileReader(file).use { reader ->
                gson.fromJson(reader, CrashInfo::class.java)
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取崩溃日志失败: ${file.name}", e)
            null
        }
    }

    /**
     * 根据 crashId 读取崩溃信息
     */
    fun readCrashInfoById(crashId: String): CrashInfo? {
        return getAllLogs().firstNotNullOfOrNull { file ->
            val info = readCrashInfo(file)
            if (info?.crashId == crashId) info else null
        }
    }

    /**
     * 标记日志为已上传
     */
    @Synchronized
    fun markAsUploaded(crashId: String): Boolean {
        return try {
            val file = findFileByCrashId(crashId) ?: return false
            val crashInfo = readCrashInfo(file) ?: return false

            val updatedInfo = crashInfo.copy(
                uploaded = true,
                uploadAttempts = crashInfo.uploadAttempts + 1
            )

            FileWriter(file).use { it.write(gson.toJson(updatedInfo)) }
            Log.d(TAG, "日志已标记为已上传: $crashId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "标记日志失败: $crashId", e)
            false
        }
    }

    /**
     * 增加上传尝试次数
     */
    @Synchronized
    fun incrementUploadAttempts(crashId: String): Boolean {
        return try {
            val file = findFileByCrashId(crashId) ?: return false
            val crashInfo = readCrashInfo(file) ?: return false

            val updatedInfo = crashInfo.copy(
                uploadAttempts = crashInfo.uploadAttempts + 1
            )

            FileWriter(file).use { it.write(gson.toJson(updatedInfo)) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "增加上传尝试次数失败: $crashId", e)
            false
        }
    }

    /**
     * 删除日志文件
     */
    @Synchronized
    fun delete(crashId: String): Boolean {
        return try {
            val file = findFileByCrashId(crashId)
            if (file?.delete() == true) {
                Log.d(TAG, "日志已删除: $crashId")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除日志失败: $crashId", e)
            false
        }
    }

    /**
     * 根据 crashId 查找文件
     */
    private fun findFileByCrashId(crashId: String): File? {
        return getAllLogs().find { file ->
            readCrashInfo(file)?.crashId == crashId
        }
    }

    /**
     * 执行清理操作
     * - 删除超过 7 天的文件
     * - 如果总大小超过 10MB，删除最旧的文件
     */
    @Synchronized
    fun cleanup() {
        try {
            val now = System.currentTimeMillis()
            val maxAge = MAX_FILE_AGE_DAYS * 24 * 60 * 60 * 1000L

            // 获取所有日志文件
            val files = getAllLogs().toMutableList()

            // 1. 删除过期文件
            val expiredFiles = files.filter { now - it.lastModified() > maxAge }
            expiredFiles.forEach { file ->
                if (file.delete()) {
                    Log.d(TAG, "删除过期日志: ${file.name}")
                    files.remove(file)
                }
            }

            // 2. 检查总大小，删除最旧的文件直到低于限制
            var totalSize = files.sumOf { it.length() }
            val sortedByAge = files.sortedBy { it.lastModified() }

            for (file in sortedByAge) {
                if (totalSize <= MAX_TOTAL_SIZE) break

                val fileSize = file.length()
                if (file.delete()) {
                    totalSize -= fileSize
                    Log.d(TAG, "删除旧日志以释放空间: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理日志失败", e)
        }
    }

    /**
     * 获取日志目录大小
     */
    fun getTotalSize(): Long {
        return getAllLogs().sumOf { it.length() }
    }

    /**
     * 获取日志数量
     */
    fun getLogCount(): Int {
        return getAllLogs().size
    }

    /**
     * 清空所有日志
     */
    @Synchronized
    fun clearAll() {
        getAllLogs().forEach { file ->
            file.delete()
        }
        Log.d(TAG, "已清空所有崩溃日志")
    }
}
