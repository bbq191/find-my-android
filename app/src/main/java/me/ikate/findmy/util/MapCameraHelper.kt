package me.ikate.findmy.util

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import me.ikate.findmy.data.model.Device

/**
 * 地图相机控制辅助类
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
    fun adjustMapPadding(map: GoogleMap?, bottomSheetOffset: Float) {
        map?.setPadding(
            0,                       // left
            0,                       // top
            0,                       // right
            bottomSheetOffset.toInt() // bottom
        )
    }

    /**
     * 平滑移动地图到指定设备位置
     *
     * @param map 地图实例
     * @param device 目标设备
     * @param zoom 缩放级别（默认 15）
     */
    fun animateToDevice(map: GoogleMap?, device: Device, zoom: Float = 15f) {
        if (device.location.latitude.isNaN() || device.location.longitude.isNaN()) return
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                device.location,
                zoom
            )
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
    fun zoomToShowAll(map: GoogleMap?, devices: List<Device>, padding: Int = 100) {
        if (devices.isEmpty()) return

        map?.let { m ->
            // 构建边界框
            val boundsBuilder = LatLngBounds.Builder()
            devices.forEach { device ->
                boundsBuilder.include(device.location)
            }
            val bounds = boundsBuilder.build()

            // 移动相机以显示所有设备
            try {
                m.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, padding)
                )
            } catch (e: Exception) {
                // 地图未加载完成时可能抛出异常
                e.printStackTrace()
            }
        }
    }

    /**
     * 移动地图到指定位置
     *
     * @param map 地图实例
     * @param latLng 目标位置
     * @param zoom 缩放级别（默认 15）
     * @param animate 是否使用动画（默认 true）
     */
    fun moveToLocation(
        map: GoogleMap?,
        latLng: LatLng,
        zoom: Float = 15f,
        animate: Boolean = true
    ) {
        if (latLng.latitude.isNaN() || latLng.longitude.isNaN()) return
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, zoom)
        if (animate) {
            map?.animateCamera(cameraUpdate)
        } else {
            map?.moveCamera(cameraUpdate)
        }
    }

    /**
     * 平滑移动地图到指定位置 (便捷方法)
     * 用于联系人位置导航
     *
     * @param map 地图实例
     * @param latLng 目标位置
     * @param zoom 缩放级别（默认 15）
     */
    fun animateToLocation(map: GoogleMap?, latLng: LatLng, zoom: Float = 15f) {
        moveToLocation(map, latLng, zoom, animate = true)
    }
}