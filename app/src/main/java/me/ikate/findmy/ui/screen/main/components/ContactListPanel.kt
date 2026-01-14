package me.ikate.findmy.ui.screen.main.components

/**
 * 联系人列表面板
 * 显示所有位置共享的联系人，包括当前用户自己
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.data.model.User
import me.ikate.findmy.ui.dialog.AppIconSelectionDialog
import me.ikate.findmy.ui.dialog.MyUidDialog
import me.ikate.findmy.ui.dialog.RemoveContactDialog
import me.ikate.findmy.ui.dialog.ResumeShareDialog
import me.ikate.findmy.util.AppIconHelper

@Composable
fun ContactListPanel(
    currentUser: User?,
    meName: String? = null,
    meAvatarUrl: String? = null,
    myDevice: Device? = null,
    myAddress: String? = null,
    contacts: List<Contact>,
    requestingLocationFor: String? = null,
    trackingContactUid: String? = null,
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
    onLostModeClick: (Contact) -> Unit = {},
    onGeofenceClick: (Contact) -> Unit = {},
    geofenceContactIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    // Dialog states
    var showUidDialog by remember { mutableStateOf(false) }
    var showIconDialog by remember { mutableStateOf(false) }
    var contactToResume by remember { mutableStateOf<Contact?>(null) }
    var contactToRemove by remember { mutableStateOf<Contact?>(null) }
    var expandedContactId by remember { mutableStateOf<String?>(null) }

    // Dialogs
    DialogHost(
        currentUser = currentUser,
        showUidDialog = showUidDialog,
        showIconDialog = showIconDialog,
        contactToResume = contactToResume,
        contactToRemove = contactToRemove,
        onDismissUidDialog = { showUidDialog = false },
        onDismissIconDialog = { showIconDialog = false },
        onDismissResumeDialog = { contactToResume = null },
        onDismissRemoveDialog = { contactToRemove = null },
        onResumeShare = { duration ->
            contactToResume?.let { onResumeShare(it, duration) }
            contactToResume = null
        },
        onConfirmRemove = {
            contactToRemove?.let { onRemoveContact(it) }
            contactToRemove = null
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 标题栏
        HeaderSection(
            contactCount = contacts.size,
            onSettingsClick = { showIconDialog = true },
            onAddClick = onAddContactClick
        )

        // "我"的 Profile
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

        // 联系人列表
        ContactList(
            contacts = contacts,
            myDevice = myDevice,
            expandedContactId = expandedContactId,
            requestingLocationFor = requestingLocationFor,
            trackingContactUid = trackingContactUid,
            onContactClick = { contact ->
                onContactClick(contact)
                expandedContactId = if (expandedContactId == contact.id) null else contact.id
            },
            onNavigate = onNavigate,
            onPauseShare = onPauseShare,
            onResumeClick = { contactToResume = it },
            onBindContact = onBindContact,
            onRemoveClick = { contactToRemove = it },
            onAcceptShare = onAcceptShare,
            onRejectShare = onRejectShare,
            onRequestLocationUpdate = onRequestLocationUpdate,
            onStartContinuousTracking = onStartContinuousTracking,
            onStopContinuousTracking = onStopContinuousTracking,
            onPlaySound = onPlaySound,
            onLostModeClick = onLostModeClick,
            onGeofenceClick = onGeofenceClick,
            geofenceContactIds = geofenceContactIds
        )
    }
}

@Composable
private fun DialogHost(
    currentUser: User?,
    showUidDialog: Boolean,
    showIconDialog: Boolean,
    contactToResume: Contact?,
    contactToRemove: Contact?,
    onDismissUidDialog: () -> Unit,
    onDismissIconDialog: () -> Unit,
    onDismissResumeDialog: () -> Unit,
    onDismissRemoveDialog: () -> Unit,
    onResumeShare: (ShareDuration) -> Unit,
    onConfirmRemove: () -> Unit
) {
    if (showUidDialog && currentUser != null) {
        MyUidDialog(
            uid = currentUser.uid,
            onDismiss = onDismissUidDialog
        )
    }

    if (showIconDialog) {
        val context = LocalContext.current
        AppIconSelectionDialog(
            currentIcon = AppIconHelper.getCurrentIcon(context),
            onDismiss = onDismissIconDialog,
            onIconSelected = {
                AppIconHelper.setIcon(context, it)
                onDismissIconDialog()
            }
        )
    }

    if (contactToResume != null) {
        ResumeShareDialog(
            onDismiss = onDismissResumeDialog,
            onConfirm = onResumeShare
        )
    }

    if (contactToRemove != null) {
        RemoveContactDialog(
            contact = contactToRemove,
            onDismiss = onDismissRemoveDialog,
            onConfirm = onConfirmRemove
        )
    }
}

@Composable
private fun HeaderSection(
    contactCount: Int,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "联系人",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (contactCount == 0) "暂无联系人" else "$contactCount 位联系人",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "设置图标",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FloatingActionButton(
                    onClick = onAddClick,
                    modifier = Modifier.size(56.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
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
}

@Composable
private fun SectionHeader(title: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun ContactList(
    contacts: List<Contact>,
    myDevice: Device?,
    expandedContactId: String?,
    requestingLocationFor: String?,
    trackingContactUid: String?,
    onContactClick: (Contact) -> Unit,
    onNavigate: (Contact) -> Unit,
    onPauseShare: (Contact) -> Unit,
    onResumeClick: (Contact) -> Unit,
    onBindContact: (Contact) -> Unit,
    onRemoveClick: (Contact) -> Unit,
    onAcceptShare: (Contact) -> Unit,
    onRejectShare: (Contact) -> Unit,
    onRequestLocationUpdate: (String) -> Unit,
    onStartContinuousTracking: (String) -> Unit,
    onStopContinuousTracking: (String) -> Unit,
    onPlaySound: (String) -> Unit,
    onLostModeClick: (Contact) -> Unit,
    onGeofenceClick: (Contact) -> Unit,
    geofenceContactIds: Set<String>
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (contacts.isNotEmpty()) {
            item { SectionHeader(title = "联系人") }

            items(contacts, key = { it.id }) { contact ->
                val isExpanded = expandedContactId == contact.id
                val isRequesting = requestingLocationFor == contact.targetUserId
                val isTracking = trackingContactUid == contact.targetUserId

                ContactListItem(
                    contact = contact,
                    myDevice = myDevice,
                    isExpanded = isExpanded,
                    isRequestingLocation = isRequesting,
                    isTracking = isTracking,
                    onClick = { onContactClick(contact) },
                    onNavigate = { onNavigate(contact) },
                    onPauseClick = { onPauseShare(contact) },
                    onResumeClick = { onResumeClick(contact) },
                    onBindClick = { onBindContact(contact) },
                    onRemoveClick = { onRemoveClick(contact) },
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
                    onLostModeClick = {
                        onLostModeClick(contact)
                    },
                    onGeofenceClick = {
                        onGeofenceClick(contact)
                    },
                    hasGeofence = contact.id in geofenceContactIds
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
            item { EmptyState() }
        }

        item { Spacer(modifier = Modifier.padding(bottom = 24.dp)) }
    }
}

@Composable
private fun EmptyState() {
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
