package me.ikate.findmy.ui.screen.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import com.tencent.tencentmap.mapsdk.maps.TencentMap
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.ui.components.LocationPermissionGuide
import me.ikate.findmy.ui.navigation.FindMyTab
import me.ikate.findmy.ui.screen.main.components.TencentMapViewWrapper
import me.ikate.findmy.ui.screen.main.components.MapLayerConfig
import android.provider.Settings as AndroidSettings

/**
 * 地图区域组件
 * 包含腾讯地图和位置权限引导层
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainMapSection(
    modifier: Modifier = Modifier,
    devices: List<Device>,
    contacts: List<Contact>,
    selectedTab: FindMyTab,
    currentDeviceHeading: Float?,
    isTrafficEnabled: Boolean,
    mapLayerConfig: MapLayerConfig,
    bottomPadding: Dp,
    locationPermissionsState: MultiplePermissionsState,
    onMapReady: (TencentMap) -> Unit,
    onMarkerClick: (Device) -> Unit,
    onContactMarkerClick: (Contact) -> Unit,
    onMapClick: () -> Unit
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        // 腾讯地图
        TencentMapViewWrapper(
            modifier = Modifier.fillMaxSize(),
            devices = devices,
            contacts = if (selectedTab == FindMyTab.PEOPLE) contacts else emptyList(),
            currentDeviceHeading = currentDeviceHeading,
            showTraffic = isTrafficEnabled,
            mapLayerConfig = mapLayerConfig,
            bottomPadding = bottomPadding,
            onMapReady = onMapReady,
            onMarkerClick = onMarkerClick,
            onContactMarkerClick = onContactMarkerClick,
            onMapClick = onMapClick
        )

        // 位置权限未授予时显示引导
        if (!locationPermissionsState.allPermissionsGranted) {
            val isPermanentlyDenied = !locationPermissionsState.shouldShowRationale &&
                locationPermissionsState.permissions.any { !it.status.isGranted }

            LocationPermissionGuide(
                onRequestPermission = {
                    locationPermissionsState.launchMultiplePermissionRequest()
                },
                onOpenSettings = {
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
}
