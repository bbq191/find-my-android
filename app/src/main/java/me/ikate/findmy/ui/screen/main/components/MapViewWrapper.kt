package me.ikate.findmy.ui.screen.main.components

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.animation.LinearInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.google.maps.android.compose.Circle
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
import kotlin.math.sqrt

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
@SuppressLint("HardwareIds", "LocalContextResourcesRead")
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

        // 准备 Marker 图标资源
        val currentIcon = remember { me.ikate.findmy.util.AppIconHelper.getCurrentIcon(context) }

        // 加载当前设备的头像 Descriptor（官方推荐：使用 fromResource）
        val avatarDescriptor = remember(currentIcon) {
            val isGirl = currentIcon == me.ikate.findmy.util.AppIconHelper.AppIcon.GIRL
            val resourceId = if (isGirl) {
                context.resources.getIdentifier("marker_girl", "drawable", context.packageName)
            } else {
                context.resources.getIdentifier("marker_boy", "drawable", context.packageName)
            }

            if (resourceId != 0) {
                BitmapDescriptorFactory.fromResource(resourceId)
            } else {
                null
            }
        }

        // 加载联系人的猪图标 Descriptor
        val pigDescriptor = remember {
            val resourceId = context.resources.getIdentifier("marker_pig", "drawable", context.packageName)
            if (resourceId != 0) {
                BitmapDescriptorFactory.fromResource(resourceId)
            } else {
                null
            }
        }

        // 加载合并图标 Descriptor（根据当前图标选择 marker_together_b 或 marker_together_g）
        // 需要缩放到与其他marker一致的尺寸（144x144）
        val togetherDescriptor = remember(currentIcon) {
            val isGirl = currentIcon == me.ikate.findmy.util.AppIconHelper.AppIcon.GIRL
            val resourceId = if (isGirl) {
                context.resources.getIdentifier("marker_together_g", "drawable", context.packageName)
            } else {
                context.resources.getIdentifier("marker_together_b", "drawable", context.packageName)
            }

            if (resourceId != 0) {
                try {
                    // 加载原始 Bitmap
                    val originalBitmap = BitmapFactory.decodeResource(context.resources, resourceId)
                    if (originalBitmap != null) {
                        // 目标尺寸：与其他marker保持一致（144x144）
                        val targetSize = 144
                        // 计算等比例缩放后的尺寸
                        val aspectRatio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
                        val (newWidth, newHeight) = if (aspectRatio >= 1f) {
                            // 宽度较大，以宽度为基准
                            Pair(targetSize, (targetSize / aspectRatio).toInt())
                        } else {
                            // 高度较大，以高度为基准
                            Pair((targetSize * aspectRatio).toInt(), targetSize)
                        }
                        // 使用高质量缩放（FILTER_BITMAP 保持清晰度）
                        val scaledBitmap = Bitmap.createScaledBitmap(
                            originalBitmap,
                            newWidth,
                            newHeight,
                            true // 使用双线性过滤，保持清晰度
                        )
                        BitmapDescriptorFactory.fromBitmap(scaledBitmap)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MapViewWrapper", "Failed to load together marker: ${e.message}")
                    null
                }
            } else {
                null
            }
        }

        // 获取当前设备位置
        val currentDevice = devices.find { it.id == currentDeviceId }
        val currentDeviceLocation = currentDevice?.location

        // 计算标记合并逻辑：距离小于3米的点合并显示
        val MERGE_DISTANCE_METERS = 3.0

        // 将所有有效联系人位置收集起来
        val validContacts = contacts.filter { contact ->
            contact.location?.let { loc ->
                !loc.latitude.isNaN() && !loc.longitude.isNaN()
            } ?: false
        }

        // 计算哪些联系人需要与当前设备合并显示
        val contactsMergedWithDevice = mutableSetOf<String>()
        if (currentDeviceLocation != null &&
            !currentDeviceLocation.latitude.isNaN() &&
            !currentDeviceLocation.longitude.isNaN()) {
            validContacts.forEach { contact ->
                contact.location?.let { contactLoc ->
                    val distance = calculateDistance(currentDeviceLocation, contactLoc)
                    if (distance < MERGE_DISTANCE_METERS) {
                        contactsMergedWithDevice.add(contact.id)
                    }
                }
            }
        }

        // 计算联系人之间的合并组（使用Union-Find算法）
        val contactGroups = calculateMergeGroups(validContacts, MERGE_DISTANCE_METERS)

        // 判断当前设备是否应该显示合并图标（有任何联系人与其距离小于3米）
        val deviceShouldShowMergedIcon = contactsMergedWithDevice.isNotEmpty()

        // 渲染当前设备的marker
        currentDevice?.let { device ->
            if (!device.location.latitude.isNaN() && !device.location.longitude.isNaN()) {
                androidx.compose.runtime.key(device.id) {
                    // 动画位置状态（用于同步 Marker、Circle 和 Polygon）
                    val animatedPosition = remember { mutableStateOf(device.location) }
                    val markerState =
                        com.google.maps.android.compose.rememberMarkerState(position = device.location)

                    // 保存当前动画引用，用于在新动画开始时取消旧动画
                    val currentAnimator = remember { mutableStateOf<ValueAnimator?>(null) }

                    // 使用动画平滑移动 Marker 和 Circle
                    LaunchedEffect(device.location) {
                        if (!device.location.latitude.isNaN() && !device.location.longitude.isNaN()) {
                            val currentPosition = animatedPosition.value
                            // 取消之前的动画（如果有）
                            currentAnimator.value?.cancel()
                            // 启动新的平滑移动动画
                            currentAnimator.value = animatePositionSmooth(
                                fromPosition = currentPosition,
                                toPosition = device.location,
                                duration = 600L, // 600ms 的平滑过渡
                                onUpdate = { newPosition ->
                                    animatedPosition.value = newPosition
                                    markerState.position = newPosition
                                }
                            )
                        }
                    }

                    // 组件销毁时取消动画
                    DisposableEffect(Unit) {
                        onDispose {
                            currentAnimator.value?.cancel()
                        }
                    }

                    // 确定此设备的显示方向
                    val rawBearing = if (currentDeviceHeading != null) {
                        currentDeviceHeading
                    } else {
                        device.bearing
                    }
                    val displayBearing =
                        if (rawBearing.isNaN() || rawBearing.isInfinite()) 0f else rawBearing

                    // 绘制方向指示雷达（使用动画位置）
                    val safeBearing = if (displayBearing.isNaN()) 0f else displayBearing

                    val sectorPoints = calculateSectorPoints(
                        center = animatedPosition.value,
                        radius = 220.0,
                        direction = safeBearing,
                        fov = 30f
                    )

                    Polygon(
                        points = sectorPoints,
                        fillColor = androidx.compose.ui.graphics.Color(0x55007AFF),
                        strokeColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                        strokeWidth = 2f,
                        zIndex = 1f
                    )

                    // 绘制当前位置的蓝色小圆点（使用动画位置）
                    Circle(
                        center = animatedPosition.value,
                        radius = 15.0,
                        fillColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                        strokeColor = androidx.compose.ui.graphics.Color.White,
                        strokeWidth = 4f,
                        zIndex = 1.5f,
                        visible = true,
                        clickable = false
                    )

                    // 选择图标：合并时使用 together 图标，否则使用普通头像图标
                    val icon = if (deviceShouldShowMergedIcon) {
                        togetherDescriptor ?: avatarDescriptor ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    } else {
                        avatarDescriptor ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    }

                    // 生成合并显示时的标题
                    val mergedTitle = if (deviceShouldShowMergedIcon) {
                        val mergedNames = contactsMergedWithDevice.mapNotNull { contactId ->
                            validContacts.find { it.id == contactId }?.name
                        }
                        "${device.name} + ${mergedNames.joinToString(", ")}"
                    } else {
                        device.name
                    }

                    val mergedSnippet = if (deviceShouldShowMergedIcon) {
                        "已合并 (距离<3m)"
                    } else {
                        "当前设备"
                    }

                    Marker(
                        state = markerState,
                        title = mergedTitle,
                        snippet = mergedSnippet,
                        icon = icon,
                        rotation = 0f,
                        flat = false,
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 1.0f),
                        zIndex = 2f,
                        onClick = {
                            onMarkerClick(device)
                            false
                        }
                    )
                }
            }
        }

        // 渲染联系人位置标记
        // 根据合并组渲染：每个组只渲染一个marker
        val renderedGroups = mutableSetOf<Int>()

        validContacts.forEach { contact ->
            // 如果联系人已与当前设备合并，则不单独渲染
            if (contactsMergedWithDevice.contains(contact.id)) {
                return@forEach
            }

            contact.location?.let { location ->
                val groupId = contactGroups[contact.id] ?: -1

                // 检查该组是否已经渲染过
                if (groupId >= 0 && renderedGroups.contains(groupId)) {
                    return@forEach
                }

                // 标记该组已渲染
                if (groupId >= 0) {
                    renderedGroups.add(groupId)
                }

                // 获取同组的其他联系人
                val sameGroupContacts = if (groupId >= 0) {
                    validContacts.filter { c ->
                        contactGroups[c.id] == groupId && !contactsMergedWithDevice.contains(c.id)
                    }
                } else {
                    listOf(contact)
                }

                // 判断是否需要合并显示（组内有多个联系人）
                val shouldMerge = sameGroupContacts.size > 1

                androidx.compose.runtime.key("group_${groupId}_${contact.id}") {
                    // 动画位置状态（用于同步 Marker 和 Circle）
                    val animatedContactPosition = remember { mutableStateOf(location) }
                    val markerState =
                        com.google.maps.android.compose.rememberMarkerState(position = location)

                    // 保存当前动画引用，用于在新动画开始时取消旧动画
                    val contactAnimator = remember { mutableStateOf<ValueAnimator?>(null) }

                    // 使用动画平滑移动联系人 Marker 和 Circle
                    LaunchedEffect(location) {
                        if (!location.latitude.isNaN() && !location.longitude.isNaN()) {
                            val currentPosition = animatedContactPosition.value
                            // 取消之前的动画（如果有）
                            contactAnimator.value?.cancel()
                            // 启动新的平滑移动动画
                            contactAnimator.value = animatePositionSmooth(
                                fromPosition = currentPosition,
                                toPosition = location,
                                duration = 600L, // 600ms 的平滑过渡
                                onUpdate = { newPosition ->
                                    animatedContactPosition.value = newPosition
                                    markerState.position = newPosition
                                }
                            )
                        }
                    }

                    // 组件销毁时取消动画
                    DisposableEffect(Unit) {
                        onDispose {
                            contactAnimator.value?.cancel()
                        }
                    }

                    // 绘制联系人位置的小圆点（使用动画位置）
                    android.util.Log.d("MapViewWrapper", "🟢 绘制联系人圆点: ${contact.name} at ${animatedContactPosition.value}")
                    Circle(
                        center = animatedContactPosition.value,
                        radius = 15.0,
                        fillColor = androidx.compose.ui.graphics.Color(0xFF34C759),
                        strokeColor = androidx.compose.ui.graphics.Color.White,
                        strokeWidth = 4f,
                        zIndex = 1.5f,
                        visible = true,
                        clickable = false
                    )

                    // 选择图标：合并时使用 together 图标，否则使用猪图标
                    val markerIcon = if (shouldMerge) {
                        // 合并显示时使用 together 图标
                        togetherDescriptor ?: avatarDescriptor ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    } else {
                        // 单独显示时使用猪图标
                        pigDescriptor ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    }

                    // 生成标题
                    val markerTitle = if (shouldMerge) {
                        sameGroupContacts.joinToString(", ") { it.name }
                    } else {
                        contact.name
                    }

                    val markerSnippet = if (shouldMerge) {
                        "已合并 (距离<3m)"
                    } else {
                        "联系人位置"
                    }

                    Marker(
                        state = markerState,
                        title = markerTitle,
                        snippet = markerSnippet,
                        icon = markerIcon,
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

        // 我们通过 MapEffect 获取原生的 GoogleMap 对象并传递出去
        @Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
        com.google.maps.android.compose.MapEffect(Unit) { map ->
            onMapReady(map)
        }
    }
}

/**
 * 计算两个LatLng之间的距离（米）
 * 使用Haversine公式
 */
private fun calculateDistance(from: LatLng, to: LatLng): Double {
    val earthRadius = 6371009.0 // 地球半径（米）

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
 * 使用Union-Find算法计算联系人的合并组
 * 返回一个Map，key为联系人ID，value为组ID
 */
private fun calculateMergeGroups(
    contacts: List<me.ikate.findmy.data.model.Contact>,
    mergeDistanceMeters: Double
): Map<String, Int> {
    if (contacts.isEmpty()) return emptyMap()

    // Union-Find 数据结构
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

    // 初始化每个联系人为自己的根
    contacts.forEach { contact ->
        parent[contact.id] = contact.id
    }

    // 计算所有联系人之间的距离，距离小于阈值则合并
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

    // 分配组ID
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

/**
 * 在两个 LatLng 之间进行线性插值
 */
private fun interpolateLatLng(from: LatLng, to: LatLng, fraction: Float): LatLng {
    val lat = from.latitude + (to.latitude - from.latitude) * fraction
    val lng = from.longitude + (to.longitude - from.longitude) * fraction
    return LatLng(lat, lng)
}

/**
 * 创建平滑移动位置的动画（带回调）
 * @param fromPosition 起始位置
 * @param toPosition 目标位置
 * @param duration 动画时长（毫秒）
 * @param onUpdate 位置更新回调
 * @return ValueAnimator 实例，可用于取消动画
 */
private fun animatePositionSmooth(
    fromPosition: LatLng,
    toPosition: LatLng,
    duration: Long = 500L,
    onUpdate: (LatLng) -> Unit
): ValueAnimator {
    return ValueAnimator.ofFloat(0f, 1f).apply {
        this.duration = duration
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            val fraction = animation.animatedValue as Float
            val newPosition = interpolateLatLng(fromPosition, toPosition, fraction)
            if (!newPosition.latitude.isNaN() && !newPosition.longitude.isNaN()) {
                onUpdate(newPosition)
            }
        }
        start()
    }
}