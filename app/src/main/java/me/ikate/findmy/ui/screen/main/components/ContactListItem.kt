package me.ikate.findmy.ui.screen.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.roundToInt
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareStatus
import me.ikate.findmy.service.TrackingState
import me.ikate.findmy.ui.components.AccuracyBadge
import me.ikate.findmy.util.DistanceCalculator
import me.ikate.findmy.util.ReverseGeocodeHelper
import me.ikate.findmy.util.TimeFormatter


/**
 * 联系人列表项组件
 *
 * 交互方式：
 * - 点击卡片：定位到地图 + 开始追踪
 * - 点击头像：开始追踪（显示停止覆盖层后可点击停止）
 * - 左滑(200dp)：显示操作按钮（电子围栏、查找设备、导航）
 * - 右滑短(72dp)：绑定联系人
 * - 右滑长(150dp)：长滑删除联系人
 *
 * 头像边框颜色表示追踪状态：
 * - 黄色(#FFC107)：等待连接
 * - 蓝色(#2196F3)：追踪中
 * - 绿色(#4CAF50)：追踪成功
 * - 绿色/灰色：在线/离线状态
 */
@Composable
fun ContactListItem(
    contact: Contact,
    myDevice: Device? = null,
    isExpanded: Boolean,
    isRequestingLocation: Boolean = false,
    isTracking: Boolean = false,
    trackingState: TrackingState = TrackingState.IDLE,
    isPinned: Boolean = false,
    onClick: () -> Unit,  // 点击卡片：定位到地图 + 追踪
    onAvatarClick: () -> Unit = {},  // 点击头像：开始/停止追踪
    onExpandClick: () -> Unit,  // 点击展开按钮：展开/收起操作栏
    onNavigate: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onBindClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onAcceptClick: () -> Unit,
    onRejectClick: () -> Unit,
    onStopTracking: () -> Unit = {},  // 停止追踪
    onFindDeviceClick: () -> Unit = {},  // 查找设备（合并响铃+丢失模式）
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
    val density = LocalDensity.current
    var addressText by remember { mutableStateOf<String?>(null) }
    var isAddressLoading by remember { mutableStateOf(false) }

    // 双向滑动状态
    val leftSwipeActionWidth = 200.dp   // 左滑显示操作按钮（导航、查找设备、电子围栏）
    val rightSwipeBindWidth = 72.dp     // 右滑短距离：绑定联系人
    val rightSwipeDeleteWidth = 150.dp  // 右滑长距离：删除

    val leftSwipeWidthPx = with(density) { leftSwipeActionWidth.toPx() }
    val rightSwipeBindPx = with(density) { rightSwipeBindWidth.toPx() }
    val rightSwipeDeletePx = with(density) { rightSwipeDeleteWidth.toPx() }

    var offsetX by remember { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(stiffness = 400f),
        label = "swipeOffset"
    )

    // 是否触发删除（长右滑）
    var isDeleteTriggered by remember { mutableStateOf(false) }

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
            kotlinx.coroutines.delay(10_000L)
            if (isAddressLoading && addressText == null) {
                addressText = "获取地址超时"
                isAddressLoading = false
            }
        }
    }

    // 判断是否可追踪（用于左滑操作按钮）
    val canTrackForSwipe = contact.shareStatus == ShareStatus.ACCEPTED &&
            !contact.isPaused &&
            (contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME ||
                    contact.shareDirection == ShareDirection.MUTUAL)
    val canNavigateForSwipe = contact.location != null && contact.isLocationAvailable

    // 外层容器支持双向滑动
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // 右滑背景 - 绑定联系人(短滑) + 删除(长滑)
        AnimatedVisibility(
            visible = animatedOffsetX > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize()
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isDeleteTriggered) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 绑定联系人按钮（短滑显示）
                    AnimatedVisibility(
                        visible = !isDeleteTriggered,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        SwipeActionButton(
                            icon = Icons.Default.Person,
                            label = "绑定联系人",
                            enabled = true,
                            onClick = {
                                onBindClick()
                                offsetX = 0f
                            }
                        )
                    }

                    // 删除按钮（长滑显示）
                    AnimatedVisibility(
                        visible = isDeleteTriggered,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "松开删除",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 左滑背景 - 操作按钮（导航、查找设备、电子围栏）
        AnimatedVisibility(
            visible = animatedOffsetX < 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize()
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier
                        .width(leftSwipeActionWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 电子围栏按钮
                    SwipeActionButton(
                        icon = Icons.Default.MyLocation,
                        label = if (hasGeofence) "围栏(已设)" else "电子围栏",
                        enabled = canTrackForSwipe,
                        onClick = {
                            onGeofenceClick()
                            offsetX = 0f
                        }
                    )
                    // 查找设备按钮
                    SwipeActionButton(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        label = "查找设备",
                        enabled = canTrackForSwipe,
                        onClick = {
                            onFindDeviceClick()
                            offsetX = 0f
                        }
                    )
                    // 导航按钮
                    SwipeActionButton(
                        icon = Icons.Default.Directions,
                        label = "导航",
                        enabled = canNavigateForSwipe,
                        onClick = {
                            onNavigate()
                            offsetX = 0f
                        }
                    )
                }
            }
        }

        // 前景卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // 根据滑动距离决定操作
                            when {
                                // 长右滑删除
                                offsetX > rightSwipeDeletePx * 0.8f -> {
                                    onRemoveClick()
                                    offsetX = 0f
                                    isDeleteTriggered = false
                                }
                                // 短右滑吸附到绑定按钮位置
                                offsetX > rightSwipeBindPx / 2 -> {
                                    offsetX = rightSwipeBindPx
                                    isDeleteTriggered = false
                                }
                                // 很短的右滑回弹
                                offsetX > 0 -> {
                                    offsetX = 0f
                                    isDeleteTriggered = false
                                }
                                // 左滑展示操作按钮
                                offsetX < -leftSwipeWidthPx / 2 -> {
                                    offsetX = -leftSwipeWidthPx
                                }
                                // 回弹
                                else -> {
                                    offsetX = 0f
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newOffset = (offsetX + dragAmount).coerceIn(-leftSwipeWidthPx, rightSwipeDeletePx)
                            offsetX = newOffset
                            // 更新删除触发状态（长右滑超过80%）
                            isDeleteTriggered = newOffset > rightSwipeDeletePx * 0.8f
                        }
                    )
                },
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

                // 点击主体区域（头像+信息）触发定位追踪
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (canTrack) {
                                Modifier.clickable(onClick = onClick)
                            } else {
                                Modifier
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContactAvatar(
                        contact = contact,
                        isOnline = isOnline,
                        trackingState = trackingState,
                        canTrack = canTrack,
                        onAvatarClick = onAvatarClick,
                        onStopClick = onStopTracking
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // 联系人信息
                    ContactInfo(
                        contact = contact,
                        myDevice = myDevice,
                        trackingState = trackingState,
                        addressText = addressText,
                        isAddressLoading = isAddressLoading,
                        modifier = Modifier.weight(1f)
                    )
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
                    // 右箭头提示（提示右滑可操作）
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "滑动操作",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
        }  // Card 结束
    }  // Box 结束
}

/**
 * 滑动操作按钮
 */
@Composable
private fun SwipeActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                   else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
        )
    }
}

/**
 * 联系人头像组件
 * 支持追踪状态显示和停止追踪覆盖层
 *
 * 颜色状态：
 * - WAITING: 黄色 (#FFC107) - 等待连接
 * - CONNECTED: 蓝色 (#2196F3) - 追踪中
 * - SUCCESS: 绿色 (#4CAF50) - 追踪成功
 * - IDLE: 在线绿/离线灰 - 原状态
 */
@Composable
private fun ContactAvatar(
    contact: Contact,
    isOnline: Boolean = false,
    trackingState: TrackingState = TrackingState.IDLE,
    canTrack: Boolean = false,
    onAvatarClick: () -> Unit = {},
    onStopClick: () -> Unit = {}
) {
    // 状态颜色定义
    val waitingColor = Color(0xFFFFC107)  // 黄色
    val connectedColor = Color(0xFF2196F3)  // 蓝色
    val successColor = Color(0xFF4CAF50)  // 绿色
    val onlineColor = Color(0xFF4CAF50)  // 绿色
    val offlineColor = MaterialTheme.colorScheme.outlineVariant  // 灰色

    // 根据追踪状态确定边框颜色和宽度
    val (borderColor, borderWidth) = when (trackingState) {
        TrackingState.WAITING -> waitingColor to 3.dp
        TrackingState.CONNECTED -> connectedColor to 3.dp
        TrackingState.SUCCESS -> successColor to 3.dp
        TrackingState.IDLE -> if (isOnline) onlineColor to 2.dp else offlineColor to 2.dp
    }

    // 是否显示活跃状态（追踪相关状态）
    val isActive = trackingState != TrackingState.IDLE

    // 是否显示停止覆盖层（等待中或连接中）
    val showStopOverlay = trackingState == TrackingState.WAITING || trackingState == TrackingState.CONNECTED

    Box(
        modifier = Modifier
            .size(56.dp)
            .then(
                if (canTrack && !showStopOverlay) {
                    Modifier.clickable(onClick = onAvatarClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // 外层边框 - 显示状态颜色
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor)
        ) {}

        // 头像内容
        Surface(
            modifier = Modifier.size(if (isActive) 48.dp else 50.dp),
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

        // 停止追踪覆盖层（黑色半透明 + 停止图标）
        AnimatedVisibility(
            visible = showStopOverlay,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onStopClick),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "停止追踪",
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }
            }
        }

        // 状态指示器（右下角小圆点）
        AnimatedVisibility(
            visible = isActive && !showStopOverlay,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(successColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = "追踪成功",
                    modifier = Modifier.size(10.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ContactInfo(
    contact: Contact,
    myDevice: Device?,
    trackingState: TrackingState = TrackingState.IDLE,
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

        // 状态逻辑
        if (contact.shareStatus == ShareStatus.ACCEPTED && !contact.isPaused && contact.isLocationAvailable) {
            when (trackingState) {
                TrackingState.WAITING -> WaitingStatusRow()
                TrackingState.CONNECTED -> TrackingStatusRow()
                TrackingState.SUCCESS -> SuccessStatusRow()
                TrackingState.IDLE -> DeviceStatusRow(contact = contact)
            }

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
 * 等待状态行 - 黄色
 */
@Composable
private fun WaitingStatusRow() {
    val waitingColor = Color(0xFFFFC107)
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp,
            color = waitingColor
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "等待连接...",
            style = MaterialTheme.typography.bodySmall,
            color = waitingColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 追踪中状态行 - 蓝色
 */
@Composable
private fun TrackingStatusRow() {
    val connectedColor = Color(0xFF2196F3)
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp,
            color = connectedColor
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "实时追踪中...",
            style = MaterialTheme.typography.bodySmall,
            color = connectedColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 追踪成功状态行 - 绿色
 */
@Composable
private fun SuccessStatusRow() {
    val successColor = Color(0xFF4CAF50)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.MyLocation,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = successColor
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "位置已更新",
            style = MaterialTheme.typography.bodySmall,
            color = successColor,
            fontWeight = FontWeight.SemiBold
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

        // 位置更新时间徽章
        AccuracyBadge(
            lastUpdateTime = contact.lastUpdateTime
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

