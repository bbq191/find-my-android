package me.ikate.findmy.ui.screen.main

import android.Manifest
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
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
import me.ikate.findmy.ui.components.DeviceAdminGuideDialog
import me.ikate.findmy.ui.components.LocationPermissionGuide
import me.ikate.findmy.ui.components.NetworkStatusIndicator
import me.ikate.findmy.ui.components.PermissionGuideDialog
import me.ikate.findmy.ui.components.TrackingStatusIndicator
import me.ikate.findmy.util.DeviceAdminHelper
import me.ikate.findmy.data.model.GeofenceType
import me.ikate.findmy.ui.screen.geofence.GeofenceConfig
import me.ikate.findmy.ui.screen.geofence.GeofenceEditorScreen
import me.ikate.findmy.ui.screen.geofence.NotificationType
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
import me.ikate.findmy.ui.screen.main.components.TencentMapViewWrapper
import me.ikate.findmy.ui.screen.main.components.QrCodeScannerSheet
import me.ikate.findmy.ui.screen.main.components.ShareLocationDialog
import me.ikate.findmy.ui.screen.main.components.SheetValue
import me.ikate.findmy.ui.screen.main.tabs.TabContent
import me.ikate.findmy.ui.components.ContactsPermissionDialog
import me.ikate.findmy.ui.theme.animatedMapThemeColors
import me.ikate.findmy.util.CompassHelper
import me.ikate.findmy.util.TencentMapCameraHelper
import me.ikate.findmy.util.DeviceIdProvider
import me.ikate.findmy.util.MapSettingsManager
import me.ikate.findmy.util.PermissionGuideHelper

/**
 * 主屏幕 - Find My 应用的主界面
 * 包含全屏地图视图和交互组件
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = koinViewModel(),
    contactViewModel: ContactViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 获取当前设备 ID 和用户 ID（使用 DeviceIdProvider 统一管理）
    val currentDeviceId = remember { DeviceIdProvider.getDeviceId(context) }
    val currentUserId = remember { AuthRepository.getUserId(context) }

    // 位置权限状态（按需请求）
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    )

    // 活动识别权限状态（Android 10+ 需要运行时权限）
    val activityRecognitionPermissionState = rememberPermissionState(
        Manifest.permission.ACTIVITY_RECOGNITION
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

    // 收集 ViewModel 聚合状态（性能优化：减少 collectAsState 调用次数）
    val uiState by viewModel.uiState.collectAsState()

    // 解构聚合状态，便于使用
    val tencentMap = uiState.tencentMap
    val isLocationCentered = uiState.isLocationCentered
    val devices = uiState.devices
    val trackingTargetId = uiState.trackingTargetId
    val currentDeviceRealtimeLocation = uiState.currentDeviceRealtimeLocation
    val currentDeviceBearing = uiState.currentDeviceBearing

    // 收集联系人 ViewModel 聚合状态（性能优化：减少 collectAsState 调用次数）
    val contactUiState by contactViewModel.uiState.collectAsState()

    // 解构聚合状态，便于使用
    val contacts = contactUiState.contacts
    val currentUser = contactUiState.currentUser
    val meName = contactUiState.meName
    val meAvatarUrl = contactUiState.meAvatarUrl
    val meStatus = contactUiState.meStatus
    val myDevice = contactUiState.myDevice
    val myAddress = contactUiState.myAddress
    val showAddDialog = contactUiState.showAddDialog
    val isLoading = contactUiState.isLoading
    val errorMessage = contactUiState.errorMessage
    val refreshingContacts = contactUiState.refreshingContacts
    val ringingContactUid = contactUiState.ringingContactUid

    // 扫码状态
    var showQrCodeScanner by remember { mutableStateOf(false) }
    var scannedUid by remember { mutableStateOf<String?>(null) }

    // 获取当前设备实时朝向
    val currentHeading = CompassHelper.rememberCompassHeading()

    // 底部面板偏移量（用于动态调整按钮位置）
    var bottomSheetOffsetPx by remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bottomSheetOffsetDp = with(density) { bottomSheetOffsetPx.toDp() }

    // 底部导航栏高度
    val bottomNavHeightDp = 80.dp
    val bottomNavHeightPx = with(density) { bottomNavHeightDp.toPx() }

    // 获取屏幕尺寸（用于智能地图缩放）
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx().toInt() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx().toInt() }

    // 底部面板默认半展开高度（约屏幕高度的 40%）
    val defaultBottomSheetHeightPx = (screenHeightPx * 0.4f).toInt()

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
    val geofenceManager = remember { GeofenceManager.getInstance(context) }
    val activeGeofences by geofenceManager.activeGeofences.collectAsState()
    val geofenceContactIds = remember(activeGeofences) {
        activeGeofences.map { it.contactId }.toSet()
    }

    // 设备相关状态
    var ringingDeviceId by remember { mutableStateOf<String?>(null) }
    var deviceToNavigate by remember { mutableStateOf<Device?>(null) }
    var deviceForLostMode by remember { mutableStateOf<Device?>(null) }
    var showDeviceAdminGuide by remember { mutableStateOf(false) }
    var pendingLostModeDevice by remember { mutableStateOf<Device?>(null) }

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

    // 过滤有效联系人（用于初始地图视野计算）
    val validContacts = remember(contacts) {
        contacts.filter { contact ->
            val hasValidLocation = contact.location?.let { loc ->
                !loc.latitude.isNaN() && !loc.longitude.isNaN()
            } ?: false
            val isActive = contact.shareStatus == me.ikate.findmy.data.model.ShareStatus.ACCEPTED &&
                    !contact.isPaused &&
                    contact.isLocationAvailable
            hasValidLocation && isActive
        }
    }

    // 记录是否已完成初始地图缩放（联系人数据加载后）
    var hasCompletedInitialZoom by remember { mutableStateOf(false) }

    // 生命周期观察：应用进入前台时检查权限状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 应用进入前台时检查权限状态（温和提示，每天最多一次）
                contactViewModel.checkPermissionStatusOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 监听联系人数据变化，首次有效联系人数据加载完成后重新触发地图缩放
    // 这确保了即使联系人数据在地图加载后才到达，也能正确显示所有点位
    LaunchedEffect(validContacts, tencentMap) {
        if (!hasCompletedInitialZoom &&
            validContacts.isNotEmpty() &&
            tencentMap != null &&
            locationPermissionsState.allPermissionsGranted
        ) {
            // 重置初始化状态，触发重新缩放
            viewModel.resetInitialCentered()
            viewModel.attemptInitialLocationCenter(
                hasContacts = true,
                contacts = validContacts,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                bottomSheetHeightPx = defaultBottomSheetHeightPx
            )
            hasCompletedInitialZoom = true
        }
    }

    // 监听位置权限状态变化，一旦获得权限立即上报位置
    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        if (locationPermissionsState.allPermissionsGranted) {
            viewModel.reportLocationNow()
            // 延迟初始定位，等待联系人数据加载
            viewModel.attemptInitialLocationCenter(
                hasContacts = validContacts.isNotEmpty(),
                contacts = validContacts,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                bottomSheetHeightPx = defaultBottomSheetHeightPx
            )

            // 位置权限授予后，请求活动识别权限（用于智能位置追踪）
            // Android 10+ 需要此权限才能使用 Google Activity Recognition API
            if (!activityRecognitionPermissionState.status.isGranted) {
                activityRecognitionPermissionState.launchPermissionRequest()
            }
        }
    }

    // 监听活动识别权限状态变化，获得权限后启动活动识别
    LaunchedEffect(activityRecognitionPermissionState.status.isGranted) {
        if (activityRecognitionPermissionState.status.isGranted &&
            locationPermissionsState.allPermissionsGranted
        ) {
            // 权限已授予，重新启动活动识别以使用 Google API
            viewModel.restartActivityRecognition()
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
                    TencentMapViewWrapper(
                        modifier = Modifier.fillMaxSize(),
                        devices = devices,
                        contacts = if (selectedTab == FindMyTab.PEOPLE) contacts else emptyList(),
                        // 优先使用指南针朝向（静止时也有效），定位服务的 bearing 只在移动时有效
                        currentDeviceHeading = currentHeading ?: currentDeviceBearing,
                        currentDeviceRealtimeLocation = currentDeviceRealtimeLocation,
                        showTraffic = isTrafficEnabled,
                        mapLayerConfig = mapLayerConfig,
                        bottomPadding = bottomSheetOffsetDp + 8.dp,
                        trackingTargetId = trackingTargetId,
                        onMapReady = { map ->
                            viewModel.setTencentMap(map)
                            if (locationPermissionsState.allPermissionsGranted) {
                                viewModel.attemptInitialLocationCenter(
                                    hasContacts = validContacts.isNotEmpty(),
                                    contacts = validContacts,
                                    screenWidthPx = screenWidthPx,
                                    screenHeightPx = screenHeightPx,
                                    bottomSheetHeightPx = defaultBottomSheetHeightPx
                                )
                            }
                        },
                        onMarkerClick = { device ->
                            selectedTab = FindMyTab.DEVICES
                            // 先立即跳转到设备位置，然后开始追踪
                            TencentMapCameraHelper.jumpToLocation(tencentMap, device.location, zoom = 18f)
                            viewModel.startTracking(device.id)
                            viewModel.updateSheetValue(SheetValue.HalfExpanded)
                        },
                        onContactMarkerClick = { contact ->
                            // 先检查权限，只有权限满足才允许定位联系人
                            contact.targetUserId?.let { targetUid ->
                                contactViewModel.refreshLocation(targetUid)
                                // 设置高频刷新（iOS Find My 风格）
                                viewModel.setFocusedContact(targetUid)
                            }

                            val (hasPermission, _) = PermissionGuideHelper.checkLocationSharePermissions(context)
                            if (hasPermission) {
                                contact.location?.let { location ->
                                    viewModel.clearSelectedDevice()
                                    // 先立即跳转到联系人位置
                                    TencentMapCameraHelper.jumpToLocation(tencentMap, location, zoom = 18f)
                                    // 开始地图追踪（相机跟随）
                                    viewModel.startTracking("contact_${contact.id}")
                                    viewModel.updateSheetValue(SheetValue.HalfExpanded)
                                }
                            }
                        },
                        onMapClick = {
                            viewModel.clearSelectedDevice()
                            contactViewModel.clearSelectedContact()
                            viewModel.stopTracking()
                        },
                        onUserInteraction = {
                            // 用户手动拖动地图时停止追踪
                            viewModel.stopTracking()
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
                    refreshingContacts = refreshingContacts,
                    geofenceContactIds = geofenceContactIds,
                    onContactClick = { contact ->
                        // 点击联系人卡片：仅定位到地图，不触发追踪
                        // 追踪由 onRefreshAndTrack（点击头像）触发

                        // 检查权限是否满足（用于决定是否跳转地图）
                        val (hasPermission, _) = PermissionGuideHelper.checkLocationSharePermissions(context)
                        if (hasPermission) {
                            viewModel.clearSelectedDevice()
                            contact.location?.let { location ->
                                // 先立即跳转到联系人位置
                                TencentMapCameraHelper.jumpToLocation(tencentMap, location, zoom = 18f)
                                // 开始地图追踪（相机跟随）
                                viewModel.startTracking("contact_${contact.id}")
                                viewModel.updateSheetValue(SheetValue.HalfExpanded)
                            }
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
                    onRefreshLocation = { targetUid ->
                        contactViewModel.refreshLocation(targetUid)
                        // 设置高频刷新（iOS Find My 风格）
                        viewModel.setFocusedContact(targetUid)
                    },
                    onPlaySound = { targetUid -> contactViewModel.requestPlaySound(targetUid) },
                    onStopSound = { contactViewModel.stopRinging() },
                    isRinging = ringingContactUid != null,
                    ringingContactUid = ringingContactUid,
                    onLostModeClick = { contact, message, phone, playSound ->
                        contact.targetUserId?.let { targetUid ->
                            contactViewModel.enableLostMode(targetUid, message, phone, playSound)
                        }
                    },
                    onGeofenceClick = { contact -> contactForGeofence = contact },
                    // Devices Tab 参数
                    devices = devices,
                    currentDeviceId = currentDeviceId,
                    currentUserId = currentUserId,
                    currentDeviceAddress = myAddress,
                    ringingDeviceId = ringingDeviceId,
                    onDeviceClick = { device ->
                        device.location.let { location ->
                            // 先立即跳转到设备位置，然后开始追踪
                            TencentMapCameraHelper.jumpToLocation(tencentMap, location, zoom = 18f)
                            viewModel.startTracking(device.id)
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
                        // 检查 Device Admin 是否已激活
                        if (DeviceAdminHelper.isAdminActive(context)) {
                            deviceForLostMode = device
                        } else {
                            // 保存待处理的设备，显示激活引导
                            pendingLostModeDevice = device
                            showDeviceAdminGuide = true
                        }
                    },
                    // Me Tab 参数
                    currentUser = currentUser,
                    meName = meName,
                    meAvatarUrl = meAvatarUrl,
                    meStatus = meStatus,
                    sharingWithCount = contacts.size,
                    onNameChange = { name -> contactViewModel.updateMeName(name) },
                    onAvatarChange = { avatarUrl -> contactViewModel.updateMeAvatar(avatarUrl) },
                    onStatusChange = { status -> contactViewModel.updateMeStatus(status) }
                )
            },
            onSheetValueChange = { value ->
                viewModel.updateSheetValue(value)
            },
            onOffsetChange = { offset ->
                TencentMapCameraHelper.adjustMapPadding(tencentMap, (offset + bottomNavHeightPx).toInt())
                bottomSheetOffsetPx = offset
            }
        )

        // 底部导航栏
        BottomNavBar(
            selectedTab = selectedTab,
            onTabSelected = { tab -> selectedTab = tab },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 顶部网络状态指示器
        NetworkStatusIndicator(
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 左上角实时追踪状态指示器
        TrackingStatusIndicator(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .padding(top = 40.dp)
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
                map = tencentMap,
                isTrafficEnabled = isTrafficEnabled,
                onTrafficToggle = { enabled ->
                    isTrafficEnabled = enabled
                    MapSettingsManager.saveTrafficEnabled(context, enabled)
                },
                config = mapLayerConfig,
                onConfigChange = { newConfig ->
                    mapLayerConfig = newConfig
                    MapSettingsManager.saveMapLayerConfig(context, newConfig)
                }
            )

            // 定位按钮 - 在地图按钮下方，不随面板浮动
            LocationButton(
                isLocationCentered = isLocationCentered,
                onClick = {
                    if (locationPermissionsState.allPermissionsGranted) {
                        viewModel.onLocationButtonClick()
                        // 点击定位按钮后开始追踪当前设备
                        viewModel.startTracking(currentDeviceId)
                    } else {
                        // 请求位置权限
                        locationPermissionsState.launchMultiplePermissionRequest()
                    }
                }
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
            initialUid = scannedUid,
            onDismiss = {
                contactViewModel.clearError()
                contactViewModel.hideAddDialog()
                scannedUid = null // 清除扫描的 UID
            },
            onConfirm = { email, duration ->
                contactViewModel.shareLocation(email, duration)
                scannedUid = null // 清除扫描的 UID
            },
            onScanQrCode = {
                showQrCodeScanner = true
            }
        )
    }

    // 二维码扫描 Sheet
    if (showQrCodeScanner) {
        QrCodeScannerSheet(
            onDismiss = { showQrCodeScanner = false },
            onScanResult = { uid ->
                scannedUid = uid
                showQrCodeScanner = false
            }
        )
    }

    // 权限引导对话框（从聚合状态中获取）
    val showPermissionGuide = contactUiState.showPermissionGuide
    val missingPermissions = contactUiState.missingPermissions

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

    // 设备管理员激活引导对话框
    if (showDeviceAdminGuide) {
        DeviceAdminGuideDialog(
            onDismiss = {
                showDeviceAdminGuide = false
                pendingLostModeDevice = null
            },
            onActivated = {
                showDeviceAdminGuide = false
                // 激活成功后，继续显示丢失模式对话框
                pendingLostModeDevice?.let { device ->
                    deviceForLostMode = device
                }
                pendingLostModeDevice = null
            },
            onSkip = {
                showDeviceAdminGuide = false
                // 跳过激活，仍然允许使用基础丢失模式功能
                pendingLostModeDevice?.let { device ->
                    deviceForLostMode = device
                }
                pendingLostModeDevice = null
            }
        )
    }

    // 地理围栏对话框
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
                    com.tencent.tencentmap.mapsdk.maps.model.LatLng(existingGeofence.ownerLatitude, existingGeofence.ownerLongitude)
                } else null,
                wasInsideOnCreate = existingGeofence.wasInsideOnCreate
            )
        } else {
            GeofenceConfig()
        }

        GeofenceEditorScreen(
            contactName = contact.name,
            contactLocation = contact.location,
            myLocation = myDevice?.location,
            currentConfig = currentConfig,
            onDismiss = { contactForGeofence = null },
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
