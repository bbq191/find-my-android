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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.ui.screen.contact.ContactViewModel
import me.ikate.findmy.ui.screen.device.DeviceManagementViewModel
import me.ikate.findmy.ui.screen.main.components.ContactDetailPanel
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
    deviceManagementViewModel: DeviceManagementViewModel = viewModel(),
    contactViewModel: ContactViewModel = viewModel(),
    onNavigateToAuth: () -> Unit = {}
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
    // val devices by viewModel.devices.collectAsState() // 移除设备列表收集
    val devices = emptyList<me.ikate.findmy.data.model.Device>() // 传递空列表
    val selectedDevice by viewModel.selectedDevice.collectAsState()

    // 收集联系人 ViewModel 状态
    val contacts by contactViewModel.contacts.collectAsState()
    val currentUser by contactViewModel.currentUser.collectAsState() // 收集当前用户
    val meName by contactViewModel.meName.collectAsState() // "我"的本地显示名称
    val meAvatarUrl by contactViewModel.meAvatarUrl.collectAsState() // "我"的本地显示头像
    val myDevice by contactViewModel.myDevice.collectAsState() // 收集当前设备
    val myAddress by contactViewModel.myAddress.collectAsState() // 收集当前设备地址
    val selectedContact by contactViewModel.selectedContact.collectAsState()
    val contactAddress by contactViewModel.contactAddress.collectAsState()
    val showAddDialog by contactViewModel.showAddDialog.collectAsState()
    val isLoading by contactViewModel.isLoading.collectAsState()
    val errorMessage by contactViewModel.errorMessage.collectAsState()

    // 获取当前设备实时朝向
    val currentHeading = CompassHelper.rememberCompassHeading()

    // 底部面板偏移量（用于动态调整按钮位置）
    var bottomSheetOffsetPx by remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bottomSheetOffsetDp = with(density) { bottomSheetOffsetPx.toDp() }

    // 记录正在绑定通讯录的联系人 (null 表示绑定"我")
    var contactToBind by remember { mutableStateOf<Contact?>(null) }

    // 联系人选择器启动器
    val contactPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            val targetContact = contactToBind
            if (targetContact != null) {
                // 绑定朋友
                contactViewModel.bindContact(targetContact.id, uri)
                contactToBind = null
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

    // 使用自定义底部面板包裹地图和设备列表
    CustomBottomSheet(
        modifier = Modifier.fillMaxSize(),
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
                        contactViewModel.selectContact(contact)
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

            // 定位按钮（右下角）
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

            // 地图图层切换按钮（右上角）
            MapLayerButton(
                map = googleMap,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .padding(top = 40.dp) // 避免与状态栏重叠
            )
        },
        sheetContent = {
            if (selectedContact != null) {
                // 显示联系人详情
                ContactDetailPanel(
                    contact = selectedContact!!,
                    address = contactAddress,
                    onClose = {
                        contactViewModel.clearSelectedContact()
                    },
                    onAccept = { contact ->
                        contactViewModel.acceptShare(contact.id)
                        contactViewModel.clearSelectedContact()
                    },
                    onReject = { contact ->
                        contactViewModel.rejectShare(contact.id)
                        contactViewModel.clearSelectedContact()
                    },
                    onNavigate = { contact ->
                        // 启动 Google Maps 导航
                        contact.location?.let { loc ->
                            val uri = Uri.parse("google.navigation:q=${loc.latitude},${loc.longitude}")
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                            mapIntent.setPackage("com.google.android.apps.maps")
                            if (mapIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(mapIntent)
                            } else {
                                // 如果没有安装 Google Maps，尝试使用通用 geo URI
                                val geoUri = Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}(${contact.name})")
                                val geoIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                context.startActivity(geoIntent)
                            }
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
                    }
                )
            } else {
                // 显示联系人列表
                val isAnonymous = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.isAnonymous ?: true
                
                if (isAnonymous) {
                     me.ikate.findmy.ui.screen.main.components.AnonymousUserPrompt(
                        onSignUpClick = onNavigateToAuth,
                         modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ContactListPanel(
                        currentUser = currentUser,
                        meName = meName,
                        meAvatarUrl = meAvatarUrl,
                        myDevice = myDevice,
                        myAddress = myAddress,
                        contacts = contacts,
                        onContactClick = { contact ->
                            // 点击联系人时，选中联系人
                            contactViewModel.selectContact(contact)
                            viewModel.clearSelectedDevice()
                            viewModel.updateSheetValue(SheetValue.HalfExpanded)

                            // 只有当有位置时才移动地图
                            contact.location?.let { location ->
                                MapCameraHelper.animateToLocation(googleMap, location, zoom = 15f)
                            }
                        },
                        onAddContactClick = {
                            contactViewModel.showAddDialog()
                        },
                        onPauseShare = { contact ->
                            contactViewModel.pauseShare(contact)
                        },
                        onResumeShare = { contact, duration ->
                            contactViewModel.resumeShare(contact, duration)
                        }
                    )
                }
            }
        },
        onSheetValueChange = { value ->
            viewModel.updateSheetValue(value)
        },
        onOffsetChange = { offset ->
            // 面板偏移量变化时，动态调整地图 Padding 和按钮位置
            bottomSheetOffsetPx = offset
            MapCameraHelper.adjustMapPadding(googleMap, offset)
        }
    )

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
}
