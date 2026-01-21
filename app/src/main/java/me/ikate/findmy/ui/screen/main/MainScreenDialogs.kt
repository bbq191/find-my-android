package me.ikate.findmy.ui.screen.main

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.service.GeofenceManager
import me.ikate.findmy.ui.components.ContactsPermissionDialog
import me.ikate.findmy.ui.components.PermissionGuideDialog
import me.ikate.findmy.ui.dialog.GeofenceConfig
import me.ikate.findmy.ui.dialog.GeofenceDialog
import me.ikate.findmy.ui.dialog.LostModeAction
import me.ikate.findmy.ui.dialog.LostModeConfig
import me.ikate.findmy.ui.dialog.LostModeDialog
import me.ikate.findmy.ui.dialog.NavigationDialog
import me.ikate.findmy.ui.screen.contact.ContactViewModel
import me.ikate.findmy.ui.screen.main.components.ShareLocationDialog
import me.ikate.findmy.util.DistanceCalculator

/**
 * 位置共享对话框
 */
@Composable
fun ShareLocationDialogWrapper(
    showAddDialog: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    contactViewModel: ContactViewModel
) {
    if (showAddDialog) {
        ShareLocationDialog(
            isLoading = isLoading,
            errorMessage = errorMessage,
            onDismiss = {
                contactViewModel.clearError()
                contactViewModel.hideAddDialog()
            },
            onConfirm = { email, duration ->
                contactViewModel.shareLocation(email, duration)
            }
        )
    }
}

/**
 * 权限引导对话框
 */
@Composable
fun PermissionGuideDialogWrapper(
    showPermissionGuide: Boolean,
    missingPermissions: List<String>,
    contactViewModel: ContactViewModel
) {
    if (showPermissionGuide) {
        PermissionGuideDialog(
            missingPermissions = missingPermissions,
            onDismiss = {
                contactViewModel.dismissPermissionGuide()
            },
            onConfirm = {
                contactViewModel.onPermissionGranted()
            }
        )
    }
}

/**
 * 联系人导航对话框
 */
@Composable
fun ContactNavigationDialogWrapper(
    contactToNavigate: Contact?,
    myDevice: Device?,
    onDismiss: () -> Unit
) {
    contactToNavigate?.let { contact ->
        contact.location?.let { destination ->
            val distanceText = myDevice?.location?.let { myLoc ->
                DistanceCalculator.calculateAndFormatDistance(myLoc, destination)
            }

            NavigationDialog(
                contactName = contact.name,
                destination = destination,
                currentLocation = myDevice?.location,
                distanceText = distanceText,
                onDismiss = onDismiss
            )
        }
    }
}

/**
 * 设备导航对话框
 */
@Composable
fun DeviceNavigationDialogWrapper(
    deviceToNavigate: Device?,
    myDevice: Device?,
    onDismiss: () -> Unit
) {
    deviceToNavigate?.let { device ->
        val destination = device.location
        val distanceText = myDevice?.location?.let { myLoc ->
            DistanceCalculator.calculateAndFormatDistance(myLoc, destination)
        }

        NavigationDialog(
            contactName = device.customName ?: device.name,
            destination = destination,
            currentLocation = myDevice?.location,
            distanceText = distanceText,
            onDismiss = onDismiss
        )
    }
}

/**
 * 联系人丢失模式对话框
 */
@Composable
fun ContactLostModeDialogWrapper(
    contactForLostMode: Contact?,
    ringingContactUid: String?,
    contactViewModel: ContactViewModel,
    onDismiss: () -> Unit
) {
    contactForLostMode?.let { contact ->
        val isSoundPlaying = ringingContactUid == contact.targetUserId

        LostModeDialog(
            contactName = contact.name,
            currentConfig = LostModeConfig(
                enabled = false,
                isSoundPlaying = isSoundPlaying
            ),
            onDismiss = onDismiss,
            onAction = { action, config ->
                contact.targetUserId?.let { targetUid ->
                    when (action) {
                        LostModeAction.ENABLE -> {
                            contactViewModel.enableLostMode(
                                targetUid = targetUid,
                                message = config.message,
                                phoneNumber = config.phoneNumber,
                                playSound = config.playSound
                            )
                        }
                        LostModeAction.DISABLE -> {
                            contactViewModel.disableLostMode(targetUid)
                        }
                        LostModeAction.STOP_SOUND -> {
                            contactViewModel.requestStopSound(targetUid)
                        }
                    }
                }
                onDismiss()
            }
        )
    }
}

/**
 * 设备丢失模式对话框
 */
@Composable
fun DeviceLostModeDialogWrapper(
    deviceForLostMode: Device?,
    ringingDeviceId: String?,
    contactViewModel: ContactViewModel,
    onDismiss: () -> Unit,
    onStopSound: () -> Unit
) {
    deviceForLostMode?.let { device ->
        val isSoundPlaying = ringingDeviceId == device.id

        LostModeDialog(
            contactName = device.customName ?: device.name,
            currentConfig = LostModeConfig(
                enabled = false,
                isSoundPlaying = isSoundPlaying
            ),
            onDismiss = onDismiss,
            onAction = { action, config ->
                when (action) {
                    LostModeAction.ENABLE -> {
                        contactViewModel.enableLostMode(
                            targetUid = device.ownerId,
                            message = config.message,
                            phoneNumber = config.phoneNumber,
                            playSound = config.playSound
                        )
                    }
                    LostModeAction.DISABLE -> {
                        contactViewModel.disableLostMode(device.ownerId)
                    }
                    LostModeAction.STOP_SOUND -> {
                        contactViewModel.requestStopSound(device.ownerId)
                        onStopSound()
                    }
                }
                onDismiss()
            }
        )
    }
}

/**
 * 地理围栏对话框
 */
@Composable
fun GeofenceDialogWrapper(
    contactForGeofence: Contact?,
    geofenceManager: GeofenceManager,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit
) {
    contactForGeofence?.let { contact ->
        val existingGeofence = geofenceManager.getGeofenceForContact(contact.id)
        val currentConfig = if (existingGeofence != null) {
            GeofenceConfig(
                enabled = true,
                locationName = existingGeofence.locationName,
                center = contact.location,
                radiusMeters = existingGeofence.radiusMeters,
                notifyOnEnter = existingGeofence.notifyOnEnter,
                notifyOnExit = existingGeofence.notifyOnExit
            )
        } else {
            GeofenceConfig()
        }

        GeofenceDialog(
            contactName = contact.name,
            contactLocation = contact.location,
            currentConfig = currentConfig,
            onDismiss = onDismiss,
            onConfirm = { config ->
                if (config.enabled && config.center != null) {
                    geofenceManager.addGeofence(
                        contactId = contact.id,
                        contactName = contact.name,
                        locationName = config.locationName,
                        center = config.center,
                        radiusMeters = config.radiusMeters,
                        notifyOnEnter = config.notifyOnEnter,
                        notifyOnExit = config.notifyOnExit,
                        onSuccess = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "已为「${contact.name}」设置地理围栏"
                                )
                            }
                        },
                        onFailure = { errorMessage ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "设置地理围栏失败: $errorMessage"
                                )
                            }
                        }
                    )
                } else {
                    geofenceManager.removeGeofence(contact.id)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "已移除「${contact.name}」的地理围栏"
                        )
                    }
                }
                onDismiss()
            }
        )
    }
}

/**
 * 通讯录权限引导对话框
 */
@Composable
fun ContactsPermissionDialogWrapper(
    showContactsPermissionDialog: Boolean,
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    if (showContactsPermissionDialog) {
        ContactsPermissionDialog(
            onRequestPermission = onRequestPermission,
            onDismiss = onDismiss,
            onOpenSettings = if (isPermanentlyDenied) {
                {
                    onDismiss()
                    val intent = Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                }
            } else null
        )
    }
}
