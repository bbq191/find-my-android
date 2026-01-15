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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Watch
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
                // 设备图标
                DeviceIcon(deviceType = device.deviceType)

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
                            onClick = if (isRinging) onStopSound else onPlaySound
                        )

                        // 丢失模式按钮（仅限自己的设备）
                        if (!isSharedDevice) {
                            ActionButton(
                                icon = Icons.Default.Lock,
                                label = "丢失模式",
                                onClick = onLostMode
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceIcon(deviceType: DeviceType) {
    val icon = when (deviceType) {
        DeviceType.PHONE -> Icons.Default.PhoneAndroid
        DeviceType.TABLET -> Icons.Default.Tablet
        DeviceType.WATCH -> Icons.Default.Watch
        else -> Icons.Default.PhoneAndroid
    }

    Surface(
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
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

@Composable
private fun BatteryIndicator(battery: Int) {
    val icon = getBatteryIcon(battery)
    val color = when {
        battery <= 20 -> MaterialTheme.colorScheme.error
        battery <= 50 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color
        )
        Text(
            text = "$battery%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
