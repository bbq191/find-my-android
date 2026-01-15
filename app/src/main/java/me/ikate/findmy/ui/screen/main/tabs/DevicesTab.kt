package me.ikate.findmy.ui.screen.main.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.ui.screen.main.components.DeviceCard

/**
 * 设备 Tab
 * 显示当前用户的所有设备列表（包括共享设备）
 */
@Composable
fun DevicesTab(
    devices: List<Device>,
    currentDeviceId: String?,
    currentUserId: String?,
    currentDeviceAddress: String?,
    ringingDeviceId: String?,
    onDeviceClick: (Device) -> Unit,
    onNavigate: (Device) -> Unit,
    onPlaySound: (Device) -> Unit,
    onStopSound: () -> Unit,
    onLostMode: (Device) -> Unit,
    modifier: Modifier = Modifier
) {
    // 展开状态
    var expandedDeviceId by remember { mutableStateOf<String?>(null) }

    // 分组设备（只显示自己的设备）
    val myDevices = devices.filter { it.ownerId == currentUserId }
    val currentDevice = myDevices.find { it.id == currentDeviceId }
    val myOtherDevices = myDevices.filter { it.id != currentDeviceId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // iOS 风格大标题
        DevicesHeader()

        if (myDevices.isEmpty()) {
            EmptyDevicesState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 当前设备
                currentDevice?.let { device ->
                    item(key = device.id) {
                        DeviceCard(
                            device = device,
                            isCurrentDevice = true,
                            isSharedDevice = false,
                            isExpanded = expandedDeviceId == device.id,
                            address = currentDeviceAddress,
                            onClick = {
                                expandedDeviceId = if (expandedDeviceId == device.id) null else device.id
                                onDeviceClick(device)
                            },
                            onNavigate = { onNavigate(device) },
                            onPlaySound = { onPlaySound(device) },
                            onStopSound = onStopSound,
                            onLostMode = { onLostMode(device) },
                            isRinging = ringingDeviceId == device.id,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    item {
                        Text(
                            text = "这是你当前正在使用的设备",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }

                // 我的其他设备
                if (myOtherDevices.isNotEmpty()) {
                    item {
                        SectionHeader(title = "我的其他设备")
                    }

                    items(
                        items = myOtherDevices,
                        key = { it.id }
                    ) { device ->
                        DeviceCard(
                            device = device,
                            isCurrentDevice = false,
                            isSharedDevice = false,
                            isExpanded = expandedDeviceId == device.id,
                            address = null,
                            onClick = {
                                expandedDeviceId = if (expandedDeviceId == device.id) null else device.id
                                onDeviceClick(device)
                            },
                            onNavigate = { onNavigate(device) },
                            onPlaySound = { onPlaySound(device) },
                            onStopSound = onStopSound,
                            onLostMode = { onLostMode(device) },
                            isRinging = ringingDeviceId == device.id,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

@Composable
private fun DevicesHeader() {
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
                text = "设备",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "管理你的设备",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun EmptyDevicesState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Smartphone,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = "未找到设备",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "正在获取设备信息...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
