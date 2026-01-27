package me.ikate.findmy.ui.screen.geofence

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import me.ikate.findmy.ui.screen.main.components.CustomBottomSheet
import me.ikate.findmy.ui.screen.main.components.SheetValue
import me.ikate.findmy.util.rememberHaptics

/**
 * iOS Find My 风格地理围栏编辑器
 * 全屏地图底座 + 悬浮控制面板架构
 *
 * @param contactName 联系人名称
 * @param contactLocation 联系人位置
 * @param myLocation 我的位置
 * @param currentConfig 当前围栏配置（编辑模式）
 * @param onDismiss 关闭回调
 * @param onConfirm 确认保存回调
 */
@Composable
fun GeofenceEditorScreen(
    contactName: String,
    contactLocation: LatLng?,
    myLocation: LatLng? = null,
    currentConfig: GeofenceConfig = GeofenceConfig(),
    onDismiss: () -> Unit,
    onConfirm: (GeofenceConfig) -> Unit
) {
    val haptics = rememberHaptics()

    // 初始化状态
    val state = rememberGeofenceEditorState(
        initialConfig = currentConfig,
        contactLocation = contactLocation,
        myLocation = myLocation
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        if (contactLocation == null) {
            // 无位置时显示错误
            NoLocationError()
        } else {
            // 正常编辑界面
            CustomBottomSheet(
                modifier = Modifier.fillMaxSize(),
                initialValue = SheetValue.HalfExpanded,
                maxExpandedFraction = 0.85f,
                backgroundContent = {
                    // 层级 A：全屏地图层
                    GeofenceMapLayer(
                        state = state,
                        contactLocation = contactLocation,
                        onCenterChanged = { latLng ->
                            state.updateCenter(latLng)
                        },
                        onAddressChanged = { address ->
                            state.updateAddress(address)
                        },
                        onDraggingChanged = { isDragging ->
                            state.updateDraggingState(isDragging)
                        }
                    )
                },
                sheetContent = {
                    // 层级 B：底部控制面板
                    GeofenceControlPanel(
                        state = state,
                        contactName = contactName,
                        haptics = haptics,
                        onSave = {
                            val config = state.buildConfig(contactName)
                            onConfirm(config)
                        },
                        onDelete = {
                            onConfirm(GeofenceConfig(enabled = false))
                        },
                        onDismiss = onDismiss
                    )
                }
            )
        }
    }
}

/**
 * 无位置错误提示
 */
@Composable
private fun NoLocationError() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "无法设置位置通知",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "联系人位置不可用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
