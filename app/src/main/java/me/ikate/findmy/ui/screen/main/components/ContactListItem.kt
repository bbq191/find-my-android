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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareStatus
import me.ikate.findmy.ui.components.ActionButton
import me.ikate.findmy.util.DistanceCalculator
import me.ikate.findmy.util.ReverseGeocodeHelper
import me.ikate.findmy.util.TimeFormatter

/**
 * 联系人列表项组件
 * 支持点击展开操作栏
 */
@Composable
fun ContactListItem(
    contact: Contact,
    myDevice: Device? = null,
    isExpanded: Boolean,
    isRequestingLocation: Boolean = false,
    isTracking: Boolean = false,
    onClick: () -> Unit,
    onNavigate: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onBindClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onAcceptClick: () -> Unit,
    onRejectClick: () -> Unit,
    onRequestLocationUpdate: () -> Unit = {},
    onStartContinuousTracking: () -> Unit = {},
    onStopContinuousTracking: () -> Unit = {},
    onPlaySound: () -> Unit = {},
    onStopSound: () -> Unit = {},
    isRinging: Boolean = false,
    onLostModeClick: () -> Unit = {},
    onGeofenceClick: () -> Unit = {},
    hasGeofence: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var addressText by remember { mutableStateOf<String?>(null) }

    // 获取地址（使用缓存）
    LaunchedEffect(contact.location) {
        if (contact.location == null) {
            addressText = null
            return@LaunchedEffect
        }

        ReverseGeocodeHelper.getAddressFromLocation(
            context = context,
            latitude = contact.location.latitude,
            longitude = contact.location.longitude
        ) { result ->
            addressText = result
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isExpanded) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像
                ContactAvatar(contact = contact)

                Spacer(modifier = Modifier.width(16.dp))

                // 联系人信息
                ContactInfo(
                    contact = contact,
                    myDevice = myDevice,
                    isTracking = isTracking,
                    isRequestingLocation = isRequestingLocation,
                    addressText = addressText,
                    modifier = Modifier.weight(1f)
                )

                // 接受/拒绝按钮
                if (contact.shareStatus == ShareStatus.PENDING &&
                    contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME) {
                    AcceptRejectButtons(
                        onAcceptClick = onAcceptClick,
                        onRejectClick = onRejectClick
                    )
                }
            }

            // 展开的操作栏
            ExpandedActionBar(
                visible = isExpanded,
                contact = contact,
                isRequestingLocation = isRequestingLocation,
                isTracking = isTracking,
                hasGeofence = hasGeofence,
                onRequestLocationUpdate = onRequestLocationUpdate,
                onStartContinuousTracking = onStartContinuousTracking,
                onStopContinuousTracking = onStopContinuousTracking,
                onPlaySound = onPlaySound,
                onStopSound = onStopSound,
                isRinging = isRinging,
                onLostModeClick = onLostModeClick,
                onGeofenceClick = onGeofenceClick,
                onNavigate = onNavigate,
                onPauseClick = onPauseClick,
                onResumeClick = onResumeClick,
                onBindClick = onBindClick,
                onRemoveClick = onRemoveClick,
                onRejectClick = onRejectClick
            )
        }
    }
}

@Composable
private fun ContactAvatar(contact: Contact) {
    Surface(
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!contact.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(contact.avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = contact.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = contact.name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ContactInfo(
    contact: Contact,
    myDevice: Device?,
    isTracking: Boolean,
    isRequestingLocation: Boolean,
    addressText: String?,
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

        // 状态逻辑
        if (contact.shareStatus == ShareStatus.ACCEPTED && !contact.isPaused && contact.isLocationAvailable) {
            if (isTracking) {
                TrackingStatusRow()
            } else if (isRequestingLocation) {
                RequestingStatusRow()
            } else {
                DeviceStatusRow(contact = contact)
            }

            // 距离 + 地址
            DistanceAndAddressRow(
                contact = contact,
                myDevice = myDevice,
                addressText = addressText
            )
        } else {
            StatusText(contact = contact)
        }
    }
}

@Composable
private fun TrackingStatusRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp,
            color = Color(0xFF4CAF50)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "实时追踪中...",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4CAF50),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RequestingStatusRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "正在定位...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DeviceStatusRow(contact: Contact) {
    val isOnline = TimeFormatter.isOnline(contact.lastUpdateTime ?: 0L)
    val onlineText = if (isOnline) "在线" else "离线"
    val onlineColor = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val timeText = TimeFormatter.formatUpdateTime(contact.lastUpdateTime ?: 0L)
    val deviceName = contact.deviceName ?: "未知设备"
    val battery = contact.battery ?: 100

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 电量指示
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Battery4Bar,
                contentDescription = "电量",
                modifier = Modifier.size(14.dp),
                tint = when {
                    battery <= 20 -> MaterialTheme.colorScheme.error
                    battery <= 50 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            Text(
                text = "$battery%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = deviceName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = onlineText,
            style = MaterialTheme.typography.bodySmall,
            color = onlineColor
        )

        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = timeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DistanceAndAddressRow(
    contact: Contact,
    myDevice: Device?,
    addressText: String?
) {
    val myLocation = myDevice?.location
    val contactLocation = contact.location
    val distanceText = if (myLocation != null && contactLocation != null) {
        DistanceCalculator.calculateAndFormatDistance(myLocation, contactLocation)
    } else null

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 位置图标
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
            text = addressText ?: "正在获取位置...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        ShareStatus.EXPIRED -> "已过期" to MaterialTheme.colorScheme.error
        ShareStatus.REJECTED -> "已拒绝" to MaterialTheme.colorScheme.error
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

@Composable
private fun ExpandedActionBar(
    visible: Boolean,
    contact: Contact,
    isRequestingLocation: Boolean,
    isTracking: Boolean,
    hasGeofence: Boolean,
    onRequestLocationUpdate: () -> Unit,
    onStartContinuousTracking: () -> Unit,
    onStopContinuousTracking: () -> Unit,
    onPlaySound: () -> Unit,
    onStopSound: () -> Unit,
    isRinging: Boolean,
    onLostModeClick: () -> Unit,
    onGeofenceClick: () -> Unit,
    onNavigate: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onBindClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onRejectClick: () -> Unit
) {
    // 权限判断
    val canRefresh = contact.shareStatus == ShareStatus.ACCEPTED &&
            !contact.isPaused &&
            (contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME ||
                    contact.shareDirection == ShareDirection.MUTUAL)
    val canNavigate = contact.location != null && contact.isLocationAvailable
    val canControlShare = contact.shareDirection == ShareDirection.I_SHARE_TO_THEM ||
            contact.shareDirection == ShareDirection.MUTUAL
    val isPendingRequest = contact.shareStatus == ShareStatus.PENDING &&
            contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME

    var showMoreMenu by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 刷新/实时追踪
            if (isTracking) {
                ActionButton(
                    icon = Icons.Default.Stop,
                    label = "停止追踪",
                    enabled = true,
                    isDestructive = true,
                    onClick = onStopContinuousTracking
                )
            } else {
                ActionButton(
                    icon = Icons.Default.Refresh,
                    label = if (isRequestingLocation) "定位中" else "刷新",
                    enabled = canRefresh && !isRequestingLocation,
                    onClick = onRequestLocationUpdate
                )
            }

            // 导航
            ActionButton(
                icon = Icons.Default.Directions,
                label = "导航",
                enabled = canNavigate,
                onClick = onNavigate
            )

            // 响铃查找 / 停止响铃
            if (isRinging) {
                ActionButton(
                    icon = Icons.Default.Stop,
                    label = "停止响铃",
                    enabled = true,
                    onClick = onStopSound
                )
            } else {
                ActionButton(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    label = "响铃",
                    enabled = canRefresh,
                    onClick = onPlaySound
                )
            }

            // 暂停/恢复
            if (contact.isPaused) {
                ActionButton(
                    icon = Icons.Default.PlayArrow,
                    label = "恢复共享",
                    enabled = canControlShare,
                    onClick = onResumeClick
                )
            } else {
                ActionButton(
                    icon = Icons.Default.Pause,
                    label = "暂停共享",
                    enabled = canControlShare,
                    onClick = onPauseClick
                )
            }

            // 更多菜单
            Box {
                ActionButton(
                    icon = Icons.Default.MoreHoriz,
                    label = "更多",
                    onClick = { showMoreMenu = true }
                )

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    // 实时追踪
                    if (!isTracking) {
                        DropdownMenuItem(
                            text = { Text("实时追踪") },
                            onClick = {
                                showMoreMenu = false
                                onStartContinuousTracking()
                            },
                            leadingIcon = {
                                androidx.compose.material3.Icon(
                                    Icons.Default.Radar,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            enabled = canRefresh && !isRequestingLocation
                        )
                    }

                    // 地理围栏
                    DropdownMenuItem(
                        text = { Text(if (hasGeofence) "地理围栏 (已设置)" else "地理围栏") },
                        onClick = {
                            showMoreMenu = false
                            onGeofenceClick()
                        },
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        enabled = canRefresh
                    )

                    // 丢失模式
                    DropdownMenuItem(
                        text = { Text("丢失模式") },
                        onClick = {
                            showMoreMenu = false
                            onLostModeClick()
                        },
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        enabled = canRefresh
                    )

                    // 绑定联系人
                    DropdownMenuItem(
                        text = { Text("绑定联系人") },
                        onClick = {
                            showMoreMenu = false
                            onBindClick()
                        },
                        leadingIcon = {
                            androidx.compose.material3.Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )

                    // 分隔线
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // 移除/拒绝
                    if (isPendingRequest) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "拒绝邀请",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onRejectClick()
                            },
                            leadingIcon = {
                                androidx.compose.material3.Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "移除联系人",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onRemoveClick()
                            },
                            leadingIcon = {
                                androidx.compose.material3.Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
