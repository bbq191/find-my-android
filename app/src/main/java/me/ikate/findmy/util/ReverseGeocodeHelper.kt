package me.ikate.findmy.util

import android.content.Context
import android.util.Log
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.ikate.findmy.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 反向地理编码帮助类
 * 使用腾讯地图 SDK 的 TencentSearch 进行逆地理编码
 *
 * 注意：
 * - 输入坐标为 GCJ-02（腾讯定位和腾讯地图使用的坐标系）
 * - 使用腾讯位置服务 WebService API
 */
object ReverseGeocodeHelper {

    private const val TAG = "ReverseGeocodeHelper"

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

    // 请求超时时间
    private const val REQUEST_TIMEOUT_MS = 8000L

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
     * @param latitude GCJ-02 纬度（腾讯坐标系）
     * @param longitude GCJ-02 经度（腾讯坐标系）
     */
    suspend fun getAddressFromLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        onResult: (String) -> Unit
    ) {
        // 先检查缓存
        getCachedAddress(latitude, longitude)?.let { cached ->
            onResult(cached)
            return
        }

        val result = getAddressSync(context, latitude, longitude)
        onResult(result)
    }

    /**
     * 获取地址（挂起函数版本）
     * 使用腾讯 TencentSearch 进行逆地理编码
     *
     * @param latitude GCJ-02 纬度（腾讯坐标系）
     * @param longitude GCJ-02 经度（腾讯坐标系）
     */
    suspend fun getAddressSync(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String {
        // 先检查缓存
        getCachedAddress(latitude, longitude)?.let { return it }

        // 检查坐标有效性
        if (latitude.isNaN() || longitude.isNaN() ||
            (latitude == 0.0 && longitude == 0.0)) {
            return "位置未知"
        }

        return withContext(Dispatchers.IO) {
            try {
                // 使用超时包装
                val result = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                    getAddressFromTencentSearch(context, latitude, longitude)
                }

                val address = result ?: formatCoordinates(latitude, longitude)
                cacheAddress(latitude, longitude, address)
                address
            } catch (e: Exception) {
                Log.e(TAG, "获取地址失败: ${e.message}")
                formatCoordinates(latitude, longitude)
            }
        }
    }

    /**
     * 使用腾讯 WebService API 获取地址
     * API 文档: https://lbs.qq.com/service/webService/webServiceGuide/webServiceGcoder
     * 签名校验: https://lbs.qq.com/faq/serverFaq/webServiceKey
     */
    private fun getAddressFromTencentSearch(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String? {
        try {
            val apiKey = BuildConfig.TENCENT_MAP_KEY
            val secretKey = BuildConfig.TENCENT_MAP_SK

            if (apiKey.isBlank()) {
                Log.w(TAG, "腾讯地图 API Key 未配置")
                return null
            }

            // 构建参数（按字母顺序排序）
            val params = sortedMapOf(
                "get_poi" to "0",
                "key" to apiKey,
                "location" to "$latitude,$longitude"
            )

            // 构建查询字符串（用于签名计算，不编码）
            val queryString = params.entries.joinToString("&") { "${it.key}=${it.value}" }

            // 计算签名（如果配置了 SK）
            val sig = if (secretKey.isNotBlank()) {
                val signString = "/ws/geocoder/v1?$queryString$secretKey"
                md5(signString)
            } else null

            // 构建最终 URL（参数需要 URL 编码）
            val encodedParams = params.entries.joinToString("&") {
                "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
            }
            val finalUrl = if (sig != null) {
                "https://apis.map.qq.com/ws/geocoder/v1?$encodedParams&sig=$sig"
            } else {
                "https://apis.map.qq.com/ws/geocoder/v1?$encodedParams"
            }

            val url = URL(finalUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "逆地理编码请求失败: HTTP $responseCode")
                return null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            return parseGeocoderResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "腾讯逆地理编码异常: ${e.message}")
            return null
        }
    }

    /**
     * 计算 MD5 签名（小写）
     */
    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 解析腾讯逆地理编码响应
     */
    private fun parseGeocoderResponse(response: String): String? {
        try {
            val json = JSONObject(response)
            val status = json.optInt("status", -1)

            if (status != 0) {
                val message = json.optString("message", "未知错误")
                // 特殊处理常见错误
                when (status) {
                    199 -> Log.e(TAG, "⚠️ 腾讯地图 API Key 未开启 WebService API，请在 https://lbs.qq.com/dev/console 中开启")
                    110 -> Log.e(TAG, "⚠️ 腾讯地图 API Key 无效或请求来源未授权")
                    120 -> Log.e(TAG, "⚠️ 腾讯地图 API Key 配额不足")
                    else -> Log.w(TAG, "逆地理编码失败: status=$status, message=$message")
                }
                return null
            }

            val result = json.optJSONObject("result") ?: return null

            // 优先使用格式化地址
            val formattedAddress = result.optString("address", "")
            if (formattedAddress.isNotBlank()) {
                // 移除省市前缀，只保留详细地址
                var address: String = formattedAddress
                val addressComponent = result.optJSONObject("address_component")

                if (addressComponent != null) {
                    val province = addressComponent.optString("province", "")
                    val city = addressComponent.optString("city", "")

                    if (province.isNotBlank() && address.startsWith(province)) {
                        address = address.removePrefix(province)
                    }
                    if (city.isNotBlank() && address.startsWith(city)) {
                        address = address.removePrefix(city)
                    }
                }

                val trimmed = address.trim()
                if (trimmed.isNotBlank()) return trimmed
                return formattedAddress
            }

            // 降级：使用街道信息
            val addressComponent = result.optJSONObject("address_component")
            if (addressComponent != null) {
                val street = addressComponent.optString("street", "")
                val streetNumber = addressComponent.optString("street_number", "")
                if (street.isNotBlank()) {
                    return if (streetNumber.isNotBlank()) "$street$streetNumber" else street
                }

                // 最后：使用区县
                val district = addressComponent.optString("district", "")
                if (district.isNotBlank()) {
                    return district
                }
            }

            return null
        } catch (e: Exception) {
            Log.e(TAG, "解析逆地理编码响应失败: ${e.message}")
            return null
        }
    }

    /**
     * 从 LatLng 获取地址
     */
    suspend fun getAddressFromLatLng(
        context: Context,
        latLng: LatLng
    ): String {
        return getAddressSync(context, latLng.latitude, latLng.longitude)
    }

    /**
     * 格式化坐标为可读字符串
     */
    private fun formatCoordinates(latitude: Double, longitude: Double): String {
        return "纬度 ${String.format("%.4f", latitude)}, 经度 ${String.format("%.4f", longitude)}"
    }

    /**
     * 清除所有缓存
     */
    fun clearCache() {
        addressCache.clear()
    }
}
