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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 丢失模式配置数据
 */
data class LostModeConfig(
    val enabled: Boolean = false,
    val message: String = "",
    val phoneNumber: String = "",
    val playSound: Boolean = true,
    val isSoundPlaying: Boolean = false  // 当前是否正在播放声音
)

/**
 * 丢失模式操作类型
 */
enum class LostModeAction {
    ENABLE,         // 启用丢失模式
    DISABLE,        // 关闭丢失模式
    STOP_SOUND      // 仅停止提示音
}

/**
 * 丢失模式对话框
 * 用于配置丢失模式的消息、电话号码等信息
 */
@Composable
fun LostModeDialog(
    contactName: String,
    currentConfig: LostModeConfig = LostModeConfig(),
    onDismiss: () -> Unit,
    onAction: (LostModeAction, LostModeConfig) -> Unit
) {
    var message by remember { mutableStateOf(currentConfig.message) }
    var phoneNumber by remember { mutableStateOf(currentConfig.phoneNumber) }
    var playSound by remember { mutableStateOf(currentConfig.playSound) }

    val isEnabled = currentConfig.enabled
    val isSoundPlaying = currentConfig.isSoundPlaying

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = if (isEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (isEnabled) "丢失模式管理" else "启用丢失模式",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (isEnabled) {
                    // 当前处于丢失模式，显示管理选项
                    Text(
                        text = "「$contactName」当前处于丢失模式。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isSoundPlaying) {
                        Text(
                            text = "提示音正在播放中，你可以选择停止提示音或完全关闭丢失模式。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        Text(
                            text = "关闭丢失模式后，设备将恢复正常状态，不再显示丢失消息。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    // 启用丢失模式的配置表单
                    Text(
                        text = "启用丢失模式后，设备屏幕将显示您的联系信息，方便拾到者联系您。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 显示消息
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("显示消息") },
                        placeholder = { Text("此设备已丢失，请联系机主") },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Message,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        singleLine = false
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 联系电话
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("联系电话") },
                        placeholder = { Text("输入您的电话号码") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 播放声音开关
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "播放提示音",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "启用后设备会持续播放提示音",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = playSound,
                            onCheckedChange = { playSound = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isEnabled) {
                // 已启用状态：显示关闭按钮
                TextButton(
                    onClick = {
                        onAction(LostModeAction.DISABLE, LostModeConfig(enabled = false))
                    }
                ) {
                    Text("关闭丢失模式", color = MaterialTheme.colorScheme.error)
                }
            } else {
                // 未启用状态：显示启用按钮
                TextButton(
                    onClick = {
                        onAction(
                            LostModeAction.ENABLE,
                            LostModeConfig(
                                enabled = true,
                                message = message.ifBlank { "此设备已丢失，请联系机主" },
                                phoneNumber = phoneNumber,
                                playSound = playSound
                            )
                        )
                    }
                ) {
                    Text("启用丢失模式")
                }
            }
        },
        dismissButton = {
            Row {
                // 如果正在播放声音，显示停止提示音按钮
                if (isEnabled && isSoundPlaying) {
                    TextButton(
                        onClick = {
                            onAction(LostModeAction.STOP_SOUND, currentConfig)
                        }
                    ) {
                        Text("停止提示音", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}
