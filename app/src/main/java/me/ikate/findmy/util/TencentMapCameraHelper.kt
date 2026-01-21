package me.ikate.findmy.util

import com.tencent.tencentmap.mapsdk.maps.CameraUpdateFactory
import com.tencent.tencentmap.mapsdk.maps.TencentMap
import com.tencent.tencentmap.mapsdk.maps.model.CameraPosition
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import com.tencent.tencentmap.mapsdk.maps.model.LatLngBounds
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device

/**
 * 腾讯地图相机控制辅助类
 * 封装地图视野调整、Padding 设置、相机动画等逻辑
 */
object TencentMapCameraHelper {

    /** 默认倾斜角度（0° 为平面视图，用户可通过图层按钮开启 3D） */
    private const val DEFAULT_TILT = 0f

    /** 3D 模式倾斜角度 */
    const val TILT_3D = 45f

    /** 默认动画时长（毫秒） */
    private const val DEFAULT_DURATION = 600L

    /** 初始化动画时长（毫秒）- 开屏使用更长的动画，更美观 */
    private const val INITIAL_ANIMATION_DURATION = 800L

    /** 最大缩放级别限制 - 避免两个近点过度放大 */
    private const val MAX_ZOOM_FOR_BOUNDS = 17f

    /** 最小缩放级别限制 - 避免太多远点过度缩小 */
    private const val MIN_ZOOM_FOR_BOUNDS = 3f

    /** 单点时的默认缩放级别 */
    private const val SINGLE_POINT_ZOOM = 16f

    /** 边距比例 - 占屏幕宽度/高度的比例 */
    private const val PADDING_RATIO = 0.12f

    /**
     * 根据底部面板偏移量调整地图 Padding
     * 确保 Marker 不被底部面板遮挡
     *
     * @param map 地图实例
     * @param bottomPadding 底部面板高度（像素）
     */
    fun adjustMapPadding(map: TencentMap?, bottomPadding: Int) {
        // 腾讯地图可通过 setPadding 设置视口偏移
        map?.uiSettings?.let { uiSettings ->
            // 腾讯地图暂不支持直接设置视口偏移，通过调整罗盘位置等间接处理
            // 如需精确偏移，可在调用 animateCamera 时手动计算目标位置
        }
    }

    /**
     * 平滑移动地图到指定设备位置
     *
     * @param map 地图实例
     * @param device 目标设备
     * @param zoom 缩放级别（默认 15）
     * @param tilt 倾斜角度（默认 0 度，平面视图）
     */
    fun animateToDevice(map: TencentMap?, device: Device, zoom: Float = 15f, tilt: Float = DEFAULT_TILT) {
        if (device.location.latitude.isNaN() || device.location.longitude.isNaN()) return

        val cameraPosition = CameraPosition.Builder()
            .target(device.location)
            .zoom(zoom)
            .tilt(tilt)
            .build()

        map?.animateCamera(
            CameraUpdateFactory.newCameraPosition(cameraPosition),
            DEFAULT_DURATION,
            null
        )
    }

    /**
     * 平滑移动地图到指定联系人位置
     *
     * @param map 地图实例
     * @param contact 目标联系人
     * @param zoom 缩放级别（默认 15）
     * @param tilt 倾斜角度（默认 0 度，平面视图）
     */
    fun animateToContact(map: TencentMap?, contact: Contact, zoom: Float = 15f, tilt: Float = DEFAULT_TILT) {
        val location = contact.location ?: return
        if (location.latitude.isNaN() || location.longitude.isNaN()) return

        val cameraPosition = CameraPosition.Builder()
            .target(location)
            .zoom(zoom)
            .tilt(tilt)
            .build()

        map?.animateCamera(
            CameraUpdateFactory.newCameraPosition(cameraPosition),
            DEFAULT_DURATION,
            null
        )
    }

    /**
     * 缩放地图以显示所有设备
     * 计算所有设备的边界框，并添加适当的 Padding
     *
     * @param map 地图实例
     * @param devices 设备列表
     * @param padding 边界框内边距（像素，默认 100）
     */
    fun zoomToShowAllDevices(map: TencentMap?, devices: List<Device>, padding: Int = 100) {
        if (devices.isEmpty()) return

        map?.let { m ->
            try {
                val validDevices = devices.filter {
                    !it.location.latitude.isNaN() && !it.location.longitude.isNaN()
                }
                if (validDevices.isEmpty()) return

                val boundsBuilder = LatLngBounds.Builder()
                validDevices.forEach { device ->
                    boundsBuilder.include(device.location)
                }

                val bounds = boundsBuilder.build()
                m.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, padding),
                    DEFAULT_DURATION,
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 缩放地图以显示所有设备和联系人
     *
     * @param map 地图实例
     * @param devices 设备列表
     * @param contacts 联系人列表
     * @param padding 边界框内边距（像素，默认 100）
     */
    fun zoomToShowAll(
        map: TencentMap?,
        devices: List<Device>,
        contacts: List<Contact>,
        padding: Int = 100
    ) {
        if (devices.isEmpty() && contacts.isEmpty()) return

        map?.let { m ->
            try {
                val boundsBuilder = LatLngBounds.Builder()
                var hasValidPoint = false

                // 添加设备位置
                devices.forEach { device ->
                    if (!device.location.latitude.isNaN() && !device.location.longitude.isNaN()) {
                        boundsBuilder.include(device.location)
                        hasValidPoint = true
                    }
                }

                // 添加联系人位置
                contacts.forEach { contact ->
                    contact.location?.let { loc ->
                        if (!loc.latitude.isNaN() && !loc.longitude.isNaN()) {
                            boundsBuilder.include(loc)
                            hasValidPoint = true
                        }
                    }
                }

                if (!hasValidPoint) return

                val bounds = boundsBuilder.build()
                m.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, padding),
                    DEFAULT_DURATION,
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 智能缩放地图以美观地显示所有点位（仿 iOS FindMy 开屏效果）
     *
     * 特点：
     * 1. 根据屏幕尺寸动态计算边距，确保点位不贴边
     * 2. 考虑底部面板高度，增加底部额外边距
     * 3. 限制最大缩放级别，避免两个近点过度放大
     * 4. 限制最小缩放级别，避免太多远点过度缩小
     * 5. 单点时使用合适的缩放级别
     * 6. 使用较长的动画时长，开屏更美观
     *
     * @param map 地图实例
     * @param devices 设备列表（当前用户的设备）
     * @param contacts 联系人列表（有效的、未暂停/超时的联系人）
     * @param screenWidthPx 屏幕宽度（像素）
     * @param screenHeightPx 屏幕高度（像素）
     * @param bottomSheetHeightPx 底部面板高度（像素，用于增加底部边距）
     * @param isInitial 是否是初始化调用（初始化时使用更长的动画）
     */
    fun zoomToShowAllSmartly(
        map: TencentMap?,
        devices: List<Device>,
        contacts: List<Contact>,
        screenWidthPx: Int,
        screenHeightPx: Int,
        bottomSheetHeightPx: Int = 0,
        isInitial: Boolean = true
    ) {
        map ?: return

        try {
            // 收集所有有效位置点
            val allPoints = mutableListOf<LatLng>()

            // 添加设备位置
            devices.forEach { device ->
                if (!device.location.latitude.isNaN() && !device.location.longitude.isNaN()) {
                    allPoints.add(device.location)
                }
            }

            // 添加联系人位置
            contacts.forEach { contact ->
                contact.location?.let { loc ->
                    if (!loc.latitude.isNaN() && !loc.longitude.isNaN()) {
                        allPoints.add(loc)
                    }
                }
            }

            if (allPoints.isEmpty()) return

            // 单点情况：直接定位到该点，使用合适的缩放级别
            if (allPoints.size == 1) {
                val singlePoint = allPoints.first()
                val cameraPosition = CameraPosition.Builder()
                    .target(singlePoint)
                    .zoom(SINGLE_POINT_ZOOM)
                    .tilt(DEFAULT_TILT)
                    .build()

                map.animateCamera(
                    CameraUpdateFactory.newCameraPosition(cameraPosition),
                    if (isInitial) INITIAL_ANIMATION_DURATION else DEFAULT_DURATION,
                    null
                )
                return
            }

            // 多点情况：计算边界框并智能缩放
            val boundsBuilder = LatLngBounds.Builder()
            allPoints.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()

            // 动态计算边距（借鉴 iOS FindMy 的美观边距）
            // 水平边距：屏幕宽度的 12%
            // 顶部边距：屏幕高度的 15%（为状态栏和顶部按钮留空间）
            // 底部边距：屏幕高度的 12% + 底部面板高度（确保点位不被面板遮挡）
            val horizontalPadding = (screenWidthPx * PADDING_RATIO).toInt()
            val topPadding = (screenHeightPx * 0.15f).toInt()
            val bottomPadding = (screenHeightPx * PADDING_RATIO).toInt() + bottomSheetHeightPx

            // 使用 newLatLngBoundsRect 设置不同方向的边距
            // 参数顺序: bounds, left, right, top, bottom
            val cameraUpdate = CameraUpdateFactory.newLatLngBoundsRect(
                bounds,
                horizontalPadding,  // left
                horizontalPadding,  // right
                topPadding,         // top
                bottomPadding       // bottom
            )

            // 使用回调来限制缩放级别
            map.animateCamera(
                cameraUpdate,
                if (isInitial) INITIAL_ANIMATION_DURATION else DEFAULT_DURATION,
                object : TencentMap.CancelableCallback {
                    override fun onFinish() {
                        // 动画完成后检查并限制缩放级别
                        val currentZoom = map.cameraPosition.zoom
                        val adjustedZoom = currentZoom.coerceIn(MIN_ZOOM_FOR_BOUNDS, MAX_ZOOM_FOR_BOUNDS)

                        if (adjustedZoom != currentZoom) {
                            // 需要调整缩放级别
                            val newPosition = CameraPosition.Builder()
                                .target(map.cameraPosition.target)
                                .zoom(adjustedZoom)
                                .tilt(DEFAULT_TILT)
                                .build()
                            map.animateCamera(
                                CameraUpdateFactory.newCameraPosition(newPosition),
                                300, // 短动画
                                null
                            )
                        }
                    }

                    override fun onCancel() {
                        // 动画被取消，不做处理
                    }
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取当前设备位置（从设备列表中获取）
     * 用于计算初始视野
     */
    fun getCurrentDeviceLocation(devices: List<Device>, currentDeviceId: String): LatLng? {
        return devices.find { it.id == currentDeviceId }?.location?.let { loc ->
            if (!loc.latitude.isNaN() && !loc.longitude.isNaN()) loc else null
        }
    }

    /**
     * 移动地图到指定位置
     *
     * @param map 地图实例
     * @param latLng 目标位置
     * @param zoom 缩放级别（默认 15）
     * @param tilt 倾斜角度（默认 0 度，平面视图）
     * @param animate 是否使用动画（默认 true）
     */
    fun moveToLocation(
        map: TencentMap?,
        latLng: LatLng,
        zoom: Float = 15f,
        tilt: Float = DEFAULT_TILT,
        animate: Boolean = true
    ) {
        if (latLng.latitude.isNaN() || latLng.longitude.isNaN()) return

        val cameraPosition = CameraPosition.Builder()
            .target(latLng)
            .zoom(zoom)
            .tilt(tilt)
            .build()

        val cameraUpdate = CameraUpdateFactory.newCameraPosition(cameraPosition)

        if (animate) {
            map?.animateCamera(cameraUpdate, DEFAULT_DURATION, null)
        } else {
            map?.moveCamera(cameraUpdate)
        }
    }

    /**
     * 平滑移动地图到指定位置 (便捷方法)
     *
     * @param map 地图实例
     * @param latLng 目标位置
     * @param zoom 缩放级别（默认 15）
     * @param tilt 倾斜角度（默认 0 度，平面视图）
     */
    fun animateToLocation(map: TencentMap?, latLng: LatLng, zoom: Float = 15f, tilt: Float = DEFAULT_TILT) {
        moveToLocation(map, latLng, zoom, tilt, animate = true)
    }

    /**
     * 立即移动地图到指定位置（无动画）
     * 用于用户点击操作，确保立即响应
     *
     * @param map 地图实例
     * @param latLng 目标位置
     * @param zoom 缩放级别（默认 15）
     * @param tilt 倾斜角度（默认 0 度，平面视图）
     */
    fun jumpToLocation(map: TencentMap?, latLng: LatLng, zoom: Float = 15f, tilt: Float = DEFAULT_TILT) {
        // 先停止任何正在进行的相机动画
        map?.stopAnimation()
        moveToLocation(map, latLng, zoom, tilt, animate = false)
    }

    /**
     * 切换 3D 视角
     *
     * @param map 地图实例
     * @param enable 是否启用 3D 视角
     */
    fun toggle3DView(map: TencentMap?, enable: Boolean) {
        val currentPosition = map?.cameraPosition ?: return

        val newTilt = if (enable) TILT_3D else 0f
        val newZoom = if (enable) {
            currentPosition.zoom.coerceAtLeast(16f)
        } else {
            currentPosition.zoom
        }

        val cameraPosition = CameraPosition.Builder()
            .target(currentPosition.target)
            .zoom(newZoom)
            .tilt(newTilt)
            .bearing(currentPosition.bearing)
            .build()

        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(cameraPosition),
            500,
            null
        )
    }
}
