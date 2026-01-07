package me.ikate.findmy.ui.screen.main.components

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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.DeviceType
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.data.model.ShareStatus
import me.ikate.findmy.data.model.User

/**
 * 联系人列表面板
 * 显示所有位置共享的联系人，包括当前用户自己
 */
@Composable
fun ContactListPanel(
    currentUser: User?,
    meName: String? = null,
    meAvatarUrl: String? = null,
    myDevice: Device? = null,
    myAddress: String? = null,
    contacts: List<Contact>,
    onContactClick: (Contact) -> Unit,
    onAddContactClick: () -> Unit,
    onPauseShare: (Contact) -> Unit = {},
    onResumeShare: (Contact, ShareDuration) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var showUidDialog by remember { mutableStateOf(false) }
    var contactToResume by remember { mutableStateOf<Contact?>(null) }

    if (showUidDialog && currentUser != null) {
        MyUidDialog(
            uid = currentUser.uid,
            onDismiss = { showUidDialog = false }
        )
    }

    if (contactToResume != null) {
        ResumeShareDialog(
            onDismiss = { contactToResume = null },
            onConfirm = { duration ->
                contactToResume?.let { onResumeShare(it, duration) }
                contactToResume = null
            }
        )
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // 标题和添加按钮
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

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. "我" 的部分
            if (currentUser != null) {
                item {
                    SectionHeader(title = "我")
                }
                item {
                    MyProfileItem(
                        user = currentUser,
                        meName = meName,
                        meAvatarUrl = meAvatarUrl,
                        device = myDevice,
                        address = myAddress,
                        onClick = { showUidDialog = true }
                    )
                }
            }

            // 2. "联系人" 部分
            if (contacts.isNotEmpty()) {
                item {
                    SectionHeader(title = "联系人")
                }
                items(contacts, key = { it.id }) { contact ->
                    ContactListItem(
                        contact = contact,
                        onClick = { onContactClick(contact) },
                        onPauseClick = { onPauseShare(contact) },
                        onResumeClick = { contactToResume = contact }
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
            
            // 底部留白，防止被圆角遮挡
            item { 
                Spacer(modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
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
                
                ShareDuration.values().forEach { duration ->
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onConfirm(duration) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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
                    modifier = Modifier.fillMaxWidth().clickable {
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
    var refreshTrigger by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(user.uid) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            refreshTrigger = System.currentTimeMillis()
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
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
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
                val timeText = formatUpdateTime(device.lastUpdateTime, refreshTrigger)
                
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
 * 联系人列表项
 */
@Composable
private fun ContactListItem(
    contact: Contact,
    onClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 自动刷新时间显示（每分钟更新一次）
    var refreshTrigger by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(contact.id) {
        while (true) {
            kotlinx.coroutines.delay(60_000) // 每60秒刷新一次
            refreshTrigger = System.currentTimeMillis()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
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
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            // 状态文本
            val (statusText, statusColor) = when (contact.shareStatus) {
                ShareStatus.PENDING -> {
                    val text = if (contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME) "邀请您查看位置" else "等待对方接受..."
                    text to MaterialTheme.colorScheme.primary
                }
                ShareStatus.ACCEPTED -> {
                    if (contact.isPaused) {
                        "已暂停共享" to MaterialTheme.colorScheme.error
                    } else if (contact.isLocationAvailable && contact.lastUpdateTime != null) {
                        // 显示最后更新时间
                        formatUpdateTime(contact.lastUpdateTime, refreshTrigger) to MaterialTheme.colorScheme.secondary
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

        // 快速操作按钮 (仅当我是发送者时)
        if (contact.shareStatus == ShareStatus.ACCEPTED && 
           (contact.shareDirection == ShareDirection.I_SHARE_TO_THEM || contact.shareDirection == ShareDirection.MUTUAL)) {
            IconButton(onClick = { 
                if (contact.isPaused) onResumeClick() else onPauseClick()
            }) {
                Icon(
                    imageVector = if (contact.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (contact.isPaused) "恢复" else "暂停",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 格式化更新时间
 */
private fun formatUpdateTime(timestamp: Long, trigger: Long = 0): String {
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

/**
 * 匿名用户提示组件
 */
@Composable
fun AnonymousUserPrompt(
    onSignUpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.Gray
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = "位置共享功能需要注册账号",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.size(16.dp))
        androidx.compose.material3.Button(
            onClick = onSignUpClick
        ) { 
            Text("去注册") 
        }
    }
}