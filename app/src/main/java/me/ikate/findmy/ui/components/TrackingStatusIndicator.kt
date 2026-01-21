package me.ikate.findmy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ikate.findmy.domain.statemachine.LocationStateMachine
import me.ikate.findmy.ui.theme.FindMyBlue
import me.ikate.findmy.ui.theme.FindMyGreen
import me.ikate.findmy.ui.theme.FindMyOrange

/**
 * 实时追踪状态指示器
 *
 * 显示当前定位状态：
 * - IDLE: 静默守望模式（绿色）
 * - LIVE_TRACKING: 实时追踪模式（蓝色脉冲动画）
 *
 * 追踪模式下显示：
 * - 追踪时长
 * - 心跳状态
 */
@Composable
fun TrackingStatusIndicator(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stateMachine = remember { LocationStateMachine.getInstance(context) }

    val currentState by stateMachine.currentState.collectAsState()
    val trackingRequesterId by stateMachine.trackingRequesterId.collectAsState()

    // 只在追踪模式下显示
    AnimatedVisibility(
        visible = currentState == LocationStateMachine.LocationState.LIVE_TRACKING,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        TrackingStatusContent(
            requesterId = trackingRequesterId
        )
    }
}

@Composable
private fun TrackingStatusContent(
    requesterId: String?
) {
    // 脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = FindMyBlue.copy(alpha = 0.15f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 脉冲圆点
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .alpha(pulseAlpha)
                    .background(FindMyBlue, CircleShape)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 追踪图标
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = null,
                tint = FindMyBlue,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // 状态文字
            Text(
                text = "实时追踪中",
                color = FindMyBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 追踪状态徽章（紧凑版本）
 *
 * 用于在地图角落或列表项中显示
 */
@Composable
fun TrackingStatusBadge(
    isTracking: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isTracking) FindMyBlue else FindMyGreen,
        animationSpec = tween(300),
        label = "bgColor"
    )

    // 追踪模式脉冲
    val infiniteTransition = rememberInfiniteTransition(label = "badge_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isTracking) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size((8 * scale).dp)
            .background(backgroundColor, CircleShape)
    )
}

/**
 * 完整状态指示器（包含静默模式）
 *
 * 用于设置页面或状态详情
 */
@Composable
fun FullStatusIndicator(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stateMachine = remember { LocationStateMachine.getInstance(context) }

    val currentState by stateMachine.currentState.collectAsState()

    val (statusText, statusColor, icon) = when (currentState) {
        LocationStateMachine.LocationState.IDLE -> Triple(
            "静默守望",
            FindMyGreen,
            Icons.Default.LocationOn
        )
        LocationStateMachine.LocationState.LIVE_TRACKING -> Triple(
            "实时追踪",
            FindMyBlue,
            Icons.Default.MyLocation
        )
    }

    // 追踪模式脉冲
    val infiniteTransition = rememberInfiniteTransition(label = "full_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (currentState == LocationStateMachine.LocationState.LIVE_TRACKING) 0.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fullPulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = statusColor.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .alpha(pulseAlpha)
                    .background(statusColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 状态文字
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
