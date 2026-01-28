package me.ikate.findmy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SignalCellularConnectedNoInternet4Bar
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ikate.findmy.domain.communication.CommunicationManager
import me.ikate.findmy.ui.theme.FindMyBlue
import me.ikate.findmy.ui.theme.FindMyGreen
import me.ikate.findmy.ui.theme.FindMyOrange
import me.ikate.findmy.ui.theme.FindMyRed

/**
 * 网络状态指示器
 *
 * 显示当前网络和连接状态：
 * - 网络类型（WiFi/蜂窝/无网络）
 * - MQTT 连接状态
 * - 离线消息数量
 * - 重连进度
 */
@Composable
fun NetworkStatusIndicator(
    modifier: Modifier = Modifier,
    showDetailed: Boolean = false
) {
    val context = LocalContext.current
    val communicationManager = remember { CommunicationManager.getInstance(context) }

    val connectionStatus by communicationManager.connectionStatus.collectAsState()
    val networkType by communicationManager.networkType.collectAsState()
    val pendingMessageCount by communicationManager.pendingMessageCount.collectAsState()
    val reconnectStats by communicationManager.reconnectStats.collectAsState()

    // 根据状态决定是否显示提示：
    // - 无网络时始终显示
    // - MQTT 连接失败或断开时显示
    // - MQTT 正在连接/重连中不显示（避免启动时闪烁红色 Banner）
    val shouldShowBanner = networkType == CommunicationManager.NetworkType.NONE ||
            connectionStatus == CommunicationManager.ConnectionStatus.FAILED ||
            connectionStatus == CommunicationManager.ConnectionStatus.RECONNECTING

    Column(modifier = modifier) {
        // 连接问题横幅
        AnimatedVisibility(
            visible = shouldShowBanner,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            NetworkStatusBanner(
                connectionStatus = connectionStatus,
                networkType = networkType,
                reconnectStats = reconnectStats,
                onRetryClick = { communicationManager.manualReconnect() }
            )
        }

        // 详细状态（可选）
        if (showDetailed) {
            Spacer(modifier = Modifier.height(8.dp))
            NetworkStatusDetailed(
                connectionStatus = connectionStatus,
                networkType = networkType,
                pendingMessageCount = pendingMessageCount,
                reconnectStats = reconnectStats
            )
        }
    }
}

/**
 * 网络状态横幅
 * 在连接问题时显示
 */
@Composable
private fun NetworkStatusBanner(
    connectionStatus: CommunicationManager.ConnectionStatus,
    networkType: CommunicationManager.NetworkType,
    reconnectStats: CommunicationManager.ReconnectStats,
    onRetryClick: () -> Unit
) {
    val (message, icon, color) = getStatusInfo(connectionStatus, networkType)

    // 重连中旋转动画
    val infiniteTransition = rememberInfiniteTransition(label = "reconnect")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Surface(
        shape = RoundedCornerShape(0.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(20.dp)
                    .then(
                        if (connectionStatus == CommunicationManager.ConnectionStatus.RECONNECTING) {
                            Modifier.rotate(rotation)
                        } else Modifier
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 状态文字
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    color = color,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                // 重连进度
                if (connectionStatus == CommunicationManager.ConnectionStatus.RECONNECTING) {
                    Text(
                        text = "第 ${reconnectStats.currentAttempt} 次尝试...",
                        color = color.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            // 重试按钮
            if (connectionStatus == CommunicationManager.ConnectionStatus.FAILED ||
                connectionStatus == CommunicationManager.ConnectionStatus.DISCONNECTED) {
                TextButton(onClick = onRetryClick) {
                    Text(
                        text = "重试",
                        color = color,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * 详细网络状态
 * 用于设置或调试页面
 */
@Composable
private fun NetworkStatusDetailed(
    connectionStatus: CommunicationManager.ConnectionStatus,
    networkType: CommunicationManager.NetworkType,
    pendingMessageCount: Int,
    reconnectStats: CommunicationManager.ReconnectStats
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "网络状态",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 网络类型
            StatusRow(
                label = "网络",
                value = getNetworkTypeName(networkType),
                icon = getNetworkIcon(networkType),
                color = if (networkType != CommunicationManager.NetworkType.NONE) FindMyGreen else FindMyRed
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 连接状态
            val (statusText, _, statusColor) = getStatusInfo(connectionStatus, networkType)
            StatusRow(
                label = "MQTT",
                value = getConnectionStatusName(connectionStatus),
                icon = getConnectionIcon(connectionStatus),
                color = statusColor
            )

            // 离线消息
            if (pendingMessageCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow(
                    label = "待发送",
                    value = "$pendingMessageCount 条消息",
                    icon = Icons.Default.Sync,
                    color = FindMyOrange
                )
            }

            // 重连统计
            if (reconnectStats.totalAttempts > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "重连统计: ${reconnectStats.successfulReconnects}/${reconnectStats.totalAttempts} 成功",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.width(60.dp)
        )

        Text(
            text = value,
            fontSize = 13.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 紧凑型网络状态徽章
 * 用于顶部栏或状态区域
 */
@Composable
fun NetworkStatusBadge(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val communicationManager = remember { CommunicationManager.getInstance(context) }

    val connectionStatus by communicationManager.connectionStatus.collectAsState()
    val networkType by communicationManager.networkType.collectAsState()

    val color by animateColorAsState(
        targetValue = when {
            networkType == CommunicationManager.NetworkType.NONE -> FindMyRed
            connectionStatus == CommunicationManager.ConnectionStatus.CONNECTED -> FindMyGreen
            connectionStatus == CommunicationManager.ConnectionStatus.RECONNECTING -> FindMyOrange
            else -> FindMyRed
        },
        animationSpec = tween(300),
        label = "badgeColor"
    )

    // 重连中脉冲
    val infiniteTransition = rememberInfiniteTransition(label = "badge_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (connectionStatus == CommunicationManager.ConnectionStatus.RECONNECTING) 0.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgeAlpha"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 网络图标
        Icon(
            imageVector = getNetworkIcon(networkType),
            contentDescription = null,
            tint = color.copy(alpha = alpha),
            modifier = Modifier.size(16.dp)
        )

        // 连接状态点
        if (connectionStatus != CommunicationManager.ConnectionStatus.CONNECTED) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color.copy(alpha = alpha), CircleShape)
            )
        }
    }
}

// 辅助函数

private fun getStatusInfo(
    connectionStatus: CommunicationManager.ConnectionStatus,
    networkType: CommunicationManager.NetworkType
): Triple<String, ImageVector, Color> {
    return when {
        networkType == CommunicationManager.NetworkType.NONE -> Triple(
            "无网络连接",
            Icons.Default.SignalWifiOff,
            FindMyRed
        )
        connectionStatus == CommunicationManager.ConnectionStatus.CONNECTED -> Triple(
            "已连接",
            Icons.Default.Sync,
            FindMyGreen
        )
        connectionStatus == CommunicationManager.ConnectionStatus.CONNECTING -> Triple(
            "正在连接...",
            Icons.Default.Sync,
            FindMyBlue
        )
        connectionStatus == CommunicationManager.ConnectionStatus.RECONNECTING -> Triple(
            "正在重连...",
            Icons.Default.Refresh,
            FindMyOrange
        )
        connectionStatus == CommunicationManager.ConnectionStatus.FAILED -> Triple(
            "连接失败",
            Icons.Default.SyncProblem,
            FindMyRed
        )
        else -> Triple(
            "未连接",
            Icons.Default.SyncDisabled,
            FindMyRed
        )
    }
}

private fun getNetworkTypeName(networkType: CommunicationManager.NetworkType): String {
    return when (networkType) {
        CommunicationManager.NetworkType.WIFI -> "WiFi"
        CommunicationManager.NetworkType.CELLULAR -> "蜂窝网络"
        CommunicationManager.NetworkType.OTHER -> "其他网络"
        CommunicationManager.NetworkType.NONE -> "无网络"
    }
}

private fun getNetworkIcon(networkType: CommunicationManager.NetworkType): ImageVector {
    return when (networkType) {
        CommunicationManager.NetworkType.WIFI -> Icons.Default.SignalWifi4Bar
        CommunicationManager.NetworkType.CELLULAR -> Icons.Default.SignalCellular4Bar
        CommunicationManager.NetworkType.OTHER -> Icons.Default.SignalCellular4Bar
        CommunicationManager.NetworkType.NONE -> Icons.Default.SignalWifiOff
    }
}

private fun getConnectionStatusName(status: CommunicationManager.ConnectionStatus): String {
    return when (status) {
        CommunicationManager.ConnectionStatus.CONNECTED -> "已连接"
        CommunicationManager.ConnectionStatus.CONNECTING -> "连接中"
        CommunicationManager.ConnectionStatus.RECONNECTING -> "重连中"
        CommunicationManager.ConnectionStatus.DISCONNECTED -> "已断开"
        CommunicationManager.ConnectionStatus.FAILED -> "连接失败"
    }
}

private fun getConnectionIcon(status: CommunicationManager.ConnectionStatus): ImageVector {
    return when (status) {
        CommunicationManager.ConnectionStatus.CONNECTED -> Icons.Default.Sync
        CommunicationManager.ConnectionStatus.CONNECTING -> Icons.Default.Sync
        CommunicationManager.ConnectionStatus.RECONNECTING -> Icons.Default.Refresh
        CommunicationManager.ConnectionStatus.DISCONNECTED -> Icons.Default.SyncDisabled
        CommunicationManager.ConnectionStatus.FAILED -> Icons.Default.SyncProblem
    }
}
