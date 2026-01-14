package me.ikate.findmy.util

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 反向地理编码帮助类
 * 统一处理坐标转地址的逻辑
 */
object ReverseGeocodeHelper {

    /**
     * 获取地址（异步回调版本）
     * 用于 Compose 中的 LaunchedEffect
     */
    suspend fun getAddressFromLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        onResult: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) {
                    onResult("无法获取地址")
                    return@withContext
                }

                val geocoder = Geocoder(context, Locale.SIMPLIFIED_CHINESE)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        if (addresses.isNotEmpty()) {
                            val formatted = AddressFormatter.formatAddress(addresses[0])
                            val result = if (AddressFormatter.isPlusCode(formatted)) {
                                formatCoordinates(latitude, longitude)
                            } else {
                                formatted
                            }
                            onResult(result)
                        } else {
                            onResult("位置未知")
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val formatted = AddressFormatter.formatAddress(addresses[0])
                        val result = if (AddressFormatter.isPlusCode(formatted)) {
                            formatCoordinates(latitude, longitude)
                        } else {
                            formatted
                        }
                        onResult(result)
                    } else {
                        onResult("位置未知")
                    }
                }
            } catch (_: Exception) {
                onResult("获取地址失败")
            }
        }
    }

    /**
     * 获取地址（挂起函数版本）
     * 返回结果字符串
     */
    suspend fun getAddressSync(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) {
                return@withContext "无法获取地址"
            }

            val geocoder = Geocoder(context, Locale.SIMPLIFIED_CHINESE)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
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
                        continuation.resume(result)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val formatted = AddressFormatter.formatAddress(addresses[0])
                    if (AddressFormatter.isPlusCode(formatted)) {
                        formatCoordinates(latitude, longitude)
                    } else {
                        formatted
                    }
                } else {
                    "位置未知"
                }
            }
        } catch (_: Exception) {
            "获取地址失败"
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
}
