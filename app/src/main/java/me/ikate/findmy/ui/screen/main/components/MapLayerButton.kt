package me.ikate.findmy.ui.screen.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import kotlinx.coroutines.launch

/**
 * 地图图层切换按钮
 * 严格模仿 Google Maps 原生样式
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLayerButton(
    map: GoogleMap?,
    modifier: Modifier = Modifier
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    FloatingActionButton(
        onClick = { showBottomSheet = true },
        modifier = modifier.size(56.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 12.dp,
            hoveredElevation = 8.dp
        )
    ) {
        Icon(
            imageVector = Icons.Default.Layers,
            contentDescription = "图层",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
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
                            .size(width = 32.dp, height = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            MapLayerContent(
                map = map,
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

@Composable
private fun MapLayerContent(
    map: GoogleMap,
    onClose: () -> Unit
) {
    // ✅ 真实API - 地图类型
    var currentMapType by remember { mutableStateOf(map.mapType) }

    // ✅ 真实API - 路况图层（Google Maps SDK 原生支持）
    var isTrafficEnabled by remember { mutableStateOf(map.isTrafficEnabled) }

    // ✅ 真实API - 3D建筑（Google Maps SDK 原生支持）
    var is3DEnabled by remember { mutableStateOf(map.isBuildingsEnabled) }

    // ⚠️ UI占位符 - 以下功能需要额外实现
    // 公交和骑行图层在Google Maps Android SDK中没有直接API
    // 需要通过Directions API获取路线数据后手动绘制
    var isTransitEnabled by remember { mutableStateOf(false) }
    var isBicyclingEnabled by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 40.dp)
    ) {
        // 标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "地图类型",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 地图类型选择
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MapTypeOption(
                label = "默认",
                isSelected = currentMapType == GoogleMap.MAP_TYPE_NORMAL,
                onClick = {
                    map.mapType = GoogleMap.MAP_TYPE_NORMAL
                    currentMapType = GoogleMap.MAP_TYPE_NORMAL
                }
            ) {
                DefaultMapPreview()
            }

            MapTypeOption(
                label = "卫星",
                isSelected = currentMapType == GoogleMap.MAP_TYPE_SATELLITE || currentMapType == GoogleMap.MAP_TYPE_HYBRID,
                onClick = {
                    map.mapType = GoogleMap.MAP_TYPE_HYBRID
                    currentMapType = GoogleMap.MAP_TYPE_HYBRID
                }
            ) {
                SatelliteMapPreview()
            }

            MapTypeOption(
                label = "地形",
                isSelected = currentMapType == GoogleMap.MAP_TYPE_TERRAIN,
                onClick = {
                    map.mapType = GoogleMap.MAP_TYPE_TERRAIN
                    currentMapType = GoogleMap.MAP_TYPE_TERRAIN
                }
            ) {
                TerrainMapPreview()
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "地图详细信息",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 详细信息网格布局
        // 第一行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MapDetailOption(
                label = "公交",
                isSelected = isTransitEnabled,
                onClick = { isTransitEnabled = !isTransitEnabled }
            ) {
                PublicTransitIcon(isTransitEnabled)
            }

            MapDetailOption(
                label = "路况",
                isSelected = isTrafficEnabled,
                onClick = {
                    val newValue = !isTrafficEnabled
                    map.isTrafficEnabled = newValue
                    isTrafficEnabled = newValue
                }
            ) {
                TrafficIcon(isTrafficEnabled)
            }

            MapDetailOption(
                label = "骑行",
                isSelected = isBicyclingEnabled,
                onClick = { isBicyclingEnabled = !isBicyclingEnabled }
            ) {
                BicyclingIcon(isBicyclingEnabled)
            }

            MapDetailOption(
                label = "3D",
                isSelected = is3DEnabled,
                onClick = {
                    val newValue = !is3DEnabled
                    map.isBuildingsEnabled = newValue
                    is3DEnabled = newValue

                    // 启用3D时自动调整相机角度以展示3D效果
                    if (newValue) {
                        val currentCamera = map.cameraPosition
                        if (currentCamera.tilt < 30f) {
                            val newCamera = CameraPosition.Builder(currentCamera)
                                .tilt(60f)
                                .zoom(currentCamera.zoom.coerceAtLeast(17f))
                                .build()
                            map.animateCamera(CameraUpdateFactory.newCameraPosition(newCamera))
                        }
                    } else {
                        // 关闭3D时恢复平面视角
                        val currentCamera = map.cameraPosition
                        if (currentCamera.tilt > 0f) {
                             val newCamera = CameraPosition.Builder(currentCamera)
                                .tilt(0f)
                                .build()
                             map.animateCamera(CameraUpdateFactory.newCameraPosition(newCamera))
                        }
                    }
                }
            ) {
                ThreeDIcon(is3DEnabled)
            }
        }
    }
}

// --- 组件绘制 ---

@Composable
private fun MapTypeOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    previewContent: @Composable () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF1A73E8) else Color.Transparent
    val labelColor = if (isSelected) Color(0xFF1A73E8) else Color(0xFF5F6368)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp) // 卡片宽度
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Surface(
            modifier = Modifier.size(width = 100.dp, height = 100.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, borderColor),
            color = Color.Transparent
        ) {
            // 使用 Box 裁剪内容到圆角
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
                previewContent()
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = labelColor,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}


@Composable
private fun MapDetailOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF1A73E8) else Color(0xFFDADCE0) // 选中蓝边，未选中灰边
    val labelColor = if (isSelected) Color(0xFF1A73E8) else Color(0xFF5F6368)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp) // 略微宽一点的点击区域
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Surface(
            modifier = Modifier.size(64.dp), // 正方形
            shape = RoundedCornerShape(16.dp), // 圆角
            border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
            color = Color.White
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                iconContent()
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = labelColor,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

// --- 地图预览图绘制 ---

@Composable
fun PublicTransitIcon(isSelected: Boolean) {
    val primaryColor = if (isSelected) Color(0xFF1A73E8) else Color(0xFF5F6368)
    Canvas(modifier = Modifier.size(32.dp)) {
        val w = size.width
        val h = size.height
        
        // 简化的公交/列车图标
        // 车头
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(w * 0.2f, h * 0.2f),
            size = Size(w * 0.6f, h * 0.5f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 3f)
        )
        // 窗户
        drawRect(
            color = primaryColor,
            topLeft = Offset(w * 0.3f, h * 0.25f),
            size = Size(w * 0.4f, h * 0.2f),
            style = Stroke(width = 2f)
        )
        // 车灯/底部
        drawLine(
            color = primaryColor,
            start = Offset(w * 0.2f, h * 0.55f),
            end = Offset(w * 0.8f, h * 0.55f),
            strokeWidth = 3f
        )
        // 轨道线
        drawLine(
            color = primaryColor,
            start = Offset(w * 0.1f, h * 0.8f),
            end = Offset(w * 0.9f, h * 0.8f),
            strokeWidth = 3f
        )
    }
}

@Composable
fun TrafficIcon(isSelected: Boolean) {
    val primaryColor = if (isSelected) Color(0xFF1A73E8) else Color(0xFF5F6368)
    Canvas(modifier = Modifier.size(36.dp)) { // 稍微大一点以填满
        val w = size.width
        val h = size.height
        
        // 十字路口
        val roadWidth = w * 0.25f
        
        // 垂直路
        drawRect(
            color = Color(0xFFE0E0E0),
            topLeft = Offset((w - roadWidth)/2, 0f),
            size = Size(roadWidth, h)
        )
        // 水平路
        drawRect(
            color = Color(0xFFE0E0E0),
            topLeft = Offset(0f, (h - roadWidth)/2),
            size = Size(w, roadWidth)
        )
        
        // 红绿拥堵线
        drawLine(
            color = Color(0xFFEA4335), // Red
            start = Offset((w - roadWidth)/2 + 4f, h * 0.1f),
            end = Offset((w - roadWidth)/2 + 4f, h * 0.4f),
            strokeWidth = 6f
        )
        drawLine(
            color = Color(0xFF34A853), // Green
            start = Offset((w + roadWidth)/2 - 4f, h * 0.6f),
            end = Offset((w + roadWidth)/2 - 4f, h * 0.9f),
            strokeWidth = 6f
        )
        drawLine(
            color = Color(0xFFFBBC04), // Yellow
            start = Offset(w * 0.1f, (h + roadWidth)/2 - 4f),
            end = Offset(w * 0.4f, (h + roadWidth)/2 - 4f),
            strokeWidth = 6f
        )
    }
}

@Composable
fun BicyclingIcon(isSelected: Boolean) {
    val primaryColor = if (isSelected) Color(0xFF1A73E8) else Color(0xFF5F6368)
    // 使用内置图标即可，因为比较标准，或者绘制一个简化的自行车
    Icon(
        imageVector = Icons.Default.DirectionsBike,
        contentDescription = null,
        tint = primaryColor,
        modifier = Modifier.size(32.dp)
    )
}

@Composable
fun ThreeDIcon(isSelected: Boolean) {
    val primaryColor = if (isSelected) Color(0xFF1A73E8) else Color(0xFF5F6368)
    Canvas(modifier = Modifier.size(32.dp)) {
        val w = size.width
        val h = size.height

        // 简化的3D楼房
        val path = Path().apply {
            // 顶面
            moveTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.9f, h * 0.3f)
            lineTo(w * 0.5f, h * 0.5f)
            lineTo(w * 0.1f, h * 0.3f)
            close()
            // 左面
            moveTo(w * 0.1f, h * 0.3f)
            lineTo(w * 0.5f, h * 0.5f)
            lineTo(w * 0.5f, h * 0.9f)
            lineTo(w * 0.1f, h * 0.7f)
            close()
            // 右面
            moveTo(w * 0.5f, h * 0.5f)
            lineTo(w * 0.9f, h * 0.3f)
            lineTo(w * 0.9f, h * 0.7f)
            lineTo(w * 0.5f, h * 0.9f)
            close()
        }
        drawPath(
            path = path,
            color = primaryColor.copy(alpha = 0.2f),
        )
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 3f)
        )
    }
}

// --- 地图类型预览绘制 ---

@Composable
fun DefaultMapPreview() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 浅色背景
        drawRect(
            color = Color(0xFFF2F2F2)
        )

        // 模拟道路网格（浅灰色道路）
        val roadColor = Color.White
        val roadWidth = 8f

        // 垂直道路
        for (i in 1..3) {
            val x = w * i / 4
            drawLine(
                color = roadColor,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = roadWidth
            )
        }

        // 水平道路
        for (i in 1..3) {
            val y = h * i / 4
            drawLine(
                color = roadColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = roadWidth
            )
        }

        // 绿色公园区域
        drawCircle(
            color = Color(0xFFB8E6B8),
            radius = w * 0.12f,
            center = Offset(w * 0.3f, h * 0.3f)
        )

        // 蓝色水域
        drawRoundRect(
            color = Color(0xFFAAD4E6),
            topLeft = Offset(w * 0.6f, h * 0.6f),
            size = Size(w * 0.3f, h * 0.25f),
            cornerRadius = CornerRadius(4f, 4f)
        )
    }
}

@Composable
fun SatelliteMapPreview() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 深色基底（模拟卫星图）
        drawRect(
            color = Color(0xFF3A4A3A)
        )

        // 模拟建筑物和道路的混合纹理
        // 深绿色区域（植被）
        drawCircle(
            color = Color(0xFF2D5A2D),
            radius = w * 0.15f,
            center = Offset(w * 0.25f, h * 0.25f)
        )

        drawCircle(
            color = Color(0xFF2D5A2D),
            radius = w * 0.12f,
            center = Offset(w * 0.7f, h * 0.7f)
        )

        // 灰色道路和建筑
        drawRect(
            color = Color(0xFF5A5A5A),
            topLeft = Offset(w * 0.4f, 0f),
            size = Size(6f, h)
        )

        drawRect(
            color = Color(0xFF5A5A5A),
            topLeft = Offset(0f, h * 0.5f),
            size = Size(w, 6f)
        )

        // 建筑物色块
        drawRoundRect(
            color = Color(0xFF6A6A6A),
            topLeft = Offset(w * 0.6f, h * 0.2f),
            size = Size(w * 0.2f, h * 0.2f),
            cornerRadius = CornerRadius(2f, 2f)
        )

        // 蓝色水域
        drawRoundRect(
            color = Color(0xFF4A7A9A),
            topLeft = Offset(w * 0.1f, h * 0.6f),
            size = Size(w * 0.25f, h * 0.3f),
            cornerRadius = CornerRadius(3f, 3f)
        )
    }
}

@Composable
fun TerrainMapPreview() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 浅黄色背景（低地）
        drawRect(
            color = Color(0xFFF5E6D3)
        )

        // 绿色区域（植被）
        drawCircle(
            color = Color(0xFFB8D9B8),
            radius = w * 0.18f,
            center = Offset(w * 0.3f, h * 0.7f)
        )

        // 深绿色区域（森林）
        drawCircle(
            color = Color(0xFF8AB98A),
            radius = w * 0.12f,
            center = Offset(w * 0.75f, h * 0.3f)
        )

        // 褐色等高线（山地）
        val terrainColor = Color(0xFFC4A57B)
        for (i in 1..4) {
            drawCircle(
                color = terrainColor.copy(alpha = 0.3f + i * 0.1f),
                radius = w * (0.4f - i * 0.08f),
                center = Offset(w * 0.6f, h * 0.6f),
                style = Stroke(width = 2f)
            )
        }

        // 蓝色河流
        val path = Path().apply {
            moveTo(0f, h * 0.2f)
            quadraticBezierTo(
                w * 0.3f, h * 0.25f,
                w * 0.5f, h * 0.4f
            )
            quadraticBezierTo(
                w * 0.7f, h * 0.5f,
                w, h * 0.55f
            )
        }
        drawPath(
            path = path,
            color = Color(0xFF87CEEB),
            style = Stroke(width = 6f)
        )

        // 白色道路
        drawLine(
            color = Color.White,
            start = Offset(w * 0.1f, h * 0.1f),
            end = Offset(w * 0.9f, h * 0.9f),
            strokeWidth = 3f
        )
    }
}