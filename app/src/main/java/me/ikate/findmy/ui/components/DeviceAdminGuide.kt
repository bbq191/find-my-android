package me.ikate.findmy.ui.components

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.ikate.findmy.util.DeviceAdminHelper

/**
 * 设备管理员引导对话框
 *
 * 引导用户激活设备管理员权限以启用丢失模式的完整功能
 * 包括远程锁定和远程擦除数据
 */
@Composable
fun DeviceAdminGuideDialog(
    onDismiss: () -> Unit,
    onActivated: () -> Unit = {},
    onSkip: () -> Unit = {}
) {
    val context = LocalContext.current
    var isAdminActive by remember { mutableStateOf(DeviceAdminHelper.isAdminActive(context)) }

    val activationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isAdminActive = DeviceAdminHelper.isAdminActive(context)
        if (result.resultCode == Activity.RESULT_OK || isAdminActive) {
            onActivated()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isAdminActive)
                    Icons.Default.Check else Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = if (isAdminActive)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                text = "设备管理员",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isAdminActive) {
                    // 已激活状态
                    Text(
                        text = "设备管理员已激活，丢失模式完整功能可用。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 功能列表
                    FeatureCard(
                        icon = Icons.Default.Lock,
                        title = "远程锁定",
                        description = "可远程锁定设备屏幕",
                        enabled = true
                    )

                    FeatureCard(
                        icon = Icons.Default.DeleteForever,
                        title = "远程擦除",
                        description = "可远程恢复出厂设置",
                        enabled = true
                    )
                } else {
                    // 未激活状态
                    Text(
                        text = "启用设备管理员后，丢失模式可以：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 功能说明
                    FeatureCard(
                        icon = Icons.Default.Lock,
                        title = "远程锁定设备",
                        description = "防止他人使用您的手机",
                        enabled = false
                    )

                    FeatureCard(
                        icon = Icons.Default.DeleteForever,
                        title = "远程擦除数据",
                        description = "在无法找回时保护隐私",
                        enabled = false
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 警告提示
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "注意事项",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "激活后，需要在系统设置中取消激活才能卸载应用。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 激活按钮
                    Button(
                        onClick = {
                            val intent = DeviceAdminHelper.createActivationIntent(context)
                            activationLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("激活设备管理员")
                    }
                }
            }
        },
        confirmButton = {
            if (isAdminActive) {
                Button(onClick = onDismiss) {
                    Text("完成")
                }
            } else {
                TextButton(onClick = onSkip) {
                    Text("稍后再说")
                }
            }
        },
        dismissButton = if (!isAdminActive) {
            {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        } else null
    )
}

/**
 * 功能卡片组件
 */
@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (enabled) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 设备管理员设置入口卡片
 * 用于在设置页面显示当前状态和提供快捷入口
 */
@Composable
fun DeviceAdminSettingsCard(
    modifier: Modifier = Modifier,
    onActivateClick: () -> Unit,
    onDeactivateClick: () -> Unit
) {
    val context = LocalContext.current
    val isAdminActive = remember { DeviceAdminHelper.isAdminActive(context) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = if (isAdminActive)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "设备管理员",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isAdminActive) "已激活 - 支持远程锁定和擦除" else "未激活 - 部分功能受限",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isAdminActive) {
                OutlinedButton(onClick = onDeactivateClick) {
                    Text("管理")
                }
            } else {
                Button(onClick = onActivateClick) {
                    Text("激活")
                }
            }
        }
    }
}

/**
 * 远程擦除确认对话框
 * 执行远程擦除前的多重确认
 */
@Composable
fun WipeDataConfirmDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmStep by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                text = when (confirmStep) {
                    0 -> "远程擦除数据"
                    else -> "最终确认"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (confirmStep) {
                    0 -> {
                        Text(
                            text = "您即将远程擦除设备「$deviceName」的所有数据。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "⚠️ 此操作将：",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "• 删除设备上的所有应用和数据\n• 恢复出厂设置\n• 无法撤销或恢复",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = "请再次确认：您真的要擦除设备「$deviceName」的所有数据吗？\n\n此操作不可逆！",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (confirmStep == 0) {
                        confirmStep = 1
                    } else {
                        onConfirm()
                    }
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = when (confirmStep) {
                        0 -> "继续"
                        else -> "确认擦除"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
