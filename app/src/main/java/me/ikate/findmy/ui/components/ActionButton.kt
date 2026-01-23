package me.ikate.findmy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.ikate.findmy.util.rememberHaptics

/**
 * 通用操作按钮组件
 * 用于联系人列表项的展开操作栏
 *
 * @param icon 按钮图标
 * @param label 按钮标签
 * @param enabled 是否可用
 * @param isDestructive 是否是破坏性操作（使用红色）
 * @param useConfirmHaptic 是否使用强确认震动（用于远程控制指令如播放声音）
 * @param onClick 点击回调
 */
@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    useConfirmHaptic: Boolean = false,
    onClick: () -> Unit
) {
    val haptics = rememberHaptics()
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isDestructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        isDestructive -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = {
                // 根据按钮类型选择震动反馈
                if (useConfirmHaptic) {
                    haptics.confirm()  // 远程控制指令：较强的"发送成功"震动
                } else {
                    haptics.click()  // 普通操作：清脆的点击感
                }
                onClick()
            })
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            tonalElevation = if (enabled) 3.dp else 0.dp,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = when {
                        enabled && isDestructive -> MaterialTheme.colorScheme.onErrorContainer
                        enabled -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = if (enabled) FontWeight.Medium else FontWeight.Normal
        )
    }
}
