package me.ikate.findmy.ui.screen.geofence

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.ikate.findmy.util.ComposeHaptics

/**
 * 地理围栏控制面板
 * 底部抽屉内的配置界面
 *
 * @param state 编辑器状态
 * @param contactName 联系人名称
 * @param haptics 触觉反馈
 * @param onSave 保存回调
 * @param onDelete 删除回调
 * @param onDismiss 关闭回调
 */
@Composable
fun GeofenceControlPanel(
    state: GeofenceEditorState,
    contactName: String,
    haptics: ComposeHaptics,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // 地址栏
        AddressBar(
            address = state.currentAddress,
            isLoading = state.isLoadingAddress,
            isDragging = state.isDraggingMap
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 快捷位置 Chips
        QuickLocationChips(
            contactName = contactName,
            hasContactLocation = state.contactLocation != null,
            hasMyLocation = state.myLocation != null,
            onQuickLocate = { location ->
                haptics.tick()
                state.quickLocate(location)
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 触发条件（分段按钮）
        TriggerTypeSelector(
            selectedType = state.notificationType,
            contactName = contactName,
            radiusMeters = state.radiusMeters.toInt(),
            haptics = haptics,
            onTypeSelected = { type ->
                state.updateNotificationType(type)
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 半径滑块
        RadiusSlider(
            radiusMeters = state.radiusMeters,
            lastStep = state.lastSliderStep,
            haptics = haptics,
            onRadiusChange = { radius ->
                state.updateRadius(radius)
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 仅通知一次开关
        OneTimeSwitch(
            isOneTime = state.isOneTime,
            haptics = haptics,
            onIsOneTimeChange = { oneTime ->
                state.updateIsOneTime(oneTime)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 操作按钮
        ActionButtons(
            isEditMode = state.isEditMode,
            canSave = state.selectedCenter != null,
            haptics = haptics,
            onSave = onSave,
            onDelete = onDelete
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 地址栏
 */
@Composable
private fun AddressBar(
    address: String,
    isLoading: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    isDragging -> "松开以选定位置..."
                    isLoading -> "正在获取地址..."
                    address.isNotBlank() -> address
                    else -> "拖动地图选择位置"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDragging || isLoading)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Search,
                contentDescription = "搜索",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 快捷位置 Chips
 */
@Composable
private fun QuickLocationChips(
    contactName: String,
    hasContactLocation: Boolean,
    hasMyLocation: Boolean,
    onQuickLocate: (QuickLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 定位到联系人
        if (hasContactLocation) {
            QuickLocationChip(
                icon = Icons.Default.Person,
                label = contactName,
                onClick = { onQuickLocate(QuickLocation.CONTACT) }
            )
        }

        // 定位到我
        if (hasMyLocation) {
            QuickLocationChip(
                icon = Icons.Default.MyLocation,
                label = "我的位置",
                onClick = { onQuickLocate(QuickLocation.ME) }
            )
        }

        // 家（预留）
        QuickLocationChip(
            icon = Icons.Default.Home,
            label = "家",
            enabled = false,
            onClick = { onQuickLocate(QuickLocation.HOME) }
        )

        // 公司（预留）
        QuickLocationChip(
            icon = Icons.Default.Work,
            label = "公司",
            enabled = false,
            onClick = { onQuickLocate(QuickLocation.WORK) }
        )
    }
}

/**
 * 快捷位置 Chip
 */
@Composable
private fun QuickLocationChip(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, maxLines = 1) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    )
}

/**
 * 触发条件选择器（分段按钮）
 */
@Composable
private fun TriggerTypeSelector(
    selectedType: NotificationType,
    contactName: String,
    radiusMeters: Int,
    haptics: ComposeHaptics,
    onTypeSelected: (NotificationType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "通知条件",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 到达/离开 分段按钮
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedType == NotificationType.ARRIVE,
                onClick = {
                    haptics.tick()
                    onTypeSelected(NotificationType.ARRIVE)
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {
                    SegmentedButtonDefaults.Icon(active = selectedType == NotificationType.ARRIVE) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Login,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            ) {
                Text("到达时")
            }
            SegmentedButton(
                selected = selectedType == NotificationType.LEAVE,
                onClick = {
                    haptics.tick()
                    onTypeSelected(NotificationType.LEAVE)
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {
                    SegmentedButtonDefaults.Icon(active = selectedType == NotificationType.LEAVE) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            ) {
                Text("离开时")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 离开我身边选项
        LeftBehindOption(
            isSelected = selectedType == NotificationType.LEFT_BEHIND,
            contactName = contactName,
            radiusMeters = radiusMeters,
            onClick = {
                haptics.tick()
                onTypeSelected(NotificationType.LEFT_BEHIND)
            }
        )
    }
}

/**
 * "离开我身边"选项
 */
@Composable
private fun LeftBehindOption(
    isSelected: Boolean,
    contactName: String,
    radiusMeters: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 选中指示器
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "离开我身边时通知",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = "当 $contactName 远离我超过 ${radiusMeters}m 时通知",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 半径滑块
 */
@Composable
private fun RadiusSlider(
    radiusMeters: Float,
    lastStep: Int,
    haptics: ComposeHaptics,
    onRadiusChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "围栏半径",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${radiusMeters.toInt()}m",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = radiusMeters,
            onValueChange = { newRadius ->
                val snapped = GeofenceEditorState.snapToKeyPoints(newRadius)
                val currentStep = (snapped / 50f).toInt()
                if (currentStep != lastStep) {
                    haptics.tick()
                }
                onRadiusChange(newRadius)
            },
            valueRange = 100f..1000f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "100m",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "1km",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 仅通知一次开关
 */
@Composable
private fun OneTimeSwitch(
    isOneTime: Boolean,
    haptics: ComposeHaptics,
    onIsOneTimeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "仅通知一次",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "触发后自动删除此通知",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isOneTime,
                onCheckedChange = {
                    haptics.tick()
                    onIsOneTimeChange(it)
                }
            )
        }
    }
}

/**
 * 操作按钮
 */
@Composable
private fun ActionButtons(
    isEditMode: Boolean,
    canSave: Boolean,
    haptics: ComposeHaptics,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 删除按钮（仅编辑模式显示）
        if (isEditMode) {
            TextButton(
                onClick = {
                    haptics.click()
                    onDelete()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("移除通知")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 保存按钮
        Button(
            onClick = {
                haptics.click()
                onSave()
            },
            enabled = canSave
        ) {
            Text(if (isEditMode) "更新通知" else "添加通知")
        }
    }
}
