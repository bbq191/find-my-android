package me.ikate.findmy.ui.screen.main.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.ikate.findmy.util.rememberHaptics

/**
 * 定位按钮组件 (Small FAB 样式)
 * 显示在地图右上角，用于控制地图中心定位到用户位置
 * 仿 iOS Find My 风格：统一使用箭头图标，通过实心/空心区分状态
 *
 * 使用 Material 3 主题色以与主体 UI 风格保持一致
 *
 * @param isLocationCentered 地图中心是否在用户位置（true = 实心箭头，false = 空心箭头）
 * @param onClick 点击事件回调
 * @param modifier 修饰符
 */
@Composable
fun LocationButton(
    isLocationCentered: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()

    FloatingActionButton(
        onClick = {
            haptics.click()  // 定位按钮：清脆的点击感
            onClick()
        },
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 4.dp,
            pressedElevation = 6.dp
        )
    ) {
        Icon(
            imageVector = if (isLocationCentered) {
                Icons.Filled.NearMe
            } else {
                Icons.Outlined.NearMe
            },
            contentDescription = if (isLocationCentered) {
                "已定位到当前位置"
            } else {
                "定位到当前位置"
            },
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}
