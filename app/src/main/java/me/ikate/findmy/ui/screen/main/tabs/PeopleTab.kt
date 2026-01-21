package me.ikate.findmy.ui.screen.main.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.data.model.ShareStatus
import me.ikate.findmy.service.TrackingState
import me.ikate.findmy.ui.dialog.FindDeviceDialog
import me.ikate.findmy.ui.dialog.RemoveContactDialog
import me.ikate.findmy.ui.dialog.ResumeShareDialog
import me.ikate.findmy.ui.screen.main.components.ContactListItem

/**
 * 联系人 Tab
 * 显示位置共享的联系人列表
 */
@Composable
fun PeopleTab(
    contacts: List<Contact>,
    requestingLocationFor: String? = null,
    trackingContactUid: String? = null,
    trackingStates: Map<String, TrackingState> = emptyMap(),
    geofenceContactIds: Set<String> = emptySet(),
    onContactClick: (Contact) -> Unit,
    onAddContactClick: () -> Unit,
    onNavigate: (Contact) -> Unit = {},
    onBindContact: (Contact) -> Unit = {},
    onPauseShare: (Contact) -> Unit = {},
    onResumeShare: (Contact, ShareDuration) -> Unit = { _, _ -> },
    onRemoveContact: (Contact) -> Unit = {},
    onAcceptShare: (Contact) -> Unit = {},
    onRejectShare: (Contact) -> Unit = {},
    onRefreshAndTrack: (String) -> Unit = {},
    onStopTracking: (String) -> Unit = {},
    onPlaySound: (String) -> Unit = {},
    onStopSound: () -> Unit = {},
    isRinging: Boolean = false,
    ringingContactUid: String? = null,
    onLostModeClick: (Contact, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    onGeofenceClick: (Contact) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expandedContactId by remember { mutableStateOf<String?>(null) }
    var contactToResume by remember { mutableStateOf<Contact?>(null) }
    var contactToRemove by remember { mutableStateOf<Contact?>(null) }
    var contactForFindDevice by remember { mutableStateOf<Contact?>(null) }

    // Dialogs
    contactToResume?.let { contact ->
        ResumeShareDialog(
            onDismiss = { contactToResume = null },
            onConfirm = { duration ->
                onResumeShare(contact, duration)
                contactToResume = null
            }
        )
    }

    contactToRemove?.let { contact ->
        RemoveContactDialog(
            contact = contact,
            onDismiss = { contactToRemove = null },
            onConfirm = {
                onRemoveContact(contact)
                contactToRemove = null
            }
        )
    }

    // FindDeviceDialog (合并响铃和丢失模式)
    contactForFindDevice?.let { contact ->
        val contactIsRinging = ringingContactUid == contact.targetUserId
        FindDeviceDialog(
            contact = contact,
            isRinging = contactIsRinging,
            onDismiss = { contactForFindDevice = null },
            onPlaySound = {
                contact.targetUserId?.let { onPlaySound(it) }
            },
            onStopSound = onStopSound,
            onEnableLostMode = { message, phone, playSound ->
                onLostModeClick(contact, message, phone, playSound)
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // iOS 风格大标题 + 添加按钮
        PeopleHeader(
            contactCount = contacts.size,
            onAddClick = onAddContactClick
        )

        // 联系人列表
        if (contacts.isEmpty()) {
            EmptyPeopleState()
        } else {
            // 筛选出待处理的好友请求
            val pendingRequests = contacts.filter { contact ->
                contact.shareStatus == ShareStatus.PENDING &&
                contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 好友请求 Banner（置顶显示）
                if (pendingRequests.isNotEmpty()) {
                    item {
                        FriendRequestBanner(
                            requests = pendingRequests,
                            onAccept = { onAcceptShare(it) },
                            onReject = { onRejectShare(it) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                itemsIndexed(
                    items = contacts,
                    key = { _, contact -> contact.id }
                ) { _, contact ->
                    val isExpanded = expandedContactId == contact.id
                    val isRequesting = requestingLocationFor == contact.targetUserId
                    val isTracking = trackingContactUid == contact.targetUserId
                    val contactTrackingState = contact.targetUserId?.let {
                        trackingStates[it]
                    } ?: TrackingState.IDLE

                    ContactListItem(
                        contact = contact,
                        myDevice = null,
                        isExpanded = isExpanded,
                        isRequestingLocation = isRequesting,
                        isTracking = isTracking,
                        trackingState = contactTrackingState,
                        onClick = {
                            // 点击卡片：定位到地图
                            onContactClick(contact)
                        },
                        onAvatarClick = {
                            // 点击头像：开始追踪
                            contact.targetUserId?.let { onRefreshAndTrack(it) }
                        },
                        onExpandClick = {
                            // 点击展开按钮（已废弃，保留兼容）
                            expandedContactId = if (expandedContactId == contact.id) null else contact.id
                        },
                        onStopTracking = {
                            contact.targetUserId?.let { onStopTracking(it) }
                        },
                        onFindDeviceClick = {
                            // 打开查找设备对话框
                            contactForFindDevice = contact
                        },
                        onNavigate = { onNavigate(contact) },
                        onPauseClick = { onPauseShare(contact) },
                        onResumeClick = { contactToResume = contact },
                        onBindClick = { onBindContact(contact) },
                        onRemoveClick = { contactToRemove = contact },
                        onAcceptClick = { onAcceptShare(contact) },
                        onRejectClick = { onRejectShare(contact) },
                        onPlaySound = {
                            contact.targetUserId?.let { onPlaySound(it) }
                        },
                        onStopSound = onStopSound,
                        isRinging = ringingContactUid == contact.targetUserId,
                        onGeofenceClick = { onGeofenceClick(contact) },
                        hasGeofence = contact.id in geofenceContactIds
                    )
                }

                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }
}

@Composable
private fun PeopleHeader(
    contactCount: Int,
    onAddClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "联系人",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (contactCount > 0) {
                    Text(
                        text = "$contactCount 位联系人共享位置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FloatingActionButton(
                onClick = onAddClick,
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 4.dp
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "添加联系人",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyPeopleState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Groups,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = "还没有联系人",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "点击右上角 + 邀请好友\n共享位置信息",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 好友请求 Banner
 * 当有待处理的好友请求时显示在列表顶部
 */
@Composable
private fun FriendRequestBanner(
    requests: List<Contact>,
    onAccept: (Contact) -> Unit,
    onReject: (Contact) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = requests.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${requests.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "好友请求",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 请求列表（最多显示 3 个）
                requests.take(3).forEach { contact ->
                    FriendRequestItem(
                        contact = contact,
                        onAccept = { onAccept(contact) },
                        onReject = { onReject(contact) }
                    )
                }

                // 更多提示
                if (requests.size > 3) {
                    Text(
                        text = "还有 ${requests.size - 3} 个请求...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendRequestItem(
    contact: Contact,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!contact.avatarUrl.isNullOrBlank()) {
                        coil3.compose.AsyncImage(
                            model = contact.avatarUrl,
                            contentDescription = contact.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 名字和提示
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "想要共享位置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 操作按钮
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onReject,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("拒绝", style = MaterialTheme.typography.labelMedium)
                }

                FilledTonalButton(
                    onClick = onAccept,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("接受", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

