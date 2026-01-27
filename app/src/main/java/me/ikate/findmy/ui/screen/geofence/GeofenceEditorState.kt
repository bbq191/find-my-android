package me.ikate.findmy.ui.screen.geofence

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import me.ikate.findmy.data.model.GeofenceType

/**
 * iOS Find My 风格通知类型
 */
enum class NotificationType {
    ARRIVE,      // 到达时通知
    LEAVE,       // 离开时通知
    LEFT_BEHIND  // 离开我身边时通知
}

/**
 * iOS Find My 风格地理围栏配置
 */
data class GeofenceConfig(
    val enabled: Boolean = false,
    val locationName: String = "",
    val address: String = "",
    val center: LatLng? = null,
    val radiusMeters: Float = 200f,
    val notificationType: NotificationType = NotificationType.ARRIVE,
    val isOneTime: Boolean = true,
    val geofenceType: GeofenceType = GeofenceType.FIXED_LOCATION,
    val ownerLocation: LatLng? = null,
    val wasInsideOnCreate: Boolean = false
)

/**
 * 快捷位置类型
 */
enum class QuickLocation {
    CONTACT,  // 联系人当前位置
    ME,       // 我的当前位置
    HOME,     // 家（预留）
    WORK      // 公司（预留）
}

/**
 * 地理围栏编辑器的集中状态管理
 * 使用 remember 保持状态在组合期间不变
 */
class GeofenceEditorState(
    initialConfig: GeofenceConfig,
    val contactLocation: LatLng?,
    val myLocation: LatLng?
) {
    // 当前选中的中心点
    var selectedCenter by mutableStateOf(initialConfig.center ?: contactLocation)
        private set

    // 半径（米）
    var radiusMeters by mutableFloatStateOf(initialConfig.radiusMeters)
        private set

    // 通知类型
    var notificationType by mutableStateOf(
        when {
            initialConfig.geofenceType == GeofenceType.LEFT_BEHIND -> NotificationType.LEFT_BEHIND
            initialConfig.notificationType == NotificationType.LEAVE -> NotificationType.LEAVE
            else -> NotificationType.ARRIVE
        }
    )
        private set

    // 仅通知一次
    var isOneTime by mutableStateOf(initialConfig.isOneTime)
        private set

    // 当前地址
    var currentAddress by mutableStateOf(initialConfig.address)
        private set

    // 是否正在拖动地图
    var isDraggingMap by mutableStateOf(false)
        private set

    // 是否正在加载地址
    var isLoadingAddress by mutableStateOf(false)
        private set

    // 是否已存在围栏（编辑模式）
    val isEditMode: Boolean = initialConfig.enabled

    // 上一次滑块步进值（用于触觉反馈）
    var lastSliderStep by mutableStateOf((initialConfig.radiusMeters / 50f).toInt())
        private set

    /**
     * 更新选中的中心点
     */
    fun updateCenter(latLng: LatLng) {
        selectedCenter = latLng
    }

    /**
     * 更新半径
     */
    fun updateRadius(radius: Float) {
        val snapped = snapToKeyPoints(radius)
        radiusMeters = snapped
        val currentStep = (snapped / 50f).toInt()
        if (currentStep != lastSliderStep) {
            lastSliderStep = currentStep
        }
    }

    /**
     * 更新通知类型
     */
    fun updateNotificationType(type: NotificationType) {
        notificationType = type
        // 切换到 LEFT_BEHIND 时，自动切换中心点到我的位置
        if (type == NotificationType.LEFT_BEHIND && myLocation != null) {
            selectedCenter = myLocation
        }
    }

    /**
     * 更新仅通知一次
     */
    fun updateIsOneTime(oneTime: Boolean) {
        isOneTime = oneTime
    }

    /**
     * 更新当前地址
     */
    fun updateAddress(address: String) {
        currentAddress = address
    }

    /**
     * 更新地图拖动状态
     */
    fun updateDraggingState(isDragging: Boolean) {
        isDraggingMap = isDragging
    }

    /**
     * 更新地址加载状态
     */
    fun updateLoadingState(isLoading: Boolean) {
        isLoadingAddress = isLoading
    }

    /**
     * 快捷定位
     */
    fun quickLocate(location: QuickLocation) {
        when (location) {
            QuickLocation.CONTACT -> contactLocation?.let { selectedCenter = it }
            QuickLocation.ME -> myLocation?.let { selectedCenter = it }
            QuickLocation.HOME -> { /* 预留 */ }
            QuickLocation.WORK -> { /* 预留 */ }
        }
    }

    /**
     * 构建最终配置
     */
    fun buildConfig(contactName: String): GeofenceConfig {
        return GeofenceConfig(
            enabled = true,
            locationName = when {
                notificationType == NotificationType.LEFT_BEHIND -> "我的位置"
                selectedCenter == contactLocation -> "${contactName}的位置"
                selectedCenter == myLocation -> "我的位置"
                else -> "自定义位置"
            },
            address = currentAddress,
            center = if (notificationType == NotificationType.LEFT_BEHIND) myLocation else selectedCenter,
            radiusMeters = radiusMeters,
            notificationType = notificationType,
            isOneTime = isOneTime,
            geofenceType = if (notificationType == NotificationType.LEFT_BEHIND)
                GeofenceType.LEFT_BEHIND else GeofenceType.FIXED_LOCATION,
            ownerLocation = if (notificationType == NotificationType.LEFT_BEHIND)
                myLocation else null,
            wasInsideOnCreate = calculateWasInsideOnCreate()
        )
    }

    /**
     * 计算创建时联系人是否在围栏内
     */
    private fun calculateWasInsideOnCreate(): Boolean {
        val center = if (notificationType == NotificationType.LEFT_BEHIND) myLocation else selectedCenter
        if (center != null && contactLocation != null) {
            val distance = calculateDistance(center, contactLocation)
            return distance < radiusMeters
        }
        return false
    }

    /**
     * 简易距离计算
     */
    private fun calculateDistance(from: LatLng, to: LatLng): Float {
        val earthRadius = 6371000.0 // 米
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLng = Math.toRadians(to.longitude - from.longitude)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(from.latitude)) *
                kotlin.math.cos(Math.toRadians(to.latitude)) *
                kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return (earthRadius * c).toFloat()
    }

    companion object {
        /**
         * 关键节点吸附（100, 200, 500, 1000）
         */
        fun snapToKeyPoints(value: Float): Float {
            val keyPoints = listOf(100f, 200f, 500f, 1000f)
            val snapThreshold = 20f

            for (point in keyPoints) {
                if (kotlin.math.abs(value - point) < snapThreshold) {
                    return point
                }
            }
            return value
        }
    }
}

/**
 * 创建并记住 GeofenceEditorState
 */
@Composable
fun rememberGeofenceEditorState(
    initialConfig: GeofenceConfig,
    contactLocation: LatLng?,
    myLocation: LatLng?
): GeofenceEditorState {
    return remember(contactLocation, myLocation) {
        GeofenceEditorState(initialConfig, contactLocation, myLocation)
    }
}
