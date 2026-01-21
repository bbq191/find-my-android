package me.ikate.findmy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.ikate.findmy.util.PermissionGuideHelper

/**
 * 权限引导对话框
 * 引导用户开启位置共享所需的关键权限
 */
@Composable
fun PermissionGuideDialog(
    missingPermissions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val hasBackgroundLocation = "后台定位" in missingPermissions
    val hasBatteryOptimization = "电池无限制" in missingPermissions
    val hasWifiScanning = "WiFi扫描" in missingPermissions
    val hasBluetoothScanning = "蓝牙扫描" in missingPermissions

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                text = "需要开启关键权限",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "为了确保位置共享功能正常运行，需要开启以下权限：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 后台定位权限卡片
                if (hasBackgroundLocation) {
                    PermissionCard(
                        icon = Icons.Default.LocationOn,
                        title = "始终允许定位",
                        description = "应用需要在后台持续获取您的位置信息",
                        onClick = {
                            PermissionGuideHelper.openBackgroundLocationSettings(context)
                        }
                    )
                }

                // 电池优化权限卡片
                if (hasBatteryOptimization) {
                    PermissionCard(
                        icon = Icons.Default.BatteryFull,
                        title = "电池无限制",
                        description = "防止系统杀死应用，确保位置持续共享",
                        onClick = {
                            PermissionGuideHelper.openBatteryOptimizationSettings(context)
                        }
                    )
                }

                // WiFi 扫描权限卡片
                if (hasWifiScanning) {
                    PermissionCard(
                        icon = Icons.Default.Wifi,
                        title = "WiFi 扫描",
                        description = "允许扫描 WiFi 热点来提高室内定位精度",
                        onClick = {
                            PermissionGuideHelper.openWifiScanningSettings(context)
                        }
                    )
                }

                // 蓝牙扫描权限卡片
                if (hasBluetoothScanning) {
                    PermissionCard(
                        icon = Icons.Default.Bluetooth,
                        title = "蓝牙扫描",
                        description = "允许扫描蓝牙信标来提高室内定位精度",
                        onClick = {
                            PermissionGuideHelper.openBluetoothScanningSettings(context)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "⚠️ 开启这些权限后，请返回应用继续操作",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("我已开启")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后设置")
            }
        }
    )
}

/**
 * 权限卡片组件
 */
@Composable
private fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }

            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.height(36.dp)
            ) {
                Text("去设置", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
