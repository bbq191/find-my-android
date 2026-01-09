package me.ikate.findmy.ui.screen.main.components

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import me.ikate.findmy.data.model.Device
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Google Maps 视图 Compose 包装器
 * 使用 Google Maps Compose 库
 *
 * @param modifier 修饰符
 * @param devices 设备列表（用于渲染 Marker）
 * @param contacts 联系人列表（用于渲染联系人位置 Marker）
 * @param onMapReady 地图准备完成回调，返回 GoogleMap 实例
 * @param onMarkerClick Marker 点击回调，返回点击的设备
 * @param onContactMarkerClick 联系人 Marker 点击回调
 * @param onMapClick 地图空白区域点击回调
 */
@SuppressLint("HardwareIds")
@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun MapViewWrapper(
    modifier: Modifier = Modifier,
    devices: List<Device> = emptyList(),
    contacts: List<me.ikate.findmy.data.model.Contact> = emptyList(),
    currentDeviceHeading: Float? = null, // 当前设备实时朝向（来自传感器）
    onMapReady: (GoogleMap) -> Unit = {},
    onMarkerClick: (Device) -> Unit = {},
    onContactMarkerClick: (me.ikate.findmy.data.model.Contact) -> Unit = {},
    onMapClick: () -> Unit = {}
) {
    // 移除默认定位，使用空初始位置，等待实际设备数据加载
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
    }

    // Map UI Settings
    val uiSettings by remember {
        mutableStateOf(
            MapUiSettings(
                zoomControlsEnabled = false, // 禁用默认缩放按钮
                compassEnabled = true, // 启用指南针
                myLocationButtonEnabled = false // 禁用默认定位按钮
            )
        )
    }

    // Map Properties
    val properties by remember {
        mutableStateOf(
            MapProperties(
                isMyLocationEnabled = false, // 禁用默认定位蓝点，使用自定义标记
                isBuildingEnabled = true
            )
        )
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = properties,
        uiSettings = uiSettings,
        onMapLoaded = {
        },
        onMapClick = {
            onMapClick()
        }
    ) {
        // 获取当前设备ID
        val context = LocalContext.current
        val currentDeviceId = remember {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
        }

        // 在 GoogleMap content scope 中添加 Marker
        devices.forEach { device ->
            // 过滤掉无效坐标，防止 Marker 渲染导致 NaN 问题
            if (!device.location.latitude.isNaN() && !device.location.longitude.isNaN()) {
                androidx.compose.runtime.key(device.id) {
                    val markerState =
                        com.google.maps.android.compose.rememberMarkerState(position = device.location)

                    // 当设备位置更新时，同步更新 Marker 状态
                    LaunchedEffect(device.location) {
                        if (!device.location.latitude.isNaN() && !device.location.longitude.isNaN()) {
                            markerState.position = device.location
                        }
                    }

                    // 判断是否为当前设备
                    val isCurrentDevice = device.id == currentDeviceId

                    // 确定此设备的显示方向
                    // 如果是当前设备且有实时传感器数据，优先使用传感器数据
                    // 否则使用设备上报的 GPS 方向
                    val rawBearing = if (isCurrentDevice && currentDeviceHeading != null) {
                        currentDeviceHeading
                    } else {
                        device.bearing
                    }
                    // 严格过滤 NaN 和 Infinite，默认为 0f
                    val displayBearing =
                        if (rawBearing.isNaN() || rawBearing.isInfinite()) 0f else rawBearing

                    // 绘制方向指示雷达
                    // 注意：即使 bearing 为 0 (正北) 也绘制，以便用户能看到效果
                    // 实际上应该区分 "无方向数据" 和 "正北"，但为了演示效果，只要在线就显示
                    val showRadar = true // 强制显示雷达以响应用户反馈，实际逻辑应判断是否有方向

                    if (showRadar) {
                        // 再次确保传入计算的值是安全的
                        val safeBearing = if (displayBearing.isNaN()) 0f else displayBearing

                        val sectorPoints = calculateSectorPoints(
                            center = device.location,
                            radius = 220.0, // 增大半径到220米，确保可见
                            direction = safeBearing,
                            fov = 30f // 增大视野角度到30度
                        )

                        Polygon(
                            points = sectorPoints,
                            fillColor = androidx.compose.ui.graphics.Color(0x55007AFF), // 加深颜色 (33%不透明度)
                            strokeColor = androidx.compose.ui.graphics.Color(0xFF007AFF), // 添加实心边框
                            strokeWidth = 2f,
                            zIndex = 1f
                        )
                    }

                    // 缓存 Marker Icon 以避免重组时闪烁
                    val icon = remember(isCurrentDevice) {
                        BitmapDescriptorFactory.defaultMarker(
                            if (isCurrentDevice) BitmapDescriptorFactory.HUE_AZURE
                            else BitmapDescriptorFactory.HUE_RED
                        )
                    }

                    Marker(
                        state = markerState,
                        title = device.name,
                        snippet = if (isCurrentDevice) "当前设备" else device.id,
                        // 当前设备使用蓝色标记，其他设备使用红色标记
                        icon = icon,
                        // 标记保持竖直，不随方向转动
                        rotation = 0f,
                        // 使标记垂直于屏幕，不平铺在地图上
                        flat = false,
                        // 设置锚点为底部中心，使标记的尖端指向设备坐标（即扇形的圆心）
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 1.0f),
                        zIndex = 2f,
                        onClick = {
                            onMarkerClick(device)
                            false // Return false to allow default behavior (showing info window), or true to consume
                        }
                    )
                }
            }
        }

        // 渲染联系人位置标记
        contacts.forEach { contact ->
            // 只渲染有位置信息且位置可用的联系人
            contact.location?.let { location ->
                if (!location.latitude.isNaN() && !location.longitude.isNaN()) {
                    androidx.compose.runtime.key(contact.id) {
                        val markerState =
                            com.google.maps.android.compose.rememberMarkerState(position = location)

                        // 当联系人位置更新时，同步更新 Marker 状态
                        LaunchedEffect(location) {
                            if (!location.latitude.isNaN() && !location.longitude.isNaN()) {
                                markerState.position = location
                            }
                        }

                        // 缓存联系人 Marker Icon (使用绿色)
                        val contactIcon = remember {
                            BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_GREEN
                            )
                        }

                        Marker(
                            state = markerState,
                            title = contact.name,
                            snippet = "联系人位置",
                            icon = contactIcon,
                            rotation = 0f,
                            flat = false,
                            anchor = androidx.compose.ui.geometry.Offset(0.5f, 1.0f),
                            zIndex = 2f,
                            onClick = {
                                onContactMarkerClick(contact)
                                false
                            }
                        )
                    }
                }
            }
        }

        // 我们通过 MapEffect 获取原生的 GoogleMap 对象并传递出去
        // 这允许外部使用 MapCameraHelper 进行复杂的相机操作
        com.google.maps.android.compose.MapEffect(Unit) { map ->
            onMapReady(map)
        }
    }
}

/**
 * 计算扇形多边形的顶点列表
 */
private fun calculateSectorPoints(
    center: LatLng,
    radius: Double,
    direction: Float,
    fov: Float = 60f
): List<LatLng> {
    val points = mutableListOf<LatLng>()
    points.add(center) // 圆心

    val startAngle = direction - fov / 2
    val endAngle = direction + fov / 2

    // 每 5 度取一个点，画弧线
    var angle = startAngle
    while (angle <= endAngle) {
        points.add(computeOffset(center, radius, angle.toDouble()))
        angle += 5
    }
    // 确保包含结束角
    points.add(computeOffset(center, radius, endAngle.toDouble()))

    points.add(center) // 闭合
    return points
}

/**
 * 计算给定距离和方位的目标坐标
 * (简化版球面公式)
 */
private fun computeOffset(from: LatLng, distance: Double, heading: Double): LatLng {
    val d = distance / 6371009.0 // 地球半径 (米)
    val h = Math.toRadians(heading)
    val fromLat = Math.toRadians(from.latitude)
    val fromLng = Math.toRadians(from.longitude)

    val lat = asin(sin(fromLat) * cos(d) + cos(fromLat) * sin(d) * cos(h))
    val lng = fromLng + atan2(sin(h) * sin(d) * cos(fromLat), cos(d) - sin(fromLat) * sin(lat))

    return LatLng(Math.toDegrees(lat), Math.toDegrees(lng))
}