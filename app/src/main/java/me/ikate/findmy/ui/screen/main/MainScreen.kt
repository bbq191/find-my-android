package me.ikate.findmy.ui.screen.main

import android.Manifest
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import me.ikate.findmy.ui.screen.device.DeviceManagementViewModel
import me.ikate.findmy.ui.screen.main.components.CustomBottomSheet
import me.ikate.findmy.ui.screen.main.components.DeviceDetailPanel
import me.ikate.findmy.ui.screen.main.components.DeviceListPanel
import me.ikate.findmy.ui.screen.main.components.LocationButton
import me.ikate.findmy.ui.screen.main.components.MapLayerButton
import me.ikate.findmy.ui.screen.main.components.MapViewWrapper
import me.ikate.findmy.util.MapCameraHelper

/**
 * 主屏幕 - Find My 应用的主界面
 * 包含全屏地图视图和交互组件
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    deviceManagementViewModel: DeviceManagementViewModel = viewModel()
) {
    // 定位权限状态（增量请求：先粗略定位，再精确定位）
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    )

    // 收集 ViewModel 状态
    val googleMap by viewModel.googleMap.collectAsState()
    val isLocationCentered by viewModel.isLocationCentered.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()

    // 底部面板偏移量（用于动态调整按钮位置）
    var bottomSheetOffsetPx by remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bottomSheetOffsetDp = with(density) { bottomSheetOffsetPx.toDp() }

    // 首次启动时请求定位权限
    LaunchedEffect(Unit) {
        if (!locationPermissionsState.allPermissionsGranted) {
            locationPermissionsState.launchMultiplePermissionRequest()
        }
    }

    // 使用自定义底部面板包裹地图和设备列表
    CustomBottomSheet(
        modifier = Modifier.fillMaxSize(),
        backgroundContent = {
            // 背景：全屏地图视图
            MapViewWrapper(
                modifier = Modifier.fillMaxSize(),
                devices = devices, // 传递设备列表用于渲染 Marker
                onMapReady = { map ->
                    viewModel.setGoogleMap(map)
                },
                onMarkerClick = { device ->
                    // 点击 Marker 时，选中设备并移动地图到设备位置
                    viewModel.selectDevice(device)
                    MapCameraHelper.animateToDevice(googleMap, device, zoom = 15f)
                },
                onMapClick = {
                    // 点击地图空白区域，取消选中设备
                    viewModel.clearSelectedDevice()
                }
            )

            // 定位按钮（右下角）
            if (locationPermissionsState.allPermissionsGranted) {
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
            // 显示设备列表（移除详情页面）
            DeviceListPanel(
                devices = devices,
                onDeviceClick = { device ->
                    // 点击设备时，移动地图到设备位置
                    MapCameraHelper.animateToDevice(googleMap, device, zoom = 15f)
                },
                onDeviceDelete = { device ->
                    // 删除设备
                    deviceManagementViewModel.deleteDevice(device.id)
                }
            )
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
    if (locationPermissionsState.shouldShowRationale) {
        AlertDialog(
            onDismissRequest = { /* 不允许关闭 */ },
            title = { Text("需要定位权限") },
            text = {
                Text(
                    "Find My 需要访问您的位置信息才能在地图上显示您的当前位置。" +
                            "请授予定位权限以获得完整体验。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        locationPermissionsState.launchMultiplePermissionRequest()
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
}
