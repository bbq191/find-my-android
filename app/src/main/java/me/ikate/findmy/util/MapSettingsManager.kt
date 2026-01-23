package me.ikate.findmy.util

import android.content.Context
import androidx.core.content.edit
import me.ikate.findmy.ui.screen.main.components.LightPreset
import me.ikate.findmy.ui.screen.main.components.MapLayerConfig

/**
 * 地图设置持久化管理器
 * 保存用户的地图偏好设置，包括图层、样式、视角等
 *
 * 支持的地图类型：
 * - STANDARD: 标准地图
 * - SATELLITE: 卫星地图
 *
 * 个性化样式由光照预设自动控制：
 * - 浅色模式：白浅样式 (styleId=1)
 * - 深色模式：墨渊样式 (styleId=2)
 */
object MapSettingsManager {

    private const val PREFS_NAME = "map_settings"

    // 设置键
    private const val KEY_MAP_STYLE = "map_style"
    private const val KEY_SHOW_TRAFFIC = "show_traffic"
    private const val KEY_SHOW_3D_BUILDINGS = "show_3d_buildings"
    private const val KEY_SHOW_LANDMARK_ICONS = "show_landmark_icons"
    private const val KEY_SHOW_PLACE_LABELS = "show_place_labels"
    private const val KEY_SHOW_ROAD_LABELS = "show_road_labels"
    private const val KEY_SHOW_POI_LABELS = "show_poi_labels"
    private const val KEY_SHOW_TRANSIT_LABELS = "show_transit_labels"
    private const val KEY_IS_3D_VIEW_ENABLED = "is_3d_view_enabled"
    private const val KEY_LIGHT_PRESET = "light_preset"

    // 默认值
    private const val DEFAULT_MAP_STYLE = "STANDARD"
    private const val DEFAULT_SHOW_TRAFFIC = false
    private const val DEFAULT_SHOW_3D_BUILDINGS = true
    private const val DEFAULT_SHOW_LANDMARK_ICONS = true
    private const val DEFAULT_SHOW_PLACE_LABELS = true
    private const val DEFAULT_SHOW_ROAD_LABELS = true
    private const val DEFAULT_SHOW_POI_LABELS = true
    private const val DEFAULT_SHOW_TRANSIT_LABELS = true
    private const val DEFAULT_IS_3D_VIEW_ENABLED = false
    private const val DEFAULT_LIGHT_PRESET = "AUTO"

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 保存地图图层配置
     */
    fun saveMapLayerConfig(context: Context, config: MapLayerConfig) {
        getPrefs(context).edit {
            putBoolean(KEY_SHOW_TRAFFIC, config.showTraffic)
            putBoolean(KEY_SHOW_3D_BUILDINGS, config.show3dBuildings)
            putBoolean(KEY_SHOW_LANDMARK_ICONS, config.showLandmarkIcons)
            putBoolean(KEY_SHOW_PLACE_LABELS, config.showPlaceLabels)
            putBoolean(KEY_SHOW_ROAD_LABELS, config.showRoadLabels)
            putBoolean(KEY_SHOW_POI_LABELS, config.showPointOfInterestLabels)
            putBoolean(KEY_SHOW_TRANSIT_LABELS, config.showTransitLabels)
            putBoolean(KEY_IS_3D_VIEW_ENABLED, config.is3DViewEnabled)
            putString(KEY_LIGHT_PRESET, config.lightPreset.name)
        }
    }

    /**
     * 加载地图图层配置
     */
    fun loadMapLayerConfig(context: Context): MapLayerConfig {
        val prefs = getPrefs(context)
        val lightPresetName = prefs.getString(KEY_LIGHT_PRESET, DEFAULT_LIGHT_PRESET) ?: DEFAULT_LIGHT_PRESET
        val lightPreset = try {
            LightPreset.valueOf(lightPresetName)
        } catch (e: Exception) {
            LightPreset.LIGHT
        }

        return MapLayerConfig(
            showTraffic = prefs.getBoolean(KEY_SHOW_TRAFFIC, DEFAULT_SHOW_TRAFFIC),
            show3dBuildings = prefs.getBoolean(KEY_SHOW_3D_BUILDINGS, DEFAULT_SHOW_3D_BUILDINGS),
            showLandmarkIcons = prefs.getBoolean(KEY_SHOW_LANDMARK_ICONS, DEFAULT_SHOW_LANDMARK_ICONS),
            showPlaceLabels = prefs.getBoolean(KEY_SHOW_PLACE_LABELS, DEFAULT_SHOW_PLACE_LABELS),
            showRoadLabels = prefs.getBoolean(KEY_SHOW_ROAD_LABELS, DEFAULT_SHOW_ROAD_LABELS),
            showPointOfInterestLabels = prefs.getBoolean(KEY_SHOW_POI_LABELS, DEFAULT_SHOW_POI_LABELS),
            showTransitLabels = prefs.getBoolean(KEY_SHOW_TRANSIT_LABELS, DEFAULT_SHOW_TRANSIT_LABELS),
            is3DViewEnabled = prefs.getBoolean(KEY_IS_3D_VIEW_ENABLED, DEFAULT_IS_3D_VIEW_ENABLED),
            lightPreset = lightPreset
        )
    }

    /**
     * 保存地图样式
     */
    fun saveMapStyle(context: Context, styleName: String) {
        getPrefs(context).edit {
            putString(KEY_MAP_STYLE, styleName)
        }
    }

    /**
     * 加载地图样式
     */
    fun loadMapStyle(context: Context): String {
        return getPrefs(context).getString(KEY_MAP_STYLE, DEFAULT_MAP_STYLE) ?: DEFAULT_MAP_STYLE
    }

    /**
     * 保存路况显示状态
     */
    fun saveTrafficEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit {
            putBoolean(KEY_SHOW_TRAFFIC, enabled)
        }
    }

    /**
     * 加载路况显示状态
     */
    fun loadTrafficEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_TRAFFIC, DEFAULT_SHOW_TRAFFIC)
    }
}
