package me.ikate.findmy.ui.screen.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.ikate.findmy.data.model.ShareDuration

/**
 * 位置共享对话框
 * 用于发起位置共享，输入对方邮箱并选择共享时长
 *
 * @param isLoading 是否正在发送
 * @param errorMessage 错误消息
 * @param onDismiss 关闭对话框回调
 * @param onConfirm 确认回调 (email, duration)
 * @param modifier 修饰符
 */
@Composable
fun ShareLocationDialog(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (email: String, duration: ShareDuration) -> Unit,
    modifier: Modifier = Modifier
) {
    var targetUid by remember { mutableStateOf("") }
    var selectedDuration by remember { mutableStateOf(ShareDuration.INDEFINITELY) }
    var localError by remember { mutableStateOf<String?>(null) }

    // 当外部 errorMessage 变化时，更新显示
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            localError = errorMessage
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("共享我的位置") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // UID 输入
                OutlinedTextField(
                    value = targetUid,
                    onValueChange = {
                        targetUid = it
                        localError = null  // 清除错误
                    },
                    label = { Text("对方 UID") },
                    placeholder = { Text("请输入对方的用户 ID") },
                    singleLine = true,
                    isError = localError != null,
                    supportingText = localError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                // 时长选择
                Text(
                    text = "共享时长",
                    style = MaterialTheme.typography.bodyMedium
                )

                ShareDuration.values().forEach { duration ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedDuration == duration,
                                onClick = { if (!isLoading) selectedDuration = duration },
                                enabled = !isLoading
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedDuration == duration,
                            onClick = { if (!isLoading) selectedDuration = duration },
                            enabled = !isLoading
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = duration.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // UID 验证
                    when {
                        targetUid.isBlank() -> {
                            localError = "请输入对方 UID"
                        }
                        else -> {
                            onConfirm(targetUid.trim(), selectedDuration)
                        }
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("发送")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("取消")
            }
        },
        modifier = modifier
    )
}
