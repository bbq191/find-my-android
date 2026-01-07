package me.ikate.findmy.ui.screen.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.ShareDirection
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
    modifier: Modifier = Modifier
) {
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
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = contact.name,
                        modifier = Modifier.padding(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 名称
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 邮箱
                Text(
                    text = contact.email,
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
                    ContactInfoRow(label = "位置", value = "位置不可用")
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
                    ShareStatus.ACCEPTED -> "已接受"
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

        } else if (contact.location != null && contact.isLocationAvailable) {
            // 导航按钮 (仅当有位置时显示)
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
