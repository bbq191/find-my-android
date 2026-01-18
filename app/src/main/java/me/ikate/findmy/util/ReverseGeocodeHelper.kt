package me.ikate.findmy.util

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.mapbox.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.ikate.findmy.data.model.latitude
import me.ikate.findmy.data.model.longitude
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * 反向地理编码帮助类
 * 统一处理坐标转地址的逻辑，带内存缓存
 *
 * 注意：
 * - 输入坐标为 WGS-84（Mapbox 使用的坐标系）
 * - 在中国大陆范围内，会自动转换为 GCJ-02 后再进行反向编码
 * - Android Geocoder 在中国使用高德服务，需要 GCJ-02 坐标
 */
object ReverseGeocodeHelper {

    private data class CacheEntry(
        val address: String,
        val timestamp: Long
    )

    // 缓存：key 为 "lat,lng" 格式（精确到小数点后 4 位）
    private val addressCache = ConcurrentHashMap<String, CacheEntry>()

    // 缓存有效期：30 分钟
    private const val CACHE_EXPIRY_MS = 30 * 60 * 1000L

    // 坐标精度：小数点后 4 位（约 11 米精度）
    private const val COORDINATE_PRECISION = 4

    private fun getCacheKey(latitude: Double, longitude: Double): String {
        val lat = String.format("%.${COORDINATE_PRECISION}f", latitude)
        val lng = String.format("%.${COORDINATE_PRECISION}f", longitude)
        return "$lat,$lng"
    }

    private fun getCachedAddress(latitude: Double, longitude: Double): String? {
        val key = getCacheKey(latitude, longitude)
        val entry = addressCache[key] ?: return null

        // 检查是否过期
        if (System.currentTimeMillis() - entry.timestamp > CACHE_EXPIRY_MS) {
            addressCache.remove(key)
            return null
        }
        return entry.address
    }

    private fun cacheAddress(latitude: Double, longitude: Double, address: String) {
        val key = getCacheKey(latitude, longitude)
        addressCache[key] = CacheEntry(address, System.currentTimeMillis())

        // 清理过期缓存（限制缓存大小）
        if (addressCache.size > 100) {
            cleanExpiredCache()
        }
    }

    private fun cleanExpiredCache() {
        val now = System.currentTimeMillis()
        addressCache.entries.removeIf { now - it.value.timestamp > CACHE_EXPIRY_MS }
    }

    /**
     * 获取地址（异步回调版本）
     * 用于 Compose 中的 LaunchedEffect
     *
     * @param latitude WGS-84 纬度（Mapbox 坐标系）
     * @param longitude WGS-84 经度（Mapbox 坐标系）
     */
    suspend fun getAddressFromLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        onResult: (String) -> Unit
    ) {
        // 先检查缓存（使用原始 WGS-84 坐标作为缓存 key）
        getCachedAddress(latitude, longitude)?.let { cached ->
            onResult(cached)
            return
        }

        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) {
                    onResult("无法获取地址")
                    return@withContext
                }

                // 在中国大陆范围内，将 WGS-84 转换为 GCJ-02
                // Android Geocoder 在中国使用高德服务，需要 GCJ-02 坐标
                val (gcjLat, gcjLng) = if (CoordinateConverter.isInChina(latitude, longitude)) {
                    val gcj = CoordinateConverter.wgs84ToGcj02(latitude, longitude)
                    gcj.latitude() to gcj.longitude()
                } else {
                    latitude to longitude
                }

                val geocoder = Geocoder(context, Locale.SIMPLIFIED_CHINESE)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(gcjLat, gcjLng, 1) { addresses ->
                        val result = if (addresses.isNotEmpty()) {
                            val formatted = AddressFormatter.formatAddress(addresses[0])
                            if (AddressFormatter.isPlusCode(formatted)) {
                                formatCoordinates(latitude, longitude)
                            } else {
                                formatted
                            }
                        } else {
                            "位置未知"
                        }
                        cacheAddress(latitude, longitude, result)
                        onResult(result)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(gcjLat, gcjLng, 1)
                    val result = if (!addresses.isNullOrEmpty()) {
                        val formatted = AddressFormatter.formatAddress(addresses[0])
                        if (AddressFormatter.isPlusCode(formatted)) {
                            formatCoordinates(latitude, longitude)
                        } else {
                            formatted
                        }
                    } else {
                        "位置未知"
                    }
                    cacheAddress(latitude, longitude, result)
                    onResult(result)
                }
            } catch (_: Exception) {
                onResult("获取地址失败")
            }
        }
    }

    /**
     * 获取地址（挂起函数版本）
     * 返回结果字符串
     *
     * @param latitude WGS-84 纬度（Mapbox 坐标系）
     * @param longitude WGS-84 经度（Mapbox 坐标系）
     */
    suspend fun getAddressSync(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String {
        // 先检查缓存（使用原始 WGS-84 坐标作为缓存 key）
        getCachedAddress(latitude, longitude)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) {
                    return@withContext "无法获取地址"
                }

                // 在中国大陆范围内，将 WGS-84 转换为 GCJ-02
                // Android Geocoder 在中国使用高德服务，需要 GCJ-02 坐标
                val (gcjLat, gcjLng) = if (CoordinateConverter.isInChina(latitude, longitude)) {
                    val gcj = CoordinateConverter.wgs84ToGcj02(latitude, longitude)
                    gcj.latitude() to gcj.longitude()
                } else {
                    latitude to longitude
                }

                val geocoder = Geocoder(context, Locale.SIMPLIFIED_CHINESE)

                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(gcjLat, gcjLng, 1) { addresses ->
                            val address = if (addresses.isNotEmpty()) {
                                val formatted = AddressFormatter.formatAddress(addresses[0])
                                if (AddressFormatter.isPlusCode(formatted)) {
                                    formatCoordinates(latitude, longitude)
                                } else {
                                    formatted
                                }
                            } else {
                                "位置未知"
                            }
                            continuation.resume(address)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(gcjLat, gcjLng, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val formatted = AddressFormatter.formatAddress(addresses[0])
                        if (AddressFormatter.isPlusCode(formatted)) {
                            formatCoordinates(latitude, longitude) // 显示原始 WGS-84 坐标
                        } else {
                            formatted
                        }
                    } else {
                        "位置未知"
                    }
                }

                // 使用原始 WGS-84 坐标作为缓存 key
                cacheAddress(latitude, longitude, result)
                result
            } catch (_: Exception) {
                "获取地址失败"
            }
        }
    }

    /**
     * 从 Point 获取地址
     */
    suspend fun getAddressFromPoint(
        context: Context,
        point: Point
    ): String {
        return getAddressSync(context, point.latitude, point.longitude)
    }

    /**
     * 格式化坐标为可读字符串
     */
    private fun formatCoordinates(latitude: Double, longitude: Double): String {
        return "纬度 ${String.format("%.4f", latitude)}, 经度 ${String.format("%.4f", longitude)}"
    }
}
