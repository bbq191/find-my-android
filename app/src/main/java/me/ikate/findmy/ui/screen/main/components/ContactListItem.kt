package me.ikate.findmy.ui.screen.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareStatus
import me.ikate.findmy.ui.components.ClickableAvatar
import me.ikate.findmy.ui.components.StaticAvatar
import me.ikate.findmy.util.DistanceCalculator
import me.ikate.findmy.util.ReverseGeocodeHelper
import me.ikate.findmy.util.TimeFormatter
import kotlinx.coroutines.delay


/**
 * 联系人列表项组件
 *
 * 交互方式（iOS Find My 风格）：
 * - 点击卡片：定位到地图
 * - 点击头像：刷新位置 + 跳转地图
 * - 点击右侧箭头：打开详情面板
 *
 * 头像边框颜色：
 * - 绿色(#4CAF50)：在线
 * - 灰色：离线
 */
@Composable
fun ContactListItem(
    contact: Contact,
    myDevice: Device? = null,
    isExpanded: Boolean,
    isPinned: Boolean = false,
    onClick: () -> Unit,  // 点击卡片：定位到地图
    onDetailClick: () -> Unit = {},  // 点击信息区域打开详情面板
    onAvatarClick: () -> Unit = {},  // 点击头像：刷新位置 + 跳转地图
    onExpandClick: () -> Unit,  // 点击展开按钮：展开/收起操作栏
    onNavigate: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onBindClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onAcceptClick: () -> Unit,
    onRejectClick: () -> Unit,
    onFindDeviceClick: () -> Unit = {},  // 查找设备
    onPlaySound: () -> Unit = {},
    onStopSound: () -> Unit = {},
    isRinging: Boolean = false,
    onLostModeClick: () -> Unit = {},
    onGeofenceClick: () -> Unit = {},
    hasGeofence: Boolean = false,
    onPinClick: () -> Unit = {},  // 置顶/取消置顶
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var addressText by remember { mutableStateOf<String?>(null) }
    var isAddressLoading by remember { mutableStateOf(false) }

    // 获取地址（使用缓存 + 超时处理）
    LaunchedEffect(contact.location) {
        if (contact.location == null) {
            addressText = null
            isAddressLoading = false
            return@LaunchedEffect
        }

        isAddressLoading = true
        addressText = null

        ReverseGeocodeHelper.getAddressFromLocation(
            context = context,
            latitude = contact.location.latitude,
            longitude = contact.location.longitude
        ) { result ->
            addressText = result
            isAddressLoading = false
        }
    }

    // 10 秒超时处理
    LaunchedEffect(contact.location, isAddressLoading) {
        if (isAddressLoading && addressText == null) {
            delay(10_000L)
            if (isAddressLoading && addressText == null) {
                addressText = "获取地址超时"
                isAddressLoading = false
            }
        }
    }

    // 卡片
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 判断是否可追踪
                val canTrack = contact.shareStatus == ShareStatus.ACCEPTED &&
                        !contact.isPaused &&
                        (contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME ||
                                contact.shareDirection == ShareDirection.MUTUAL)

                // 判断在线状态
                val isOnline = TimeFormatter.isOnline(contact.lastUpdateTime ?: 0L)

                // MONET: 点击头像追踪，点击信息区域打开详情面板
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 头像：点击跳转地图并刷新位置
                    ContactAvatar(
                        contact = contact,
                        isOnline = isOnline,
                        canTrack = canTrack,
                        onAvatarClick = onAvatarClick
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // 联系人信息：点击打开详情面板
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onDetailClick)
                    ) {
                        ContactInfo(
                            contact = contact,
                            myDevice = myDevice,
                            addressText = addressText,
                            isAddressLoading = isAddressLoading,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 接受/拒绝按钮（仅待处理的邀请显示）
                if (contact.shareStatus == ShareStatus.PENDING &&
                    contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME) {
                    AcceptRejectButtons(
                        onAcceptClick = onAcceptClick,
                        onRejectClick = onRejectClick
                    )
                } else {
                    // 右箭头提示（点击打开详情面板）
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onDetailClick)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "查看详情",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 联系人头像组件
 * iOS Find My 风格：单击即刷新位置
 *
 * 交互方式：
 * - canTrack = true: 点击刷新位置
 * - canTrack = false: 静态显示，不可交互
 */
@Composable
private fun ContactAvatar(
    contact: Contact,
    isOnline: Boolean = false,
    canTrack: Boolean = false,
    onAvatarClick: () -> Unit = {}
) {
    if (canTrack) {
        // 可追踪：使用可点击头像组件
        ClickableAvatar(
            avatarUrl = contact.avatarUrl,
            fallbackText = contact.name,
            isOnline = isOnline,
            isEnabled = true,
            onClick = onAvatarClick,
            size = 56.dp
        )
    } else {
        // 不可追踪：使用静态头像组件
        StaticAvatar(
            avatarUrl = contact.avatarUrl,
            fallbackText = contact.name,
            isOnline = isOnline,
            size = 56.dp
        )
    }
}

@Composable
private fun ContactInfo(
    contact: Contact,
    myDevice: Device?,
    addressText: String?,
    isAddressLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 联系人姓名
        Text(
            text = contact.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )

        // 状态逻辑 - 只显示设备状态，不显示追踪文字
        if (contact.shareStatus == ShareStatus.ACCEPTED && !contact.isPaused && contact.isLocationAvailable) {
            DeviceStatusRow(contact = contact)

            // 距离 + 地址
            DistanceAndAddressRow(
                contact = contact,
                myDevice = myDevice,
                addressText = addressText,
                isLoading = isAddressLoading
            )
        } else {
            StatusText(contact = contact)
        }
    }
}

/**
 * 设备状态行 - 使用 Material Chips 展示电量和时间
 * 电量 ≤20% 时背景变红，其他时候使用 Surface 色
 */
@Composable
private fun DeviceStatusRow(contact: Contact) {
    val isOnline = TimeFormatter.isOnline(contact.lastUpdateTime ?: 0L)
    val timeText = TimeFormatter.formatUpdateTime(contact.lastUpdateTime ?: 0L)
    val deviceName = contact.deviceName ?: "未知设备"
    val battery = contact.battery ?: 100
    val locationFreshness = TimeFormatter.getLocationFreshness(contact.lastUpdateTime ?: 0L)

    // 电量状态颜色
    val isLowBattery = battery <= 20
    val batteryIcon = when {
        isLowBattery -> Icons.Default.BatteryAlert
        battery >= 90 -> Icons.Default.BatteryFull
        else -> Icons.Default.BatteryChargingFull
    }
    val batteryChipColor = if (isLowBattery) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val batteryContentColor = if (isLowBattery) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 电量胶囊 (Chip)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = batteryChipColor,
            modifier = Modifier.height(24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = batteryIcon,
                    contentDescription = "电量",
                    modifier = Modifier.size(14.dp),
                    tint = batteryContentColor
                )
                Text(
                    text = "$battery%",
                    style = MaterialTheme.typography.labelSmall,
                    color = batteryContentColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 时间胶囊 (Chip) - 位置过期时显示警告色
        val isLocationStale = locationFreshness == TimeFormatter.LocationFreshness.STALE ||
                locationFreshness == TimeFormatter.LocationFreshness.VERY_STALE
        val timeChipColor = when (locationFreshness) {
            TimeFormatter.LocationFreshness.VERY_STALE -> MaterialTheme.colorScheme.errorContainer
            TimeFormatter.LocationFreshness.STALE -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
        val timeContentColor = when (locationFreshness) {
            TimeFormatter.LocationFreshness.VERY_STALE -> MaterialTheme.colorScheme.onErrorContainer
            TimeFormatter.LocationFreshness.STALE -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = timeChipColor,
            modifier = Modifier.height(24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "更新时间",
                    modifier = Modifier.size(14.dp),
                    tint = timeContentColor
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = timeContentColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 设备名称（简化显示）
        Text(
            text = deviceName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )

        // 在线状态小圆点
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (isOnline) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outlineVariant
                )
        )
    }
}

@Composable
private fun DistanceAndAddressRow(
    contact: Contact,
    myDevice: Device?,
    addressText: String?,
    isLoading: Boolean = false
) {
    val myLocation = myDevice?.location
    val contactLocation = contact.location
    val distanceText = if (myLocation != null && contactLocation != null) {
        DistanceCalculator.calculateAndFormatDistance(myLocation, contactLocation)
    } else null

    // 判断是否超时
    val isTimeout = addressText == "获取地址超时"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 加载指示器或位置图标
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isTimeout) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (distanceText != null) {
            Text(
                text = distanceText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "•",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = when {
                isLoading -> "获取中..."
                addressText != null -> addressText
                else -> "正在获取位置..."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (isTimeout) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun StatusText(contact: Contact) {
    val (statusText, statusColor) = when (contact.shareStatus) {
        ShareStatus.PENDING -> {
            val text = if (contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME) {
                "邀请您查看位置"
            } else {
                "等待对方接受..."
            }
            text to MaterialTheme.colorScheme.primary
        }
        ShareStatus.ACCEPTED -> {
            if (contact.isPaused) {
                "已暂停共享" to MaterialTheme.colorScheme.error
            } else {
                "位置不可用" to MaterialTheme.colorScheme.secondary
            }
        }
        ShareStatus.EXPIRED -> {
            // 根据共享方向给出不同提示
            val text = if (contact.shareDirection == ShareDirection.I_SHARE_TO_THEM ||
                contact.shareDirection == ShareDirection.MUTUAL) {
                "已过期，点击续期"
            } else {
                "已过期"
            }
            text to MaterialTheme.colorScheme.error
        }
        ShareStatus.REJECTED -> "已拒绝" to MaterialTheme.colorScheme.error
        ShareStatus.REMOVED -> "已被移出" to MaterialTheme.colorScheme.error
    }

    Text(
        text = statusText,
        style = MaterialTheme.typography.bodySmall,
        color = statusColor
    )
}

@Composable
private fun AcceptRejectButtons(
    onAcceptClick: () -> Unit,
    onRejectClick: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = onRejectClick,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("拒绝", style = MaterialTheme.typography.labelLarge)
        }

        FilledTonalButton(
            onClick = onAcceptClick,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("接受", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

