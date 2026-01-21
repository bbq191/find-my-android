package me.ikate.findmy.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ikate.findmy.ui.theme.FindMyBlue
import me.ikate.findmy.ui.theme.FindMyGreen
import me.ikate.findmy.ui.theme.FindMyOrange
import me.ikate.findmy.ui.theme.FindMyRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 定位精度等级
 */
enum class AccuracyLevel {
    /** 精确定位 (< 10m) */
    HIGH,

    /** 中等精度 (10-50m) */
    MEDIUM,

    /** 低精度 (50-100m) */
    LOW,

    /** 粗略位置 (> 100m) */
    APPROXIMATE,

    /** 未知 */
    UNKNOWN
}

/**
 * 位置新鲜度等级
 */
enum class FreshnessLevel {
    /** 实时 (< 1分钟) */
    REALTIME,

    /** 刚刚 (1-5分钟) */
    RECENT,

    /** 稍早 (5-30分钟) */
    STALE,

    /** 较旧 (30分钟-1小时) */
    OLD,

    /** 过期 (> 1小时) */
    EXPIRED
}

/**
 * 定位精度可视化组件
 *
 * 显示：
 * - 精度等级（条形/环形指示）
 * - 精度半径（米）
 * - 位置新鲜度（最后更新时间）
 * - 定位源类型（GPS/网络/WiFi）
 */
@Composable
fun LocationAccuracyIndicator(
    accuracyMeters: Float? = null,
    lastUpdateTime: Long? = null,
    modifier: Modifier = Modifier,
    style: AccuracyIndicatorStyle = AccuracyIndicatorStyle.COMPACT
) {
    val accuracyLevel = getAccuracyLevel(accuracyMeters)
    val freshnessLevel = getFreshnessLevel(lastUpdateTime)

    when (style) {
        AccuracyIndicatorStyle.COMPACT -> {
            CompactAccuracyIndicator(
                accuracyLevel = accuracyLevel,
                freshnessLevel = freshnessLevel,
                accuracyMeters = accuracyMeters,
                lastUpdateTime = lastUpdateTime,
                modifier = modifier
            )
        }
        AccuracyIndicatorStyle.DETAILED -> {
            DetailedAccuracyIndicator(
                accuracyLevel = accuracyLevel,
                freshnessLevel = freshnessLevel,
                accuracyMeters = accuracyMeters,
                lastUpdateTime = lastUpdateTime,
                modifier = modifier
            )
        }
        AccuracyIndicatorStyle.RING -> {
            RingAccuracyIndicator(
                accuracyLevel = accuracyLevel,
                freshnessLevel = freshnessLevel,
                modifier = modifier
            )
        }
    }
}

/**
 * 紧凑型精度指示器
 * 用于列表项或地图标记旁
 */
@Composable
private fun CompactAccuracyIndicator(
    accuracyLevel: AccuracyLevel,
    freshnessLevel: FreshnessLevel,
    accuracyMeters: Float?,
    lastUpdateTime: Long?,
    modifier: Modifier = Modifier
) {
    val color by animateColorAsState(
        targetValue = getAccuracyColor(accuracyLevel, freshnessLevel),
        animationSpec = tween(300),
        label = "accuracyColor"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // GPS 图标
        Icon(
            imageVector = getAccuracyIcon(accuracyLevel),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        // 精度/时间文本
        val displayText = when {
            accuracyMeters != null -> formatAccuracy(accuracyMeters)
            lastUpdateTime != null -> formatRelativeTime(lastUpdateTime)
            else -> "未知"
        }

        Text(
            text = displayText,
            fontSize = 12.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 详细精度指示器
 * 用于详情页或设置页
 */
@Composable
private fun DetailedAccuracyIndicator(
    accuracyLevel: AccuracyLevel,
    freshnessLevel: FreshnessLevel,
    accuracyMeters: Float?,
    lastUpdateTime: Long?,
    modifier: Modifier = Modifier
) {
    val color = getAccuracyColor(accuracyLevel, freshnessLevel)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getAccuracyIcon(accuracyLevel),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "定位精度",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 精度条
            AccuracyProgressBar(
                accuracyLevel = accuracyLevel,
                color = color
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 详细信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 精度半径
                if (accuracyMeters != null) {
                    InfoChip(
                        icon = Icons.Default.NearMe,
                        label = "精度",
                        value = formatAccuracy(accuracyMeters),
                        color = color
                    )
                }

                // 更新时间
                if (lastUpdateTime != null) {
                    InfoChip(
                        icon = Icons.Default.Schedule,
                        label = "更新",
                        value = formatRelativeTime(lastUpdateTime),
                        color = getfreshnessColor(freshnessLevel)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 精度说明
            Text(
                text = getAccuracyDescription(accuracyLevel),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun AccuracyProgressBar(
    accuracyLevel: AccuracyLevel,
    color: Color
) {
    val progress by animateFloatAsState(
        targetValue = when (accuracyLevel) {
            AccuracyLevel.HIGH -> 1f
            AccuracyLevel.MEDIUM -> 0.75f
            AccuracyLevel.LOW -> 0.5f
            AccuracyLevel.APPROXIMATE -> 0.25f
            AccuracyLevel.UNKNOWN -> 0f
        },
        animationSpec = tween(500),
        label = "progress"
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = getAccuracyLevelName(accuracyLevel),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
    }
}

@Composable
private fun InfoChip(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = "$label: ",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

/**
 * 环形精度指示器
 * 用于地图上的位置标记
 */
@Composable
private fun RingAccuracyIndicator(
    accuracyLevel: AccuracyLevel,
    freshnessLevel: FreshnessLevel,
    modifier: Modifier = Modifier
) {
    val color = getAccuracyColor(accuracyLevel, freshnessLevel)
    val sweepAngle by animateFloatAsState(
        targetValue = when (accuracyLevel) {
            AccuracyLevel.HIGH -> 360f
            AccuracyLevel.MEDIUM -> 270f
            AccuracyLevel.LOW -> 180f
            AccuracyLevel.APPROXIMATE -> 90f
            AccuracyLevel.UNKNOWN -> 0f
        },
        animationSpec = tween(500),
        label = "sweepAngle"
    )

    Box(
        modifier = modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(28.dp)) {
            // 背景圆环
            drawCircle(
                color = color.copy(alpha = 0.2f),
                radius = size.minDimension / 2,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // 精度弧线
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 中心图标
        Icon(
            imageVector = getAccuracyIcon(accuracyLevel),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * 简易精度徽章
 * 用于紧凑空间
 */
@Composable
fun AccuracyBadge(
    accuracyMeters: Float? = null,
    lastUpdateTime: Long? = null,
    modifier: Modifier = Modifier
) {
    val accuracyLevel = getAccuracyLevel(accuracyMeters)
    val freshnessLevel = getFreshnessLevel(lastUpdateTime)
    val color = getAccuracyColor(accuracyLevel, freshnessLevel)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = when {
                    accuracyMeters != null -> formatAccuracy(accuracyMeters)
                    lastUpdateTime != null -> formatRelativeTime(lastUpdateTime)
                    else -> "--"
                },
                fontSize = 11.sp,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

enum class AccuracyIndicatorStyle {
    COMPACT,
    DETAILED,
    RING
}

// 辅助函数

private fun getAccuracyLevel(meters: Float?): AccuracyLevel {
    return when {
        meters == null -> AccuracyLevel.UNKNOWN
        meters < 10 -> AccuracyLevel.HIGH
        meters < 50 -> AccuracyLevel.MEDIUM
        meters < 100 -> AccuracyLevel.LOW
        else -> AccuracyLevel.APPROXIMATE
    }
}

private fun getFreshnessLevel(timestamp: Long?): FreshnessLevel {
    if (timestamp == null) return FreshnessLevel.EXPIRED

    val ageMs = System.currentTimeMillis() - timestamp
    val ageMinutes = TimeUnit.MILLISECONDS.toMinutes(ageMs)

    return when {
        ageMinutes < 1 -> FreshnessLevel.REALTIME
        ageMinutes < 5 -> FreshnessLevel.RECENT
        ageMinutes < 30 -> FreshnessLevel.STALE
        ageMinutes < 60 -> FreshnessLevel.OLD
        else -> FreshnessLevel.EXPIRED
    }
}

private fun getAccuracyColor(accuracy: AccuracyLevel, freshness: FreshnessLevel): Color {
    // 过期位置显示为灰色
    if (freshness == FreshnessLevel.EXPIRED) {
        return Color.Gray
    }

    return when (accuracy) {
        AccuracyLevel.HIGH -> FindMyGreen
        AccuracyLevel.MEDIUM -> FindMyBlue
        AccuracyLevel.LOW -> FindMyOrange
        AccuracyLevel.APPROXIMATE -> FindMyRed
        AccuracyLevel.UNKNOWN -> Color.Gray
    }
}

private fun getfreshnessColor(freshness: FreshnessLevel): Color {
    return when (freshness) {
        FreshnessLevel.REALTIME -> FindMyGreen
        FreshnessLevel.RECENT -> FindMyBlue
        FreshnessLevel.STALE -> FindMyOrange
        FreshnessLevel.OLD -> FindMyRed
        FreshnessLevel.EXPIRED -> Color.Gray
    }
}

private fun getAccuracyIcon(accuracy: AccuracyLevel): ImageVector {
    return when (accuracy) {
        AccuracyLevel.HIGH -> Icons.Default.GpsFixed
        AccuracyLevel.MEDIUM -> Icons.Default.GpsFixed
        AccuracyLevel.LOW -> Icons.Default.GpsNotFixed
        AccuracyLevel.APPROXIMATE -> Icons.Default.GpsOff
        AccuracyLevel.UNKNOWN -> Icons.Default.GpsOff
    }
}

private fun getAccuracyLevelName(accuracy: AccuracyLevel): String {
    return when (accuracy) {
        AccuracyLevel.HIGH -> "精确定位"
        AccuracyLevel.MEDIUM -> "中等精度"
        AccuracyLevel.LOW -> "低精度"
        AccuracyLevel.APPROXIMATE -> "粗略位置"
        AccuracyLevel.UNKNOWN -> "未知"
    }
}

private fun getAccuracyDescription(accuracy: AccuracyLevel): String {
    return when (accuracy) {
        AccuracyLevel.HIGH -> "使用 GPS 精确定位，误差 < 10米"
        AccuracyLevel.MEDIUM -> "使用 GPS/WiFi 混合定位，误差 10-50米"
        AccuracyLevel.LOW -> "使用网络定位，误差 50-100米"
        AccuracyLevel.APPROXIMATE -> "仅有粗略位置信息，误差 > 100米"
        AccuracyLevel.UNKNOWN -> "无法确定定位精度"
    }
}

private fun formatAccuracy(meters: Float): String {
    return when {
        meters < 10 -> "< 10m"
        meters < 1000 -> "${meters.toInt()}m"
        else -> "${String.format("%.1f", meters / 1000)}km"
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val ageMs = System.currentTimeMillis() - timestamp
    val ageSeconds = TimeUnit.MILLISECONDS.toSeconds(ageMs)
    val ageMinutes = TimeUnit.MILLISECONDS.toMinutes(ageMs)
    val ageHours = TimeUnit.MILLISECONDS.toHours(ageMs)
    val ageDays = TimeUnit.MILLISECONDS.toDays(ageMs)

    return when {
        ageSeconds < 60 -> "刚刚"
        ageMinutes < 60 -> "${ageMinutes}分钟前"
        ageHours < 24 -> "${ageHours}小时前"
        ageDays < 7 -> "${ageDays}天前"
        else -> {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
