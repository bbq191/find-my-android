package me.ikate.findmy.ui.screen.main

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.repository.AuthRepository
import me.ikate.findmy.data.model.Device
import android.content.Intent
import android.provider.Settings as AndroidSettings
import me.ikate.findmy.ui.components.LocationPermissionGuide
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
import me.ikate.findmy.ui.screen.main.components.MapLayerConfig
import me.ikate.findmy.ui.screen.main.components.MapboxViewWrapper
import me.ikate.findmy.ui.screen.main.components.ShareLocationDialog
import me.ikate.findmy.ui.screen.main.components.SheetValue
import me.ikate.findmy.ui.screen.main.tabs.TabContent
import me.ikate.findmy.ui.components.ContactsPermissionDialog
import me.ikate.findmy.ui.theme.animatedMapThemeColors
import me.ikate.findmy.util.CompassHelper
import me.ikate.findmy.util.MapCameraHelper
import me.ikate.findmy.util.MapSettingsManager

/**
 * 主屏幕 - Find My 应用的主界面
 * 包含全屏地图视图和交互组件
 */
@SuppressLint("HardwareIds")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    contactViewModel: ContactViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 获取当前设备 ID 和用户 ID（使用 Android ID）
    val currentDeviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
    val currentUserId = remember { AuthRepository.getUserId(context) }

    // 位置权限状态（按需请求）
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    )

    // 通讯录权限状态（按需请求）
    val contactsPermissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)

    // 兼容旧代码：组合权限状态
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CONTACTS
        )
    )

    // 收集 ViewModel 状态
    val mapboxMap by viewModel.mapboxMap.collectAsState()
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

    // 通讯录权限对话框状态
    var showContactsPermissionDialog by remember { mutableStateOf(false) }

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

    // 设备相关状态
    var ringingDeviceId by remember { mutableStateOf<String?>(null) }
    var deviceToNavigate by remember { mutableStateOf<Device?>(null) }
    var deviceForLostMode by remember { mutableStateOf<Device?>(null) }

    // 地图图层状态（从本地存储加载）
    var isTrafficEnabled by remember { mutableStateOf(MapSettingsManager.loadTrafficEnabled(context)) }
    var mapLayerConfig by remember { mutableStateOf(MapSettingsManager.loadMapLayerConfig(context)) }

    // 根据光照预设获取主题颜色（带动画过渡）
    val themeColors = animatedMapThemeColors(mapLayerConfig.lightPreset)

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

    // 监听位置权限状态变化，一旦获得权限立即上报位置
    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        if (locationPermissionsState.allPermissionsGranted) {
            viewModel.reportLocationNow()
            viewModel.attemptInitialLocationCenter()
        }
    }

    // 监听通讯录权限状态变化，获得权限后自动打开联系人选择器
    LaunchedEffect(contactsPermissionState.status.isGranted) {
        if (contactsPermissionState.status.isGranted && contactToBind != null) {
            contactPickerLauncher.launch(null)
        }
    }

    // 使用 Box 包裹所有内容，确保按钮在最上层
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 使用自定义底部面板包裹地图和设备列表
        // 面板最大高度不超过右上角按钮区域（约 75% 屏幕高度）
        CustomBottomSheet(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomNavHeightDp), // 为底部导航栏留出空间
            initialValue = SheetValue.HalfExpanded, // 启动时半展开
            maxExpandedFraction = 0.75f, // 限制最大展开高度，确保不遮挡右上角按钮
            backgroundContent = {
                // 背景：全屏地图视图 + 权限引导覆盖层
                Box(modifier = Modifier.fillMaxSize()) {
                    MapboxViewWrapper(
                        modifier = Modifier.fillMaxSize(),
                        devices = devices,
                        contacts = if (selectedTab == FindMyTab.PEOPLE) contacts else emptyList(),
                        currentDeviceHeading = currentHeading,
                        showTraffic = isTrafficEnabled,
                        mapLayerConfig = mapLayerConfig,
                        bottomPadding = bottomSheetOffsetDp + 8.dp,
                        onMapReady = { map ->
                            viewModel.setMapboxMap(map)
                            if (locationPermissionsState.allPermissionsGranted) {
                                viewModel.attemptInitialLocationCenter()
                            }
                        },
                        onMarkerClick = { device ->
                            selectedTab = FindMyTab.DEVICES
                        },
                        onContactMarkerClick = { contact ->
                            contact.location?.let { location ->
                                viewModel.clearSelectedDevice()
                                MapCameraHelper.animateToLocation(mapboxMap, location, zoom = 15.0)
                                viewModel.updateSheetValue(SheetValue.HalfExpanded)
                            }
                        },
                        onMapClick = {
                            viewModel.clearSelectedDevice()
                            contactViewModel.clearSelectedContact()
                        }
                    )

                    // 位置权限未授予时显示引导
                    if (!locationPermissionsState.allPermissionsGranted) {
                        // 判断是否需要显示"前往设置"按钮
                        // 当 shouldShowRationale 为 false 且权限未授予时，可能是被永久拒绝
                        val isPermanentlyDenied = !locationPermissionsState.shouldShowRationale &&
                            locationPermissionsState.permissions.any { !it.status.isGranted }

                        LocationPermissionGuide(
                            onRequestPermission = {
                                locationPermissionsState.launchMultiplePermissionRequest()
                            },
                            onOpenSettings = {
                                // 打开应用设置页面
                                val intent = Intent(
                                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(intent)
                            },
                            showSettingsButton = isPermanentlyDenied
                        )
                    }
                }
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
                            MapCameraHelper.animateToLocation(mapboxMap, location, zoom = 15.0)
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
                        // 检查通讯录权限
                        if (contactsPermissionState.status.isGranted) {
                            contactPickerLauncher.launch(null)
                        } else {
                            showContactsPermissionDialog = true
                        }
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
                    devices = devices,
                    currentDeviceId = currentDeviceId,
                    currentUserId = currentUserId,
                    currentDeviceAddress = myAddress,
                    ringingDeviceId = ringingDeviceId,
                    onDeviceClick = { device ->
                        device.location.let { location ->
                            MapCameraHelper.animateToLocation(mapboxMap, location, zoom = 15.0)
                            viewModel.updateSheetValue(SheetValue.HalfExpanded)
                        }
                    },
                    onNavigateToDevice = { device ->
                        deviceToNavigate = device
                    },
                    onPlaySoundOnDevice = { device ->
                        ringingDeviceId = device.id
                        contactViewModel.requestPlaySound(device.ownerId)
                    },
                    onStopSoundOnDevice = {
                        ringingDeviceId?.let { _ ->
                            // 停止响铃
                            contactViewModel.stopRinging()
                        }
                        ringingDeviceId = null
                    },
                    onLostModeOnDevice = { device ->
                        deviceForLostMode = device
                    },
                    // Me Tab 参数
                    currentUser = currentUser,
                    meName = meName,
                    meAvatarUrl = meAvatarUrl,
                    sharingWithCount = contacts.size,
                    onNameChange = { name -> contactViewModel.updateMeName(name) },
                    onAvatarChange = { avatarUrl -> contactViewModel.updateMeAvatar(avatarUrl) }
                )
            },
            onSheetValueChange = { value ->
                viewModel.updateSheetValue(value)
            },
            onOffsetChange = { offset ->
                MapCameraHelper.adjustMapPadding(mapboxMap, offset + bottomNavHeightPx)
                bottomSheetOffsetPx = offset
            }
        )

        // 底部导航栏
        BottomNavBar(
            selectedTab = selectedTab,
            onTabSelected = { tab -> selectedTab = tab },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 右上角按钮组（地图图层 + 定位按钮）- 不随面板浮动
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .padding(top = 40.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            // 地图图层切换按钮
            MapLayerButton(
                map = mapboxMap,
                isTrafficEnabled = isTrafficEnabled,
                onTrafficToggle = { enabled ->
                    isTrafficEnabled = enabled
                    MapSettingsManager.saveTrafficEnabled(context, enabled)
                },
                config = mapLayerConfig,
                onConfigChange = { newConfig ->
                    mapLayerConfig = newConfig
                    MapSettingsManager.saveMapLayerConfig(context, newConfig)
                },
                themeColors = themeColors
            )

            // 定位按钮 - 在地图按钮下方，不随面板浮动
            LocationButton(
                isLocationCentered = isLocationCentered,
                onClick = {
                    if (locationPermissionsState.allPermissionsGranted) {
                        viewModel.onLocationButtonClick()
                    } else {
                        // 请求位置权限
                        locationPermissionsState.launchMultiplePermissionRequest()
                    }
                },
                themeColors = themeColors
            )
        }

        // Snackbar 提示（用于显示操作结果）
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomNavHeightDp + 16.dp)
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

    // 联系人导航对话框
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

    // 设备导航对话框
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
            onDismiss = { deviceToNavigate = null }
        )
    }

    // 联系人丢失模式对话框
    contactForLostMode?.let { contact ->
        // 判断当前联系人是否正在播放提示音
        val isSoundPlaying = ringingContactUid == contact.targetUserId

        LostModeDialog(
            contactName = contact.name,
            currentConfig = LostModeConfig(
                // 丢失模式状态在目标设备上管理，本地无法获取远程设备的实时状态
                // 每次打开对话框默认显示为未启用，用户可以选择启用/禁用
                enabled = false,
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

    // 设备丢失模式对话框
    deviceForLostMode?.let { device ->
        val isSoundPlaying = ringingDeviceId == device.id

        LostModeDialog(
            contactName = device.customName ?: device.name,
            currentConfig = LostModeConfig(
                enabled = false,
                isSoundPlaying = isSoundPlaying
            ),
            onDismiss = { deviceForLostMode = null },
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
                        ringingDeviceId = null
                    }
                }
                deviceForLostMode = null
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
                contactForGeofence = null
            }
        )
    }

    // 通讯录权限引导对话框
    if (showContactsPermissionDialog) {
        // 判断是否被永久拒绝
        val isPermanentlyDenied = !contactsPermissionState.status.isGranted &&
            !contactsPermissionState.status.shouldShowRationale

        ContactsPermissionDialog(
            onRequestPermission = {
                showContactsPermissionDialog = false
                contactsPermissionState.launchPermissionRequest()
            },
            onDismiss = {
                showContactsPermissionDialog = false
                contactToBind = null
            },
            onOpenSettings = if (isPermanentlyDenied) {
                {
                    showContactsPermissionDialog = false
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
