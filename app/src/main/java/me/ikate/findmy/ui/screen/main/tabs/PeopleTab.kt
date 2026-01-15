package me.ikate.findmy.ui.screen.main.tabs

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Groups
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
import me.ikate.findmy.data.model.ShareDuration
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
    onRequestLocationUpdate: (String) -> Unit = {},
    onStartContinuousTracking: (String) -> Unit = {},
    onStopContinuousTracking: (String) -> Unit = {},
    onPlaySound: (String) -> Unit = {},
    onStopSound: () -> Unit = {},
    isRinging: Boolean = false,
    onLostModeClick: (Contact) -> Unit = {},
    onGeofenceClick: (Contact) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expandedContactId by remember { mutableStateOf<String?>(null) }
    var contactToResume by remember { mutableStateOf<Contact?>(null) }
    var contactToRemove by remember { mutableStateOf<Contact?>(null) }

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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                itemsIndexed(
                    items = contacts,
                    key = { _, contact -> contact.id }
                ) { _, contact ->
                    val isExpanded = expandedContactId == contact.id
                    val isRequesting = requestingLocationFor == contact.targetUserId
                    val isTracking = trackingContactUid == contact.targetUserId

                    ContactListItem(
                        contact = contact,
                        myDevice = null,
                        isExpanded = isExpanded,
                        isRequestingLocation = isRequesting,
                        isTracking = isTracking,
                        onClick = {
                            onContactClick(contact)
                            expandedContactId = if (expandedContactId == contact.id) null else contact.id
                        },
                        onNavigate = { onNavigate(contact) },
                        onPauseClick = { onPauseShare(contact) },
                        onResumeClick = { contactToResume = contact },
                        onBindClick = { onBindContact(contact) },
                        onRemoveClick = { contactToRemove = contact },
                        onAcceptClick = { onAcceptShare(contact) },
                        onRejectClick = { onRejectShare(contact) },
                        onRequestLocationUpdate = {
                            contact.targetUserId?.let { onRequestLocationUpdate(it) }
                        },
                        onStartContinuousTracking = {
                            contact.targetUserId?.let { onStartContinuousTracking(it) }
                        },
                        onStopContinuousTracking = {
                            contact.targetUserId?.let { onStopContinuousTracking(it) }
                        },
                        onPlaySound = {
                            contact.targetUserId?.let { onPlaySound(it) }
                        },
                        onStopSound = onStopSound,
                        isRinging = isRinging,
                        onLostModeClick = { onLostModeClick(contact) },
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

