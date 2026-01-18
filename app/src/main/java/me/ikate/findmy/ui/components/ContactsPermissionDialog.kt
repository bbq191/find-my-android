package me.ikate.findmy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 通讯录权限引导对话框
 * 当用户需要访问通讯录功能时显示
 */
@Composable
fun ContactsPermissionDialog(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Contacts,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "需要通讯录权限",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "此功能需要访问您的通讯录：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "• 从通讯录选择联系人头像",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• 导入系统联系人信息",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• 快速绑定联系人资料",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = "您也可以选择手动输入信息，跳过此权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRequestPermission) {
                Text("授权")
            }
        },
        dismissButton = {
            if (onOpenSettings != null) {
                TextButton(onClick = onOpenSettings) {
                    Text("前往设置")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("跳过")
                }
            }
        }
    )
}
