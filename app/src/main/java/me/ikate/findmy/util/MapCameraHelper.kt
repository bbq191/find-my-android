package me.ikate.findmy.util

import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxDelicateApi
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.latitude
import me.ikate.findmy.data.model.longitude

/**
 * Mapbox 地图相机控制辅助类
 * 封装地图视野调整、Padding 设置、相机动画等逻辑
 */
object MapCameraHelper {

    /**
     * 根据底部面板偏移量调整地图 Padding
     * 确保 Marker 不被底部面板遮挡
     *
     * @param map 地图实例
     * @param bottomSheetOffset 底部面板从屏幕底部向上的距离（像素）
     */
    fun adjustMapPadding(map: MapboxMap?, bottomSheetOffset: Float) {
        map?.setCamera(
            CameraOptions.Builder()
                .padding(EdgeInsets(0.0, 0.0, bottomSheetOffset.toDouble(), 0.0))
                .build()
        )
    }

    /** 默认倾斜角度（0° 为平面视图，用户可通过图层按钮开启 3D） */
    private const val DEFAULT_PITCH = 0.0

    /** 3D 模式倾斜角度 */
    const val PITCH_3D = 45.0

    /**
     * 平滑移动地图到指定设备位置
     *
     * @param map 地图实例
     * @param device 目标设备
     * @param zoom 缩放级别（默认 15）
     * @param pitch 倾斜角度（默认 45 度，展示 3D 效果）
     */
    fun animateToDevice(map: MapboxMap?, device: Device, zoom: Double = 15.0, pitch: Double = DEFAULT_PITCH) {
        if (device.location.latitude.isNaN() || device.location.longitude.isNaN()) return
        map?.flyTo(
            CameraOptions.Builder()
                .center(device.location)
                .zoom(zoom)
                .pitch(pitch)
                .build(),
            MapAnimationOptions.mapAnimationOptions {
                duration(600)
            }
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
    @OptIn(MapboxDelicateApi::class)
    fun zoomToShowAll(map: MapboxMap?, devices: List<Device>, padding: Int = 100) {
        if (devices.isEmpty()) return

        map?.let { m ->
            try {
                // 计算边界
                val points = devices.map { it.location }
                val minLat = points.minOf { it.latitude }
                val maxLat = points.maxOf { it.latitude }
                val minLng = points.minOf { it.longitude }
                val maxLng = points.maxOf { it.longitude }

                // 计算中心点和合适的缩放级别
                val centerLat = (minLat + maxLat) / 2
                val centerLng = (minLng + maxLng) / 2

                // 使用 cameraForCoordinates 计算合适的相机位置
                val cameraOptions = m.cameraForCoordinates(
                    coordinates = points,
                    camera = CameraOptions.Builder().build(),
                    coordinatesPadding = EdgeInsets(
                        padding.toDouble(),
                        padding.toDouble(),
                        padding.toDouble(),
                        padding.toDouble()
                    ),
                    maxZoom = null,
                    offset = null
                )

                m.flyTo(
                    cameraOptions,
                    MapAnimationOptions.mapAnimationOptions {
                        duration(600)
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 移动地图到指定位置
     *
     * @param map 地图实例
     * @param point 目标位置
     * @param zoom 缩放级别（默认 15）
     * @param pitch 倾斜角度（默认 45 度，展示 3D 效果）
     * @param animate 是否使用动画（默认 true）
     */
    fun moveToLocation(
        map: MapboxMap?,
        point: Point,
        zoom: Double = 15.0,
        pitch: Double = DEFAULT_PITCH,
        animate: Boolean = true
    ) {
        if (point.latitude().isNaN() || point.longitude().isNaN()) return

        val cameraOptions = CameraOptions.Builder()
            .center(point)
            .zoom(zoom)
            .pitch(pitch)
            .build()

        if (animate) {
            map?.flyTo(
                cameraOptions,
                MapAnimationOptions.mapAnimationOptions {
                    duration(600)
                }
            )
        } else {
            map?.setCamera(cameraOptions)
        }
    }

    /**
     * 平滑移动地图到指定位置 (便捷方法)
     * 用于联系人位置导航
     *
     * @param map 地图实例
     * @param point 目标位置
     * @param zoom 缩放级别（默认 15）
     * @param pitch 倾斜角度（默认 45 度，展示 3D 效果）
     */
    fun animateToLocation(map: MapboxMap?, point: Point, zoom: Double = 15.0, pitch: Double = DEFAULT_PITCH) {
        moveToLocation(map, point, zoom, pitch, animate = true)
    }
}