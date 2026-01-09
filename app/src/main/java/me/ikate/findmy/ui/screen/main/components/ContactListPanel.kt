package me.ikate.findmy.ui.screen.main.components

/**
 * 联系人列表面板
 * 显示所有位置共享的联系人，包括当前用户自己
 */
import android.location.Geocoder
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.DeviceType
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.data.model.ShareStatus
import me.ikate.findmy.data.model.User
import me.ikate.findmy.util.AddressFormatter
import me.ikate.findmy.util.ShareHelper

// ... existing imports ...

@Composable
fun ContactListPanel(
    currentUser: User?,
    meName: String? = null,
    meAvatarUrl: String? = null,
    myDevice: Device? = null,
    myAddress: String? = null,
    contacts: List<Contact>,
    requestingLocationFor: String? = null,
    onContactClick: (Contact) -> Unit,
    onAddContactClick: () -> Unit,
    onNavigate: (Contact) -> Unit = {},
    onBindContact: (Contact) -> Unit = {},
    onPauseShare: (Contact) -> Unit = {},
    onResumeShare: (Contact, ShareDuration) -> Unit = { _, _ -> },
    onRemoveContact: (Contact) -> Unit = {},
    onAcceptShare: (Contact) -> Unit = {},
    onRejectShare: (Contact) -> Unit = {},
    onRequestLocationUpdate: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showUidDialog by remember { mutableStateOf(false) }
    var contactToResume by remember { mutableStateOf<Contact?>(null) }
    var contactToRemove by remember { mutableStateOf<Contact?>(null) } // 移除确认弹窗
    // 记录当前展开的联系人 ID
    var expandedContactId by remember { mutableStateOf<String?>(null) }

    if (showUidDialog && currentUser != null) {
        MyUidDialog(
            uid = currentUser.uid,
            onDismiss = { showUidDialog = false }
        )
    }

    if (contactToResume != null) {
        ResumeShareDialog(
            onDismiss = { contactToResume = null },
            onConfirm = { duration: ShareDuration ->
                contactToResume?.let { onResumeShare(it, duration) }
                contactToResume = null
            }
        )
    }

    if (contactToRemove != null) {
        RemoveContactDialog(
            contact = contactToRemove!!,
            onDismiss = { contactToRemove = null },
            onConfirm = {
                contactToRemove?.let { onRemoveContact(it) }
                contactToRemove = null
            }
        )
    }

    Column(modifier = modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)) {
        // ... existing header ...
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "联系人",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            FloatingActionButton(
                onClick = onAddContactClick,
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加联系人")
            }
        }

        // 固定的"我"section
        if (currentUser != null) {
            SectionHeader(title = "我")
            MyProfileItem(
                user = currentUser,
                meName = meName,
                meAvatarUrl = meAvatarUrl,
                device = myDevice,
                address = myAddress,
                onClick = { showUidDialog = true }
            )
        }

        // 可滚动的联系人列表
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // 2. "联系人" 部分
            if (contacts.isNotEmpty()) {
                item {
                    SectionHeader(title = "联系人")
                }
                items(contacts, key = { it.id }) { contact ->
                    val isExpanded = expandedContactId == contact.id
                    val isRequesting = requestingLocationFor == contact.targetUserId

                    ContactListItem(
                        contact = contact,
                        isExpanded = isExpanded,
                        isRequestingLocation = isRequesting,
                        onClick = {
                            onContactClick(contact)
                            // 切换展开状态
                            expandedContactId = if (isExpanded) null else contact.id
                        },
                        onNavigate = { onNavigate(contact) },
                        onPauseClick = { onPauseShare(contact) },
                        onResumeClick = { contactToResume = contact },
                        onBindClick = { onBindContact(contact) },
                        onRemoveClick = { contactToRemove = contact },
                        onAcceptClick = { onAcceptShare(contact) },
                        onRejectClick = { onRejectShare(contact) },
                        onRequestLocationUpdate = {
                            contact.targetUserId?.let {
                                onRequestLocationUpdate(
                                    it
                                )
                            }
                        }
                    )
                    if (contacts.last() != contact) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp, end = 20.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            } else {
// ... existing empty state ...
                // 空状态
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无联系人\n点击右上角 + 添加位置共享",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 底部留白
            item {
                Spacer(modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

/**
 * 移除联系人确认对话框
 */
@Composable
private fun RemoveContactDialog(
    contact: Contact,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移除联系人") },
        text = {
            Text("确定要移除 ${contact.name} 吗？\n这将停止与对方的所有位置共享。")
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("移除")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 恢复共享时长选择对话框
 */
@Composable
private fun ResumeShareDialog(
    onDismiss: () -> Unit,
    onConfirm: (ShareDuration) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "恢复共享",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                ShareDuration.entries.forEach { duration ->
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onConfirm(duration) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(duration.displayName)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                androidx.compose.material3.TextButton(
                    onClick = onDismiss
                ) {
                    Text("取消")
                }
            }
        }
    }
}

/**
 * 显示我的 UID 对话框
 */
@Composable
private fun MyUidDialog(
    uid: String,
    onDismiss: () -> Unit
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("我的 UID") },
        text = {
            Column {
                Text(
                    text = "分享此 ID 给好友，让他们添加您。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.size(16.dp))
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // 复制到剪贴板
                            clipboardManager.setText(AnnotatedString(uid))
                        }
                ) {
                    Text(
                        text = uid,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "点击 ID 复制",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    // 使用通用分享工具分享 UID
                    ShareHelper.shareUid(context, uid)
                }
            ) {
                Text("分享")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(top = 8.dp)
    )
}

/**
 * 当前用户/设备列表项
 */
@Composable
private fun MyProfileItem(
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像部分保持不变
        val avatarUrl = meAvatarUrl

        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
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

        Spacer(modifier = Modifier.width(16.dp))

        // 信息部分优化
        Column(
            verticalArrangement = Arrangement.Center
        ) {
            // 第一行：显示设备自定义名称 (如果为空则显示 "我")
            // 优先顺序: 设备自定义名称 > 用户昵称 > "我"
            val title = device?.customName ?: meName ?: "我"

            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            if (device != null) {
                // 判断在线状态 (5分钟内更新视为在线)
                val isOnline = (System.currentTimeMillis() - device.lastUpdateTime) < 5 * 60 * 1000
                val onlineText = if (isOnline) "在线" else "离线"
                val onlineColor = if (isOnline) MaterialTheme.colorScheme.primary else Color.Gray

                // 第二行：设备型号 • 在线状态 • 更新时间
                // 示例: "Xiaomi 14 • 在线 • 刚刚"
                val modelPart = device.name
                val timeText = formatUpdateTime(device.lastUpdateTime)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$modelPart • ",
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

                // 第三行：去除国家省市及其分割逗号后的精简地址
                // 示例: "中关村大街1号"
                val addressText = address ?: "位置未知"

                Text(
                    text = addressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "正在共享我的位置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

/**
 * 联系人列表项 (支持点击展开操作栏)
 */
@Composable
private fun ContactListItem(
    contact: Contact,
    isExpanded: Boolean,
    isRequestingLocation: Boolean = false,
    onClick: () -> Unit,
    onNavigate: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onBindClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onAcceptClick: () -> Unit,
    onRejectClick: () -> Unit,
    onRequestLocationUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // ... existing time and address logic ...
    // 自动刷新时间显示（每分钟更新一次）
    val context = LocalContext.current
    var addressText by remember { mutableStateOf<String?>(null) } // 初始为null，加载中

    LaunchedEffect(contact.id) {
        while (true) {
            kotlinx.coroutines.delay(60_000) // 每60秒刷新一次
        }
    }

    // 获取地址
    LaunchedEffect(contact.location) {
        if (contact.location == null) {
            addressText = null
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, java.util.Locale.getDefault())
                    geocoder.getFromLocation(
                        contact.location.latitude,
                        contact.location.longitude,
                        1
                    ) { addresses ->
                        if (addresses.isNotEmpty()) {
                            addressText = AddressFormatter.formatAddress(addresses[0])
                        } else {
                            addressText = "位置未知"
                        }
                    }
                } else {
                    addressText = "无法获取地址"
                }
            } catch (_: Exception) {
                addressText = "获取地址失败"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isExpanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.Transparent)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!contact.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(contact.avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = contact.name,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = contact.name.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 联系人信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 第一行：联系人姓名
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )

                // 状态逻辑处理
                if (contact.shareStatus == ShareStatus.ACCEPTED && !contact.isPaused && contact.isLocationAvailable) {
                    // 如果正在请求位置更新，显示"正在定位..."
                    if (isRequestingLocation) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "正在定位...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        // 第二行：设备型号 • 在线状态 • 更新时间
                        val isOnline = (System.currentTimeMillis() - (contact.lastUpdateTime
                            ?: 0L)) < 5 * 60 * 1000
                        val onlineText = if (isOnline) "在线" else "离线"
                        val onlineColor =
                            if (isOnline) MaterialTheme.colorScheme.primary else Color.Gray
                        val timeText = formatUpdateTime(contact.lastUpdateTime ?: 0L)
                        val deviceName = contact.deviceName ?: "未知设备"

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$deviceName • ",
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
                            if (contact.battery != null && contact.battery < 100) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.BatteryFull,
                                    contentDescription = "电量",
                                    modifier = Modifier.size(14.dp),
                                    tint = if (contact.battery < 20) Color.Red else Color.Gray
                                )
                                Text(
                                    text = "${contact.battery}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (contact.battery < 20) Color.Red else Color.Gray
                                )
                            }
                        }
                    }

                    // 第三行：精简地址
                    Text(
                        text = addressText ?: "正在获取位置...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                } else {
                    // 如果是 PENDING, EXPIRED, REJECTED, PAUSED 或 LOCATION UNAVAILABLE
                    val (statusText, statusColor) = when (contact.shareStatus) {
                        ShareStatus.PENDING -> {
                            val text =
                                if (contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME) "邀请您查看位置" else "等待对方接受..."
                            text to MaterialTheme.colorScheme.primary
                        }

                        ShareStatus.ACCEPTED -> {
                            if (contact.isPaused) {
                                "已暂停共享" to MaterialTheme.colorScheme.error
                            } else {
                                "位置不可用" to MaterialTheme.colorScheme.secondary
                            }
                        }

                        ShareStatus.EXPIRED -> "已过期" to MaterialTheme.colorScheme.error
                        ShareStatus.REJECTED -> "已拒绝" to MaterialTheme.colorScheme.error
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }
            }

            // 快速操作指示器 (接受/拒绝按钮)
            // 如果是 PENDING 且需要我接受，显示接受和拒绝按钮
            if (contact.shareStatus == ShareStatus.PENDING && contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 拒绝按钮
                    androidx.compose.material3.OutlinedButton(
                        onClick = onRejectClick,
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("拒绝", style = MaterialTheme.typography.labelMedium)
                    }

                    // 接受按钮
                    androidx.compose.material3.Button(
                        onClick = onAcceptClick,
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("接受", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // 展开的操作栏
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 1. 刷新位置 (仅当联系人有共享且不在暂停状态时可用)
                val canRefresh = contact.shareStatus == ShareStatus.ACCEPTED &&
                        !contact.isPaused &&
                        (contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME ||
                                contact.shareDirection == ShareDirection.MUTUAL)
                ActionButton(
                    icon = Icons.Default.Refresh,
                    label = "刷新",
                    enabled = canRefresh && !isRequestingLocation,
                    onClick = onRequestLocationUpdate
                )

                // 2. 导航 (仅当有位置时可用)
                val canNavigate = contact.location != null && contact.isLocationAvailable
                ActionButton(
                    icon = Icons.Default.Directions,
                    label = "导航",
                    enabled = canNavigate,
                    onClick = onNavigate
                )

                // 3. 暂停/恢复 (仅当我是发送者时可用)
                val canControlShare = contact.shareDirection == ShareDirection.I_SHARE_TO_THEM ||
                        contact.shareDirection == ShareDirection.MUTUAL

                if (contact.isPaused) {
                    ActionButton(
                        icon = Icons.Default.PlayArrow,
                        label = "恢复",
                        enabled = canControlShare,
                        onClick = onResumeClick
                    )
                } else {
                    ActionButton(
                        icon = Icons.Default.Pause,
                        label = "暂停",
                        enabled = canControlShare,
                        onClick = onPauseClick
                    )
                }

                // 4. 绑定
                ActionButton(
                    icon = Icons.Default.Person,
                    label = "绑定",
                    onClick = onBindClick
                )

                // 5. 删除/拒绝
                val isPendingRequest =
                    contact.shareStatus == ShareStatus.PENDING && contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME
                if (isPendingRequest) {
                    ActionButton(
                        icon = Icons.Default.Delete,
                        label = "拒绝",
                        isDestructive = true,
                        onClick = onRejectClick
                    )
                } else {
                    ActionButton(
                        icon = Icons.Default.Delete,
                        label = "移除",
                        isDestructive = true,
                        onClick = onRemoveClick
                    )
                }
            }
        }
    }
}

/**
 * 操作按钮组件
 */
@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor =
        if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val containerColor =
        if (isDestructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (enabled) containerColor else Color.Gray.copy(alpha = 0.1f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (enabled) contentColor else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) contentColor else Color.Gray
        )
    }
}

/**
 * 格式化更新时间
 */
private fun formatUpdateTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        else -> {
            val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestamp))
        }
    }
}