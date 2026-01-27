package me.ikate.findmy.ui.screen.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareStatus
import me.ikate.findmy.ui.theme.FindMyShapes
import me.ikate.findmy.util.TimeFormatter
import androidx.compose.material.icons.filled.Warning

/**
 * 联系人详情 BottomSheet
 *
 * MONET 设计规范：
 * 1. 去深色块：保持浅色背景，与 BottomSheet 一致
 * 2. 主要动作组 (Primary Actions)：查找设备、电子围栏、导航 → 一行三个 Filled Tonal Button
 * 3. 次要动作组 (Secondary Actions)：绑定/删除联系人等 → Outlined Card 列表式卡片，右侧带箭头
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailBottomSheet(
    contact: Contact,
    hasGeofence: Boolean = false,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
    onFindDevice: () -> Unit,
    onGeofence: () -> Unit,
    onBindContact: () -> Unit,
    onPauseShare: () -> Unit,
    onResumeShare: () -> Unit,
    onRemoveContact: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 判断是否可以进行操作
    val canTrack = contact.shareStatus == ShareStatus.ACCEPTED &&
            !contact.isPaused &&
            (contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME ||
                    contact.shareDirection == ShareDirection.MUTUAL)
    val canNavigate = contact.location != null && contact.isLocationAvailable
    val isOnline = TimeFormatter.isOnline(contact.lastUpdateTime ?: 0L)
    val locationFreshness = TimeFormatter.getLocationFreshness(contact.lastUpdateTime ?: 0L)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,  // 保持浅色背景
        shape = FindMyShapes.BottomSheetTop,
        dragHandle = {
            // 标准拖拽手柄
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 36.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // 联系人信息头部
            ContactHeader(contact = contact, isOnline = isOnline)

            // 位置过期警告提示
            if (contact.location != null && !contact.isPaused) {
                LocationFreshnessWarning(freshness = locationFreshness)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 主要动作组 - 三个 Filled Tonal Button
            PrimaryActionsRow(
                canNavigate = canNavigate,
                canTrack = canTrack,
                hasGeofence = hasGeofence,
                onNavigate = {
                    onNavigate()
                    onDismiss()
                },
                onFindDevice = {
                    onFindDevice()
                    onDismiss()
                },
                onGeofence = {
                    onGeofence()
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 次要动作组 - Outlined Card 列表
            SecondaryActionsCard(
                contact = contact,
                onBindContact = {
                    onBindContact()
                    onDismiss()
                },
                onPauseShare = {
                    onPauseShare()
                    onDismiss()
                },
                onResumeShare = {
                    onResumeShare()
                    onDismiss()
                },
                onRemoveContact = {
                    onRemoveContact()
                    onDismiss()
                }
            )
        }
    }
}

/**
 * 位置新鲜度警告提示
 * 当位置可能过期时显示警告，帮助用户理解当前位置可能是缓存的旧数据
 */
@Composable
private fun LocationFreshnessWarning(
    freshness: TimeFormatter.LocationFreshness
) {
    // 只有在位置可能过期时才显示警告
    if (freshness == TimeFormatter.LocationFreshness.FRESH ||
        freshness == TimeFormatter.LocationFreshness.RECENT) {
        return
    }

    val (message, backgroundColor, contentColor) = when (freshness) {
        TimeFormatter.LocationFreshness.STALE -> Triple(
            "位置可能已过期，对方设备可能离线",
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        TimeFormatter.LocationFreshness.VERY_STALE -> Triple(
            "位置已超过 24 小时未更新",
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.onErrorContainer
        )
        else -> Triple(
            "位置信息不可用",
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
        }
    }
}

/**
 * 联系人信息头部
 */
@Composable
private fun ContactHeader(
    contact: Contact,
    isOnline: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            border = BorderStroke(
                2.dp,
                if (isOnline) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!contact.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(contact.avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = contact.name,
                        contentScale = ContentScale.Crop,
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

        Spacer(modifier = Modifier.width(16.dp))

        // 联系人信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 状态文字
            val statusText = when {
                contact.isPaused -> "共享已暂停"
                isOnline -> "在线 · ${contact.deviceName ?: "未知设备"}"
                else -> "离线 · ${TimeFormatter.formatUpdateTime(contact.lastUpdateTime ?: 0L)}"
            }
            val statusColor = when {
                contact.isPaused -> MaterialTheme.colorScheme.error
                isOnline -> Color(0xFF4CAF50)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )
        }
    }
}

/**
 * 主要动作组 - 一行三个 Filled Tonal Button
 * MONET：查找设备、电子围栏、导航
 */
@Composable
private fun PrimaryActionsRow(
    canNavigate: Boolean,
    canTrack: Boolean,
    hasGeofence: Boolean,
    onNavigate: () -> Unit,
    onFindDevice: () -> Unit,
    onGeofence: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 查找设备
        PrimaryActionButton(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            label = "查找设备",
            enabled = canTrack,
            onClick = onFindDevice,
            modifier = Modifier.weight(1f)
        )

        // 电子围栏
        PrimaryActionButton(
            icon = Icons.Default.MyLocation,
            label = if (hasGeofence) "围栏(已设)" else "电子围栏",
            enabled = canTrack,
            onClick = onGeofence,
            modifier = Modifier.weight(1f),
            isActivated = hasGeofence
        )

        // 导航
        PrimaryActionButton(
            icon = Icons.Default.Directions,
            label = "导航",
            enabled = canNavigate,
            onClick = onNavigate,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 主要动作按钮 - 胶囊形横向按钮
 * 图标 + 文字水平排列，更紧凑
 */
@Composable
private fun PrimaryActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActivated: Boolean = false  // 已激活状态（如围栏已设）
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),  // 胶囊形
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 图标（带激活状态指示）
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(32.dp)
                )
                // 已激活状态：显示小绿点
                if (isActivated) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .offset(x = 2.dp, y = (-2).dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * 次要动作组 - iOS 风格 Grouped List
 * 功能岛：所有列表项合并在一个圆角容器中，细分割线隔开
 */
@Composable
private fun SecondaryActionsCard(
    contact: Contact,
    onBindContact: () -> Unit,
    onPauseShare: () -> Unit,
    onResumeShare: () -> Unit,
    onRemoveContact: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column {
            // 绑定联系人
            SecondaryActionItem(
                icon = Icons.Default.Link,
                label = "绑定联系人",
                subtitle = "关联系统联系人",
                onClick = onBindContact
            )

            // 细分割线（从图标后开始，iOS 风格）
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // 暂停/恢复共享
            if (contact.isPaused) {
                SecondaryActionItem(
                    icon = Icons.Default.PlayArrow,
                    label = "恢复共享",
                    subtitle = "继续共享您的位置",
                    onClick = onResumeShare
                )
            } else {
                SecondaryActionItem(
                    icon = Icons.Default.Pause,
                    label = "暂停共享",
                    subtitle = "暂时停止共享您的位置",
                    onClick = onPauseShare
                )
            }

            // 细分割线
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // 删除联系人（红色警告样式）
            SecondaryActionItem(
                icon = Icons.Default.PersonRemove,
                label = "删除联系人",
                subtitle = "停止与此人共享位置",
                onClick = onRemoveContact,
                isDestructive = true
            )
        }
    }
}

/**
 * 次要动作项 - 列表式卡片行
 * 左侧图标 + 标题/副标题 + 右侧箭头
 */
@Composable
private fun SecondaryActionItem(
    icon: ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val contentColor = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconContainerColor = if (isDestructive) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }
    val iconTint = if (isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标容器
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(10.dp),
            color = iconContainerColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconTint
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 标题和副标题
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 右箭头
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
