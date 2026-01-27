package me.ikate.findmy.ui.screen.main

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.GeofenceType
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.service.GeofenceManager
import me.ikate.findmy.ui.components.ContactsPermissionDialog
import me.ikate.findmy.ui.components.PermissionGuideDialog
import me.ikate.findmy.ui.screen.geofence.GeofenceConfig
import me.ikate.findmy.ui.screen.geofence.GeofenceEditorScreen
import me.ikate.findmy.ui.screen.geofence.NotificationType
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
 * 地理围栏编辑器 (iOS Find My 风格)
 * 使用全屏地图底座 + 悬浮控制面板架构
 */
@Composable
fun GeofenceDialogWrapper(
    contactForGeofence: Contact?,
    myLocation: LatLng?,
    geofenceManager: GeofenceManager,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit
) {
    contactForGeofence?.let { contact ->
        // 使用 targetUserId 查询围栏，与创建时保持一致
        val geofenceContactId = contact.targetUserId ?: contact.id
        val existingGeofence = geofenceManager.getGeofenceForContact(geofenceContactId)
        val currentConfig = if (existingGeofence != null) {
            GeofenceConfig(
                enabled = true,
                locationName = existingGeofence.locationName,
                address = existingGeofence.address,
                center = contact.location,
                radiusMeters = existingGeofence.radiusMeters,
                notificationType = when {
                    existingGeofence.geofenceType == GeofenceType.LEFT_BEHIND -> NotificationType.LEFT_BEHIND
                    existingGeofence.notifyOnExit && !existingGeofence.notifyOnEnter -> NotificationType.LEAVE
                    else -> NotificationType.ARRIVE
                },
                isOneTime = existingGeofence.isOneTime,
                geofenceType = existingGeofence.geofenceType,
                ownerLocation = if (existingGeofence.ownerLatitude != null && existingGeofence.ownerLongitude != null) {
                    LatLng(existingGeofence.ownerLatitude, existingGeofence.ownerLongitude)
                } else null,
                wasInsideOnCreate = existingGeofence.wasInsideOnCreate
            )
        } else {
            GeofenceConfig()
        }

        GeofenceEditorScreen(
            contactName = contact.name,
            contactLocation = contact.location,
            myLocation = myLocation,
            currentConfig = currentConfig,
            onDismiss = onDismiss,
            onConfirm = { config ->
                // 使用 targetUserId 而非 contact.id（共享记录ID）
                // 因为位置消息中的 userId 与 targetUserId 对应
                val targetId = contact.targetUserId ?: contact.id
                if (config.enabled && config.center != null) {
                    // 确定通知类型
                    val notifyOnEnter = config.notificationType == NotificationType.ARRIVE
                    val notifyOnExit = config.notificationType == NotificationType.LEAVE ||
                            config.notificationType == NotificationType.LEFT_BEHIND

                    geofenceManager.addGeofence(
                        contactId = targetId,
                        contactName = contact.name,
                        locationName = config.locationName,
                        center = config.center,
                        radiusMeters = config.radiusMeters,
                        notifyOnEnter = notifyOnEnter,
                        notifyOnExit = notifyOnExit,
                        geofenceType = config.geofenceType,
                        address = config.address,
                        wasInsideOnCreate = config.wasInsideOnCreate,
                        ownerLocation = config.ownerLocation,
                        isOneTime = config.isOneTime,
                        onSuccess = {
                            scope.launch {
                                val message = when (config.notificationType) {
                                    NotificationType.ARRIVE -> "已设置「${contact.name}」到达通知"
                                    NotificationType.LEAVE -> "已设置「${contact.name}」离开通知"
                                    NotificationType.LEFT_BEHIND -> "已设置「${contact.name}」离开我身边通知"
                                }
                                snackbarHostState.showSnackbar(message = message)
                            }
                        },
                        onFailure = { errorMessage ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "设置位置通知失败: $errorMessage"
                                )
                            }
                        }
                    )
                } else {
                    geofenceManager.removeGeofence(targetId)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "已移除「${contact.name}」的位置通知"
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
