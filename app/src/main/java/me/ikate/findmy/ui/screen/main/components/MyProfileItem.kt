package me.ikate.findmy.ui.screen.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.DeviceType
import me.ikate.findmy.data.model.User
import me.ikate.findmy.util.TimeFormatter

/**
 * 当前用户/设备列表项组件
 */
@Composable
fun MyProfileItem(
    user: User,
    meName: String?,
    meAvatarUrl: String?,
    device: Device?,
    address: String? = null,
    onClick: () -> Unit
) {
    // 自动刷新时间显示
    LaunchedEffect(user.uid) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            System.currentTimeMillis()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            ProfileAvatar(
                avatarUrl = meAvatarUrl,
                device = device,
                meName = meName
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 信息部分
            ProfileInfo(
                meName = meName,
                device = device,
                address = address
            )
        }
    }
}

@Composable
private fun ProfileAvatar(
    avatarUrl: String?,
    device: Device?,
    meName: String?
) {
    Surface(
        modifier = Modifier.size(64.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 3.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Avatar",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else if (device != null) {
                Icon(
                    imageVector = when (device.deviceType) {
                        DeviceType.PHONE -> Icons.Default.Phone
                        DeviceType.TABLET -> Icons.Default.Tablet
                        DeviceType.WATCH -> Icons.Default.Watch
                        else -> Icons.Default.Phone
                    },
                    contentDescription = device.name,
                    modifier = Modifier.padding(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                Text(
                    text = (meName ?: "我").take(1).uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ProfileInfo(
    meName: String?,
    device: Device?,
    address: String?
) {
    Column(verticalArrangement = Arrangement.Center) {
        // 标题
        val title = device?.customName ?: meName ?: "我"
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )

        if (device != null) {
            DeviceStatusRow(device = device)
            AddressRow(address = address)
        } else {
            Text(
                text = "正在共享我的位置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun DeviceStatusRow(device: Device) {
    val isOnline = TimeFormatter.isOnline(device.lastUpdateTime)
    val onlineText = if (isOnline) "在线" else "离线"
    val onlineColor = if (isOnline) MaterialTheme.colorScheme.primary else Color.Gray
    val timeText = TimeFormatter.formatUpdateTime(device.lastUpdateTime)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${device.name} • ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = onlineText,
            style = MaterialTheme.typography.bodySmall,
            color = onlineColor
        )
        Text(
            text = " • $timeText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        // 电量指示
        if (device.battery < 100) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.BatteryFull,
                contentDescription = "电量",
                modifier = Modifier.size(14.dp),
                tint = if (device.battery < 20) Color.Red else Color.Gray
            )
            Text(
                text = "${device.battery}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (device.battery < 20) Color.Red else Color.Gray
            )
        }
    }
}

@Composable
private fun AddressRow(address: String?) {
    Text(
        text = address ?: "位置未知",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
