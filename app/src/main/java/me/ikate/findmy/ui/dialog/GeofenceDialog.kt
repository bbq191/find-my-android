@file:Suppress("DEPRECATION")

package me.ikate.findmy.ui.dialog

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tencent.tencentmap.mapsdk.maps.CameraUpdateFactory
import com.tencent.tencentmap.mapsdk.maps.MapView
import com.tencent.tencentmap.mapsdk.maps.TencentMap
import com.tencent.tencentmap.mapsdk.maps.model.BitmapDescriptorFactory
import com.tencent.tencentmap.mapsdk.maps.model.CameraPosition
import com.tencent.tencentmap.mapsdk.maps.model.Circle
import com.tencent.tencentmap.mapsdk.maps.model.CircleOptions
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import com.tencent.tencentmap.mapsdk.maps.model.Marker
import com.tencent.tencentmap.mapsdk.maps.model.MarkerOptions
import kotlinx.coroutines.launch
import me.ikate.findmy.util.ReverseGeocodeHelper
import me.ikate.findmy.util.rememberHaptics

/**
 * 地理围栏事件类型
 */
enum class GeofenceEventType {
    ENTER,      // 进入
    EXIT,       // 离开
    BOTH        // 两者
}

/**
 * 地理围栏配置
 */
data class GeofenceConfig(
    val enabled: Boolean = false,
    val locationName: String = "",
    val center: LatLng? = null,
    val radiusMeters: Float = 200f,
    val eventType: GeofenceEventType = GeofenceEventType.BOTH,
    val notifyOnEnter: Boolean = true,
    val notifyOnExit: Boolean = true
)

/**
 * 地理围栏设置对话框（全屏，带地图）
 *
 * 功能：
 * - 在地图上显示联系人位置
 * - 可点击地图设置围栏中心
 * - 可调整围栏半径
 * - 可设置进入/离开通知
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceDialog(
    contactName: String,
    contactLocation: LatLng?,
    currentConfig: GeofenceConfig = GeofenceConfig(),
    onDismiss: () -> Unit,
    onConfirm: (GeofenceConfig) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    // 状态
    var locationName by remember { mutableStateOf(currentConfig.locationName.ifBlank { "$contactName 的位置" }) }
    // 用于刻度震动：记录上一个步进值
    var lastSliderStep by remember { mutableStateOf((currentConfig.radiusMeters / 50f).toInt()) }
    var isLoadingAddress by remember { mutableStateOf(false) }
    var radiusMeters by remember { mutableFloatStateOf(currentConfig.radiusMeters) }
    var notifyOnEnter by remember { mutableStateOf(currentConfig.notifyOnEnter) }
    var notifyOnExit by remember { mutableStateOf(currentConfig.notifyOnExit) }
    var geofenceCenter by remember { mutableStateOf(currentConfig.center ?: contactLocation) }

    val isEnabled = currentConfig.enabled

    // 地图相关状态
    val mapView = remember { MapView(context) }
    val tencentMapRef = remember { mutableStateOf<TencentMap?>(null) }
    val circleRef = remember { mutableStateOf<Circle?>(null) }
    // 中心 Marker 已改用 Compose 层叠的 Pin 图标实现
    val contactMarkerRef = remember { mutableStateOf<Marker?>(null) }

    // 加载联系人图标
    val pigBitmap = remember {
        val resourceId = context.resources.getIdentifier("marker_pig", "drawable", context.packageName)
        if (resourceId != 0) {
            BitmapFactory.decodeResource(context.resources, resourceId)
        } else null
    }

    // 围栏圆圈颜色 - 更轻盈的视觉效果 (透明度 10-15%)
    val circleStrokeColor = 0xFF007AFF.toInt()
    val circleFillColor = 0x1A007AFF  // 约 10% 透明度

    // 地图拖动状态
    var isMapDragging by remember { mutableStateOf(false) }

    // 逆地理编码获取地址
    fun fetchAddressForLocation(latLng: LatLng) {
        scope.launch {
            isLoadingAddress = true
            val address = ReverseGeocodeHelper.getAddressFromLatLng(context, latLng)
            locationName = address
            isLoadingAddress = false
        }
    }

    // 初始化时获取位置名称（如果是新围栏）
    LaunchedEffect(Unit) {
        if (currentConfig.locationName.isBlank() && contactLocation != null) {
            fetchAddressForLocation(geofenceCenter ?: contactLocation)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部栏 - 简洁版，操作按钮移至底部
                TopAppBar(
                    title = {
                        Text(
                            text = if (isEnabled) "编辑地理围栏" else "设置地理围栏",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                if (contactLocation == null) {
                    // 无位置时显示错误
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "无法设置地理围栏",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "联系人位置不可用",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                } else {
                    // 地图区域
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // 生命周期管理
                        DisposableEffect(lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                when (event) {
                                    Lifecycle.Event.ON_START -> mapView.onStart()
                                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                                    else -> {}
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                        AndroidView(
                            factory = { mapView },
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                view.map?.let { tencentMap ->
                                    if (tencentMapRef.value == null) {
                                        tencentMapRef.value = tencentMap

                                        // 配置地图
                                        tencentMap.uiSettings.apply {
                                            isZoomControlsEnabled = false
                                            isCompassEnabled = false
                                            isScaleViewEnabled = true
                                        }

                                        // 设置相机变化监听 - "定针不动，地图动"模式
                                        tencentMap.setOnCameraChangeListener(object : TencentMap.OnCameraChangeListener {
                                            override fun onCameraChange(position: CameraPosition) {
                                                // 拖动中：实时更新围栏位置
                                                isMapDragging = true
                                                geofenceCenter = position.target
                                                updateGeofenceCircleOnly(
                                                    circleRef = circleRef,
                                                    center = position.target,
                                                    radius = radiusMeters.toDouble()
                                                )
                                            }

                                            override fun onCameraChangeFinished(position: CameraPosition) {
                                                // 拖动结束：更新位置并获取地址
                                                isMapDragging = false
                                                geofenceCenter = position.target
                                                updateGeofenceCircleOnly(
                                                    circleRef = circleRef,
                                                    center = position.target,
                                                    radius = radiusMeters.toDouble()
                                                )
                                                // 逆地理编码获取地址
                                                fetchAddressForLocation(position.target)
                                            }
                                        })

                                        // 添加联系人位置标记
                                        pigBitmap?.let { bitmap ->
                                            contactMarkerRef.value = tencentMap.addMarker(
                                                MarkerOptions()
                                                    .position(contactLocation)
                                                    .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                                                    .anchor(0.5f, 0.95f)
                                            )
                                        }

                                        // 初始化围栏圆圈（不添加中心 Marker，使用 Compose 层叠的 Pin）
                                        val initialCenter = geofenceCenter ?: contactLocation
                                        circleRef.value = tencentMap.addCircle(
                                            CircleOptions()
                                                .center(initialCenter)
                                                .radius(radiusMeters.toDouble())
                                                .strokeColor(circleStrokeColor)
                                                .strokeWidth(2f)  // 2dp 描边
                                                .fillColor(circleFillColor)
                                        )

                                        // 移动相机到围栏中心
                                        tencentMap.moveCamera(
                                            CameraUpdateFactory.newLatLngZoom(initialCenter, 15f)
                                        )
                                    }
                                }
                            }
                        )

                        // 中心固定 Pin 图标 - "定针不动，地图动"
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "围栏中心",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(y = (-20).dp)  // 向上偏移，使 Pin 底部对准中心
                                .size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        // 提示文字
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isMapDragging) "松开以选定位置" else "拖动地图选择围栏中心",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 控制面板
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // 位置名称 - 纯文本展示，去除输入框感
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isLoadingAddress) "正在获取地址..." else locationName.ifBlank { "未知位置" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isLoadingAddress) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                           else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // 围栏半径 - M3 标准 Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "围栏半径",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${radiusMeters.toInt()} 米",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = radiusMeters,
                                onValueChange = { newRadius ->
                                    // 关键节点吸附 (100, 200, 500, 1000)
                                    val snappedRadius = snapToKeyPoints(newRadius)
                                    // 检测步进变化，触发刻度震动
                                    val currentStep = (snappedRadius / 50f).toInt()
                                    if (currentStep != lastSliderStep) {
                                        haptics.tick()
                                        lastSliderStep = currentStep
                                    }
                                    radiusMeters = snappedRadius
                                    // 实时更新地图上的圆圈
                                    updateGeofenceCircleOnly(
                                        circleRef = circleRef,
                                        center = geofenceCenter,
                                        radius = snappedRadius.toDouble()
                                    )
                                },
                                valueRange = 50f..1000f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("50m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("1km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // 触发条件 - Segmented Button
                            Text(
                                text = "触发条件",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SegmentedButton(
                                    selected = notifyOnEnter && !notifyOnExit,
                                    onClick = {
                                        notifyOnEnter = true
                                        notifyOnExit = false
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                    icon = { Icon(Icons.Outlined.Login, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                ) {
                                    Text("到达")
                                }
                                SegmentedButton(
                                    selected = notifyOnExit && !notifyOnEnter,
                                    onClick = {
                                        notifyOnEnter = false
                                        notifyOnExit = true
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                    icon = { Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                ) {
                                    Text("离开")
                                }
                                SegmentedButton(
                                    selected = notifyOnEnter && notifyOnExit,
                                    onClick = {
                                        notifyOnEnter = true
                                        notifyOnExit = true
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                                ) {
                                    Text("两者")
                                }
                            }

                            // 智能推荐提示 - Banner 样式
                            val currentCenter = geofenceCenter
                            if (currentCenter != null) {
                                val distance = calculateDistance(currentCenter, contactLocation)
                                val isInside = distance < radiusMeters
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isInside) {
                                                "联系人当前在围栏内，推荐选择「离开」"
                                            } else {
                                                "联系人当前在围栏外，推荐选择「到达」"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // 操作按钮 - 全部在底部
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 删除按钮（仅编辑模式显示）
                                if (isEnabled) {
                                    TextButton(
                                        onClick = {
                                            onConfirm(GeofenceConfig(enabled = false))
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("移除围栏")
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // 保存/更新按钮 - FilledButton
                                Button(
                                    onClick = {
                                        if ((notifyOnEnter || notifyOnExit) && geofenceCenter != null) {
                                            onConfirm(
                                                GeofenceConfig(
                                                    enabled = true,
                                                    locationName = locationName,
                                                    center = geofenceCenter,
                                                    radiusMeters = radiusMeters,
                                                    notifyOnEnter = notifyOnEnter,
                                                    notifyOnExit = notifyOnExit
                                                )
                                            )
                                        }
                                    },
                                    enabled = (notifyOnEnter || notifyOnExit) && geofenceCenter != null
                                ) {
                                    Text(if (isEnabled) "更新围栏" else "保存围栏")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 关键节点吸附（100, 200, 500, 1000）
 */
private fun snapToKeyPoints(value: Float): Float {
    val keyPoints = listOf(100f, 200f, 500f, 1000f)
    val snapThreshold = 20f  // 吸附阈值

    for (point in keyPoints) {
        if (kotlin.math.abs(value - point) < snapThreshold) {
            return point
        }
    }
    return value
}

/**
 * 仅更新围栏圆圈位置和半径（不添加 Marker，使用 Compose 层叠的 Pin）
 */
private fun updateGeofenceCircleOnly(
    circleRef: androidx.compose.runtime.MutableState<Circle?>,
    center: LatLng?,
    radius: Double
) {
    center ?: return
    circleRef.value?.let { circle ->
        circle.center = center
        circle.radius = radius
    }
}

/**
 * 计算两点间距离（Haversine 公式）
 */
private fun calculateDistance(point1: LatLng, point2: LatLng): Double {
    val earthRadius = 6371000.0 // 地球半径（米）

    val lat1 = Math.toRadians(point1.latitude)
    val lat2 = Math.toRadians(point2.latitude)
    val deltaLat = Math.toRadians(point2.latitude - point1.latitude)
    val deltaLng = Math.toRadians(point2.longitude - point1.longitude)

    val a = kotlin.math.sin(deltaLat / 2) * kotlin.math.sin(deltaLat / 2) +
            kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
            kotlin.math.sin(deltaLng / 2) * kotlin.math.sin(deltaLng / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

    return earthRadius * c
}
