package me.ikate.findmy.ui.screen.main.tabs

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ikate.findmy.ui.theme.FindMyShapes

/**
 * 历史轨迹 Tab
 * 显示"敬请期待"及功能点描述
 */
@Composable
fun HistoryTab(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // iOS 风格大标题
        HistoryHeader()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 敬请期待卡片
            item {
                ComingSoonCard()
            }

            // 功能点描述
            item {
                FeaturesSection()
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
private fun HistoryHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 12.dp)
        ) {
            Text(
                text = "历史轨迹",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 敬请期待卡片 - 带动画效果
 * MONET 设计：使用 Compose 动画替代 Lottie
 */
@Composable
private fun ComingSoonCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = FindMyShapes.Medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 雷达扫描动画
            RadarScanAnimation(
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "时光机正在组装中...",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "历史轨迹回溯功能正在开发中，该功能可帮助您查看联系人的历史移动轨迹，类似 iOS 查找应用的时间轴功能。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 骨架屏预览（模拟时间轴）
            SkeletonTimelinePreview()
        }
    }
}

/**
 * 雷达扫描动画
 * 使用 Compose Canvas 绘制动态雷达效果
 */
@Composable
private fun RadarScanAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")

    // 扫描角度动画
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    // 脉冲动画
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 * 0.9f

            // 背景圆环
            for (i in 1..3) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.1f),
                    radius = radius * i / 3,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // 脉冲圆
            drawCircle(
                color = primaryColor.copy(alpha = 0.2f * (1f - pulseScale)),
                radius = radius * pulseScale,
                center = center
            )

            // 扫描扇形
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        primaryColor.copy(alpha = 0.3f),
                        primaryColor.copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    center = center
                ),
                startAngle = sweepAngle - 60f,
                sweepAngle = 60f,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )

            // 中心点
            drawCircle(
                color = primaryColor,
                radius = 8.dp.toPx(),
                center = center
            )
        }

        // 路径图标
        Icon(
            imageVector = Icons.Default.Route,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 骨架屏时间轴预览
 * 模拟未来数据加载时的闪烁效果
 */
@Composable
private fun SkeletonTimelinePreview() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")

    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.surfaceContainerHigh
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "预览：垂直时间轴",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        // 模拟 3 条时间轴条目
        repeat(3) { index ->
            SkeletonTimelineItem(
                shimmerProgress = shimmerProgress,
                shimmerColors = shimmerColors,
                delay = index * 0.15f
            )
        }
    }
}

/**
 * 单个骨架屏时间轴条目
 */
@Composable
private fun SkeletonTimelineItem(
    shimmerProgress: Float,
    shimmerColors: List<Color>,
    delay: Float
) {
    val adjustedProgress = ((shimmerProgress + delay) % 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 时间轴竖线 + 圆点
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 圆点
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = shimmerColors,
                            start = Offset(adjustedProgress * 200f, 0f),
                            end = Offset(adjustedProgress * 200f + 100f, 100f)
                        )
                    )
            )
            // 竖线
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(24.dp)
                    .background(shimmerColors[0])
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 内容骨架
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 标题骨架
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = shimmerColors,
                            start = Offset(adjustedProgress * 300f, 0f),
                            end = Offset(adjustedProgress * 300f + 150f, 0f)
                        )
                    )
            )
            // 描述骨架
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = shimmerColors,
                            start = Offset(adjustedProgress * 300f + 50f, 0f),
                            end = Offset(adjustedProgress * 300f + 200f, 0f)
                        )
                    )
            )
        }

        // 时间骨架
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = shimmerColors,
                        start = Offset(adjustedProgress * 100f, 0f),
                        end = Offset(adjustedProgress * 100f + 50f, 0f)
                    )
                )
        )
    }
}

@Composable
private fun FeaturesSection() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "即将推出的功能",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 功能列表
            FeatureItem(
                icon = Icons.Default.Schedule,
                title = "时间轴回溯",
                description = "查看联系人过去24小时或更长时间的移动轨迹，支持日期范围选择"
            )

            FeatureItem(
                icon = Icons.Default.FilterAlt,
                title = "轨迹平滑与降噪",
                description = "采用卡尔曼滤波算法去除 GPS 漂移，使用 Douglas-Peucker 算法抽稀轨迹点"
            )

            FeatureItem(
                icon = Icons.Default.PlayCircle,
                title = "轨迹回放动画",
                description = "在地图上以动画形式回放历史轨迹，支持速度调节和进度控制"
            )

            FeatureItem(
                icon = Icons.Default.BatteryChargingFull,
                title = "智能采集策略",
                description = "根据运动状态自动调整采集频率：静止时低频采集，移动时高频采集，平衡精度与电量"
            )

            FeatureItem(
                icon = Icons.Default.Storage,
                title = "高效数据存储",
                description = "本地 Room 数据库缓冲 + 云端分段聚合存储，降低 Firestore 写入成本"
            )
        }
    }
}

@Composable
private fun FeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
