package me.ikate.findmy.ui.screen.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import me.ikate.findmy.ui.theme.FindMyShapes
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
import com.tencent.tencentmap.mapsdk.maps.TencentMap
import com.tencent.tencentmap.mapsdk.maps.CameraUpdateFactory
import com.tencent.tencentmap.mapsdk.maps.model.CameraPosition
import kotlinx.coroutines.launch
import me.ikate.findmy.util.TencentMapCameraHelper
import me.ikate.findmy.util.MapSettingsManager
import me.ikate.findmy.util.rememberHaptics

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
 * MONET: 简化为三个选项 [自动] [浅色] [深色]
 */
enum class LightPreset(val displayName: String, val icon: ImageVector) {
    AUTO("自动", Icons.Outlined.AutoMode),   // 根据本地时间自动切换
    LIGHT("浅色", Icons.Outlined.LightMode), // 白天模式
    DARK("深色", Icons.Outlined.DarkMode);   // 夜间模式

    companion object {
        /**
         * 根据当前时间计算应该使用的光照预设
         * - 06:00 - 18:00: 浅色
         * - 18:00 - 06:00: 深色
         */
        fun calculateByTime(): LightPreset {
            val calendar = java.util.Calendar.getInstance()
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            return if (hour in 6..17) LIGHT else DARK
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
    map: TencentMap?,
    modifier: Modifier = Modifier,
    isTrafficEnabled: Boolean = false,
    onTrafficToggle: (Boolean) -> Unit = {},
    config: MapLayerConfig = MapLayerConfig(),
    onConfigChange: (MapLayerConfig) -> Unit = {}
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // 合并 traffic 状态到 config
    val currentConfig = config.copy(showTraffic = isTrafficEnabled)

    // Small FAB 样式 - 使用 Material 3 主题色
    FloatingActionButton(
        onClick = { showBottomSheet = true },
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 4.dp,
            pressedElevation = 6.dp
        )
    ) {
        Icon(
            imageVector = Icons.Default.Layers,
            contentDescription = "图层",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }

    if (showBottomSheet && map != null) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
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
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            },
            shape = FindMyShapes.BottomSheetTop
        ) {
            MapLayerContent(
                map = map,
                config = currentConfig,
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
 * 腾讯地图类型枚举
 *
 * 地图类型：标准地图、卫星地图
 * 地图风格（浅色/深色）由光照预设自动控制
 */
private enum class MapStyleType(
    val mapType: Int,
    val displayName: String,
    val description: String
) {
    STANDARD(TencentMap.MAP_TYPE_NORMAL, "标准", "默认地图样式"),
    SATELLITE(TencentMap.MAP_TYPE_SATELLITE, "卫星", "高清卫星影像")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapLayerContent(
    map: TencentMap,
    config: MapLayerConfig,
    onConfigChange: (MapLayerConfig) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()

    // 从本地存储加载样式
    val savedStyleName = remember { MapSettingsManager.loadMapStyle(context) }
    val initialStyle = remember {
        MapStyleType.entries.find { it.name == savedStyleName } ?: MapStyleType.STANDARD
    }
    var currentStyle by remember { mutableStateOf(initialStyle) }
    val scrollState = rememberScrollState()

    // 首次加载时应用保存的地图类型
    LaunchedEffect(Unit) {
        map.mapType = currentStyle.mapType
    }

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
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 地图样式（可视化卡片）
        Text(
            text = "地图类型",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MapStyleType.entries.forEach { style ->
                MapStyleCard(
                    style = style,
                    isSelected = currentStyle == style,
                    onClick = {
                        haptics.click()  // 地图图层切换：模拟按下实体开关的质感
                        map.mapType = style.mapType
                        currentStyle = style
                        MapSettingsManager.saveMapStyle(context, style.name)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // MONET: 地图功能 - 使用 Filter Chips (过滤胶囊)
        // 只保留高频功能：实时路况、3D建筑
        Text(
            text = "地图功能",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 流式布局排列 Filter Chips
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 实时路况
            FilterChip(
                selected = config.showTraffic,
                onClick = {
                    haptics.click()  // Filter Chip 开关：轻微的确认感
                    onConfigChange(config.copy(showTraffic = !config.showTraffic))
                },
                label = { Text("实时路况") },
                leadingIcon = if (config.showTraffic) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                )
            )

            // 3D建筑
            FilterChip(
                selected = config.show3dBuildings,
                onClick = {
                    haptics.click()  // Filter Chip 开关：轻微的确认感
                    onConfigChange(config.copy(show3dBuildings = !config.show3dBuildings))
                },
                label = { Text("3D建筑") },
                leadingIcon = if (config.show3dBuildings) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 分隔线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 光照设置 - 使用 Segmented Button
        Text(
            text = "光照效果",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 当前自动计算的光照提示
        val currentAutoPreset = LightPreset.calculateByTime()
        if (config.lightPreset == LightPreset.AUTO) {
            Surface(
                shape = FindMyShapes.Small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = currentAutoPreset.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "当前自动应用: ${currentAutoPreset.displayName}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Segmented Button Row
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            LightPreset.entries.forEachIndexed { index, preset ->
                SegmentedButton(
                    selected = config.lightPreset == preset,
                    onClick = {
                        haptics.click()  // 光照设置切换：清脆的点击感
                        onConfigChange(config.copy(lightPreset = preset))
                    },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = LightPreset.entries.size
                    ),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = config.lightPreset == preset) {
                            Icon(
                                imageVector = preset.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = preset.displayName,
                        fontSize = 12.sp,
                        fontWeight = if (config.lightPreset == preset) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 地图样式可视化卡片
 * 显示地图预览缩略图，选中时显示边框和对勾
 */
@Composable
private fun MapStyleCard(
    style: MapStyleType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) primaryColor else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(200),
        label = "border"
    )
    val borderWidth by animateFloatAsState(
        targetValue = if (isSelected) 2.5f else 1f,
        animationSpec = tween(200),
        label = "borderWidth"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = FindMyShapes.Medium,
            shadowElevation = if (isSelected) 4.dp else 1.dp,
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(borderWidth.dp, borderColor)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 地图预览
                when (style) {
                    MapStyleType.STANDARD -> StandardMapPreview()
                    MapStyleType.SATELLITE -> SatelliteMapPreview()
                }

                // 选中指示器（右上角对勾）
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(primaryColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
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
            color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface
        )

        // 描述文字
        Text(
            text = style.description,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor by animateColorAsState(
        targetValue = if (isEnabled) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
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
            color = if (isEnabled) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else primaryColor.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = currentPreset.icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else primaryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "当前: ${currentPreset.displayName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else primaryColor
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
    val iconColor = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
    val iconColor = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val starColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
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
    val iconColor = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
    val iconColor = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val roadColor = if (isEnabled) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f) else Color(0xFFE2E8F0)
    val textColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
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
    val iconColor = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val dotColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
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
    val color = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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

