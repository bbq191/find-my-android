package me.ikate.findmy.ui.screen.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
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
 * 设备详情面板
 * 显示选中设备的详细信息
 *
 * @param device 选中的设备
 * @param onClose 关闭详情回调
 * @param onEdit 编辑设备回调
 * @param onDelete 删除设备回调
 * @param modifier 修饰符
 */
@Composable
fun DeviceDetailPanel(
    device: Device,
    onClose: () -> Unit,
    onEdit: ((Device) -> Unit)? = null,
    onDelete: ((Device) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // 顶部：关闭按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "设备详情",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 设备信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 设备图标
                Surface(
                    modifier = Modifier.size(80.dp),
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
                        modifier = Modifier.padding(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 设备名称
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 在线状态
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = if (device.isOnline) Color.Green else Color.Gray
                    ) {}
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (device.isOnline) "在线" else "离线",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (device.isOnline) Color.Green else Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 详细信息列表
                DeviceInfoRow(label = "位置", value = formatLocation(device))
                Spacer(modifier = Modifier.height(12.dp))

                DeviceInfoRow(
                    label = "电量",
                    value = "${device.battery}%",
                    valueColor = if (device.battery < 20) Color.Red else Color.Unspecified
                )
                Spacer(modifier = Modifier.height(12.dp))

                DeviceInfoRow(
                    label = "最后更新",
                    value = formatUpdateTime(device.lastUpdateTime)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 操作按钮（仅当设备不是当前设备时显示）
        if (onEdit != null || onDelete != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 编辑按钮
                if (onEdit != null) {
                    OutlinedButton(
                        onClick = { onEdit(device) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("编辑")
                    }
                }

                // 删除按钮
                if (onDelete != null) {
                    Button(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("删除")
                    }
                }
            }
        }

        // 删除确认对话框
        if (showDeleteConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("确认删除") },
                text = { Text("确定要删除设备 \"${device.name}\" 吗？此操作不可撤销。") },
                confirmButton = {
                    Button(
                        onClick = {
                            onDelete?.invoke(device)
                            showDeleteConfirm = false
                            onClose()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showDeleteConfirm = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

/**
 * 设备信息行组件
 */
@Composable
private fun DeviceInfoRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

/**
 * 格式化位置信息
 */
private fun formatLocation(device: Device): String {
    return String.format("%.4f, %.4f", device.location.latitude, device.location.longitude)
}

/**
 * 格式化更新时间
 */
private fun formatUpdateTime(timestamp: Long): String {
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
