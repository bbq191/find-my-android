package me.ikate.findmy.ui.screen.main.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.data.model.User
import me.ikate.findmy.ui.navigation.FindMyTab

/**
 * Tab内容切换器
 * 根据选中的Tab显示对应内容，带有切换动画
 */
@Composable
fun TabContent(
    selectedTab: FindMyTab,
    modifier: Modifier = Modifier,
    // People Tab 参数
    contacts: List<Contact>,
    requestingLocationFor: String?,
    trackingContactUid: String?,
    geofenceContactIds: Set<String>,
    onContactClick: (Contact) -> Unit,
    onAddContactClick: () -> Unit,
    onNavigate: (Contact) -> Unit,
    onBindContact: (Contact) -> Unit,
    onPauseShare: (Contact) -> Unit,
    onResumeShare: (Contact, ShareDuration) -> Unit,
    onRemoveContact: (Contact) -> Unit,
    onAcceptShare: (Contact) -> Unit,
    onRejectShare: (Contact) -> Unit,
    onRefreshAndTrack: (String) -> Unit,
    onPlaySound: (String) -> Unit,
    onStopSound: () -> Unit,
    isRinging: Boolean,
    ringingContactUid: String?,
    onLostModeClick: (Contact, String, String, Boolean) -> Unit,
    onGeofenceClick: (Contact) -> Unit,
    // Devices Tab 参数
    devices: List<Device>,
    currentDeviceId: String?,
    currentUserId: String?,
    currentDeviceAddress: String?,
    ringingDeviceId: String?,
    onDeviceClick: (Device) -> Unit,
    onNavigateToDevice: (Device) -> Unit,
    onPlaySoundOnDevice: (Device) -> Unit,
    onStopSoundOnDevice: () -> Unit,
    onLostModeOnDevice: (Device) -> Unit,
    // Me Tab 参数
    currentUser: User?,
    meName: String?,
    meAvatarUrl: String?,
    sharingWithCount: Int,
    onNameChange: (String) -> Unit,
    onAvatarChange: (String?) -> Unit
) {
    AnimatedContent(
        targetState = selectedTab,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(animationSpec = tween(150)) +
                    slideInVertically(
                        animationSpec = tween(150),
                        initialOffsetY = { it / 16 }
                    )).togetherWith(
                fadeOut(animationSpec = tween(100)) +
                        slideOutVertically(
                            animationSpec = tween(100),
                            targetOffsetY = { -it / 16 }
                        )
            )
        },
        label = "TabContentAnimation"
    ) { tab ->
        when (tab) {
            FindMyTab.PEOPLE -> PeopleTab(
                contacts = contacts,
                requestingLocationFor = requestingLocationFor,
                trackingContactUid = trackingContactUid,
                geofenceContactIds = geofenceContactIds,
                onContactClick = onContactClick,
                onAddContactClick = onAddContactClick,
                onNavigate = onNavigate,
                onBindContact = onBindContact,
                onPauseShare = onPauseShare,
                onResumeShare = onResumeShare,
                onRemoveContact = onRemoveContact,
                onAcceptShare = onAcceptShare,
                onRejectShare = onRejectShare,
                onRefreshAndTrack = onRefreshAndTrack,
                onPlaySound = onPlaySound,
                onStopSound = onStopSound,
                isRinging = isRinging,
                ringingContactUid = ringingContactUid,
                onLostModeClick = onLostModeClick,
                onGeofenceClick = onGeofenceClick
            )

            FindMyTab.DEVICES -> DevicesTab(
                devices = devices,
                currentDeviceId = currentDeviceId,
                currentUserId = currentUserId,
                currentDeviceAddress = currentDeviceAddress,
                ringingDeviceId = ringingDeviceId,
                onDeviceClick = onDeviceClick,
                onNavigate = onNavigateToDevice,
                onPlaySound = onPlaySoundOnDevice,
                onStopSound = onStopSoundOnDevice,
                onLostMode = onLostModeOnDevice
            )

            FindMyTab.HISTORY -> HistoryTab()

            FindMyTab.ME -> MeTab(
                currentUser = currentUser,
                meName = meName,
                meAvatarUrl = meAvatarUrl,
                sharingWithCount = sharingWithCount,
                onNameChange = onNameChange,
                onAvatarChange = onAvatarChange
            )
        }
    }
}
