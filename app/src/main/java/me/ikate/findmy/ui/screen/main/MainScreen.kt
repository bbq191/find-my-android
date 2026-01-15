package me.ikate.findmy.ui.screen.main

import android.Manifest
import android.net.Uri
import androidx.compose.foundation.layout.Box
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.ui.components.PermissionGuideDialog
import me.ikate.findmy.ui.dialog.GeofenceConfig
import me.ikate.findmy.ui.dialog.GeofenceDialog
import me.ikate.findmy.ui.dialog.LostModeAction
import me.ikate.findmy.ui.dialog.LostModeConfig
import me.ikate.findmy.ui.dialog.LostModeDialog
import me.ikate.findmy.ui.dialog.NavigationDialog
import me.ikate.findmy.ui.navigation.BottomNavBar
import me.ikate.findmy.ui.navigation.FindMyTab
import me.ikate.findmy.service.GeofenceManager
import me.ikate.findmy.ui.screen.contact.ContactViewModel
import me.ikate.findmy.util.DistanceCalculator
import me.ikate.findmy.ui.screen.main.components.CustomBottomSheet
import me.ikate.findmy.ui.screen.main.components.LocationButton
import me.ikate.findmy.ui.screen.main.components.MapLayerButton
import me.ikate.findmy.ui.screen.main.components.MapViewWrapper
import me.ikate.findmy.ui.screen.main.components.ShareLocationDialog
import me.ikate.findmy.ui.screen.main.components.SheetValue
import me.ikate.findmy.ui.screen.main.tabs.TabContent
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
    val isSmartLocationEnabled by viewModel.isSmartLocationEnabled.collectAsState() // 智能位置状态

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
    val ringingContactUid by contactViewModel.ringingContactUid.collectAsState() // 正在响铃的联系人

    // 获取当前设备实时朝向
    val currentHeading = CompassHelper.rememberCompassHeading()

    // 底部面板偏移量（用于动态调整按钮位置）
    var bottomSheetOffsetPx by remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bottomSheetOffsetDp = with(density) { bottomSheetOffsetPx.toDp() }

    // 底部导航栏高度
    val bottomNavHeightDp = 80.dp
    val bottomNavHeightPx = with(density) { bottomNavHeightDp.toPx() }

    // Tab 状态
    var selectedTab by remember { mutableStateOf(FindMyTab.PEOPLE) }

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
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 使用自定义底部面板包裹地图和设备列表
        CustomBottomSheet(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomNavHeightDp), // 为底部导航栏留出空间
            initialValue = SheetValue.HalfExpanded, // 启动时半展开
            backgroundContent = {
                // 背景：全屏地图视图
                MapViewWrapper(
                    modifier = Modifier.fillMaxSize(),
                    devices = devices,
                    contacts = if (selectedTab == FindMyTab.PEOPLE) contacts else emptyList(),
                    currentDeviceHeading = currentHeading,
                    onMapReady = { map ->
                        viewModel.setGoogleMap(map)
                        if (permissionsState.allPermissionsGranted) {
                            viewModel.attemptInitialLocationCenter()
                        }
                    },
                    onMarkerClick = { device ->
                        selectedTab = FindMyTab.DEVICES
                    },
                    onContactMarkerClick = { contact ->
                        contact.location?.let { location ->
                            viewModel.clearSelectedDevice()
                            MapCameraHelper.animateToLocation(googleMap, location, zoom = 15f)
                            viewModel.updateSheetValue(SheetValue.HalfExpanded)
                        }
                    },
                    onMapClick = {
                        viewModel.clearSelectedDevice()
                        contactViewModel.clearSelectedContact()
                    }
                )
            },
            sheetContent = {
                // Tab 内容切换
                TabContent(
                    selectedTab = selectedTab,
                    // People Tab 参数
                    contacts = contacts,
                    requestingLocationFor = requestingLocationFor,
                    trackingContactUid = trackingContactUid,
                    geofenceContactIds = geofenceContactIds,
                    onContactClick = { contact ->
                        viewModel.clearSelectedDevice()
                        contact.location?.let { location ->
                            MapCameraHelper.animateToLocation(googleMap, location, zoom = 15f)
                            viewModel.updateSheetValue(SheetValue.HalfExpanded)
                        }
                    },
                    onAddContactClick = { contactViewModel.showAddDialog() },
                    onNavigate = { contact ->
                        if (contact.location != null) {
                            contactToNavigate = contact
                        }
                    },
                    onBindContact = { contact ->
                        contactToBind = contact
                        contactPickerLauncher.launch(null)
                    },
                    onPauseShare = { contact -> contactViewModel.pauseShare(contact) },
                    onResumeShare = { contact, shareDuration ->
                        contactViewModel.resumeShare(contact, shareDuration)
                    },
                    onRemoveContact = { contact -> contactViewModel.removeContact(contact) },
                    onAcceptShare = { contact -> contactViewModel.acceptShare(contact.id) },
                    onRejectShare = { contact -> contactViewModel.rejectShare(contact.id) },
                    onRequestLocationUpdate = { targetUid ->
                        contactViewModel.requestLocationUpdate(targetUid)
                    },
                    onStartContinuousTracking = { targetUid ->
                        contactViewModel.startContinuousTracking(targetUid)
                    },
                    onStopContinuousTracking = { targetUid ->
                        contactViewModel.stopContinuousTracking(targetUid)
                    },
                    onPlaySound = { targetUid -> contactViewModel.requestPlaySound(targetUid) },
                    onStopSound = { contactViewModel.stopRinging() },
                    isRinging = ringingContactUid != null,
                    onLostModeClick = { contact -> contactForLostMode = contact },
                    onGeofenceClick = { contact -> contactForGeofence = contact },
                    // Devices Tab 参数
                    myDevice = myDevice,
                    myAddress = myAddress,
                    // Me Tab 参数
                    currentUser = currentUser,
                    meName = meName,
                    meAvatarUrl = meAvatarUrl,
                    sharingWithCount = contacts.size,
                    isSmartLocationEnabled = isSmartLocationEnabled,
                    onSmartLocationToggle = { enabled ->
                        viewModel.setSmartModeEnabled(enabled)
                    },
                    onNameChange = { name -> contactViewModel.updateMeName(name) },
                    onAvatarChange = { avatarUrl -> contactViewModel.updateMeAvatar(avatarUrl) }
                )
            },
            onSheetValueChange = { value ->
                viewModel.updateSheetValue(value)
            },
            onOffsetChange = { offset ->
                MapCameraHelper.adjustMapPadding(googleMap, offset + bottomNavHeightPx)
                bottomSheetOffsetPx = offset
            }
        )

        // 底部导航栏
        BottomNavBar(
            selectedTab = selectedTab,
            onTabSelected = { tab -> selectedTab = tab },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 定位按钮（右下角）- 移到最上层确保可见
        if (permissionsState.allPermissionsGranted) {
            LocationButton(
                isLocationCentered = isLocationCentered,
                onClick = { viewModel.onLocationButtonClick() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .padding(bottom = bottomSheetOffsetDp + bottomNavHeightDp + 16.dp)
            )
        }

        // 地图图层切换按钮（右上角）- 移到最上层确保可见
        MapLayerButton(
            map = googleMap,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .padding(top = 40.dp)
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
        // 判断当前联系人是否正在播放提示音
        val isSoundPlaying = ringingContactUid == contact.targetUserId

        LostModeDialog(
            contactName = contact.name,
            currentConfig = LostModeConfig(
                enabled = false, // TODO: 从联系人状态读取丢失模式是否启用
                isSoundPlaying = isSoundPlaying
            ),
            onDismiss = { contactForLostMode = null },
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
