package me.ikate.findmy.ui.screen.main

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.ui.components.PermissionGuideDialog
import me.ikate.findmy.ui.dialog.GeofenceConfig
import me.ikate.findmy.ui.dialog.GeofenceDialog
import me.ikate.findmy.ui.dialog.LostModeConfig
import me.ikate.findmy.ui.dialog.LostModeDialog
import me.ikate.findmy.ui.dialog.NavigationDialog
import me.ikate.findmy.service.GeofenceManager
import me.ikate.findmy.ui.screen.contact.ContactViewModel
import me.ikate.findmy.util.DistanceCalculator
import me.ikate.findmy.ui.screen.main.components.ContactListPanel
import me.ikate.findmy.ui.screen.main.components.CustomBottomSheet
import me.ikate.findmy.ui.screen.main.components.LocationButton
import me.ikate.findmy.ui.screen.main.components.MapLayerButton
import me.ikate.findmy.ui.screen.main.components.MapViewWrapper
import me.ikate.findmy.ui.screen.main.components.ShareLocationDialog
import me.ikate.findmy.ui.screen.main.components.SheetValue
import me.ikate.findmy.util.CompassHelper
import me.ikate.findmy.util.MapCameraHelper

/**
 * 主屏幕 - Find My 应用的主界面
 * 包含全屏地图视图和交互组件
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    contactViewModel: ContactViewModel = viewModel()
) {
    val context = LocalContext.current

    // 定位权限状态（增量请求：先粗略定位，再精确定位，加通讯录权限）
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CONTACTS
        )
    )

    // 收集 ViewModel 状态
    val googleMap by viewModel.googleMap.collectAsState()
    val isLocationCentered by viewModel.isLocationCentered.collectAsState()
    val devices by viewModel.devices.collectAsState() // 收集设备列表

    // 收集联系人 ViewModel 状态
    val contacts by contactViewModel.contacts.collectAsState()
    val currentUser by contactViewModel.currentUser.collectAsState() // 收集当前用户
    val meName by contactViewModel.meName.collectAsState() // "我"的本地显示名称
    val meAvatarUrl by contactViewModel.meAvatarUrl.collectAsState() // "我"的本地显示头像
    val myDevice by contactViewModel.myDevice.collectAsState() // 收集当前设备
    val myAddress by contactViewModel.myAddress.collectAsState() // 收集当前设备地址
    val showAddDialog by contactViewModel.showAddDialog.collectAsState()
    val isLoading by contactViewModel.isLoading.collectAsState()
    val errorMessage by contactViewModel.errorMessage.collectAsState()
    val requestingLocationFor by contactViewModel.requestingLocationFor.collectAsState() // 正在请求位置的联系人
    val trackingContactUid by contactViewModel.trackingContactUid.collectAsState() // 正在实时追踪的联系人

    // 获取当前设备实时朝向
    val currentHeading = CompassHelper.rememberCompassHeading()

    // 底部面板偏移量（用于动态调整按钮位置）
    var bottomSheetOffsetPx by remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bottomSheetOffsetDp = with(density) { bottomSheetOffsetPx.toDp() }

    // 记录正在绑定通讯录的联系人 (null 表示绑定"我")
    var contactToBind by remember { mutableStateOf<Contact?>(null) }

    // 导航对话框状态
    var contactToNavigate by remember { mutableStateOf<Contact?>(null) }

    // 丢失模式对话框状态
    var contactForLostMode by remember { mutableStateOf<Contact?>(null) }

    // 地理围栏对话框状态
    var contactForGeofence by remember { mutableStateOf<Contact?>(null) }
    val geofenceManager = remember { GeofenceManager(context) }
    val activeGeofences by geofenceManager.activeGeofences.collectAsState()
    val geofenceContactIds = remember(activeGeofences) {
        activeGeofences.map { it.contactId }.toSet()
    }

    // 联系人选择器启动器
    val contactPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            val targetContact = contactToBind
            if (targetContact != null) {
                // 绑定朋友
                contactViewModel.bindContact(targetContact.id, uri)
            }
        }
    }

    // 监听权限状态变化，一旦获得权限立即上报位置
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.reportLocationNow()
            viewModel.attemptInitialLocationCenter()
        }
    }

    // 首次启动时请求权限
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    // 使用 Box 包裹所有内容，确保按钮在最上层
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 使用自定义底部面板包裹地图和设备列表
        CustomBottomSheet(
            modifier = Modifier.fillMaxSize(),
            initialValue = SheetValue.Expanded, // 启动时直接展开联系人页面
            backgroundContent = {
                // 背景：全屏地图视图
                MapViewWrapper(
                    modifier = Modifier.fillMaxSize(),
                    devices = devices, // 传递空列表，不显示设备
                    contacts = contacts, // 传递联系人列表用于渲染联系人位置 Marker
                    currentDeviceHeading = currentHeading, // 传递实时朝向
                    onMapReady = { map ->
                        viewModel.setGoogleMap(map)
                        if (permissionsState.allPermissionsGranted) {
                            viewModel.attemptInitialLocationCenter()
                        }
                    },
                    onMarkerClick = { device ->
                        // 理论上不会触发，因为没有设备 Marker
                    },
                    onContactMarkerClick = { contact ->
                        // 点击联系人 Marker 时，移动地图到联系人位置
                        contact.location?.let { location ->
                            // 仅移动地图，不再设置 selectedContact 触发详情页
                            // contactViewModel.selectContact(contact)
                            viewModel.clearSelectedDevice() // 清除选中的设备
                            MapCameraHelper.animateToLocation(googleMap, location, zoom = 15f)
                            viewModel.updateSheetValue(SheetValue.HalfExpanded)
                        }
                    },
                    onMapClick = {
                        // 点击地图空白区域，取消选中设备和联系人
                        viewModel.clearSelectedDevice()
                        contactViewModel.clearSelectedContact()
                    }
                )
            },
        sheetContent = {
            // 直接显示联系人列表和当前设备
            ContactListPanel(
                currentUser = currentUser,
                meName = meName,
                meAvatarUrl = meAvatarUrl,
                myDevice = myDevice,
                myAddress = myAddress,
                contacts = contacts,
                requestingLocationFor = requestingLocationFor,
                trackingContactUid = trackingContactUid,
                onContactClick = { contact ->
                    // 点击联系人时：移动地图
                    viewModel.clearSelectedDevice()

                    contact.location?.let { location ->
                        MapCameraHelper.animateToLocation(googleMap, location, zoom = 15f)
                        viewModel.updateSheetValue(SheetValue.HalfExpanded)
                    }
                },
                onAddContactClick = {
                    contactViewModel.showAddDialog()
                },
                onNavigate = { contact ->
                    // 显示导航选择对话框
                    if (contact.location != null) {
                        contactToNavigate = contact
                    }
                },
                onBindContact = { contact ->
                    contactToBind = contact
                    contactPickerLauncher.launch(null)
                },
                onPauseShare = { contact ->
                    contactViewModel.pauseShare(contact)
                },
                onResumeShare = { contact, duration ->
                    contactViewModel.resumeShare(contact, duration)
                },
                onRemoveContact = { contact ->
                    contactViewModel.removeContact(contact)
                },
                onAcceptShare = { contact ->
                    contactViewModel.acceptShare(contact.id)
                },
                onRejectShare = { contact ->
                    contactViewModel.rejectShare(contact.id)
                },
                onRequestLocationUpdate = { targetUid ->
                    contactViewModel.requestLocationUpdate(targetUid)
                },
                onStartContinuousTracking = { targetUid ->
                    contactViewModel.startContinuousTracking(targetUid)
                },
                onStopContinuousTracking = { targetUid ->
                    contactViewModel.stopContinuousTracking(targetUid)
                },
                onPlaySound = { targetUid ->
                    contactViewModel.requestPlaySound(targetUid)
                },
                onLostModeClick = { contact ->
                    contactForLostMode = contact
                },
                onGeofenceClick = { contact ->
                    contactForGeofence = contact
                },
                geofenceContactIds = geofenceContactIds
            )
        },
        onSheetValueChange = { value ->
            viewModel.updateSheetValue(value)
        },
        onOffsetChange = { offset ->
            // 面板偏移量变化时，动态调整地图 Padding 和按钮位置
            MapCameraHelper.adjustMapPadding(googleMap, offset)
            bottomSheetOffsetPx = offset
        }
    )

        // 定位按钮（右下角）- 移到最上层确保可见
        if (permissionsState.allPermissionsGranted) {
            LocationButton(
                isLocationCentered = isLocationCentered,
                onClick = { viewModel.onLocationButtonClick() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .padding(bottom = bottomSheetOffsetDp + 16.dp) // 动态避开底部面板
            )
        }

        // 地图图层切换按钮（右上角）- 移到最上层确保可见
        MapLayerButton(
            map = googleMap,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .padding(top = 40.dp) // 避免与状态栏重叠
        )
    }

    // 权限被拒绝时显示提示对话框
    if (permissionsState.shouldShowRationale) {
        AlertDialog(
            onDismissRequest = { /* 不允许关闭 */ },
            title = { Text("需要权限") },
            text = {
                Text(
                    "Find My 需要访问您的位置信息和通讯录(用于显示机主信息)才能正常工作。" +
                            "请授予相关权限以获得完整体验。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        permissionsState.launchMultiplePermissionRequest()
                    }
                ) {
                    Text("授权")
                }
            },
            dismissButton = {
                TextButton(onClick = { /* 暂时不处理拒绝 */ }) {
                    Text("暂不授权")
                }
            }
        )
    }

    // 位置共享对话框
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

    // 权限引导对话框
    val showPermissionGuide by contactViewModel.showPermissionGuide.collectAsState()
    val missingPermissions by contactViewModel.missingPermissions.collectAsState()

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

    // 导航对话框
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
                onDismiss = { contactToNavigate = null }
            )
        }
    }

    // 丢失模式对话框
    contactForLostMode?.let { contact ->
        LostModeDialog(
            contactName = contact.name,
            currentConfig = LostModeConfig(), // TODO: 从联系人状态读取
            onDismiss = { contactForLostMode = null },
            onConfirm = { config ->
                contact.targetUserId?.let { targetUid ->
                    if (config.enabled) {
                        contactViewModel.enableLostMode(
                            targetUid = targetUid,
                            message = config.message,
                            phoneNumber = config.phoneNumber,
                            playSound = config.playSound
                        )
                    } else {
                        contactViewModel.disableLostMode(targetUid)
                    }
                }
                contactForLostMode = null
            }
        )
    }

    // 地理围栏对话框
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
            onDismiss = { contactForGeofence = null },
            onConfirm = { config ->
                if (config.enabled && config.center != null) {
                    geofenceManager.addGeofence(
                        contactId = contact.id,
                        contactName = contact.name,
                        locationName = config.locationName,
                        center = config.center,
                        radiusMeters = config.radiusMeters,
                        notifyOnEnter = config.notifyOnEnter,
                        notifyOnExit = config.notifyOnExit
                    ) { success, error ->
                        if (!success) {
                            // TODO: 显示错误提示
                        }
                    }
                } else {
                    geofenceManager.removeGeofencesForContact(contact.id) { _, _ -> }
                }
                contactForGeofence = null
            }
        )
    }
}
