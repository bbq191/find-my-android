@file:Suppress("DEPRECATION")

package me.ikate.findmy.ui.screen.geofence

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

/**
 * iOS Find My 风格围栏颜色
 */
private object GeofenceColors {
    val strokeColor = 0xFF007AFF.toInt()  // iOS 蓝色
    val fillColor = 0x1A007AFF            // 10% 透明度
}

/**
 * 地理围栏地图层
 * 全屏显示腾讯地图，包含：
 * - 中心固定 Pin 图标
 * - 实时围栏圆圈（与半径联动）
 * - 联系人位置标记
 *
 * @param state 编辑器状态
 * @param contactLocation 联系人位置
 * @param onCenterChanged 中心点变化回调
 * @param onAddressChanged 地址变化回调
 * @param onDraggingChanged 拖动状态变化回调
 */
@Composable
fun GeofenceMapLayer(
    state: GeofenceEditorState,
    contactLocation: LatLng?,
    onCenterChanged: (LatLng) -> Unit,
    onAddressChanged: (String) -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // 地图相关引用
    val mapView = remember { MapView(context) }
    var tencentMap by remember { mutableStateOf<TencentMap?>(null) }
    var circle by remember { mutableStateOf<Circle?>(null) }
    var contactMarker by remember { mutableStateOf<Marker?>(null) }
    var isMapInitialized by remember { mutableStateOf(false) }

    // 加载联系人图标
    val pigBitmap = remember {
        val resourceId = context.resources.getIdentifier("marker_pig", "drawable", context.packageName)
        if (resourceId != 0) {
            BitmapFactory.decodeResource(context.resources, resourceId)
        } else null
    }

    // 获取地址
    fun fetchAddress(latLng: LatLng) {
        scope.launch {
            state.updateLoadingState(true)
            val address = ReverseGeocodeHelper.getAddressFromLatLng(context, latLng)
            onAddressChanged(address)
            state.updateLoadingState(false)
        }
    }

    // 监听半径变化，更新圆圈
    LaunchedEffect(state.radiusMeters) {
        circle?.radius = state.radiusMeters.toDouble()
    }

    // 监听选中中心点变化
    LaunchedEffect(state.selectedCenter) {
        state.selectedCenter?.let { center ->
            circle?.center = center
            if (isMapInitialized) {
                tencentMap?.animateCamera(
                    CameraUpdateFactory.newLatLng(center),
                    300,
                    null
                )
            }
        }
    }

    // 初始获取地址
    LaunchedEffect(Unit) {
        state.selectedCenter?.let { fetchAddress(it) }
    }

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

    Box(modifier = modifier.fillMaxSize()) {
        // 腾讯地图
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.map?.let { map ->
                    if (tencentMap == null) {
                        tencentMap = map

                        // 配置地图 UI
                        map.uiSettings.apply {
                            isZoomControlsEnabled = false
                            isCompassEnabled = false
                            isScaleViewEnabled = true
                        }

                        // 设置相机变化监听
                        map.setOnCameraChangeListener(object : TencentMap.OnCameraChangeListener {
                            override fun onCameraChange(position: CameraPosition) {
                                onDraggingChanged(true)
                                onCenterChanged(position.target)
                                circle?.center = position.target
                            }

                            override fun onCameraChangeFinished(position: CameraPosition) {
                                onDraggingChanged(false)
                                onCenterChanged(position.target)
                                circle?.center = position.target
                                fetchAddress(position.target)
                            }
                        })

                        // 添加联系人位置标记
                        if (contactLocation != null && pigBitmap != null) {
                            contactMarker = map.addMarker(
                                MarkerOptions()
                                    .position(contactLocation)
                                    .icon(BitmapDescriptorFactory.fromBitmap(pigBitmap))
                                    .anchor(0.5f, 0.95f)
                            )
                        }

                        // 初始化围栏圆圈
                        val initialCenter = state.selectedCenter ?: contactLocation
                        if (initialCenter != null) {
                            circle = map.addCircle(
                                CircleOptions()
                                    .center(initialCenter)
                                    .radius(state.radiusMeters.toDouble())
                                    .strokeColor(GeofenceColors.strokeColor)
                                    .strokeWidth(2f)
                                    .fillColor(GeofenceColors.fillColor)
                            )

                            // 移动相机到初始位置
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(initialCenter, 15f)
                            )
                        }

                        isMapInitialized = true
                    }
                }
            }
        )

        // 中心固定 Pin 图标
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = "围栏中心",
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-20).dp)
                .size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
