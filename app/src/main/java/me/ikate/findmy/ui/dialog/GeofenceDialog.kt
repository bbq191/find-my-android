package me.ikate.findmy.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng

/**
 * 地理围栏事件类型
 */
enum class GeofenceEventType {
    ENTER,      // 进入
    EXIT,       // 离开
    BOTH        // 两者
}

/**
 * 地理围栏配置
 */
data class GeofenceConfig(
    val enabled: Boolean = false,
    val locationName: String = "",
    val center: LatLng? = null,
    val radiusMeters: Float = 200f,
    val eventType: GeofenceEventType = GeofenceEventType.BOTH,
    val notifyOnEnter: Boolean = true,
    val notifyOnExit: Boolean = true
)

/**
 * 地理围栏设置对话框
 */
@Composable
fun GeofenceDialog(
    contactName: String,
    contactLocation: LatLng?,
    currentConfig: GeofenceConfig = GeofenceConfig(),
    onDismiss: () -> Unit,
    onConfirm: (GeofenceConfig) -> Unit
) {
    var locationName by remember { mutableStateOf(currentConfig.locationName.ifBlank { "$contactName 的位置" }) }
    var radiusMeters by remember { mutableFloatStateOf(currentConfig.radiusMeters) }
    var notifyOnEnter by remember { mutableStateOf(currentConfig.notifyOnEnter) }
    var notifyOnExit by remember { mutableStateOf(currentConfig.notifyOnExit) }

    val isEnabled = currentConfig.enabled

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = null,
                tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (isEnabled) "编辑地理围栏" else "设置地理围栏",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (contactLocation == null) {
                    Text(
                        text = "无法设置地理围栏：联系人位置不可用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "当 $contactName 进入或离开指定区域时，您将收到通知。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 位置名称
                    OutlinedTextField(
                        value = locationName,
                        onValueChange = { locationName = it },
                        label = { Text("位置名称") },
                        placeholder = { Text("例如：家、公司") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 围栏半径
                    Text(
                        text = "围栏半径: ${radiusMeters.toInt()} 米",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = radiusMeters,
                        onValueChange = { radiusMeters = it },
                        valueRange = 50f..1000f,
                        steps = 18, // 50米步进
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("50m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        Text("1000m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 通知设置
                    Text(
                        text = "通知触发条件",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 进入通知
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("进入区域时通知", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = notifyOnEnter,
                            onCheckedChange = { notifyOnEnter = it }
                        )
                    }

                    // 离开通知
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("离开区域时通知", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = notifyOnExit,
                            onCheckedChange = { notifyOnExit = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (contactLocation != null) {
                if (isEnabled) {
                    Row {
                        TextButton(
                            onClick = {
                                onConfirm(GeofenceConfig(enabled = false))
                            }
                        ) {
                            Text("移除围栏", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(
                            onClick = {
                                onConfirm(
                                    GeofenceConfig(
                                        enabled = true,
                                        locationName = locationName,
                                        center = contactLocation,
                                        radiusMeters = radiusMeters,
                                        notifyOnEnter = notifyOnEnter,
                                        notifyOnExit = notifyOnExit
                                    )
                                )
                            }
                        ) {
                            Text("更新")
                        }
                    }
                } else {
                    TextButton(
                        onClick = {
                            if (notifyOnEnter || notifyOnExit) {
                                onConfirm(
                                    GeofenceConfig(
                                        enabled = true,
                                        locationName = locationName,
                                        center = contactLocation,
                                        radiusMeters = radiusMeters,
                                        notifyOnEnter = notifyOnEnter,
                                        notifyOnExit = notifyOnExit
                                    )
                                )
                            }
                        },
                        enabled = notifyOnEnter || notifyOnExit
                    ) {
                        Text("启用围栏")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
