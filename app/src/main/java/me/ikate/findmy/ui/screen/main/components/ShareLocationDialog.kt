package me.ikate.findmy.ui.screen.main.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.ui.theme.FindMyShapes
import me.ikate.findmy.util.rememberHaptics

/**
 * 位置共享底部面板 (Modal Bottom Sheet)
 *
 * 设计参考 ADD_CONTACT_UI.md：
 * - 从居中弹窗改为底部面板，单手操作更便捷
 * - 扫码图标快速添加联系人
 * - 剪贴板检测自动填充 UID
 * - Material 3 Segmented Button 选择共享时长
 * - 全宽主按钮，触觉反馈
 *
 * @param isLoading 是否正在发送
 * @param errorMessage 错误消息
 * @param onDismiss 关闭面板回调
 * @param onConfirm 确认回调 (uid, duration)
 * @param onScanQrCode 扫码回调（可选，用于打开扫码界面）
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLocationDialog(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    initialUid: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (uid: String, duration: ShareDuration) -> Unit,
    onScanQrCode: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var targetUid by remember { mutableStateOf(initialUid ?: "") }

    // 当 initialUid 变化时更新 targetUid（例如扫码返回）
    LaunchedEffect(initialUid) {
        if (!initialUid.isNullOrBlank()) {
            targetUid = initialUid
        }
    }
    var selectedDuration by remember { mutableStateOf(ShareDuration.INDEFINITELY) }
    var localError by remember { mutableStateOf<String?>(null) }

    // 剪贴板检测
    var clipboardContent by remember { mutableStateOf<String?>(null) }
    var showClipboardHint by remember { mutableStateOf(false) }

    // 检测剪贴板内容
    LaunchedEffect(Unit) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()
            if (text != null && isValidUidFormat(text)) {
                clipboardContent = text
                showClipboardHint = true
            }
        }
    }

    // 自动聚焦输入框
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
            // 忽略聚焦失败
        }
    }

    // 当外部 errorMessage 变化时，更新显示
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            localError = errorMessage
            haptics.error()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isLoading) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = FindMyShapes.BottomSheetTop,
        dragHandle = {
            // 标准拖拽手柄
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 36.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 标题
            Text(
                text = "添加联系人",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // UID 输入区域
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = targetUid,
                    onValueChange = {
                        targetUid = it
                        localError = null
                        // 输入后隐藏剪贴板提示
                        if (it.isNotEmpty()) {
                            showClipboardHint = false
                        }
                    },
                    label = { Text("输入对方 UID 或 邮箱") },
                    placeholder = { Text("粘贴对方的 UID") },
                    singleLine = true,
                    isError = localError != null,
                    supportingText = if (localError != null) {
                        { Text(localError!!, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                haptics.click()
                                onScanQrCode?.invoke()
                            },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "扫描二维码",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                // 剪贴板提示
                AnimatedVisibility(
                    visible = showClipboardHint && clipboardContent != null && targetUid.isEmpty(),
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 }
                ) {
                    ClipboardHintChip(
                        content = clipboardContent ?: "",
                        onClick = {
                            haptics.click()
                            targetUid = clipboardContent ?: ""
                            showClipboardHint = false
                        }
                    )
                }
            }

            // 共享时长选择
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "共享位置时长",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ShareDuration.entries.forEachIndexed { index, duration ->
                        SegmentedButton(
                            selected = selectedDuration == duration,
                            onClick = {
                                if (!isLoading) {
                                    haptics.click()
                                    selectedDuration = duration
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ShareDuration.entries.size
                            ),
                            enabled = !isLoading,
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            icon = {
                                Icon(
                                    imageVector = duration.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        ) {
                            Text(
                                text = duration.shortName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedDuration == duration)
                                    FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 发送邀请按钮（全宽）
            Button(
                onClick = {
                    when {
                        targetUid.isBlank() -> {
                            localError = "请输入对方的 UID"
                            haptics.error()
                        }
                        else -> {
                            haptics.confirm()
                            keyboardController?.hide()
                            onConfirm(targetUid.trim(), selectedDuration)
                        }
                    }
                },
                enabled = !isLoading && targetUid.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "发送中...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "发送位置共享邀请",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 剪贴板提示 Chip
 */
@Composable
private fun ClipboardHintChip(
    content: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "点击粘贴：${content.take(16)}${if (content.length > 16) "..." else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 验证 UID 格式（简单验证：8位以上字母数字组合）
 */
private fun isValidUidFormat(text: String): Boolean {
    val trimmed = text.trim()
    // UID 格式：至少 8 位，只包含字母、数字、下划线、横线
    // 或者是邮箱格式
    return trimmed.length >= 8 &&
            (trimmed.matches(Regex("^[a-zA-Z0-9_-]{8,}$")) ||
                    trimmed.contains("@"))
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

/**
 * ShareDuration 扩展属性 - 图标
 */
private val ShareDuration.icon: ImageVector
    get() = when (this) {
        ShareDuration.ONE_HOUR -> Icons.Default.Schedule
        ShareDuration.END_OF_DAY -> Icons.Default.WbSunny
        ShareDuration.INDEFINITELY -> Icons.Default.AllInclusive
    }
