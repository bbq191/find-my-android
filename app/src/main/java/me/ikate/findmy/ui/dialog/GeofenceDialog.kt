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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.tencent.tencentmap.mapsdk.maps.model.Circle
import com.tencent.tencentmap.mapsdk.maps.model.CircleOptions
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import com.tencent.tencentmap.mapsdk.maps.model.Marker
import com.tencent.tencentmap.mapsdk.maps.model.MarkerOptions
import kotlinx.coroutines.launch
import me.ikate.findmy.util.ReverseGeocodeHelper

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

    // 状态
    var locationName by remember { mutableStateOf(currentConfig.locationName.ifBlank { "$contactName 的位置" }) }
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
    val centerMarkerRef = remember { mutableStateOf<Marker?>(null) }
    val contactMarkerRef = remember { mutableStateOf<Marker?>(null) }

    // 加载联系人图标
    val pigBitmap = remember {
        val resourceId = context.resources.getIdentifier("marker_pig", "drawable", context.packageName)
        if (resourceId != 0) {
            BitmapFactory.decodeResource(context.resources, resourceId)
        } else null
    }

    // 围栏圆圈颜色
    val circleStrokeColor = 0xFF007AFF.toInt()
    val circleFillColor = 0x33007AFF

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
                // 顶部栏
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
                    actions = {
                        if (contactLocation != null) {
                            if (isEnabled) {
                                TextButton(
                                    onClick = {
                                        onConfirm(GeofenceConfig(enabled = false))
                                    }
                                ) {
                                    Text("移除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            TextButton(
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
                                Text(if (isEnabled) "更新" else "保存")
                            }
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

                                        // 设置地图点击监听
                                        tencentMap.setOnMapClickListener { latLng ->
                                            geofenceCenter = latLng
                                            updateGeofenceOnMap(
                                                tencentMap = tencentMap,
                                                center = latLng,
                                                radius = radiusMeters.toDouble(),
                                                circleRef = circleRef,
                                                centerMarkerRef = centerMarkerRef,
                                                strokeColor = circleStrokeColor,
                                                fillColor = circleFillColor
                                            )
                                            // 逆地理编码获取地址
                                            fetchAddressForLocation(latLng)
                                        }

                                        // 添加联系人位置标记
                                        pigBitmap?.let { bitmap ->
                                            contactMarkerRef.value = tencentMap.addMarker(
                                                MarkerOptions()
                                                    .position(contactLocation)
                                                    .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                                                    .anchor(0.5f, 0.95f)
                                            )
                                        }

                                        // 初始化围栏圆圈
                                        val initialCenter = geofenceCenter ?: contactLocation
                                        updateGeofenceOnMap(
                                            tencentMap = tencentMap,
                                            center = initialCenter,
                                            radius = radiusMeters.toDouble(),
                                            circleRef = circleRef,
                                            centerMarkerRef = centerMarkerRef,
                                            strokeColor = circleStrokeColor,
                                            fillColor = circleFillColor
                                        )

                                        // 移动相机到围栏中心
                                        tencentMap.moveCamera(
                                            CameraUpdateFactory.newLatLngZoom(initialCenter, 15f)
                                        )
                                    }
                                }
                            }
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
                                text = "点击地图设置围栏中心",
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
                            // 位置名称（支持逆地理编码自动填充）
                            OutlinedTextField(
                                value = if (isLoadingAddress) "正在获取地址..." else locationName,
                                onValueChange = { locationName = it },
                                label = { Text("位置名称") },
                                placeholder = { Text("例如：家、公司") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isLoadingAddress
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 围栏半径
                            Text(
                                text = "围栏半径: ${radiusMeters.toInt()} 米",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = radiusMeters,
                                onValueChange = { newRadius ->
                                    radiusMeters = newRadius
                                    // 实时更新地图上的圆圈
                                    tencentMapRef.value?.let { tencentMap ->
                                        geofenceCenter?.let { center ->
                                            updateGeofenceOnMap(
                                                tencentMap = tencentMap,
                                                center = center,
                                                radius = newRadius.toDouble(),
                                                circleRef = circleRef,
                                                centerMarkerRef = centerMarkerRef,
                                                strokeColor = circleStrokeColor,
                                                fillColor = circleFillColor
                                            )
                                        }
                                    }
                                },
                                valueRange = 50f..1000f,
                                steps = 18,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("50m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                Text("1000m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 通知类型选择（统一颜色）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = notifyOnEnter,
                                    onClick = { notifyOnEnter = !notifyOnEnter },
                                    label = { Text("到达时通知") },
                                    leadingIcon = if (notifyOnEnter) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = notifyOnExit,
                                    onClick = { notifyOnExit = !notifyOnExit },
                                    label = { Text("离开时通知") },
                                    leadingIcon = if (notifyOnExit) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // 智能推荐提示
                            val currentCenter = geofenceCenter
                            if (currentCenter != null) {
                                val distance = calculateDistance(currentCenter, contactLocation)
                                val isInside = distance < radiusMeters
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isInside) {
                                        "💡 联系人当前在围栏内，推荐选择「离开时通知」"
                                    } else {
                                        "💡 联系人当前在围栏外，推荐选择「到达时通知」"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 更新地图上的围栏圆圈
 */
private fun updateGeofenceOnMap(
    tencentMap: TencentMap,
    center: LatLng,
    radius: Double,
    circleRef: androidx.compose.runtime.MutableState<Circle?>,
    centerMarkerRef: androidx.compose.runtime.MutableState<Marker?>,
    strokeColor: Int,
    fillColor: Int
) {
    // 更新或创建圆圈
    val existingCircle = circleRef.value
    if (existingCircle != null) {
        existingCircle.center = center
        existingCircle.radius = radius
    } else {
        circleRef.value = tencentMap.addCircle(
            CircleOptions()
                .center(center)
                .radius(radius)
                .strokeColor(strokeColor)
                .strokeWidth(3f)
                .fillColor(fillColor)
        )
    }

    // 更新中心点标记（可选，用于更清晰显示）
    centerMarkerRef.value?.remove()
    centerMarkerRef.value = tencentMap.addMarker(
        MarkerOptions()
            .position(center)
            .anchor(0.5f, 0.5f)
    ).apply {
        // 设置小圆点标记
        alpha = 0.8f
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
