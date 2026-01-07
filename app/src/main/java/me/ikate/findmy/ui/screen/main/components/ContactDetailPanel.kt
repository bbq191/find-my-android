package me.ikate.findmy.ui.screen.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.data.model.ShareStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 联系人详情面板
 * 显示选中联系人的详细信息，包括地址和导航按钮
 *
 * @param contact 选中的联系人
 * @param address 联系人当前位置的地址（反向地理编码结果）
 * @param onClose 关闭详情回调
 * @param onNavigate 导航到联系人回调
 * @param modifier 修饰符
 */
@Composable
fun ContactDetailPanel(
    contact: Contact,
    address: String?,
    onClose: () -> Unit,
    onNavigate: (Contact) -> Unit,
    onAccept: (Contact) -> Unit = {},
    onReject: (Contact) -> Unit = {},
    onBindContact: (Contact) -> Unit = {},
    onPauseShare: (Contact) -> Unit = {},
    onResumeShare: (Contact, ShareDuration) -> Unit = { _, _ -> },
    onRemoveContact: (Contact) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 恢复共享弹窗状态
    var showResumeDialog by remember { mutableStateOf(false) }

    if (showResumeDialog) {
        ResumeShareDialog(
            onDismiss = { showResumeDialog = false },
            onConfirm = { duration ->
                onResumeShare(contact, duration)
                showResumeDialog = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // 顶部：关闭按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "联系人详情",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 联系人信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // 头像
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
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
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = contact.name,
                                modifier = Modifier.padding(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 名称
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 邮箱 (如果被移除了 email 字段，显示占位或 shareId)
                Text(
                    text = contact.email.ifBlank { "ID: ${contact.id.take(8)}..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 详细信息列表
                
                // 1. 地址
                if (address != null) {
                    ContactInfoRow(label = "位置", value = address)
                } else if (contact.location != null) {
                     ContactInfoRow(label = "坐标", value = formatLocation(contact))
                } else {
                    val locationText = if (contact.isPaused) "对方暂停了共享" else "位置不可用"
                    ContactInfoRow(label = "位置", value = locationText)
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                // 2. 最后更新
                if (contact.lastUpdateTime != null) {
                    ContactInfoRow(
                        label = "最后更新",
                        value = formatUpdateTime(contact.lastUpdateTime)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 3. 共享状态
                 val statusText = when (contact.shareStatus) {
                    ShareStatus.PENDING -> {
                        if (contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME) "邀请您查看位置"
                        else "等待对方接受"
                    }
                    ShareStatus.ACCEPTED -> {
                        if (contact.isPaused) "已暂停" else "正在共享"
                    }
                    ShareStatus.EXPIRED -> "已过期"
                    ShareStatus.REJECTED -> "已拒绝"
                }
                ContactInfoRow(label = "状态", value = statusText)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 操作按钮区域
        if (contact.shareStatus == ShareStatus.PENDING && 
            contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME) {
            
            // 接受/拒绝按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { onAccept(contact) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("接受共享")
                }

                androidx.compose.material3.OutlinedButton(
                    onClick = { onReject(contact) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("拒绝")
                }
            }

        } else {
            // 普通状态：显示功能按钮
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                
                // 1. 导航按钮 (仅当有位置时显示)
                if (contact.location != null && contact.isLocationAvailable && !contact.isPaused) {
                    Button(
                        onClick = { onNavigate(contact) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = "导航",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("导航到此处")
                    }
                }
                
                // 2. 暂停/恢复 共享按钮 (仅当我是发送者时)
                if (contact.shareDirection == ShareDirection.I_SHARE_TO_THEM || 
                    contact.shareDirection == ShareDirection.MUTUAL) {
                    
                    if (contact.isPaused) {
                        // 恢复共享
                        Button(
                            onClick = { showResumeDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "恢复",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("恢复位置共享")
                        }
                    } else {
                        // 暂停共享
                        androidx.compose.material3.OutlinedButton(
                            onClick = { onPauseShare(contact) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "暂停",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("临时暂停共享")
                        }
                    }
                }

                // 3. 绑定联系人按钮
                androidx.compose.material3.OutlinedButton(
                    onClick = { onBindContact(contact) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "绑定",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("绑定通讯录联系人")
                }

                // 4. 移除联系人 (Destructive Action)
                androidx.compose.material3.OutlinedButton(
                    onClick = { onRemoveContact(contact) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "移除",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("移除联系人 (停止共享)")
                }
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请选择恢复共享的时长",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
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
 * 信息行组件
 */
@Composable
private fun ContactInfoRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
             modifier = Modifier.weight(2f)
        )
    }
}

/**
 * 格式化位置信息
 */
private fun formatLocation(contact: Contact): String {
    return contact.location?.let {
        String.format("%.4f, %.4f", it.latitude, it.longitude)
    } ?: "未知"
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
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}