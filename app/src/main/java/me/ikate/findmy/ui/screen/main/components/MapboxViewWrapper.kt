@file:Suppress("DEPRECATION", "ComposableNaming")

package me.ikate.findmy.ui.screen.main.components

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.animation.LinearInterpolator
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.rememberIconImage
import com.mapbox.maps.extension.compose.style.BooleanValue
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.extension.style.expressions.dsl.generated.match
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.vectorSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.plugin.gestures.gestures
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.latitude
import me.ikate.findmy.data.model.longitude
import me.ikate.findmy.data.model.pointOf
import me.ikate.findmy.util.AppIconHelper
import me.ikate.findmy.util.MapSettingsManager
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mapbox Maps 视图 Compose 包装器
 * 替代 Google Maps，使用 Mapbox Maps SDK
 *
 * @suppress DEPRECATION - MapboxStandardStyle 在新版 SDK 中标记为过时，但仍可正常使用
 * @suppress ComposableNaming - Mapbox Compose SDK 的内部设计警告，不影响功能
 *
 * @param modifier 修饰符
 * @param devices 设备列表（用于渲染 Marker）
 * @param contacts 联系人列表（用于渲染联系人位置 Marker）
 * @param currentDeviceHeading 当前设备实时朝向（来自传感器）
 * @param showTraffic 是否显示路况图层
 * @param mapLayerConfig 地图图层配置
 * @param bottomPadding 底部 padding
 * @param onMapReady 地图准备完成回调
 * @param onMarkerClick Marker 点击回调
 * @param onContactMarkerClick 联系人 Marker 点击回调
 * @param onMapClick 地图空白区域点击回调
 */
@SuppressLint("HardwareIds")
@Composable
fun MapboxViewWrapper(
    modifier: Modifier = Modifier,
    devices: List<Device> = emptyList(),
    contacts: List<Contact> = emptyList(),
    currentDeviceHeading: Float? = null,
    showTraffic: Boolean = false,
    mapLayerConfig: MapLayerConfig = MapLayerConfig(),
    bottomPadding: Dp = 0.dp,
    onMapReady: (MapboxMap) -> Unit = {},
    onMarkerClick: (Device) -> Unit = {},
    onContactMarkerClick: (Contact) -> Unit = {},
    onMapClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()

    // 选中的 Marker ID
    val selectedMarkerId = remember { mutableStateOf<String?>(null) }

    // 相机视口状态（默认平面视图，用户可通过图层按钮开启 3D）
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(116.4, 39.9)) // 默认北京
            zoom(10.0)
            pitch(0.0) // 默认平面视图，3D 效果由用户按需开启
        }
    }

    // 获取当前设备 ID
    val currentDeviceId = remember {
        android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }

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
//            try {
//                val original = BitmapFactory.decodeResource(context.resources, resourceId)
//                if (original != null) {
//                    val targetSize = 144
//                    val aspectRatio = original.width.toFloat() / original.height.toFloat()
//                    val (newWidth, newHeight) = if (aspectRatio >= 1f) {
//                        Pair(targetSize, (targetSize / aspectRatio).toInt())
//                    } else {
//                        Pair((targetSize * aspectRatio).toInt(), targetSize)
//                    }
//                    Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
//                } else null
//            } catch (e: Exception) {
//                null
//            }
        } else null
    }

    // 创建 IconImage
    val avatarIcon = avatarBitmap?.let {
        rememberIconImage(key = "avatar", painter = BitmapPainter(it.asImageBitmap()))
    }
    val pigIcon = pigBitmap?.let {
        rememberIconImage(key = "pig", painter = BitmapPainter(it.asImageBitmap()))
    }
    val togetherIcon = togetherBitmap?.let {
        rememberIconImage(key = "together", painter = BitmapPainter(it.asImageBitmap()))
    }

    // 获取当前设备
    val currentDevice = devices.find { it.id == currentDeviceId }

    // 合并距离阈值
    val mergeDistanceMeters = 3.0

    // 过滤有效联系人（必须有位置 + 状态为已接受 + 未暂停 + 位置可用）
    val validContacts = remember(contacts) {
        contacts.filter { contact ->
            // 必须有位置信息且坐标有效
            val hasValidLocation = contact.location?.let { loc ->
                !loc.latitude().isNaN() && !loc.longitude().isNaN()
            } ?: false

            // 必须是已接受的共享且未暂停且位置可用
            val isActive = contact.shareStatus == me.ikate.findmy.data.model.ShareStatus.ACCEPTED &&
                    !contact.isPaused &&
                    contact.isLocationAvailable

            hasValidLocation && isActive
        }
    }

    // 计算与当前设备合并的联系人
    val contactsMergedWithDevice = remember(currentDevice?.location, validContacts) {
        val result = mutableSetOf<String>()
        val deviceLoc = currentDevice?.location
        if (deviceLoc != null && !deviceLoc.latitude().isNaN() && !deviceLoc.longitude().isNaN()) {
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

    // 根据配置确定光照预设（支持自动模式）
    val actualLightPreset = LightPreset.getEffectivePreset(mapLayerConfig.lightPreset)
    val effectiveLightPreset = when (actualLightPreset) {
        LightPreset.DAY -> LightPresetValue.DAY
        LightPreset.DUSK -> LightPresetValue.DUSK
        LightPreset.DAWN -> LightPresetValue.DAWN
        LightPreset.NIGHT -> LightPresetValue.NIGHT
        LightPreset.AUTO -> LightPresetValue.DAY // 不会到达这里，getEffectivePreset 已处理
    }

    MapboxMap(
        modifier = modifier,
        mapViewportState = mapViewportState,
        style = {
            // 使用 Standard 样式，支持 3D 建筑和地标
            MapboxStandardStyle {
                lightPreset = effectiveLightPreset
                show3dObjects = BooleanValue(mapLayerConfig.show3dBuildings)
                showPlaceLabels = BooleanValue(mapLayerConfig.showPlaceLabels)
                showRoadLabels = BooleanValue(mapLayerConfig.showRoadLabels)
                showPointOfInterestLabels = BooleanValue(mapLayerConfig.showPointOfInterestLabels)
                showTransitLabels = BooleanValue(mapLayerConfig.showTransitLabels)
            }
        },
        onMapClickListener = {
            selectedMarkerId.value = null
            onMapClick()
            true
        }
    ) {
        // 渲染当前设备
        currentDevice?.let { device ->
            if (!device.location.latitude().isNaN() && !device.location.longitude().isNaN()) {
                // 动画位置
                val animatedPosition = remember { mutableStateOf(device.location) }
                val currentAnimator = remember { mutableStateOf<ValueAnimator?>(null) }

                LaunchedEffect(device.location) {
                    if (!device.location.latitude().isNaN() && !device.location.longitude().isNaN()) {
                        currentAnimator.value?.cancel()
                        currentAnimator.value = animatePositionSmooth(
                            fromPosition = animatedPosition.value,
                            toPosition = device.location,
                            duration = 600L
                        ) { newPosition ->
                            animatedPosition.value = newPosition
                        }
                    }
                }

                DisposableEffect(Unit) {
                    onDispose { currentAnimator.value?.cancel() }
                }

                val rawBearing = currentDeviceHeading ?: device.bearing
                val displayBearing = if (rawBearing.isNaN() || rawBearing.isInfinite()) 0f else rawBearing

                // 方向扇形
                val sectorPoints = calculateSectorPoints(
                    center = animatedPosition.value,
                    radius = 220.0,
                    direction = displayBearing,
                    fov = 30f
                )

                PolygonAnnotation(
                    points = listOf(sectorPoints)
                ) {
                    fillColor = Color(0x55007AFF)
                    fillOutlineColor = Color(0xFF007AFF)
                }

                // 蓝色小圆点
                CircleAnnotation(
                    point = animatedPosition.value
                ) {
                    circleRadius = 15.0
                    circleColor = Color(0xFF007AFF)
                    circleStrokeColor = Color.White
                    circleStrokeWidth = 4.0
                }

                // 选中高亮圈
                val isSelected = selectedMarkerId.value == device.id
                if (isSelected) {
                    CircleAnnotation(point = animatedPosition.value) {
                        circleRadius = 50.0
                        circleColor = Color(0x33007AFF)
                        circleStrokeColor = Color(0xFF007AFF)
                        circleStrokeWidth = 3.0
                    }
                }

                // 设备 Marker
                val iconToUse = if (deviceShouldShowMergedIcon) togetherIcon else avatarIcon
                iconToUse?.let { icon ->
                    PointAnnotation(point = animatedPosition.value) {
                        iconImage = icon
                        iconAnchor = com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor.BOTTOM
                        interactionsState.onClicked {
                            selectedMarkerId.value = device.id
                            onMarkerClick(device)
                            true
                        }
                    }
                }
            }
        }

        // 渲染联系人
        val renderedGroups = remember { mutableSetOf<Int>() }
        renderedGroups.clear()

        validContacts.forEach { contact ->
            if (contactsMergedWithDevice.contains(contact.id)) return@forEach

            contact.location?.let { location ->
                val groupId = contactGroups[contact.id] ?: -1
                if (groupId >= 0 && renderedGroups.contains(groupId)) return@forEach
                if (groupId >= 0) renderedGroups.add(groupId)

                val sameGroupContacts = if (groupId >= 0) {
                    validContacts.filter { c ->
                        contactGroups[c.id] == groupId && !contactsMergedWithDevice.contains(c.id)
                    }
                } else listOf(contact)

                val shouldMerge = sameGroupContacts.size > 1

                // 动画
                val animatedContactPosition = remember { mutableStateOf(location) }
                val contactAnimator = remember { mutableStateOf<ValueAnimator?>(null) }

                LaunchedEffect(location) {
                    if (!location.latitude().isNaN() && !location.longitude().isNaN()) {
                        contactAnimator.value?.cancel()
                        contactAnimator.value = animatePositionSmooth(
                            fromPosition = animatedContactPosition.value,
                            toPosition = location,
                            duration = 600L
                        ) { newPosition ->
                            animatedContactPosition.value = newPosition
                        }
                    }
                }

                DisposableEffect(Unit) {
                    onDispose { contactAnimator.value?.cancel() }
                }

                // 联系人绿色圆点
                CircleAnnotation(point = animatedContactPosition.value) {
                    circleRadius = 15.0
                    circleColor = Color(0xFF34C759)
                    circleStrokeColor = Color.White
                    circleStrokeWidth = 4.0
                }

                // 选中高亮
                val isContactSelected = selectedMarkerId.value == "contact_${contact.id}"
                if (isContactSelected) {
                    CircleAnnotation(point = animatedContactPosition.value) {
                        circleRadius = 50.0
                        circleColor = Color(0x3334C759)
                        circleStrokeColor = Color(0xFF34C759)
                        circleStrokeWidth = 3.0
                    }
                }

                // 联系人 Marker
                val contactIcon = if (shouldMerge) togetherIcon else pigIcon
                contactIcon?.let { icon ->
                    PointAnnotation(point = animatedContactPosition.value) {
                        iconImage = icon
                        iconAnchor = com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor.BOTTOM
                        interactionsState.onClicked {
                            selectedMarkerId.value = "contact_${contact.id}"
                            onContactMarkerClick(contact)
                            true
                        }
                    }
                }
            }
        }

        // 获取 MapboxMap 实例并应用保存的样式
        MapEffect(Unit) { mapView ->
            // 禁用默认缩放按钮
            mapView.gestures.apply {
                // 配置手势
            }
            onMapReady(mapView.mapboxMap)

            // 加载保存的地图样式
            val savedStyleName = MapSettingsManager.loadMapStyle(context)
            val styleUri = when (savedStyleName) {
                "SATELLITE" -> com.mapbox.maps.Style.STANDARD_SATELLITE
                "OUTDOORS" -> com.mapbox.maps.Style.OUTDOORS
                else -> null // STANDARD 是默认样式，无需重新加载
            }
            styleUri?.let { mapView.mapboxMap.loadStyle(it) }
        }

        // 路况图层控制
        MapEffect(showTraffic) { mapView ->
            val style = mapView.mapboxMap.style
            if (style != null) {
                val trafficSourceId = "mapbox-traffic-source"
                val trafficLayerId = "traffic-layer"

                if (showTraffic) {
                    // 添加路况图层
                    if (style.getSource(trafficSourceId) == null) {
                        style.addSource(
                            vectorSource(trafficSourceId) {
                                url("mapbox://mapbox.mapbox-traffic-v1")
                            }
                        )
                    }
                    if (style.getLayer(trafficLayerId) == null) {
                        style.addLayer(
                            lineLayer(trafficLayerId, trafficSourceId) {
                                sourceLayer("traffic")
                                // 根据拥堵程度设置颜色
                                lineColor(
                                    match {
                                        get("congestion")
                                        literal("low")
                                        color(android.graphics.Color.parseColor("#4CAF50")) // 绿色 - 畅通
                                        literal("moderate")
                                        color(android.graphics.Color.parseColor("#FFC107")) // 黄色 - 缓行
                                        literal("heavy")
                                        color(android.graphics.Color.parseColor("#FF9800")) // 橙色 - 拥堵
                                        literal("severe")
                                        color(android.graphics.Color.parseColor("#F44336")) // 红色 - 严重拥堵
                                        color(android.graphics.Color.parseColor("#9E9E9E")) // 默认灰色
                                    }
                                )
                                lineWidth(3.0)
                                lineCap(LineCap.ROUND)
                                lineJoin(LineJoin.ROUND)
                            }
                        )
                    }
                } else {
                    // 移除路况图层
                    style.getLayer(trafficLayerId)?.let {
                        style.removeStyleLayer(trafficLayerId)
                    }
                    style.getSource(trafficSourceId)?.let {
                        style.removeStyleSource(trafficSourceId)
                    }
                }
            }
        }
    }
}

/**
 * 计算两个 Point 之间的距离（米）
 */
private fun calculateDistance(from: Point, to: Point): Double {
    val earthRadius = 6371009.0
    val lat1 = Math.toRadians(from.latitude())
    val lat2 = Math.toRadians(to.latitude())
    val deltaLat = Math.toRadians(to.latitude() - from.latitude())
    val deltaLng = Math.toRadians(to.longitude() - from.longitude())

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
    center: Point,
    radius: Double,
    direction: Float,
    fov: Float = 60f
): List<Point> {
    val points = mutableListOf<Point>()
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
private fun computeOffset(from: Point, distance: Double, heading: Double): Point {
    val d = distance / 6371009.0
    val h = Math.toRadians(heading)
    val fromLat = Math.toRadians(from.latitude())
    val fromLng = Math.toRadians(from.longitude())

    val lat = asin(sin(fromLat) * cos(d) + cos(fromLat) * sin(d) * cos(h))
    val lng = fromLng + atan2(sin(h) * sin(d) * cos(fromLat), cos(d) - sin(fromLat) * sin(lat))

    return pointOf(Math.toDegrees(lat), Math.toDegrees(lng))
}

/**
 * 在两个 Point 之间进行线性插值
 */
private fun interpolatePoint(from: Point, to: Point, fraction: Float): Point {
    val lat = from.latitude() + (to.latitude() - from.latitude()) * fraction
    val lng = from.longitude() + (to.longitude() - from.longitude()) * fraction
    return pointOf(lat, lng)
}

/**
 * 创建平滑移动位置的动画
 */
private fun animatePositionSmooth(
    fromPosition: Point,
    toPosition: Point,
    duration: Long = 500L,
    onUpdate: (Point) -> Unit
): ValueAnimator {
    return ValueAnimator.ofFloat(0f, 1f).apply {
        this.duration = duration
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            val fraction = animation.animatedValue as Float
            val newPosition = interpolatePoint(fromPosition, toPosition, fraction)
            if (!newPosition.latitude().isNaN() && !newPosition.longitude().isNaN()) {
                onUpdate(newPosition)
            }
        }
        start()
    }
}
