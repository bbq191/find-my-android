@file:Suppress("DEPRECATION")

package me.ikate.findmy.ui.screen.main.components

import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import android.view.animation.LinearInterpolator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.ShareStatus
import me.ikate.findmy.data.model.latLngOf
import me.ikate.findmy.service.TrackingState
import android.util.Log
import android.app.Activity
import me.ikate.findmy.util.AppIconHelper
import me.ikate.findmy.util.DeviceIdProvider
import me.ikate.findmy.util.DeviceOptimizationConfig
import me.ikate.findmy.util.MapSettingsManager
import me.ikate.findmy.util.RefreshRateManager
import me.ikate.findmy.util.animation.SmoothMarker
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 腾讯地图 Compose 包装器
 * 使用腾讯地图 SDK 实现地图显示和标记功能
 *
 * @param modifier 修饰符
 * @param devices 设备列表（用于渲染 Marker）
 * @param contacts 联系人列表（用于渲染联系人位置 Marker）
 * @param currentDeviceHeading 当前设备实时朝向（来自传感器）
 * @param currentDeviceRealtimeLocation 当前设备实时位置（追踪自己时更新更快）
 * @param showTraffic 是否显示路况图层
 * @param mapLayerConfig 地图图层配置
 * @param bottomPadding 底部 padding
 * @param trackingTargetId 正在追踪的目标 ID（设备 ID 或联系人 ID，null 表示不追踪）
 * @param trackingContactUid 正在追踪的联系人 UID（用于脉冲动画）
 * @param trackingStates 追踪状态 Map（用于脉冲动画颜色）
 * @param allContacts 完整联系人列表（用于脉冲动画查找位置，不受 tab 过滤影响）
 * @param onMapReady 地图准备完成回调
 * @param onMarkerClick Marker 点击回调
 * @param onContactMarkerClick 联系人 Marker 点击回调
 * @param onMapClick 地图空白区域点击回调
 * @param onUserInteraction 用户手动拖动地图时的回调（用于停止追踪）
 */
@Composable
fun TencentMapViewWrapper(
    modifier: Modifier = Modifier,
    devices: List<Device> = emptyList(),
    contacts: List<Contact> = emptyList(),
    currentDeviceHeading: Float? = null,
    currentDeviceRealtimeLocation: LatLng? = null,
    showTraffic: Boolean = false,
    mapLayerConfig: MapLayerConfig = MapLayerConfig(),
    bottomPadding: Dp = 0.dp,
    trackingTargetId: String? = null,
    trackingContactUid: String? = null,
    trackingStates: Map<String, TrackingState> = emptyMap(),
    allContacts: List<Contact> = emptyList(),  // 用于脉冲动画查找联系人位置
    onMapReady: (TencentMap) -> Unit = {},
    onMarkerClick: (Device) -> Unit = {},
    onContactMarkerClick: (Contact) -> Unit = {},
    onMapClick: () -> Unit = {},
    onUserInteraction: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // MapView 实例和初始化状态
    val mapView = remember { MapView(context) }
    val isMapCreated = remember { mutableStateOf(false) }

    // 选中的 Marker ID
    val selectedMarkerId = remember { mutableStateOf<String?>(null) }

    // 获取当前设备 ID（使用 DeviceIdProvider 统一管理）
    val currentDeviceId = remember { DeviceIdProvider.getDeviceId(context) }

    // 准备 Marker 图标
    val currentIcon = remember { AppIconHelper.getCurrentIcon(context) }

    // 加载图标 Bitmap（使用 mutableStateOf + DisposableEffect 管理生命周期，防止内存泄漏）
    val avatarBitmapState = remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val pigBitmapState = remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val togetherBitmapState = remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // 加载并管理 avatarBitmap 生命周期
    DisposableEffect(currentIcon) {
        val isGirl = currentIcon == AppIconHelper.AppIcon.GIRL
        val resourceId = if (isGirl) {
            context.resources.getIdentifier("marker_girl", "drawable", context.packageName)
        } else {
            context.resources.getIdentifier("marker_boy", "drawable", context.packageName)
        }
        val bitmap = if (resourceId != 0) {
            BitmapFactory.decodeResource(context.resources, resourceId)
        } else null
        avatarBitmapState.value = bitmap

        onDispose {
            bitmap?.recycle()
            avatarBitmapState.value = null
        }
    }

    // 加载并管理 pigBitmap 生命周期
    DisposableEffect(Unit) {
        val resourceId = context.resources.getIdentifier("marker_pig", "drawable", context.packageName)
        val bitmap = if (resourceId != 0) {
            BitmapFactory.decodeResource(context.resources, resourceId)
        } else null
        pigBitmapState.value = bitmap

        onDispose {
            bitmap?.recycle()
            pigBitmapState.value = null
        }
    }

    // 加载并管理 togetherBitmap 生命周期
    DisposableEffect(currentIcon) {
        val isGirl = currentIcon == AppIconHelper.AppIcon.GIRL
        val resourceId = if (isGirl) {
            context.resources.getIdentifier("marker_together_g", "drawable", context.packageName)
        } else {
            context.resources.getIdentifier("marker_together_b", "drawable", context.packageName)
        }
        val bitmap = if (resourceId != 0) {
            BitmapFactory.decodeResource(context.resources, resourceId)
        } else null
        togetherBitmapState.value = bitmap

        onDispose {
            bitmap?.recycle()
            togetherBitmapState.value = null
        }
    }

    // 获取当前 Bitmap 值（供后续使用）
    val avatarBitmap = avatarBitmapState.value
    val pigBitmap = pigBitmapState.value
    val togetherBitmap = togetherBitmapState.value

    // 获取当前设备
    val currentDevice = devices.find { it.id == currentDeviceId }

    // 合并距离阈值
    val mergeDistanceMeters = 10.0

    // 过滤有效联系人
    val validContacts = remember(contacts) {
        contacts.filter { contact ->
            val hasValidLocation = contact.location?.let { loc ->
                !loc.latitude.isNaN() && !loc.longitude.isNaN()
            } ?: false
            val isActive = contact.shareStatus == ShareStatus.ACCEPTED &&
                    !contact.isPaused &&
                    contact.isLocationAvailable
            hasValidLocation && isActive
        }
    }

    // 计算与当前设备合并的联系人
    val contactsMergedWithDevice = remember(currentDevice?.location, validContacts) {
        val result = mutableSetOf<String>()
        val deviceLoc = currentDevice?.location
        if (deviceLoc != null && !deviceLoc.latitude.isNaN() && !deviceLoc.longitude.isNaN()) {
            validContacts.forEach { contact ->
                contact.location?.let { contactLoc ->
                    val distance = calculateDistance(deviceLoc, contactLoc)
                    if (distance < mergeDistanceMeters) {
                        result.add(contact.id)
                    }
                }
            }
        }
        result
    }

    // 计算联系人合并组
    val contactGroups = remember(validContacts) {
        calculateMergeGroups(validContacts, mergeDistanceMeters)
    }

    val deviceShouldShowMergedIcon = contactsMergedWithDevice.isNotEmpty()

    // 保存 Marker 引用，用于后续更新（使用 SmoothMarker 封装实现 iOS Find My 级别丝滑动画）
    val deviceMarkerRef = remember { mutableStateOf<Marker?>(null) }
    val deviceSmoothMarkerRef = remember { mutableStateOf<SmoothMarker?>(null) }
    val contactMarkersRef = remember { mutableStateOf<Map<String, SmoothMarker>>(emptyMap()) }
    val contactCirclesRef = remember { mutableStateOf<Map<String, Circle>>(emptyMap()) }

    // 追踪脉冲动画相关状态
    val pulseCircleRef = remember { mutableStateOf<Circle?>(null) }
    val pulseAnimatorRef = remember { mutableStateOf<ValueAnimator?>(null) }
    val lastTrackingContactUidRef = remember { mutableStateOf<String?>(null) }
    val lastPulseStateRef = remember { mutableStateOf<TrackingState?>(null) }

    // 设备优化动画配置（针对三星 S24 Ultra 等高刷屏设备优化）
    val animConfig = remember { DeviceOptimizationConfig.getAnimationConfig() }

    // 地图实例引用
    val mapInstanceRef = remember { mutableStateOf<TencentMap?>(null) }

    // 联系人动画位置（用于相机追踪）
    val animatedContactPositionsRef = remember { mutableStateOf<Map<String, LatLng>>(emptyMap()) }

    // 追踪相机动画相关状态
    val cameraAnimatorRef = remember { mutableStateOf<ValueAnimator?>(null) }
    val lastTrackingPositionRef = remember { mutableStateOf<LatLng?>(null) }

    // 刷新率管理器（针对三星 S24 Ultra 等 120Hz 设备优化）
    val refreshRateManager = remember {
        (context as? Activity)?.let { RefreshRateManager(it) }
    }

    // 根据追踪状态控制刷新率
    DisposableEffect(trackingTargetId, animConfig.preferHighRefreshRate) {
        if (trackingTargetId != null && animConfig.preferHighRefreshRate) {
            // 追踪模式：请求 120Hz 高刷新率
            refreshRateManager?.requestHighRefreshRate()
        } else {
            // 非追踪模式：使用系统自适应刷新率
            refreshRateManager?.setAdaptiveMode()
        }

        onDispose {
            // 组件销毁时恢复自适应模式
            refreshRateManager?.setAdaptiveMode()
        }
    }

    // 生命周期管理
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {}
                Lifecycle.Event.ON_START -> {
                    if (isMapCreated.value) {
                        mapView.onStart()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isMapCreated.value) {
                        mapView.onResume()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    if (isMapCreated.value) {
                        mapView.onPause()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (isMapCreated.value) {
                        mapView.onStop()
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    if (isMapCreated.value) {
                        mapView.onDestroy()
                        isMapCreated.value = false
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 取消设备 SmoothMarker 动画
            deviceSmoothMarkerRef.value?.cancel()
            // 取消相机动画
            cameraAnimatorRef.value?.cancel()
            cameraAnimatorRef.value = null
            // 取消所有联系人的 SmoothMarker 动画
            contactMarkersRef.value.values.forEach { it.cancel() }
            // 取消脉冲动画
            pulseAnimatorRef.value?.cancel()
            pulseAnimatorRef.value = null
            pulseCircleRef.value?.remove()
            pulseCircleRef.value = null
            if (isMapCreated.value) {
                mapView.onDestroy()
                isMapCreated.value = false
            }
        }
    }

    // 计算深色模式遮罩的透明度
    val effectivePreset = LightPreset.getEffectivePreset(mapLayerConfig.lightPreset)
    val isDarkMode = effectivePreset == LightPreset.DARK
    val overlayAlpha by animateFloatAsState(
        targetValue = if (isDarkMode) 0.35f else 0f,
        animationSpec = tween(500),
        label = "mapOverlay"
    )

    Box(modifier = modifier) {
        AndroidView(
            factory = {
                mapView.apply {
                    // 腾讯 MapView 无需显式调用 onCreate，直接标记已创建
                    if (!isMapCreated.value) {
                        isMapCreated.value = true
                    }
                    map.apply {
                        // UI 设置
                        uiSettings.apply {
                            isZoomControlsEnabled = false
                            isCompassEnabled = false
                            isMyLocationButtonEnabled = false
                            isScaleViewEnabled = false
                        }

                        // 地图点击事件
                        setOnMapClickListener {
                            selectedMarkerId.value = null
                            onMapClick()
                        }

                        // 用户拖动地图时停止追踪
                        setOnMapPoiClickListener {
                            if (trackingTargetId != null) {
                                onUserInteraction()
                            }
                        }

                        // Marker 点击事件
                        setOnMarkerClickListener { marker ->
                            val tag = marker.tag
                            when {
                                tag is Device -> {
                                    selectedMarkerId.value = tag.id
                                    onMarkerClick(tag)
                                }
                                tag is Contact -> {
                                    selectedMarkerId.value = "contact_${tag.id}"
                                    onContactMarkerClick(tag)
                                }
                            }
                            true
                        }

                        // 加载保存的地图类型
                        val savedStyleName = MapSettingsManager.loadMapStyle(context)
                        mapType = if (savedStyleName == "SATELLITE") {
                            TencentMap.MAP_TYPE_SATELLITE
                        } else {
                            TencentMap.MAP_TYPE_NORMAL
                        }

                        // 保存地图实例引用
                        mapInstanceRef.value = this

                        // 回调
                        onMapReady(this)
                    }
                }
            },
            update = { view ->
                try {
                    updateMapContent(
                        view = view,
                        showTraffic = showTraffic,
                        mapLayerConfig = mapLayerConfig,
                        currentDevice = currentDevice,
                        currentDeviceRealtimeLocation = currentDeviceRealtimeLocation,
                        currentDeviceHeading = currentDeviceHeading,
                        deviceShouldShowMergedIcon = deviceShouldShowMergedIcon,
                        avatarBitmap = avatarBitmap,
                        togetherBitmap = togetherBitmap,
                        pigBitmap = pigBitmap,
                        validContacts = validContacts,
                        contactsMergedWithDevice = contactsMergedWithDevice,
                        contactGroups = contactGroups,
                        trackingTargetId = trackingTargetId,
                        currentDeviceId = currentDeviceId,
                        devices = devices,
                        contacts = contacts,
                        // Refs（使用 SmoothMarker 封装）
                        deviceMarkerRef = deviceMarkerRef,
                        deviceSmoothMarkerRef = deviceSmoothMarkerRef,
                        contactMarkersRef = contactMarkersRef,
                        contactCirclesRef = contactCirclesRef,
                        animatedContactPositionsRef = animatedContactPositionsRef,
                        cameraAnimatorRef = cameraAnimatorRef,
                        lastTrackingPositionRef = lastTrackingPositionRef,
                        animConfig = animConfig
                    )

                    // 追踪脉冲动画逻辑
                    val tencentMap = view.map
                    val currentTrackingState = trackingContactUid?.let { trackingStates[it] }
                    // 使用 allContacts 查找联系人，而不是可能为空的 contacts
                    val trackedContact = trackingContactUid?.let { uid ->
                        allContacts.find { it.targetUserId == uid }
                    }

                    // 检测追踪目标或状态是否变化
                    val uidChanged = trackingContactUid != lastTrackingContactUidRef.value
                    val stateChanged = currentTrackingState != lastPulseStateRef.value

                    // 只在追踪目标或状态变化时更新脉冲圈
                    if (uidChanged || stateChanged) {
                        lastTrackingContactUidRef.value = trackingContactUid
                        lastPulseStateRef.value = currentTrackingState

                        // 移除旧的脉冲圈和动画
                        pulseAnimatorRef.value?.cancel()
                        pulseAnimatorRef.value = null
                        pulseCircleRef.value?.remove()
                        pulseCircleRef.value = null

                        if (trackingContactUid != null && trackedContact?.location != null) {
                            val location = trackedContact.location

                            when (currentTrackingState) {
                                TrackingState.WAITING, TrackingState.CONNECTED -> {
                                    val pulseColor = if (currentTrackingState == TrackingState.WAITING) {
                                        0x80FFC107.toInt()  // 黄色半透明
                                    } else {
                                        0x802196F3.toInt()  // 蓝色半透明
                                    }

                                    // 创建脉冲圈
                                    val pulseCircle = tencentMap.addCircle(
                                        CircleOptions()
                                            .center(location)
                                            .radius(20.0)
                                            .fillColor(pulseColor)
                                            .strokeColor(pulseColor or 0xFF000000.toInt())
                                            .strokeWidth(2f)
                                    )
                                    pulseCircleRef.value = pulseCircle

                                    // 创建脉冲动画
                                    val animator = ValueAnimator.ofFloat(20f, 100f).apply {
                                        duration = 1200
                                        repeatCount = ValueAnimator.INFINITE
                                        repeatMode = ValueAnimator.RESTART
                                        interpolator = LinearInterpolator()
                                        addUpdateListener { anim ->
                                            try {
                                                val radius = anim.animatedValue as Float
                                                val alpha = 1f - (radius - 20f) / 80f
                                                pulseCircle.radius = radius.toDouble()
                                                val baseColor = pulseColor and 0x00FFFFFF
                                                val newAlpha = (alpha * 128).toInt().coerceIn(0, 255)
                                                pulseCircle.fillColor = (newAlpha shl 24) or baseColor
                                            } catch (e: Exception) {
                                                // 忽略动画更新异常
                                            }
                                        }
                                        start()
                                    }
                                    pulseAnimatorRef.value = animator
                                }

                                TrackingState.SUCCESS -> {
                                    val pulseColor = 0x804CAF50.toInt()  // 绿色半透明
                                    // 成功时显示短暂闪烁
                                    val pulseCircle = tencentMap.addCircle(
                                        CircleOptions()
                                            .center(location)
                                            .radius(50.0)
                                            .fillColor(pulseColor)
                                            .strokeColor(0xFF4CAF50.toInt())
                                            .strokeWidth(3f)
                                    )
                                    pulseCircleRef.value = pulseCircle

                                    // 淡出动画
                                    val animator = ValueAnimator.ofFloat(1f, 0f).apply {
                                        duration = 1500
                                        addUpdateListener { anim ->
                                            try {
                                                val alpha = anim.animatedValue as Float
                                                val newAlpha = (alpha * 128).toInt().coerceIn(0, 255)
                                                pulseCircle.fillColor = (newAlpha shl 24) or 0x004CAF50
                                            } catch (e: Exception) {
                                                // 忽略动画更新异常
                                            }
                                        }
                                        start()
                                    }
                                    pulseAnimatorRef.value = animator
                                }

                                TrackingState.FAILED -> {
                                    val pulseColor = 0x80F44336.toInt()  // 红色半透明
                                    // 失败时显示短暂闪烁
                                    val pulseCircle = tencentMap.addCircle(
                                        CircleOptions()
                                            .center(location)
                                            .radius(50.0)
                                            .fillColor(pulseColor)
                                            .strokeColor(0xFFF44336.toInt())
                                            .strokeWidth(3f)
                                    )
                                    pulseCircleRef.value = pulseCircle

                                    // 淡出动画
                                    val animator = ValueAnimator.ofFloat(1f, 0f).apply {
                                        duration = 1500
                                        addUpdateListener { anim ->
                                            try {
                                                val alpha = anim.animatedValue as Float
                                                val newAlpha = (alpha * 128).toInt().coerceIn(0, 255)
                                                pulseCircle.fillColor = (newAlpha shl 24) or 0x00F44336
                                            } catch (e: Exception) {
                                                // 忽略动画更新异常
                                            }
                                        }
                                        start()
                                    }
                                    pulseAnimatorRef.value = animator
                                }

                                else -> {
                                    // IDLE 或 null 状态，不显示脉冲
                                }
                            }
                        }
                    } else if (pulseCircleRef.value != null && trackedContact?.location != null) {
                        // 状态未变但需要更新位置
                        pulseCircleRef.value?.center = trackedContact.location
                    }
                } catch (e: ArrayIndexOutOfBoundsException) {
                    // 腾讯地图 SDK 内部并发 bug，忽略此异常
                    Log.w("TencentMapWrapper", "SDK 内部并发异常（已忽略）: ${e.message}")
                } catch (e: Exception) {
                    Log.e("TencentMapWrapper", "地图更新异常: ${e.message}", e)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 深色模式遮罩层 - 自己控制地图深色效果
        if (overlayAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = overlayAlpha))
            )
        }
    }
}

/**
 * 地图内容更新逻辑（使用 SmoothMarker 封装实现 iOS Find My 级别丝滑动画）
 */
private fun updateMapContent(
    view: MapView,
    showTraffic: Boolean,
    mapLayerConfig: MapLayerConfig,
    currentDevice: Device?,
    currentDeviceRealtimeLocation: LatLng?,
    currentDeviceHeading: Float?,
    deviceShouldShowMergedIcon: Boolean,
    avatarBitmap: android.graphics.Bitmap?,
    togetherBitmap: android.graphics.Bitmap?,
    pigBitmap: android.graphics.Bitmap?,
    validContacts: List<Contact>,
    contactsMergedWithDevice: Set<String>,
    contactGroups: Map<String, Int>,
    trackingTargetId: String?,
    currentDeviceId: String,
    devices: List<Device>,
    contacts: List<Contact>,
    // Refs（使用 SmoothMarker 封装）
    deviceMarkerRef: androidx.compose.runtime.MutableState<Marker?>,
    deviceSmoothMarkerRef: androidx.compose.runtime.MutableState<SmoothMarker?>,
    contactMarkersRef: androidx.compose.runtime.MutableState<Map<String, SmoothMarker>>,
    contactCirclesRef: androidx.compose.runtime.MutableState<Map<String, Circle>>,
    animatedContactPositionsRef: androidx.compose.runtime.MutableState<Map<String, LatLng>>,
    cameraAnimatorRef: androidx.compose.runtime.MutableState<ValueAnimator?>,
    lastTrackingPositionRef: androidx.compose.runtime.MutableState<LatLng?>,
    animConfig: DeviceOptimizationConfig.AnimationConfig
) {
    val tencentMap = view.map

    // 更新路况图层
    tencentMap.isTrafficEnabled = showTraffic

    // 更新 3D 建筑显示
    tencentMap.setBuilding3dEffectEnable(mapLayerConfig.show3dBuildings)

    // ==================== 设备 Marker 更新（使用 SmoothMarker）====================
    val deviceLocationSource = currentDeviceRealtimeLocation ?: currentDevice?.location
    currentDevice?.let { device ->
        val effectiveLocation = deviceLocationSource ?: device.location
        if (!effectiveLocation.latitude.isNaN() && !effectiveLocation.longitude.isNaN()) {
            val targetPosition = effectiveLocation
            val rawBearing = currentDeviceHeading ?: device.bearing
            val displayBearing = if (rawBearing.isNaN() || rawBearing.isInfinite()) 0f else rawBearing

            // 选择设备图标
            val deviceIconBitmap = if (deviceShouldShowMergedIcon) togetherBitmap else avatarBitmap
            val anchorU = if (deviceShouldShowMergedIcon) 0.45f else 0.5f
            val anchorV = 0.95f

            // 创建或获取 SmoothMarker
            var smoothMarker = deviceSmoothMarkerRef.value
            if (smoothMarker == null && deviceIconBitmap != null) {
                // 首次创建 Marker 和 SmoothMarker
                val marker = tencentMap.addMarker(
                    MarkerOptions(targetPosition)
                        .icon(BitmapDescriptorFactory.fromBitmap(deviceIconBitmap))
                        .anchor(anchorU, anchorV)
                        .zIndex(10f)
                ).apply {
                    tag = device
                }
                deviceMarkerRef.value = marker
                smoothMarker = SmoothMarker(
                    marker = marker,
                    bearingThresholdMeters = animConfig.bearingThresholdMeters,
                    enableBuffer = animConfig.enableBuffer
                )
                deviceSmoothMarkerRef.value = smoothMarker
            } else if (deviceIconBitmap != null) {
                // 更新图标
                deviceMarkerRef.value?.setIcon(BitmapDescriptorFactory.fromBitmap(deviceIconBitmap))
                deviceMarkerRef.value?.setAnchor(anchorU, anchorV)
                deviceMarkerRef.value?.tag = device
            }

            // 使用 SmoothMarker 平滑移动（内部自动处理动画、插值、航向角等）
            smoothMarker?.animateTo(targetPosition, displayBearing)
        }
    }

    // ==================== 联系人 Markers 更新（使用 SmoothMarker）====================
    val renderedGroups = mutableSetOf<Int>()
    val newContactMarkers = mutableMapOf<String, SmoothMarker>()
    val newContactCircles = mutableMapOf<String, Circle>()
    val newAnimatedPositions = animatedContactPositionsRef.value.toMutableMap()

    validContacts.forEach { contact ->
        if (contactsMergedWithDevice.contains(contact.id)) return@forEach

        contact.location?.let { targetLocation ->
            val groupId = contactGroups[contact.id] ?: -1
            if (groupId >= 0 && renderedGroups.contains(groupId)) return@forEach
            if (groupId >= 0) renderedGroups.add(groupId)

            val sameGroupContacts = if (groupId >= 0) {
                validContacts.filter { c ->
                    contactGroups[c.id] == groupId && !contactsMergedWithDevice.contains(c.id)
                }
            } else listOf(contact)

            val shouldMerge = sameGroupContacts.size > 1
            val contactIconBitmap = if (shouldMerge) togetherBitmap else pigBitmap
            val contactAnchorU = if (shouldMerge) 0.45f else 0.5f
            val contactAnchorV = 0.95f

            // 获取或创建 SmoothMarker
            val existingSmoothMarker = contactMarkersRef.value[contact.id]
            if (existingSmoothMarker != null) {
                // 更新图标
                if (contactIconBitmap != null) {
                    existingSmoothMarker.getMarker().setIcon(BitmapDescriptorFactory.fromBitmap(contactIconBitmap))
                    existingSmoothMarker.getMarker().setAnchor(contactAnchorU, contactAnchorV)
                }
                existingSmoothMarker.getMarker().tag = contact

                // 使用 SmoothMarker 平滑移动
                existingSmoothMarker.animateTo(targetLocation, 0f) { position, _ ->
                    // 更新联系人绿色圆点位置
                    contactCirclesRef.value[contact.id]?.center = position
                    // 保存动画中的位置（用于相机追踪）
                    newAnimatedPositions[contact.id] = position
                }

                newContactMarkers[contact.id] = existingSmoothMarker

                // 更新或创建联系人绿色圆点
                val existingCircle = contactCirclesRef.value[contact.id]
                if (existingCircle != null) {
                    newContactCircles[contact.id] = existingCircle
                } else {
                    val circle = tencentMap.addCircle(
                        CircleOptions()
                            .center(targetLocation)
                            .radius(8.0)
                            .fillColor(0xFF34C759.toInt())
                            .strokeColor(android.graphics.Color.WHITE)
                            .strokeWidth(2f)
                    )
                    newContactCircles[contact.id] = circle
                }
            } else if (contactIconBitmap != null) {
                // 创建新 Marker 和 SmoothMarker
                val marker = tencentMap.addMarker(
                    MarkerOptions(targetLocation)
                        .icon(BitmapDescriptorFactory.fromBitmap(contactIconBitmap))
                        .anchor(contactAnchorU, contactAnchorV)
                        .zIndex(10f)
                ).apply {
                    tag = contact
                }
                val smoothMarker = SmoothMarker(
                    marker = marker,
                    bearingThresholdMeters = animConfig.bearingThresholdMeters,
                    enableBuffer = animConfig.enableBuffer
                )
                newContactMarkers[contact.id] = smoothMarker
                newAnimatedPositions[contact.id] = targetLocation

                // 创建联系人绿色圆点
                val circle = tencentMap.addCircle(
                    CircleOptions()
                        .center(targetLocation)
                        .radius(8.0)
                        .fillColor(0xFF34C759.toInt())
                        .strokeColor(android.graphics.Color.WHITE)
                        .strokeWidth(2f)
                )
                newContactCircles[contact.id] = circle
            }
        }
    }

    // 清理不再显示的联系人
    contactMarkersRef.value.forEach { (id, smoothMarker) ->
        if (!newContactMarkers.containsKey(id)) {
            smoothMarker.cancel()
            smoothMarker.getMarker().remove()
            newAnimatedPositions.remove(id)
        }
    }
    contactCirclesRef.value.forEach { (id, circle) ->
        if (!newContactCircles.containsKey(id)) {
            circle.remove()
        }
    }

    // 更新状态
    contactMarkersRef.value = newContactMarkers
    contactCirclesRef.value = newContactCircles
    animatedContactPositionsRef.value = newAnimatedPositions

    // ==================== 相机追踪 ====================
    if (trackingTargetId != null) {
        val targetLocation: LatLng? = when {
            trackingTargetId == currentDeviceId -> {
                currentDeviceRealtimeLocation
                    ?: deviceSmoothMarkerRef.value?.getAnimatedPosition()
                    ?: devices.find { it.id == trackingTargetId }?.location
            }
            devices.any { it.id == trackingTargetId } -> {
                devices.find { it.id == trackingTargetId }?.location
            }
            trackingTargetId.startsWith("contact_") -> {
                val contactId = trackingTargetId.removePrefix("contact_")
                animatedContactPositionsRef.value[contactId]
                    ?: contacts.find { it.id == contactId }?.location
            }
            else -> {
                animatedContactPositionsRef.value[trackingTargetId]
                    ?: contacts.find { it.id == trackingTargetId }?.location
            }
        }

        targetLocation?.let { newPosition ->
            if (!newPosition.latitude.isNaN() && !newPosition.longitude.isNaN()) {
                val lastPosition = lastTrackingPositionRef.value

                val hasSignificantChange = lastPosition == null ||
                    kotlin.math.abs(lastPosition.latitude - newPosition.latitude) > 0.000001 ||
                    kotlin.math.abs(lastPosition.longitude - newPosition.longitude) > 0.000001

                if (hasSignificantChange) {
                    cameraAnimatorRef.value?.cancel()

                    if (lastPosition != null) {
                        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                            duration = animConfig.cameraAnimDurationMs
                            interpolator = LinearInterpolator()
                            addUpdateListener { animation ->
                                val fraction = animation.animatedValue as Float
                                val lat = lastPosition.latitude + (newPosition.latitude - lastPosition.latitude) * fraction
                                val lng = lastPosition.longitude + (newPosition.longitude - lastPosition.longitude) * fraction
                                val interpolatedPosition = latLngOf(lat, lng)

                                tencentMap.moveCamera(
                                    CameraUpdateFactory.newLatLng(interpolatedPosition)
                                )
                            }
                        }
                        animator.start()
                        cameraAnimatorRef.value = animator
                    }

                    lastTrackingPositionRef.value = newPosition
                }
            }
        }
    } else {
        cameraAnimatorRef.value?.cancel()
        cameraAnimatorRef.value = null
        lastTrackingPositionRef.value = null
    }
}

/**
 * 计算两个 LatLng 之间的距离（米）
 */
private fun calculateDistance(from: LatLng, to: LatLng): Double {
    val earthRadius = 6371009.0
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val deltaLat = Math.toRadians(to.latitude - from.latitude)
    val deltaLng = Math.toRadians(to.longitude - from.longitude)

    val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) *
            sin(deltaLng / 2) * sin(deltaLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return earthRadius * c
}

/**
 * Union-Find 算法计算合并组
 */
private fun calculateMergeGroups(
    contacts: List<Contact>,
    mergeDistanceMeters: Double
): Map<String, Int> {
    if (contacts.isEmpty()) return emptyMap()

    val parent = mutableMapOf<String, String>()

    fun find(id: String): String {
        if (parent[id] != id) {
            parent[id] = find(parent[id]!!)
        }
        return parent[id]!!
    }

    fun union(id1: String, id2: String) {
        val root1 = find(id1)
        val root2 = find(id2)
        if (root1 != root2) {
            parent[root1] = root2
        }
    }

    contacts.forEach { contact -> parent[contact.id] = contact.id }

    for (i in contacts.indices) {
        for (j in i + 1 until contacts.size) {
            val loc1 = contacts[i].location
            val loc2 = contacts[j].location
            if (loc1 != null && loc2 != null) {
                val distance = calculateDistance(loc1, loc2)
                if (distance < mergeDistanceMeters) {
                    union(contacts[i].id, contacts[j].id)
                }
            }
        }
    }

    val rootToGroupId = mutableMapOf<String, Int>()
    var groupIdCounter = 0
    val result = mutableMapOf<String, Int>()

    contacts.forEach { contact ->
        val root = find(contact.id)
        val groupId = rootToGroupId.getOrPut(root) { groupIdCounter++ }
        result[contact.id] = groupId
    }

    return result
}


