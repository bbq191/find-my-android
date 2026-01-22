package me.ikate.findmy.crash

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import me.ikate.findmy.BuildConfig
import me.ikate.findmy.crash.model.AppInfo
import me.ikate.findmy.crash.model.CrashInfo
import me.ikate.findmy.crash.model.CrashType
import me.ikate.findmy.crash.model.DeviceInfo
import me.ikate.findmy.crash.model.ExceptionInfo
import me.ikate.findmy.crash.model.MemoryInfo
import me.ikate.findmy.crash.model.NetworkInfo
import me.ikate.findmy.crash.model.StorageInfo
import me.ikate.findmy.util.DeviceIdProvider
import me.ikate.findmy.util.SamsungDeviceDetector
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID

/**
 * 崩溃信息收集器
 * 收集完整的崩溃上下文信息，用于问题排查
 */
class CrashInfoCollector(private val context: Context) {

    companion object {
        private const val MAX_STACK_TRACE_LINES = 100
        private const val MAX_CAUSE_CHAIN_DEPTH = 5
    }

    // 缓存当前 Activity 名称（由 ActivityLifecycleCallbacks 更新）
    @Volatile
    var currentActivityName: String? = null

    // 缓存前后台状态
    @Volatile
    var isForeground: Boolean = false

    // 缓存用户 ID
    @Volatile
    var userId: String? = null

    /**
     * 收集崩溃信息
     *
     * @param throwable 异常对象
     * @param threadName 崩溃线程名称
     * @param type 崩溃类型 (CRASH/ANR)
     * @return 完整的崩溃信息
     */
    fun collect(
        throwable: Throwable,
        threadName: String,
        type: CrashType = CrashType.CRASH
    ): CrashInfo {
        return CrashInfo(
            crashId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            type = type,
            exception = collectExceptionInfo(throwable, threadName),
            device = collectDeviceInfo(),
            memory = collectMemoryInfo(),
            storage = collectStorageInfo(),
            app = collectAppInfo(),
            network = collectNetworkInfo()
        )
    }

    /**
     * 收集 ANR 信息（包含所有线程堆栈）
     */
    fun collectAnr(mainThreadStackTrace: Array<StackTraceElement>): CrashInfo {
        val anrException = buildAnrException(mainThreadStackTrace)
        return CrashInfo(
            crashId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            type = CrashType.ANR,
            exception = collectExceptionInfo(anrException, "main"),
            device = collectDeviceInfo(),
            memory = collectMemoryInfo(),
            storage = collectStorageInfo(),
            app = collectAppInfo(),
            network = collectNetworkInfo()
        )
    }

    /**
     * 构建 ANR 异常对象
     */
    private fun buildAnrException(mainThreadStackTrace: Array<StackTraceElement>): Throwable {
        return AnrException("Application Not Responding").apply {
            stackTrace = mainThreadStackTrace
        }
    }

    /**
     * ANR 异常类
     */
    private class AnrException(message: String) : Exception(message)

    /**
     * 收集异常信息
     */
    private fun collectExceptionInfo(throwable: Throwable, threadName: String): ExceptionInfo {
        return ExceptionInfo(
            className = throwable.javaClass.name,
            message = throwable.message,
            stackTrace = getStackTraceString(throwable),
            causeChain = getCauseChain(throwable),
            threadName = threadName
        )
    }

    /**
     * 获取堆栈字符串（限制行数）
     */
    private fun getStackTraceString(throwable: Throwable): String {
        val writer = StringWriter()
        val printWriter = PrintWriter(writer)
        throwable.printStackTrace(printWriter)
        printWriter.close()

        val lines = writer.toString().lines()
        return if (lines.size > MAX_STACK_TRACE_LINES) {
            lines.take(MAX_STACK_TRACE_LINES).joinToString("\n") +
                    "\n... ${lines.size - MAX_STACK_TRACE_LINES} more lines truncated"
        } else {
            writer.toString()
        }
    }

    /**
     * 获取异常原因链
     */
    private fun getCauseChain(throwable: Throwable): List<String> {
        val chain = mutableListOf<String>()
        var cause: Throwable? = throwable.cause
        var depth = 0

        while (cause != null && depth < MAX_CAUSE_CHAIN_DEPTH) {
            chain.add("${cause.javaClass.name}: ${cause.message}")
            cause = cause.cause
            depth++
        }

        if (cause != null) {
            chain.add("... more causes truncated")
        }

        return chain
    }

    /**
     * 收集设备信息
     */
    private fun collectDeviceInfo(): DeviceInfo {
        val oneUiVersion = SamsungDeviceDetector.getOneUIVersion(context)

        return DeviceInfo(
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            oneUiVersion = oneUiVersion?.versionString,
            kernelVersion = getKernelVersion(),
            buildFingerprint = Build.FINGERPRINT,
            deviceId = DeviceIdProvider.getDeviceId(context)
        )
    }

    /**
     * 获取内核版本
     */
    private fun getKernelVersion(): String {
        return try {
            System.getProperty("os.version") ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * 收集内存信息
     */
    private fun collectMemoryInfo(): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val runtime = Runtime.getRuntime()

        return MemoryInfo(
            totalMemoryMb = memoryInfo.totalMem / (1024 * 1024),
            availableMemoryMb = memoryInfo.availMem / (1024 * 1024),
            javaHeapMaxMb = runtime.maxMemory() / (1024 * 1024),
            javaHeapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
            isLowMemory = memoryInfo.lowMemory
        )
    }

    /**
     * 收集存储信息
     */
    private fun collectStorageInfo(): StorageInfo {
        val internalStat = StatFs(context.filesDir.path)
        val externalStat = context.getExternalFilesDir(null)?.let { StatFs(it.path) }

        return StorageInfo(
            internalFreeMb = internalStat.availableBytes / (1024 * 1024),
            externalFreeMb = externalStat?.availableBytes?.div(1024 * 1024) ?: 0
        )
    }

    /**
     * 收集应用信息
     */
    private fun collectAppInfo(): AppInfo {
        val processName = getProcessName()

        return AppInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            processName = processName,
            isForeground = isForeground,
            currentActivity = currentActivityName,
            userId = userId
        )
    }

    /**
     * 获取当前进程名
     */
    private fun getProcessName(): String {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val myPid = android.os.Process.myPid()
            activityManager.runningAppProcesses?.find { it.pid == myPid }?.processName
                ?: context.packageName
        } catch (e: Exception) {
            context.packageName
        }
    }

    /**
     * 收集网络信息
     */
    private fun collectNetworkInfo(): NetworkInfo {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        val networkType = when {
            capabilities == null -> "NONE"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }

        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        return NetworkInfo(
            type = networkType,
            isConnected = isConnected
        )
    }
}
