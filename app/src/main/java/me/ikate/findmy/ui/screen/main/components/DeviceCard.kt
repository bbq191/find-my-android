package me.ikate.findmy.ui.screen.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.LinearProgressIndicator
import me.ikate.findmy.ui.theme.FindMyShapes
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Tablet
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.DeviceType
import me.ikate.findmy.ui.components.ActionButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设备卡片组件
 * 可展开的设备卡片，显示设备信息和操作按钮
 */
@Composable
fun DeviceCard(
    device: Device,
    isCurrentDevice: Boolean,
    isSharedDevice: Boolean,
    isExpanded: Boolean,
    address: String?,
    onClick: () -> Unit,
    onNavigate: () -> Unit,
    onPlaySound: () -> Unit,
    onStopSound: () -> Unit,
    onLostMode: () -> Unit,
    isRinging: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 设备信息行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 设备图标（根据型号精细化显示）
                DeviceIcon(
                    deviceType = device.deviceType,
                    deviceModel = device.name  // name 字段存储设备型号
                )

                Spacer(modifier = Modifier.width(16.dp))

                // 设备详情
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = device.customName ?: device.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // 标签
                        when {
                            isCurrentDevice -> DeviceLabel(text = "此设备", isPrimary = true)
                            isSharedDevice -> DeviceLabel(text = "共享", isPrimary = false)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 位置信息
                    val displayAddress = if (isCurrentDevice) address else null
                    if (displayAddress != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = displayAddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // 电量和更新时间
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 电量
                        BatteryIndicator(battery = device.battery)

                        // 更新时间
                        Text(
                            text = formatRelativeTime(device.lastUpdateTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 在线状态
                        if (!device.isOnline) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "离线",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 展开的操作栏
            AnimatedVisibility(
                visible = isExpanded && !isCurrentDevice,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 主要操作按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // 导航按钮
                        ActionButton(
                            icon = Icons.Default.Directions,
                            label = "导航",
                            onClick = onNavigate
                        )

                        // 响铃按钮
                        ActionButton(
                            icon = if (isRinging) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            label = if (isRinging) "停止" else "响铃",
                            isDestructive = isRinging,
                            useConfirmHaptic = true,  // 远程控制指令需要明确的确认震动
                            onClick = if (isRinging) onStopSound else onPlaySound
                        )
                    }

                    // 丢失模式独立卡片（仅限自己的设备）
                    if (!isSharedDevice) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LostModeCard(onClick = onLostMode)
                    }
                }
            }
        }
    }
}

/**
 * 设备图标组件 - 根据设备型号精细化显示
 * 支持识别 Samsung、iPhone、Pixel 等不同品牌
 */
@Composable
private fun DeviceIcon(
    deviceType: DeviceType,
    deviceModel: String? = null
) {
    // 根据设备型号识别品牌
    val brand = deviceModel?.let { model ->
        when {
            model.contains("Samsung", ignoreCase = true) ||
            model.contains("Galaxy", ignoreCase = true) ||
            model.contains("SM-", ignoreCase = true) -> DeviceBrand.SAMSUNG
            model.contains("iPhone", ignoreCase = true) ||
            model.contains("iPad", ignoreCase = true) -> DeviceBrand.APPLE
            model.contains("Pixel", ignoreCase = true) -> DeviceBrand.GOOGLE
            model.contains("Xiaomi", ignoreCase = true) ||
            model.contains("Redmi", ignoreCase = true) ||
            model.contains("Mi ", ignoreCase = true) -> DeviceBrand.XIAOMI
            model.contains("HUAWEI", ignoreCase = true) ||
            model.contains("Honor", ignoreCase = true) -> DeviceBrand.HUAWEI
            model.contains("OnePlus", ignoreCase = true) -> DeviceBrand.ONEPLUS
            model.contains("OPPO", ignoreCase = true) -> DeviceBrand.OPPO
            model.contains("vivo", ignoreCase = true) -> DeviceBrand.VIVO
            else -> DeviceBrand.OTHER
        }
    } ?: DeviceBrand.OTHER

    // 使用 Outlined 风格图标（线框风格，更接近 Apple 设计）
    val icon = when (deviceType) {
        DeviceType.PHONE -> Icons.Outlined.PhoneAndroid
        DeviceType.TABLET -> Icons.Outlined.Tablet
        DeviceType.WATCH -> Icons.Outlined.Watch
        DeviceType.AIRTAG -> Icons.Default.LocationOn
        DeviceType.OTHER -> Icons.Outlined.Laptop
    }

    // 根据品牌和设备类型设置不同的主题色
    val (iconTint, containerColor) = when (brand) {
        DeviceBrand.SAMSUNG -> MaterialTheme.colorScheme.primary to
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        DeviceBrand.APPLE -> MaterialTheme.colorScheme.onSurface to
                MaterialTheme.colorScheme.surfaceContainerHighest
        DeviceBrand.GOOGLE -> MaterialTheme.colorScheme.tertiary to
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        DeviceBrand.XIAOMI -> androidx.compose.ui.graphics.Color(0xFFFF6900) to
                androidx.compose.ui.graphics.Color(0xFFFF6900).copy(alpha = 0.1f)
        DeviceBrand.HUAWEI -> androidx.compose.ui.graphics.Color(0xFFCF0A2C) to
                androidx.compose.ui.graphics.Color(0xFFCF0A2C).copy(alpha = 0.1f)
        else -> when (deviceType) {
            DeviceType.PHONE -> MaterialTheme.colorScheme.primary to
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            DeviceType.TABLET -> MaterialTheme.colorScheme.tertiary to
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            DeviceType.WATCH -> MaterialTheme.colorScheme.secondary to
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            DeviceType.AIRTAG -> MaterialTheme.colorScheme.primary to
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            DeviceType.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant to
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        }
    }

    Surface(
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = deviceType.name,
                modifier = Modifier.size(28.dp),
                tint = iconTint
            )
        }
    }
}

/**
 * 设备品牌枚举
 */
private enum class DeviceBrand {
    SAMSUNG, APPLE, GOOGLE, XIAOMI, HUAWEI, ONEPLUS, OPPO, VIVO, OTHER
}

/**
 * 丢失模式独立卡片
 * 使用红色警告色，突出显示这是一个重要的安全功能
 */
@Composable
private fun LostModeCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FindMyShapes.Medium)
            .clickable(onClick = onClick),
        shape = FindMyShapes.Medium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 警告图标
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 文字说明
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "标记为丢失",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "启用后将显示您的联系方式",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 右箭头
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun DeviceLabel(text: String, isPrimary: Boolean) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isPrimary) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isPrimary) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.tertiary
            },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * 电量指示器 - 使用进度条可视化
 * 电量 ≤20% 时显示红色，≤50% 显示黄色，其他显示绿色
 */
@Composable
private fun BatteryIndicator(battery: Int, showProgressBar: Boolean = true) {
    val color = when {
        battery <= 20 -> MaterialTheme.colorScheme.error
        battery <= 50 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    val trackColor = when {
        battery <= 20 -> MaterialTheme.colorScheme.errorContainer
        battery <= 50 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 电量进度条
        if (showProgressBar) {
            LinearProgressIndicator(
                progress = { battery / 100f },
                modifier = Modifier
                    .width(60.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = trackColor,
            )
        }

        // 电量百分比
        Text(
            text = "$battery%",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )

        // 低电量警告图标
        if (battery <= 20) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "低电量",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun getBatteryIcon(battery: Int): ImageVector {
    return when {
        battery >= 95 -> Icons.Default.BatteryFull
        battery >= 80 -> Icons.Default.Battery6Bar
        battery >= 65 -> Icons.Default.Battery5Bar
        battery >= 50 -> Icons.Default.Battery4Bar
        battery >= 35 -> Icons.Default.Battery3Bar
        battery >= 20 -> Icons.Default.Battery2Bar
        battery >= 10 -> Icons.Default.Battery1Bar
        else -> Icons.Default.Battery0Bar
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 86400_000 -> "${diff / 3600_000} 小时前"
        else -> {
            val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
