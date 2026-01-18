package me.ikate.findmy.ui.screen.main.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import me.ikate.findmy.ui.theme.LocalMapThemeColors
import me.ikate.findmy.ui.theme.MapTheme
import me.ikate.findmy.ui.theme.MapThemeColors
import me.ikate.findmy.util.MapCameraHelper
import me.ikate.findmy.util.MapSettingsManager

/**
 * 地图图层配置数据类
 */
data class MapLayerConfig(
    val showTraffic: Boolean = false,
    val show3dBuildings: Boolean = true,
    val showLandmarkIcons: Boolean = true,
    val showPlaceLabels: Boolean = true,
    val showRoadLabels: Boolean = true,
    val showPointOfInterestLabels: Boolean = true,
    val showTransitLabels: Boolean = true,
    val is3DViewEnabled: Boolean = false,
    val lightPreset: LightPreset = LightPreset.AUTO
)

/**
 * 光照预设
 */
enum class LightPreset(val displayName: String, val icon: ImageVector) {
    AUTO("自动", Icons.Outlined.AutoMode),  // 根据本地时间自动切换
    DAY("白天", Icons.Outlined.LightMode),
    DUSK("黄昏", Icons.Outlined.WbTwilight),
    DAWN("黎明", Icons.Outlined.WbTwilight),
    NIGHT("夜间", Icons.Outlined.DarkMode);

    companion object {
        /**
         * 根据当前时间计算应该使用的光照预设
         * - 05:00 - 06:30: 黎明
         * - 06:30 - 17:30: 白天
         * - 17:30 - 19:00: 黄昏
         * - 19:00 - 05:00: 夜间
         */
        fun calculateByTime(): LightPreset {
            val calendar = java.util.Calendar.getInstance()
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = calendar.get(java.util.Calendar.MINUTE)
            val timeInMinutes = hour * 60 + minute

            return when {
                timeInMinutes in 300..389 -> DAWN      // 05:00 - 06:29
                timeInMinutes in 390..1049 -> DAY      // 06:30 - 17:29
                timeInMinutes in 1050..1139 -> DUSK    // 17:30 - 18:59
                else -> NIGHT                           // 19:00 - 04:59
            }
        }

        /**
         * 获取实际应用的光照预设
         * 如果是 AUTO 模式，则根据时间计算
         */
        fun getEffectivePreset(preset: LightPreset): LightPreset {
            return if (preset == AUTO) calculateByTime() else preset
        }
    }
}

/**
 * 地图图层切换按钮
 * 支持主题颜色同步
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLayerButton(
    map: MapboxMap?,
    modifier: Modifier = Modifier,
    isTrafficEnabled: Boolean = false,
    onTrafficToggle: (Boolean) -> Unit = {},
    config: MapLayerConfig = MapLayerConfig(),
    onConfigChange: (MapLayerConfig) -> Unit = {},
    themeColors: MapThemeColors = MapTheme.Day
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // 合并 traffic 状态到 config
    val currentConfig = config.copy(showTraffic = isTrafficEnabled)

    FloatingActionButton(
        onClick = { showBottomSheet = true },
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        containerColor = themeColors.buttonBackground,
        contentColor = themeColors.buttonIcon,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Icon(
            imageVector = Icons.Default.Layers,
            contentDescription = "图层",
            modifier = Modifier.size(22.dp)
        )
    }

    if (showBottomSheet && map != null) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = themeColors.surface,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .background(
                                themeColors.border,
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            MapLayerContent(
                map = map,
                config = currentConfig,
                themeColors = themeColors,
                onConfigChange = { newConfig ->
                    if (newConfig.showTraffic != currentConfig.showTraffic) {
                        onTrafficToggle(newConfig.showTraffic)
                    }
                    onConfigChange(newConfig)
                },
                onClose = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showBottomSheet = false
                        }
                    }
                }
            )
        }
    }
}

/**
 * Mapbox 地图样式枚举
 */
private enum class MapStyleType(
    val styleUri: String,
    val displayName: String,
    val description: String
) {
    STANDARD(Style.STANDARD, "标准", "清晰的街道地图"),
    SATELLITE(Style.STANDARD_SATELLITE, "卫星", "高清卫星影像"),
    OUTDOORS(Style.OUTDOORS, "户外", "地形和等高线")
}

@Composable
private fun MapLayerContent(
    map: MapboxMap,
    config: MapLayerConfig,
    themeColors: MapThemeColors,
    onConfigChange: (MapLayerConfig) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    // 从本地存储加载样式
    val savedStyleName = remember { MapSettingsManager.loadMapStyle(context) }
    val initialStyle = remember {
        MapStyleType.entries.find { it.name == savedStyleName } ?: MapStyleType.STANDARD
    }
    var currentStyle by remember { mutableStateOf(initialStyle) }
    val scrollState = rememberScrollState()

    // 首次加载时应用保存的样式
    LaunchedEffect(Unit) {
        if (currentStyle.styleUri != Style.STANDARD) {
            map.loadStyle(currentStyle.styleUri)
        }
    }

    CompositionLocalProvider(LocalMapThemeColors provides themeColors) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // 标题
            Text(
                text = "地图样式",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = themeColors.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

        // 地图类型选择
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(MapStyleType.entries) { style ->
                MapStyleCard(
                    style = style,
                    isSelected = currentStyle == style,
                    onClick = {
                        map.loadStyle(style.styleUri)
                        currentStyle = style
                        MapSettingsManager.saveMapStyle(context, style.name)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(themeColors.border)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 地图功能（紧凑网格布局）
        Text(
            text = "地图功能",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = themeColors.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 第一行：路况、3D建筑、地标、3D视角
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CompactFeatureIcon(
                icon = { TrafficIcon(config.showTraffic) },
                label = "路况",
                isEnabled = config.showTraffic,
                onClick = { onConfigChange(config.copy(showTraffic = !config.showTraffic)) },
                modifier = Modifier.weight(1f)
            )
            CompactFeatureIcon(
                icon = { BuildingIcon(config.show3dBuildings) },
                label = "3D建筑",
                isEnabled = config.show3dBuildings,
                onClick = { onConfigChange(config.copy(show3dBuildings = !config.show3dBuildings)) },
                modifier = Modifier.weight(1f)
            )
            CompactFeatureIcon(
                icon = { LandmarkIcon(config.showLandmarkIcons) },
                label = "地标",
                isEnabled = config.showLandmarkIcons,
                onClick = { onConfigChange(config.copy(showLandmarkIcons = !config.showLandmarkIcons)) },
                modifier = Modifier.weight(1f)
            )
            CompactFeatureIcon(
                icon = { ThreeDViewIcon(config.is3DViewEnabled) },
                label = "3D视角",
                isEnabled = config.is3DViewEnabled,
                onClick = {
                    val newEnabled = !config.is3DViewEnabled
                    onConfigChange(config.copy(is3DViewEnabled = newEnabled))
                    val currentCamera = map.cameraState
                    if (newEnabled) {
                        map.flyTo(
                            CameraOptions.Builder()
                                .pitch(MapCameraHelper.PITCH_3D)
                                .zoom(currentCamera.zoom.coerceAtLeast(16.0))
                                .build(),
                            MapAnimationOptions.mapAnimationOptions { duration(500) }
                        )
                    } else {
                        map.flyTo(
                            CameraOptions.Builder()
                                .pitch(0.0)
                                .build(),
                            MapAnimationOptions.mapAnimationOptions { duration(500) }
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 第二行：地点标签、道路标签、兴趣点
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CompactFeatureIcon(
                icon = { LabelIcon(config.showPlaceLabels) },
                label = "地点",
                isEnabled = config.showPlaceLabels,
                onClick = { onConfigChange(config.copy(showPlaceLabels = !config.showPlaceLabels)) },
                modifier = Modifier.weight(1f)
            )
            CompactFeatureIcon(
                icon = { RoadLabelIcon(config.showRoadLabels) },
                label = "道路",
                isEnabled = config.showRoadLabels,
                onClick = { onConfigChange(config.copy(showRoadLabels = !config.showRoadLabels)) },
                modifier = Modifier.weight(1f)
            )
            CompactFeatureIcon(
                icon = { PoiIcon(config.showPointOfInterestLabels) },
                label = "兴趣点",
                isEnabled = config.showPointOfInterestLabels,
                onClick = { onConfigChange(config.copy(showPointOfInterestLabels = !config.showPointOfInterestLabels)) },
                modifier = Modifier.weight(1f)
            )
            // 占位，保持对齐
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(themeColors.border)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 光照设置
        Text(
            text = "光照效果",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = themeColors.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 自动选项（第一行，显示当前实际使用的光照）
        val currentAutoPreset = LightPreset.calculateByTime()
        AutoLightPresetChip(
            isSelected = config.lightPreset == LightPreset.AUTO,
            currentPreset = currentAutoPreset,
            onClick = { onConfigChange(config.copy(lightPreset = LightPreset.AUTO)) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 手动选项（第二行）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LightPreset.entries.filter { it != LightPreset.AUTO }.forEach { preset ->
                LightPresetChip(
                    preset = preset,
                    isSelected = config.lightPreset == preset,
                    onClick = { onConfigChange(config.copy(lightPreset = preset)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MapStyleCard(
    style: MapStyleType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val themeColors = LocalMapThemeColors.current
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) themeColors.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "border"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Surface(
            modifier = Modifier.size(100.dp, 72.dp),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = if (isSelected) 4.dp else 1.dp,
            color = Color.White
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                borderColor.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            ) {
                when (style) {
                    MapStyleType.STANDARD -> StandardMapPreview()
                    MapStyleType.SATELLITE -> SatelliteMapPreview()
                    MapStyleType.OUTDOORS -> OutdoorsMapPreview()
                }

                // 选中指示器
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .background(themeColors.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = style.displayName,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) themeColors.primary else themeColors.onSurface
        )
    }
}

/**
 * 紧凑型功能图标（点击切换）
 */
@Composable
private fun CompactFeatureIcon(
    icon: @Composable () -> Unit,
    label: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = LocalMapThemeColors.current
    val backgroundColor by animateColorAsState(
        targetValue = if (isEnabled) themeColors.primary else themeColors.surfaceVariant,
        animationSpec = tween(200),
        label = "bg"
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(backgroundColor, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isEnabled) FontWeight.Medium else FontWeight.Normal,
            color = if (isEnabled) themeColors.primary else themeColors.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun LightPresetChip(
    preset: LightPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = LocalMapThemeColors.current
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) themeColors.primary else themeColors.surfaceVariant,
        animationSpec = tween(200),
        label = "bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else themeColors.onSurfaceVariant,
        animationSpec = tween(200),
        label = "content"
    )

    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = preset.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = preset.displayName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

/**
 * 自动光照预设选项（显示当前自动计算的光照）
 */
@Composable
private fun AutoLightPresetChip(
    isSelected: Boolean,
    currentPreset: LightPreset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = LocalMapThemeColors.current
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) themeColors.primary else themeColors.surfaceVariant,
        animationSpec = tween(200),
        label = "bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else themeColors.onSurfaceVariant,
        animationSpec = tween(200),
        label = "content"
    )

    Surface(
        modifier = modifier
            .height(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoMode,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "自动",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 显示当前自动计算的光照
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) Color.White.copy(alpha = 0.2f) else themeColors.primary.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = currentPreset.icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else themeColors.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "当前: ${currentPreset.displayName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isSelected) Color.White else themeColors.primary
                    )
                }
            }
        }
    }
}

// ==================== 图标组件 ====================

@Composable
private fun TrafficIcon(isEnabled: Boolean) {
    // 路况图标使用固定的彩色线条，不受主题影响
    val roadColor = if (isEnabled) Color.White.copy(alpha = 0.3f) else Color(0xFFE2E8F0)
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        // 道路
        drawRoundRect(
            color = roadColor,
            topLeft = Offset(w * 0.1f, h * 0.35f),
            size = Size(w * 0.8f, h * 0.3f),
            cornerRadius = CornerRadius(4f)
        )

        // 路况线（绿=畅通，黄=缓行，红=拥堵）
        drawLine(
            color = Color(0xFF48BB78),  // 绿色
            start = Offset(w * 0.15f, h * 0.5f),
            end = Offset(w * 0.4f, h * 0.5f),
            strokeWidth = 4f
        )
        drawLine(
            color = Color(0xFFECC94B),  // 黄色
            start = Offset(w * 0.45f, h * 0.5f),
            end = Offset(w * 0.65f, h * 0.5f),
            strokeWidth = 4f
        )
        drawLine(
            color = Color(0xFFF56565),  // 红色
            start = Offset(w * 0.7f, h * 0.5f),
            end = Offset(w * 0.85f, h * 0.5f),
            strokeWidth = 4f
        )
    }
}

@Composable
private fun BuildingIcon(isEnabled: Boolean) {
    val themeColors = LocalMapThemeColors.current
    val iconColor = if (isEnabled) Color.White else themeColors.onSurfaceVariant
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        // 3D 建筑
        val path = Path().apply {
            moveTo(w * 0.3f, h * 0.8f)
            lineTo(w * 0.3f, h * 0.35f)
            lineTo(w * 0.5f, h * 0.2f)
            lineTo(w * 0.7f, h * 0.35f)
            lineTo(w * 0.7f, h * 0.8f)
            close()
        }
        drawPath(path, iconColor.copy(alpha = 0.3f))
        drawPath(path, iconColor, style = Stroke(2f))

        // 窗户
        drawRect(
            color = iconColor,
            topLeft = Offset(w * 0.4f, h * 0.45f),
            size = Size(w * 0.1f, h * 0.1f)
        )
        drawRect(
            color = iconColor,
            topLeft = Offset(w * 0.55f, h * 0.45f),
            size = Size(w * 0.1f, h * 0.1f)
        )
    }
}

@Composable
private fun LandmarkIcon(isEnabled: Boolean) {
    val themeColors = LocalMapThemeColors.current
    val iconColor = if (isEnabled) Color.White else themeColors.onSurfaceVariant
    val starColor = if (isEnabled) themeColors.primary else Color.White
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        // 地标标记
        drawCircle(
            color = iconColor.copy(alpha = 0.2f),
            radius = w * 0.35f,
            center = Offset(w * 0.5f, h * 0.45f)
        )
        drawCircle(
            color = iconColor,
            radius = w * 0.2f,
            center = Offset(w * 0.5f, h * 0.45f)
        )

        // 星星
        val starPath = Path().apply {
            moveTo(w * 0.5f, h * 0.3f)
            lineTo(w * 0.55f, h * 0.42f)
            lineTo(w * 0.65f, h * 0.45f)
            lineTo(w * 0.58f, h * 0.55f)
            lineTo(w * 0.6f, h * 0.65f)
            lineTo(w * 0.5f, h * 0.58f)
            lineTo(w * 0.4f, h * 0.65f)
            lineTo(w * 0.42f, h * 0.55f)
            lineTo(w * 0.35f, h * 0.45f)
            lineTo(w * 0.45f, h * 0.42f)
            close()
        }
        drawPath(starPath, starColor)
    }
}

@Composable
private fun LabelIcon(isEnabled: Boolean) {
    val themeColors = LocalMapThemeColors.current
    val iconColor = if (isEnabled) Color.White else themeColors.onSurfaceVariant
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        // 标签框
        drawRoundRect(
            color = iconColor.copy(alpha = 0.15f),
            topLeft = Offset(w * 0.1f, h * 0.3f),
            size = Size(w * 0.8f, h * 0.4f),
            cornerRadius = CornerRadius(6f)
        )

        // 文字线条
        drawLine(
            color = iconColor,
            start = Offset(w * 0.2f, h * 0.45f),
            end = Offset(w * 0.8f, h * 0.45f),
            strokeWidth = 3f
        )
        drawLine(
            color = iconColor.copy(alpha = 0.5f),
            start = Offset(w * 0.2f, h * 0.58f),
            end = Offset(w * 0.6f, h * 0.58f),
            strokeWidth = 2f
        )
    }
}

@Composable
private fun RoadLabelIcon(isEnabled: Boolean) {
    val themeColors = LocalMapThemeColors.current
    val iconColor = if (isEnabled) Color.White else themeColors.onSurfaceVariant
    val roadColor = if (isEnabled) Color.White.copy(alpha = 0.3f) else Color(0xFFE2E8F0)
    val textColor = if (isEnabled) themeColors.primary else Color.White
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        // 道路
        drawLine(
            color = roadColor,
            start = Offset(0f, h * 0.5f),
            end = Offset(w, h * 0.5f),
            strokeWidth = 8f
        )

        // 路牌
        drawRoundRect(
            color = iconColor,
            topLeft = Offset(w * 0.25f, h * 0.25f),
            size = Size(w * 0.5f, h * 0.25f),
            cornerRadius = CornerRadius(4f)
        )

        // 文字
        drawLine(
            color = textColor,
            start = Offset(w * 0.32f, h * 0.375f),
            end = Offset(w * 0.68f, h * 0.375f),
            strokeWidth = 2f
        )
    }
}

@Composable
private fun PoiIcon(isEnabled: Boolean) {
    val themeColors = LocalMapThemeColors.current
    val iconColor = if (isEnabled) Color.White else themeColors.onSurfaceVariant
    val dotColor = if (isEnabled) themeColors.primary else Color.White
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height

        // POI 标记
        val pinPath = Path().apply {
            moveTo(w * 0.5f, h * 0.85f)
            lineTo(w * 0.3f, h * 0.45f)
            quadraticTo(w * 0.3f, h * 0.15f, w * 0.5f, h * 0.15f)
            quadraticTo(w * 0.7f, h * 0.15f, w * 0.7f, h * 0.45f)
            close()
        }
        drawPath(pinPath, iconColor)

        drawCircle(
            color = dotColor,
            radius = w * 0.12f,
            center = Offset(w * 0.5f, h * 0.38f)
        )
    }
}

@Composable
private fun ThreeDViewIcon(isEnabled: Boolean) {
    val themeColors = LocalMapThemeColors.current
    val color = if (isEnabled) Color.White else themeColors.onSurfaceVariant
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            // 顶面
            moveTo(w * 0.5f, h * 0.15f)
            lineTo(w * 0.85f, h * 0.3f)
            lineTo(w * 0.5f, h * 0.45f)
            lineTo(w * 0.15f, h * 0.3f)
            close()
        }
        drawPath(path, color.copy(alpha = 0.4f))

        // 左面
        val leftPath = Path().apply {
            moveTo(w * 0.15f, h * 0.3f)
            lineTo(w * 0.5f, h * 0.45f)
            lineTo(w * 0.5f, h * 0.8f)
            lineTo(w * 0.15f, h * 0.65f)
            close()
        }
        drawPath(leftPath, color.copy(alpha = 0.6f))

        // 右面
        val rightPath = Path().apply {
            moveTo(w * 0.5f, h * 0.45f)
            lineTo(w * 0.85f, h * 0.3f)
            lineTo(w * 0.85f, h * 0.65f)
            lineTo(w * 0.5f, h * 0.8f)
            close()
        }
        drawPath(rightPath, color.copy(alpha = 0.8f))

        // 边框
        drawPath(path, color, style = Stroke(1.5f))
        drawPath(leftPath, color, style = Stroke(1.5f))
        drawPath(rightPath, color, style = Stroke(1.5f))
    }
}

// ==================== 地图预览 ====================

@Composable
private fun StandardMapPreview() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(color = Color(0xFFF8F9FA))

        // 道路网格
        val roadColor = Color.White
        drawLine(roadColor, Offset(w * 0.3f, 0f), Offset(w * 0.3f, h), 4f)
        drawLine(roadColor, Offset(w * 0.7f, 0f), Offset(w * 0.7f, h), 4f)
        drawLine(roadColor, Offset(0f, h * 0.4f), Offset(w, h * 0.4f), 4f)
        drawLine(roadColor, Offset(0f, h * 0.7f), Offset(w, h * 0.7f), 4f)

        // 公园
        drawCircle(Color(0xFFB8E6B8), w * 0.1f, Offset(w * 0.5f, h * 0.55f))

        // 水域
        drawRoundRect(
            Color(0xFFAAD4E6),
            Offset(w * 0.1f, h * 0.1f),
            Size(w * 0.25f, h * 0.2f),
            CornerRadius(4f)
        )
    }
}

@Composable
private fun SatelliteMapPreview() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(color = Color(0xFF2D3A2D))

        // 植被
        drawCircle(Color(0xFF1E4D1E), w * 0.15f, Offset(w * 0.25f, h * 0.3f))
        drawCircle(Color(0xFF1E4D1E), w * 0.12f, Offset(w * 0.7f, h * 0.6f))

        // 道路
        drawLine(Color(0xFF4A4A4A), Offset(w * 0.4f, 0f), Offset(w * 0.4f, h), 3f)
        drawLine(Color(0xFF4A4A4A), Offset(0f, h * 0.5f), Offset(w, h * 0.5f), 3f)

        // 建筑
        drawRoundRect(
            Color(0xFF5A5A5A),
            Offset(w * 0.55f, h * 0.2f),
            Size(w * 0.25f, h * 0.2f),
            CornerRadius(2f)
        )
    }
}

@Composable
private fun OutdoorsMapPreview() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(color = Color(0xFFF5EDE0))

        // 等高线
        val contourColor = Color(0xFFD4C4A8)
        for (i in 1..3) {
            drawCircle(
                contourColor,
                w * (0.35f - i * 0.08f),
                Offset(w * 0.6f, h * 0.5f),
                style = Stroke(1.5f)
            )
        }

        // 河流
        val riverPath = Path().apply {
            moveTo(0f, h * 0.3f)
            quadraticTo(w * 0.4f, h * 0.4f, w, h * 0.6f)
        }
        drawPath(riverPath, Color(0xFF87CEEB), style = Stroke(4f))

        // 植被
        drawCircle(Color(0xFFA8D9A8), w * 0.12f, Offset(w * 0.2f, h * 0.7f))

        // 小路
        drawLine(Color.White, Offset(w * 0.1f, h * 0.2f), Offset(w * 0.9f, h * 0.8f), 2f)
    }
}
