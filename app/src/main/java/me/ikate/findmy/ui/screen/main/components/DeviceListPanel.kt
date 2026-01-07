package me.ikate.findmy.ui.screen.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.DeviceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设备列表面板
 * 显示所有设备的列表，支持点击跳转到地图位置
 * 底部包含仿 iOS Find My 的导航栏
 *
 * @param devices 设备列表
 * @param contacts 联系人列表
 * @param onDeviceClick 设备点击回调
 * @param onContactClick 联系人点击回调
 * @param onAddContactClick 添加联系人回调
 * @param onDeviceDelete 设备删除回调
 * @param onNavigateToAuth 导航到登录页回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListPanel(
    devices: List<Device>,
    contacts: List<me.ikate.findmy.data.model.Contact> = emptyList(),
    onDeviceClick: (Device) -> Unit,
    onContactClick: (me.ikate.findmy.data.model.Contact) -> Unit = {},
    onAddContactClick: () -> Unit = {},
    onDeviceDelete: (Device) -> Unit = {},
    onNavigateToAuth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 选中的 Tab 索引 (0: 联系人, 1: 设备, 2: 物品, 3: 我)
    // 默认选中 "设备" (1)
    var selectedTab by remember { mutableIntStateOf(1) }

    Column(modifier = modifier.fillMaxSize()) {
        // 标题 (仅在"设备" Tab 显示，或者根据 Tab 变化)
        if (selectedTab == 1) {
            Text(
                text = "我的设备",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // 内容区域
        when (selectedTab) {
            0 -> {
                // 联系人列表
                // 检查是否匿名用户
                val isAnonymous = com.google.firebase.auth.FirebaseAuth.getInstance()
                    .currentUser?.isAnonymous ?: true

                if (isAnonymous) {
                    // 显示匿名用户提示
                    AnonymousUserPrompt(
                        onSignUpClick = onNavigateToAuth,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // 显示联系人列表
                    ContactListPanel(
                        currentUser = null,
                        contacts = contacts,
                        onContactClick = onContactClick,
                        onAddContactClick = onAddContactClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            1 -> {
                // 设备列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(
                        items = devices,
                        key = { it.id }
                    ) { device ->
                        SwipeToDeleteItem(
                            device = device,
                            onClick = { onDeviceClick(device) },
                            onDelete = { onDeviceDelete(device) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = Color.Gray.copy(alpha = 0.2f)
                        )
                    }
                }
            }
            2, 3 -> {
                // 其他 Tab 的占位内容
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when (selectedTab) {
                            2 -> "物品功能开发中"
                            3 -> "个人中心开发中"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            }
        }

        // 底部导航栏
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            NavigationBarItem(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                icon = { Icon(Icons.Default.Person, contentDescription = "联系人") },
                label = { Text("联系人") }
            )
            NavigationBarItem(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                icon = { Icon(Icons.Default.Devices, contentDescription = "设备") },
                label = { Text("设备") }
            )
            NavigationBarItem(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                icon = { Icon(Icons.Default.Backpack, contentDescription = "物品") },
                label = { Text("物品") }
            )
            NavigationBarItem(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                icon = { Icon(Icons.Default.AccountCircle, contentDescription = "我") },
                label = { Text("我") }
            )
        }
    }
}

/**
 * 左滑删除包装组件
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SwipeToDeleteItem(
    device: Device,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // 左滑时显示的删除背景（只在滑动时显示）
            val backgroundColor = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                else -> Color.Transparent
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false, // 只允许从右向左滑动
        content = {
            Surface(
                color = MaterialTheme.colorScheme.surface
            ) {
                DeviceListItem(
                    device = device,
                    onClick = onClick
                )
            }
        }
    )
}

/**
 * 设备列表项
 */
@Composable
private fun DeviceListItem(
    device: Device,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 自动刷新时间显示（每分钟更新一次）
    var refreshTrigger by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(device.id) {
        while (true) {
            kotlinx.coroutines.delay(60_000) // 每60秒刷新一次
            refreshTrigger = System.currentTimeMillis()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 设备图标（圆形）
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = when (device.deviceType) {
                    DeviceType.PHONE -> Icons.Default.Phone
                    DeviceType.TABLET -> Icons.Default.Tablet
                    DeviceType.WATCH -> Icons.Default.Watch
                    else -> Icons.Default.Phone
                },
                contentDescription = device.name,
                modifier = Modifier.padding(12.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 设备信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 设备名称：设备自定义名称 + "的" + 设备名称（型号）
            val displayName = if (device.customName != null && device.customName.isNotBlank()) {
                "${device.customName}的${device.name}"
            } else {
                device.name
            }
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            // 最后更新时间（使用refreshTrigger触发重新计算）
            val timeText = formatUpdateTime(device.lastUpdateTime, refreshTrigger)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                // 电量指示
                if (device.battery < 100) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.BatteryFull,
                        contentDescription = "电量",
                        modifier = Modifier.size(16.dp),
                        tint = if (device.battery < 20) Color.Red else Color.Gray
                    )
                    Text(
                        text = "${device.battery}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (device.battery < 20) Color.Red else Color.Gray
                    )
                }
            }
        }

        // 在线状态指示器
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = if (device.isOnline) Color.Green else Color.Gray
        ) {}
    }
}

/**
 * 格式化更新时间
 * 规则：
 * - 1分钟内：刚刚
 * - 1小时内：X分钟前
 * - 今天：X小时前
 * - 其他：具体时间
 *
 * @param timestamp 设备最后更新时间戳
 * @param trigger 刷新触发器（用于强制重新计算）
 */
private fun formatUpdateTime(timestamp: Long, trigger: Long = 0): String {
    // 使用当前时间计算差值（trigger参数仅用于触发重组）
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        else -> {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
