package me.ikate.findmy.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 三星设备检测工具类
 *
 * 提供三星设备特征检测功能：
 * - 设备制造商检测
 * - Galaxy S24 Ultra 型号识别
 * - OneUI 版本解析
 * - 高刷新率支持检测
 */
object SamsungDeviceDetector {

    private const val TAG = "SamsungDeviceDetector"

    // 三星制造商标识
    private const val SAMSUNG_MANUFACTURER = "samsung"

    // Galaxy S24 Ultra 型号前缀（SM-S928 系列）
    private val S24_ULTRA_MODEL_PREFIXES = listOf("SM-S928", "SM-S9280")

    // Galaxy S24 系列型号前缀
    private val S24_SERIES_MODEL_PREFIXES = listOf(
        "SM-S921",  // S24
        "SM-S926",  // S24+
        "SM-S928"   // S24 Ultra
    )

    /**
     * OneUI 版本数据类
     */
    data class OneUIVersion(
        val major: Int,
        val minor: Int
    ) {
        val versionString: String get() = "$major.$minor"

        fun isAtLeast(major: Int, minor: Int = 0): Boolean {
            return this.major > major || (this.major == major && this.minor >= minor)
        }
    }

    /**
     * 检测是否为三星设备
     */
    fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals(SAMSUNG_MANUFACTURER, ignoreCase = true)
    }

    /**
     * 检测是否为 Galaxy S24 Ultra
     */
    fun isGalaxyS24Ultra(): Boolean {
        if (!isSamsungDevice()) return false
        val model = Build.MODEL.uppercase()
        return S24_ULTRA_MODEL_PREFIXES.any { model.startsWith(it) }
    }

    /**
     * 检测是否为 Galaxy S24 系列
     */
    fun isGalaxyS24Series(): Boolean {
        if (!isSamsungDevice()) return false
        val model = Build.MODEL.uppercase()
        return S24_SERIES_MODEL_PREFIXES.any { model.startsWith(it) }
    }

    /**
     * 获取 OneUI 版本
     *
     * OneUI 版本检测顺序：
     * 1. 系统属性 ro.build.version.oneui
     * 2. Settings.System sem_oneui_version
     * 3. 系统属性 ro.build.version.sem（旧版兼容）
     */
    fun getOneUIVersion(): OneUIVersion? {
        if (!isSamsungDevice()) return null

        return try {
            // 方法 1: 系统属性 ro.build.version.oneui
            val oneuiProp = getSystemProperty("ro.build.version.oneui")
            if (!oneuiProp.isNullOrBlank()) {
                parseOneUIVersion(oneuiProp)?.let { return it }
            }

            // 方法 2: Settings.System（需要 Context，此处无法使用）
            // 将在 getOneUIVersion(context) 中实现

            // 方法 3: 系统属性 ro.build.version.sem
            val semProp = getSystemProperty("ro.build.version.sem")
            if (!semProp.isNullOrBlank()) {
                parseOneUIVersion(semProp)?.let { return it }
            }

            // 方法 4: 从 Build.DISPLAY 推断
            inferOneUIVersionFromDisplay()
        } catch (e: Exception) {
            Log.w(TAG, "获取 OneUI 版本失败", e)
            null
        }
    }

    /**
     * 获取 OneUI 版本（带 Context）
     */
    fun getOneUIVersion(context: Context): OneUIVersion? {
        // 先尝试无 Context 的方法
        getOneUIVersion()?.let { return it }

        // 尝试通过 Settings.System 获取
        return try {
            val semVersion = Settings.System.getFloat(
                context.contentResolver,
                "sem_oneui_version",
                0f
            )
            if (semVersion > 0f) {
                val major = semVersion.toInt()
                val minor = ((semVersion - major) * 10).toInt()
                OneUIVersion(major, minor)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "从 Settings.System 获取 OneUI 版本失败", e)
            null
        }
    }

    /**
     * 检测设备是否支持 120Hz 刷新率
     */
    fun supports120Hz(): Boolean {
        // Galaxy S24 系列全系支持 120Hz
        if (isGalaxyS24Series()) return true

        // 检查 OneUI 版本（OneUI 3.0+ 的旗舰机型通常支持高刷）
        val oneui = getOneUIVersion()
        if (oneui != null && oneui.isAtLeast(3, 0) && isFlagshipModel()) {
            return true
        }

        // 检查系统属性
        val maxRefreshRate = getSystemProperty("ro.surface_flinger.max_frame_buffer_acquired_buffers")
        if (maxRefreshRate != null) {
            val rate = maxRefreshRate.toIntOrNull() ?: 0
            if (rate >= 120) return true
        }

        return false
    }

    /**
     * 检测是否为三星旗舰机型
     */
    private fun isFlagshipModel(): Boolean {
        val model = Build.MODEL.uppercase()
        // Galaxy S 系列和 Galaxy Note/Fold/Flip 系列
        return model.matches(Regex("SM-[SGNF]\\d{3,4}.*"))
    }

    /**
     * 解析 OneUI 版本字符串
     */
    private fun parseOneUIVersion(versionString: String): OneUIVersion? {
        return try {
            // 格式可能是 "60100"（6.1.0）或 "6.1" 或 "6"
            val cleaned = versionString.trim()

            when {
                cleaned.contains(".") -> {
                    val parts = cleaned.split(".")
                    val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
                    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    OneUIVersion(major, minor)
                }
                cleaned.length >= 5 -> {
                    // 格式如 "60100"
                    val major = cleaned.substring(0, 1).toIntOrNull() ?: return null
                    val minor = cleaned.substring(1, 2).toIntOrNull() ?: 0
                    OneUIVersion(major, minor)
                }
                else -> {
                    val major = cleaned.toIntOrNull() ?: return null
                    OneUIVersion(major, 0)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从 Build.DISPLAY 推断 OneUI 版本
     */
    private fun inferOneUIVersionFromDisplay(): OneUIVersion? {
        // Galaxy S24 系列预装 OneUI 6.0+
        if (isGalaxyS24Series()) {
            return OneUIVersion(6, 0)
        }
        return null
    }

    /**
     * 读取系统属性
     */
    private fun getSystemProperty(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("getprop $key")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()?.trim()
            reader.close()
            process.waitFor()
            if (result.isNullOrBlank()) null else result
        } catch (e: Exception) {
            Log.w(TAG, "读取系统属性 $key 失败", e)
            null
        }
    }

    /**
     * 获取设备诊断信息
     */
    fun getDiagnosticInfo(context: Context? = null): String {
        return buildString {
            appendLine("=== Samsung Device Detector ===")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Is Samsung: ${isSamsungDevice()}")
            appendLine("Is S24 Ultra: ${isGalaxyS24Ultra()}")
            appendLine("Is S24 Series: ${isGalaxyS24Series()}")
            appendLine("OneUI Version: ${context?.let { getOneUIVersion(it) } ?: getOneUIVersion()}")
            appendLine("Supports 120Hz: ${supports120Hz()}")
        }
    }
}
