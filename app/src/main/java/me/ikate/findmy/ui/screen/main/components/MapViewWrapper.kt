package me.ikate.findmy.ui.screen.main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.MapsComposeExperimentalApi
import me.ikate.findmy.data.model.Device

/**
 * Google Maps 视图 Compose 包装器
 * 使用 Google Maps Compose 库
 *
 * @param modifier 修饰符
 * @param devices 设备列表（用于渲染 Marker）
 * @param onMapReady 地图准备完成回调，返回 GoogleMap 实例
 * @param onMarkerClick Marker 点击回调，返回点击的设备
 * @param onMapClick 地图空白区域点击回调
 */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun MapViewWrapper(
    modifier: Modifier = Modifier,
    devices: List<Device> = emptyList(),
    onMapReady: (GoogleMap) -> Unit = {},
    onMarkerClick: (Device) -> Unit = {},
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
    
    // 监听 MapLoaded
    var isMapLoaded by remember { mutableStateOf(false) }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = properties,
        uiSettings = uiSettings,
        onMapLoaded = {
            isMapLoaded = true
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
            // 判断是否为当前设备
            val isCurrentDevice = device.id == currentDeviceId

            Marker(
                state = MarkerState(position = device.location),
                title = device.name,
                snippet = if (isCurrentDevice) "当前设备" else device.id,
                // 当前设备使用蓝色标记，其他设备使用红色标记
                icon = BitmapDescriptorFactory.defaultMarker(
                    if (isCurrentDevice) BitmapDescriptorFactory.HUE_AZURE
                    else BitmapDescriptorFactory.HUE_RED
                ),
                // 设置旋转角度，显示GPS方向
                // bearing为0表示正北，顺时针增加
                // 只有在设备有明确方向时才旋转（bearing > 0）
                rotation = if (device.bearing > 0) device.bearing else 0f,
                // 使标记平铺在地图上，旋转效果更明显
                flat = device.bearing > 0,
                // 设置锚点，使标记以底部中心为旋转点
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 1.0f),
                onClick = {
                    onMarkerClick(device)
                    false // Return false to allow default behavior (showing info window), or true to consume
                }
            )
        }
        
        // 我们通过 MapEffect 获取原生的 GoogleMap 对象并传递出去
        // 这允许外部使用 MapCameraHelper 进行复杂的相机操作
        com.google.maps.android.compose.MapEffect(Unit) { map ->
            onMapReady(map)
        }
    }
}