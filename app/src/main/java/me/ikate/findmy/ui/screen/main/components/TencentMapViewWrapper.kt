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
import com.tencent.tencentmap.mapsdk.maps.model.Polygon
import com.tencent.tencentmap.mapsdk.maps.model.PolygonOptions
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
import me.ikate.findmy.util.animation.BearingEvaluator
import me.ikate.findmy.util.animation.DynamicDurationCalculator
import me.ikate.findmy.util.animation.LatLngEvaluator
import me.ikate.findmy.util.animation.PositionBuffer
import kotlin.math.asin
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

    // 加载图标 Bitmap
    val avatarBitmap = remember(currentIcon) {
        val isGirl = currentIcon == AppIconHelper.AppIcon.GIRL
        val resourceId = if (isGirl) {
            context.resources.getIdentifier("marker_girl", "drawable", context.packageName)
        } else {
            context.resources.getIdentifier("marker_boy", "drawable", context.packageName)
        }
        if (resourceId != 0) {
            BitmapFactory.decodeResource(context.resources, resourceId)
        } else null
    }

    val pigBitmap = remember {
        val resourceId = context.resources.getIdentifier("marker_pig", "drawable", context.packageName)
        if (resourceId != 0) {
            BitmapFactory.decodeResource(context.resources, resourceId)
        } else null
    }

    val togetherBitmap = remember(currentIcon) {
        val isGirl = currentIcon == AppIconHelper.AppIcon.GIRL
        val resourceId = if (isGirl) {
            context.resources.getIdentifier("marker_together_g", "drawable", context.packageName)
        } else {
            context.resources.getIdentifier("marker_together_b", "drawable", context.packageName)
        }
        if (resourceId != 0) {
            BitmapFactory.decodeResource(context.resources, resourceId)
        } else null
    }

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

    // 保存 Marker 引用，用于后续更新
    val deviceMarkerRef = remember { mutableStateOf<Marker?>(null) }
    val deviceCircleRef = remember { mutableStateOf<Circle?>(null) }
    val sectorPolygonRef = remember { mutableStateOf<Polygon?>(null) }
    val contactMarkersRef = remember { mutableStateOf<Map<String, Marker>>(emptyMap()) }
    val contactCirclesRef = remember { mutableStateOf<Map<String, Circle>>(emptyMap()) }  // 联系人绿色圆点

    // 追踪脉冲动画相关状态
    val pulseCircleRef = remember { mutableStateOf<Circle?>(null) }
    val pulseAnimatorRef = remember { mutableStateOf<ValueAnimator?>(null) }
    val lastTrackingContactUidRef = remember { mutableStateOf<String?>(null) }
    val lastPulseStateRef = remember { mutableStateOf<TrackingState?>(null) }

    // 设备优化动画配置（针对三星 S24 Ultra 等高刷屏设备优化）
    val animConfig = remember { DeviceOptimizationConfig.getAnimationConfig() }

    // 平滑动画相关状态
    val lastDevicePositionRef = remember { mutableStateOf<LatLng?>(null) }
    val lastDeviceBearingRef = remember { mutableStateOf<Float?>(null) }
    val positionAnimatorRef = remember { mutableStateOf<ValueAnimator?>(null) }
    val animatedPositionRef = remember { mutableStateOf<LatLng?>(null) }
    val mapInstanceRef = remember { mutableStateOf<TencentMap?>(null) }

    // 动态时长计算器（iOS Find My 级别的丝滑体验核心）
    val deviceDurationCalculator = remember {
        DynamicDurationCalculator(
            minDurationMs = animConfig.minAnimDurationMs,
            maxDurationMs = animConfig.maxAnimDurationMs,
            defaultDurationMs = animConfig.defaultAnimDurationMs
        )
    }

    // 坐标插值器
    val latLngEvaluator = remember { LatLngEvaluator() }

    // 设备位置缓冲区（预测缓冲，实现 Marker 永不停顿）
    val devicePositionBuffer = remember {
        PositionBuffer(enabled = animConfig.enableBuffer)
    }

    // 联系人平滑动画相关状态（像 iOS Find My 一样，对端点位也平滑移动）
    val lastContactPositionsRef = remember { mutableStateOf<Map<String, LatLng>>(emptyMap()) }
    val lastContactBearingsRef = remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    val contactAnimatorsRef = remember { mutableStateOf<Map<String, ValueAnimator>>(emptyMap()) }
    val animatedContactPositionsRef = remember { mutableStateOf<Map<String, LatLng>>(emptyMap()) }

    // 联系人动态时长计算器
    val contactDurationCalculators = remember { mutableStateOf<Map<String, DynamicDurationCalculator>>(emptyMap()) }

    // 联系人位置缓冲区（预测缓冲）
    val contactPositionBuffers = remember { mutableStateOf<Map<String, PositionBuffer>>(emptyMap()) }

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
            // 取消位置动画和相机动画
            positionAnimatorRef.value?.cancel()
            positionAnimatorRef.value = null
            cameraAnimatorRef.value?.cancel()
            cameraAnimatorRef.value = null
            // 取消所有联系人的动画
            contactAnimatorsRef.value.values.forEach { it.cancel() }
            contactAnimatorsRef.value = emptyMap()
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
                        // Refs
                        deviceMarkerRef = deviceMarkerRef,
                        deviceCircleRef = deviceCircleRef,
                        sectorPolygonRef = sectorPolygonRef,
                        contactMarkersRef = contactMarkersRef,
                        contactCirclesRef = contactCirclesRef,
                        lastDevicePositionRef = lastDevicePositionRef,
                        lastDeviceBearingRef = lastDeviceBearingRef,
                        positionAnimatorRef = positionAnimatorRef,
                        animatedPositionRef = animatedPositionRef,
                        lastContactPositionsRef = lastContactPositionsRef,
                        lastContactBearingsRef = lastContactBearingsRef,
                        contactAnimatorsRef = contactAnimatorsRef,
                        animatedContactPositionsRef = animatedContactPositionsRef,
                        contactDurationCalculators = contactDurationCalculators,
                        cameraAnimatorRef = cameraAnimatorRef,
                        lastTrackingPositionRef = lastTrackingPositionRef,
                        animConfig = animConfig,
                        deviceDurationCalculator = deviceDurationCalculator,
                        latLngEvaluator = latLngEvaluator,
                        devicePositionBuffer = devicePositionBuffer,
                        contactPositionBuffers = contactPositionBuffers
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
 * 地图内容更新逻辑（抽取为独立函数，便于异常捕获）
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
    // Refs
    deviceMarkerRef: androidx.compose.runtime.MutableState<Marker?>,
    deviceCircleRef: androidx.compose.runtime.MutableState<Circle?>,
    sectorPolygonRef: androidx.compose.runtime.MutableState<Polygon?>,
    contactMarkersRef: androidx.compose.runtime.MutableState<Map<String, Marker>>,
    contactCirclesRef: androidx.compose.runtime.MutableState<Map<String, Circle>>,
    lastDevicePositionRef: androidx.compose.runtime.MutableState<LatLng?>,
    lastDeviceBearingRef: androidx.compose.runtime.MutableState<Float?>,
    positionAnimatorRef: androidx.compose.runtime.MutableState<ValueAnimator?>,
    animatedPositionRef: androidx.compose.runtime.MutableState<LatLng?>,
    lastContactPositionsRef: androidx.compose.runtime.MutableState<Map<String, LatLng>>,
    lastContactBearingsRef: androidx.compose.runtime.MutableState<Map<String, Float>>,
    contactAnimatorsRef: androidx.compose.runtime.MutableState<Map<String, ValueAnimator>>,
    animatedContactPositionsRef: androidx.compose.runtime.MutableState<Map<String, LatLng>>,
    contactDurationCalculators: androidx.compose.runtime.MutableState<Map<String, DynamicDurationCalculator>>,
    cameraAnimatorRef: androidx.compose.runtime.MutableState<ValueAnimator?>,
    lastTrackingPositionRef: androidx.compose.runtime.MutableState<LatLng?>,
    animConfig: DeviceOptimizationConfig.AnimationConfig,
    deviceDurationCalculator: DynamicDurationCalculator,
    latLngEvaluator: LatLngEvaluator,
    devicePositionBuffer: PositionBuffer,
    contactPositionBuffers: androidx.compose.runtime.MutableState<Map<String, PositionBuffer>>
) {
    val tencentMap = view.map

            // 更新路况图层
            tencentMap.isTrafficEnabled = showTraffic

            // 更新 3D 建筑显示
            tencentMap.setBuilding3dEffectEnable(mapLayerConfig.show3dBuildings)


            // 更新当前设备 Marker（带平滑移动动画）
            // 像 iOS Find My 一样：优先使用实时位置（追踪自己时更新更快）
            val deviceLocationSource = currentDeviceRealtimeLocation ?: currentDevice?.location
            currentDevice?.let { device ->
                val effectiveLocation = deviceLocationSource ?: device.location
                if (!effectiveLocation.latitude.isNaN() && !effectiveLocation.longitude.isNaN()) {
                    val targetPosition = effectiveLocation
                    val rawBearing = currentDeviceHeading ?: device.bearing
                    val displayBearing = if (rawBearing.isNaN() || rawBearing.isInfinite()) 0f else rawBearing

                    // 获取上一个位置和航向角，用于计算平滑动画
                    val lastPosition = lastDevicePositionRef.value
                    val lastBearing = lastDeviceBearingRef.value ?: displayBearing
                    val shouldAnimate = lastPosition != null &&
                            (lastPosition.latitude != targetPosition.latitude ||
                             lastPosition.longitude != targetPosition.longitude)

                    // 计算移动距离，决定是否更新航向角（低速过滤，防止定位漂移原地转圈）
                    val moveDistance = if (lastPosition != null) {
                        calculateDistance(lastPosition, targetPosition)
                    } else 0.0
                    val shouldUpdateBearing = BearingEvaluator.shouldUpdateBearing(
                        moveDistance,
                        animConfig.bearingThresholdMeters
                    )

                    // 更新位置的函数（用于动画每帧更新），支持航向角参数
                    fun updatePositionUI(position: LatLng, bearing: Float) {
                        // 更新或创建方向扇形（半径缩短为 60 米，更符合 iOS Find My 的视觉效果）
                        val sectorPoints = calculateSectorPoints(
                            center = position,
                            radius = 60.0,
                            direction = bearing,
                            fov = 45f
                        )

                        val existingSector = sectorPolygonRef.value
                        if (existingSector != null) {
                            existingSector.points = sectorPoints
                        } else {
                            sectorPolygonRef.value = tencentMap.addPolygon(
                                PolygonOptions()
                                    .addAll(sectorPoints)
                                    .fillColor(0x40007AFF)  // 稍微降低透明度
                                    .strokeColor(0xFF007AFF.toInt())
                                    .strokeWidth(1f)
                            )
                        }

                        // 更新或创建蓝色圆点（半径缩小为 8 米，更紧凑）
                        val existingCircle = deviceCircleRef.value
                        if (existingCircle != null) {
                            existingCircle.center = position
                        } else {
                            deviceCircleRef.value = tencentMap.addCircle(
                                CircleOptions()
                                    .center(position)
                                    .radius(8.0)
                                    .fillColor(0xFF007AFF.toInt())
                                    .strokeColor(android.graphics.Color.WHITE)
                                    .strokeWidth(2f)
                            )
                        }

                        // 更新设备 Marker 位置（图标不旋转，保持正常方向）
                        deviceMarkerRef.value?.position = position
                    }

                    // 创建或更新设备 Marker
                    val iconBitmap = if (deviceShouldShowMergedIcon) togetherBitmap else avatarBitmap
                    val anchorU = if (deviceShouldShowMergedIcon) 0.45f else 0.5f
                    val anchorV = 0.95f

                    if (deviceMarkerRef.value == null && iconBitmap != null) {
                        deviceMarkerRef.value = tencentMap.addMarker(
                            MarkerOptions(targetPosition)
                                .icon(BitmapDescriptorFactory.fromBitmap(iconBitmap))
                                .anchor(anchorU, anchorV)
                                .zIndex(10f)  // 层级高于圆点，确保头像在圆点之上
                        ).apply {
                            tag = device
                        }
                    } else {
                        if (iconBitmap != null) {
                            deviceMarkerRef.value?.setIcon(BitmapDescriptorFactory.fromBitmap(iconBitmap))
                            deviceMarkerRef.value?.setAnchor(anchorU, anchorV)
                        }
                        deviceMarkerRef.value?.tag = device
                    }

                    // 将新位置入队到缓冲区（用于预测缓冲模式）
                    if (animConfig.enableBuffer) {
                        devicePositionBuffer.enqueue(targetPosition, displayBearing)
                    }

                    // 执行平滑移动动画（iOS Find My 级别丝滑体验）
                    if (shouldAnimate) {
                        // 取消之前的动画（防止鬼畜）
                        positionAnimatorRef.value?.cancel()

                        // 确定动画起点和终点
                        val (animStartPosition, animEndPosition, animStartBearing, animEndBearing, animDuration) =
                            if (animConfig.enableBuffer && devicePositionBuffer.hasEnoughForAnimation()) {
                                // 缓冲模式：使用缓冲区的动画段（会有 1-2 秒延迟，但更丝滑）
                                val segment = devicePositionBuffer.peekNextSegment()
                                if (segment != null) {
                                    AnimationParams(
                                        segment.from.position,
                                        segment.to.position,
                                        segment.from.bearing,
                                        segment.to.bearing,
                                        segment.duration
                                    )
                                } else {
                                    // 缓冲区数据不足，使用直接模式
                                    val startBearing = BearingEvaluator.normalizeAngle(lastBearing)
                                    val endBearing = if (shouldUpdateBearing) {
                                        BearingEvaluator.normalizeAngle(displayBearing)
                                    } else startBearing
                                    val duration = if (animConfig.enableDynamicDuration) {
                                        deviceDurationCalculator.calculate()
                                    } else animConfig.positionAnimDurationMs
                                    AnimationParams(lastPosition, targetPosition, startBearing, endBearing, duration)
                                }
                            } else {
                                // 直接模式：从上一位置到新位置
                                val startBearing = BearingEvaluator.normalizeAngle(lastBearing)
                                val endBearing = if (shouldUpdateBearing) {
                                    BearingEvaluator.normalizeAngle(displayBearing)
                                } else startBearing
                                val duration = if (animConfig.enableDynamicDuration) {
                                    deviceDurationCalculator.calculate()
                                } else animConfig.positionAnimDurationMs
                                AnimationParams(lastPosition, targetPosition, startBearing, endBearing, duration)
                            }

                        // 创建新的平滑移动动画（使用坐标插值器 + 航向角最短路径）
                        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                            duration = animDuration
                            interpolator = LinearInterpolator()
                            addUpdateListener { animation ->
                                val fraction = animation.animatedValue as Float

                                // 使用 LatLngEvaluator 计算插值坐标
                                val interpolatedPosition = latLngEvaluator.evaluate(
                                    fraction, animStartPosition, animEndPosition
                                )

                                // 使用 BearingEvaluator 计算最短路径旋转（防止绕远路）
                                val interpolatedBearing = BearingEvaluator.evaluate(
                                    fraction, animStartBearing, animEndBearing
                                )

                                animatedPositionRef.value = interpolatedPosition
                                updatePositionUI(interpolatedPosition, interpolatedBearing)
                            }
                        }
                        animator.start()
                        positionAnimatorRef.value = animator
                    } else {
                        // 没有上一个位置或位置相同，直接更新
                        updatePositionUI(targetPosition, displayBearing)
                    }

                    // 保存当前位置和航向角作为下一次动画的起点
                    lastDevicePositionRef.value = targetPosition
                    if (shouldUpdateBearing) {
                        lastDeviceBearingRef.value = displayBearing
                    }
                }
            }

            // 更新联系人 Markers（带平滑移动动画，像 iOS Find My 一样）
            val renderedGroups = mutableSetOf<Int>()
            val newContactMarkers = mutableMapOf<String, Marker>()
            val newContactCircles = mutableMapOf<String, Circle>()  // 联系人绿色圆点
            val newLastPositions = lastContactPositionsRef.value.toMutableMap()
            val newLastBearings = lastContactBearingsRef.value.toMutableMap()
            val newAnimators = contactAnimatorsRef.value.toMutableMap()
            val newAnimatedPositions = animatedContactPositionsRef.value.toMutableMap()
            val newDurationCalculators = contactDurationCalculators.value.toMutableMap()

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
                    val iconBitmap = if (shouldMerge) togetherBitmap else pigBitmap

                    // 获取上一个位置和航向角
                    val lastPosition = lastContactPositionsRef.value[contact.id]
                    val lastBearing = lastContactBearingsRef.value[contact.id] ?: 0f

                    val shouldAnimate = lastPosition != null &&
                            (lastPosition.latitude != targetLocation.latitude ||
                             lastPosition.longitude != targetLocation.longitude)

                    // 计算移动距离，决定是否更新航向角
                    val contactMoveDistance = if (lastPosition != null) {
                        calculateDistance(lastPosition, targetLocation)
                    } else 0.0
                    val shouldUpdateContactBearing = BearingEvaluator.shouldUpdateBearing(
                        contactMoveDistance,
                        animConfig.bearingThresholdMeters
                    )

                    // 根据移动方向计算航向角（联系人没有传感器数据，需要根据轨迹计算）
                    val targetBearing = if (lastPosition != null && shouldUpdateContactBearing) {
                        BearingEvaluator.calculateBearing(
                            lastPosition.latitude, lastPosition.longitude,
                            targetLocation.latitude, targetLocation.longitude
                        )
                    } else {
                        lastBearing
                    }

                    // 联系人图标锚点设置
                    val contactAnchorU = if (shouldMerge) 0.45f else 0.5f
                    val contactAnchorV = 0.95f

                    // 获取或创建该联系人的动态时长计算器
                    val durationCalc = newDurationCalculators.getOrPut(contact.id) {
                        DynamicDurationCalculator(
                            minDurationMs = animConfig.minAnimDurationMs,
                            maxDurationMs = animConfig.maxAnimDurationMs,
                            defaultDurationMs = animConfig.defaultAnimDurationMs
                        )
                    }

                    // 获取或创建该联系人的位置缓冲区
                    val contactBuffers = contactPositionBuffers.value.toMutableMap()
                    val contactBuffer = contactBuffers.getOrPut(contact.id) {
                        PositionBuffer(enabled = animConfig.enableBuffer)
                    }

                    // 将新位置入队到缓冲区
                    if (animConfig.enableBuffer) {
                        contactBuffer.enqueue(targetLocation, targetBearing)
                    }
                    contactPositionBuffers.value = contactBuffers

                    val existingMarker = contactMarkersRef.value[contact.id]
                    if (existingMarker != null) {
                        // 更新图标和锚点
                        if (iconBitmap != null) {
                            existingMarker.setIcon(BitmapDescriptorFactory.fromBitmap(iconBitmap))
                            existingMarker.setAnchor(contactAnchorU, contactAnchorV)
                        }
                        existingMarker.tag = contact

                        // 平滑移动动画（像 iOS Find My 一样，对端点位也平滑移动）
                        if (shouldAnimate) {
                            // 取消之前的动画
                            newAnimators[contact.id]?.cancel()

                            // 确定动画参数
                            val animParams = if (animConfig.enableBuffer && contactBuffer.hasEnoughForAnimation()) {
                                // 缓冲模式
                                val segment = contactBuffer.peekNextSegment()
                                if (segment != null) {
                                    AnimationParams(
                                        segment.from.position,
                                        segment.to.position,
                                        segment.from.bearing,
                                        segment.to.bearing,
                                        segment.duration
                                    )
                                } else {
                                    // 缓冲区数据不足，使用直接模式
                                    val startBearing = BearingEvaluator.normalizeAngle(lastBearing)
                                    val endBearing = if (shouldUpdateContactBearing) {
                                        BearingEvaluator.normalizeAngle(targetBearing)
                                    } else startBearing
                                    val duration = if (animConfig.enableDynamicDuration) {
                                        durationCalc.calculate()
                                    } else animConfig.positionAnimDurationMs
                                    AnimationParams(lastPosition, targetLocation, startBearing, endBearing, duration)
                                }
                            } else {
                                // 直接模式
                                val startBearing = BearingEvaluator.normalizeAngle(lastBearing)
                                val endBearing = if (shouldUpdateContactBearing) {
                                    BearingEvaluator.normalizeAngle(targetBearing)
                                } else startBearing
                                val duration = if (animConfig.enableDynamicDuration) {
                                    durationCalc.calculate()
                                } else animConfig.positionAnimDurationMs
                                AnimationParams(lastPosition, targetLocation, startBearing, endBearing, duration)
                            }

                            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                                duration = animParams.duration
                                interpolator = LinearInterpolator()
                                addUpdateListener { animation ->
                                    val fraction = animation.animatedValue as Float

                                    // 使用 LatLngEvaluator 计算插值坐标
                                    val interpolatedPosition = latLngEvaluator.evaluate(
                                        fraction, animParams.startPosition, animParams.endPosition
                                    )

                                    // 使用 BearingEvaluator 计算最短路径旋转
                                    val interpolatedBearing = BearingEvaluator.evaluate(
                                        fraction, animParams.startBearing, animParams.endBearing
                                    )

                                    // 更新 Marker 位置（图标不旋转，保持正常方向）
                                    existingMarker.position = interpolatedPosition

                                    // 更新联系人绿色圆点位置
                                    contactCirclesRef.value[contact.id]?.center = interpolatedPosition

                                    // 保存动画中的位置（用于相机追踪）
                                    val currentAnimated = animatedContactPositionsRef.value.toMutableMap()
                                    currentAnimated[contact.id] = interpolatedPosition
                                    animatedContactPositionsRef.value = currentAnimated
                                }
                            }
                            animator.start()
                            newAnimators[contact.id] = animator
                        } else {
                            // 位置相同或首次显示，直接设置
                            existingMarker.position = targetLocation
                            newAnimatedPositions[contact.id] = targetLocation
                        }

                        newContactMarkers[contact.id] = existingMarker

                        // 更新或创建联系人绿色圆点
                        val existingCircle = contactCirclesRef.value[contact.id]
                        if (existingCircle != null) {
                            existingCircle.center = targetLocation
                            newContactCircles[contact.id] = existingCircle
                        } else {
                            val circle = tencentMap.addCircle(
                                CircleOptions()
                                    .center(targetLocation)
                                    .radius(8.0)
                                    .fillColor(0xFF34C759.toInt())  // iOS 风格绿色
                                    .strokeColor(android.graphics.Color.WHITE)
                                    .strokeWidth(2f)
                            )
                            newContactCircles[contact.id] = circle
                        }
                    } else if (iconBitmap != null) {
                        // 创建新 Marker
                        val marker = tencentMap.addMarker(
                            MarkerOptions(targetLocation)
                                .icon(BitmapDescriptorFactory.fromBitmap(iconBitmap))
                                .anchor(contactAnchorU, contactAnchorV)
                                .zIndex(10f)  // 层级高于圆点
                        ).apply {
                            tag = contact
                        }
                        newContactMarkers[contact.id] = marker
                        newAnimatedPositions[contact.id] = targetLocation

                        // 创建联系人绿色圆点
                        val circle = tencentMap.addCircle(
                            CircleOptions()
                                .center(targetLocation)
                                .radius(8.0)
                                .fillColor(0xFF34C759.toInt())  // iOS 风格绿色
                                .strokeColor(android.graphics.Color.WHITE)
                                .strokeWidth(2f)
                        )
                        newContactCircles[contact.id] = circle
                    }

                    // 保存当前位置和航向角作为下一次动画的起点
                    newLastPositions[contact.id] = targetLocation
                    if (shouldUpdateContactBearing) {
                        newLastBearings[contact.id] = targetBearing
                    }
                }
            }

            // 清理不再显示的联系人的动画、圆点和位置状态
            contactMarkersRef.value.forEach { (id, marker) ->
                if (!newContactMarkers.containsKey(id)) {
                    marker.remove()
                    newAnimators[id]?.cancel()
                    newAnimators.remove(id)
                    newLastPositions.remove(id)
                    newLastBearings.remove(id)
                    newAnimatedPositions.remove(id)
                    newDurationCalculators.remove(id)
                }
            }
            // 清理不再显示的联系人绿色圆点
            contactCirclesRef.value.forEach { (id, circle) ->
                if (!newContactCircles.containsKey(id)) {
                    circle.remove()
                }
            }

            // 更新状态
            contactMarkersRef.value = newContactMarkers
            contactCirclesRef.value = newContactCircles
            lastContactPositionsRef.value = newLastPositions
            lastContactBearingsRef.value = newLastBearings
            contactAnimatorsRef.value = newAnimators
            animatedContactPositionsRef.value = newAnimatedPositions
            contactDurationCalculators.value = newDurationCalculators

            // 追踪模式：平滑移动相机跟随目标
            if (trackingTargetId != null) {
                val targetLocation: LatLng? = when {
                    trackingTargetId == currentDeviceId -> {
                        currentDeviceRealtimeLocation
                            ?: animatedPositionRef.value
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
                                    duration = animConfig.cameraAnimDurationMs // 默认 800ms，三星 S24 Ultra 为 600ms
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
    points.add(center)

    val startAngle = direction - fov / 2
    val endAngle = direction + fov / 2

    var angle = startAngle
    while (angle <= endAngle) {
        points.add(computeOffset(center, radius, angle.toDouble()))
        angle += 5
    }
    points.add(computeOffset(center, radius, endAngle.toDouble()))
    points.add(center)

    return points
}

/**
 * 计算给定距离和方位的目标坐标
 */
private fun computeOffset(from: LatLng, distance: Double, heading: Double): LatLng {
    val d = distance / 6371009.0
    val h = Math.toRadians(heading)
    val fromLat = Math.toRadians(from.latitude)
    val fromLng = Math.toRadians(from.longitude)

    val lat = asin(sin(fromLat) * cos(d) + cos(fromLat) * sin(d) * cos(h))
    val lng = fromLng + atan2(sin(h) * sin(d) * cos(fromLat), cos(d) - sin(fromLat) * sin(lat))

    return latLngOf(Math.toDegrees(lat), Math.toDegrees(lng))
}

/**
 * 动画参数数据类
 * 用于封装动画的起点、终点、航向角和时长
 */
private data class AnimationParams(
    val startPosition: LatLng,
    val endPosition: LatLng,
    val startBearing: Float,
    val endBearing: Float,
    val duration: Long
)
