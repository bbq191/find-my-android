package me.ikate.findmy.ui.screen.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.ikate.findmy.data.model.ShareDuration

/**
 * 位置共享对话框（紧凑版）
 * 用于发起位置共享，输入对方 UID 并选择共享时长
 *
 * @param isLoading 是否正在发送
 * @param errorMessage 错误消息
 * @param onDismiss 关闭对话框回调
 * @param onConfirm 确认回调 (uid, duration)
 * @param modifier 修饰符
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareLocationDialog(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (uid: String, duration: ShareDuration) -> Unit,
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
        title = {
            Text(
                "添加联系人",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // UID 输入
                OutlinedTextField(
                    value = targetUid,
                    onValueChange = {
                        targetUid = it
                        localError = null
                    },
                    label = { Text("对方 UID") },
                    placeholder = { Text("粘贴对方的 UID") },
                    singleLine = true,
                    isError = localError != null,
                    supportingText = if (localError != null) {
                        { Text(localError!!, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 时长选择（水平 Chips）
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "共享时长",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShareDuration.entries.forEach { duration ->
                            FilterChip(
                                selected = selectedDuration == duration,
                                onClick = { if (!isLoading) selectedDuration = duration },
                                label = {
                                    Text(
                                        text = duration.shortName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selectedDuration == duration)
                                            FontWeight.Medium else FontWeight.Normal
                                    )
                                },
                                enabled = !isLoading,
                                shape = RoundedCornerShape(8.dp),
                                border = if (selectedDuration == duration) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = false,
                                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        targetUid.isBlank() -> localError = "请输入对方的 UID"
                        else -> onConfirm(targetUid.trim(), selectedDuration)
                    }
                },
                enabled = !isLoading && targetUid.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("发送中")
                } else {
                    Text("发送邀请", fontWeight = FontWeight.SemiBold)
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
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    )
}

/**
 * ShareDuration 扩展属性 - 短名称
 */
private val ShareDuration.shortName: String
    get() = when (this) {
        ShareDuration.ONE_HOUR -> "1小时"
        ShareDuration.END_OF_DAY -> "今天"
        ShareDuration.INDEFINITELY -> "始终"
    }
