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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareStatus

/**
 * 联系人列表面板
 * 显示所有位置共享的联系人
 *
 * @param contacts 联系人列表
 * @param onContactClick 联系人点击回调
 * @param onAddContactClick 添加联系人按钮点击回调
 * @param modifier 修饰符
 */
@Composable
fun ContactListPanel(
    contacts: List<Contact>,
    onContactClick: (Contact) -> Unit,
    onAddContactClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 标题和添加按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "联系人",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            FloatingActionButton(
                onClick = onAddContactClick,
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加联系人")
            }
        }

        // 联系人列表
        if (contacts.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无联系人\n点击右上角 + 添加位置共享",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(contacts, key = { it.id }) { contact ->
                    ContactListItem(
                        contact = contact,
                        onClick = { onContactClick(contact) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = Color.Gray.copy(alpha = 0.2f)
                    )
                }
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = contact.name,
                modifier = Modifier.padding(12.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 联系人信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            // 状态文本
            val statusText = when (contact.shareStatus) {
                ShareStatus.PENDING -> {
                    when (contact.shareDirection) {
                        ShareDirection.I_SHARE_TO_THEM -> "等待对方接受..."
                        ShareDirection.THEY_SHARE_TO_ME -> "邀请您查看位置"
                        ShareDirection.MUTUAL -> "等待对方接受..."
                    }
                }
                ShareStatus.ACCEPTED -> {
                    if (contact.isLocationAvailable && contact.lastUpdateTime != null) {
                        // 显示最后更新时间
                        formatUpdateTime(contact.lastUpdateTime, refreshTrigger)
                    } else if (contact.shareDirection == ShareDirection.I_SHARE_TO_THEM) {
                        "已分享"
                    } else {
                        "位置不可用"
                    }
                }
                ShareStatus.EXPIRED -> "已过期"
                ShareStatus.REJECTED -> "已拒绝"
            }

            val statusColor = when (contact.shareStatus) {
                ShareStatus.PENDING -> MaterialTheme.colorScheme.primary
                ShareStatus.ACCEPTED -> Color.Gray
                ShareStatus.EXPIRED -> Color.Red
                ShareStatus.REJECTED -> Color.Red
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor
            )
        }

        // 共享方向指示
        val directionText = when (contact.shareDirection) {
            ShareDirection.I_SHARE_TO_THEM -> "→"
            ShareDirection.THEY_SHARE_TO_ME -> "←"
            ShareDirection.MUTUAL -> "↔"
        }

        Text(
            text = directionText,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Gray
        )
    }
}

/**
 * 格式化更新时间
 * 规则:
 * - 1分钟内: 刚刚
 * - 1小时内: X分钟前
 * - 今天: X小时前
 * - 其他: 具体时间
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
