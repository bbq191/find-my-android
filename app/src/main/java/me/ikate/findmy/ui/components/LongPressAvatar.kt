package me.ikate.findmy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import me.ikate.findmy.util.rememberHaptics

/**
 * iOS Find My 风格可点击头像组件
 *
 * 核心特性：
 * - 单击触发：点击即刷新位置（iOS Find My 风格）
 * - 缩放反馈：按下时头像缩小到 0.95，释放时弹回
 * - 触觉震动：点击时 tick()
 * - 在线状态边框
 *
 * @param avatarUrl 头像图片URL
 * @param fallbackText 无头像时显示的文字（通常为名字首字母）
 * @param isOnline 是否在线（影响边框颜色）
 * @param isEnabled 是否启用点击交互
 * @param onClick 点击时的回调
 * @param size 头像大小
 */
@Composable
fun ClickableAvatar(
    avatarUrl: String?,
    fallbackText: String,
    isOnline: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val haptics = rememberHaptics()
    val density = LocalDensity.current

    // 交互状态
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 颜色配置
    val onlineColor = Color(0xFF4CAF50)  // 绿色 - 在线
    val offlineColor = MaterialTheme.colorScheme.outlineVariant  // 灰色 - 离线
    val borderColor = if (isOnline) onlineColor else offlineColor
    val borderWidth = 2.5.dp

    // 尺寸计算
    val avatarInnerSize = size - 6.dp  // 内部头像尺寸，留出边框空间
    val strokeWidthPx = with(density) { borderWidth.toPx() }

    // 缩放动画
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "avatarScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isEnabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null // 禁用默认涟漪效果
                    ) {
                        haptics.tick()
                        onClick()
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // 在线状态边框
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        color = borderColor,
                        style = Stroke(width = strokeWidthPx)
                    )
                }
        )

        // 头像内容
        Surface(
            modifier = Modifier.size(avatarInnerSize),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = fallbackText,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = fallbackText.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 静态头像组件（不可交互）
 * 用于 canTrack = false 的情况
 */
@Composable
fun StaticAvatar(
    avatarUrl: String?,
    fallbackText: String,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val density = LocalDensity.current

    // 颜色配置
    val onlineColor = Color(0xFF4CAF50)
    val offlineColor = MaterialTheme.colorScheme.outlineVariant
    val borderColor = if (isOnline) onlineColor else offlineColor
    val borderWidth = 2.5.dp
    val avatarInnerSize = size - 6.dp
    val strokeWidthPx = with(density) { borderWidth.toPx() }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // 边框
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        color = borderColor,
                        style = Stroke(width = strokeWidthPx)
                    )
                }
        )

        // 头像内容
        Surface(
            modifier = Modifier.size(avatarInnerSize),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = fallbackText,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = fallbackText.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
