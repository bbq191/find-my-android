package me.ikate.findmy.ui.screen.main.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 定位按钮组件
 * 显示在地图右下角，用于控制地图中心定位到用户位置
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
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        containerColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Icon(
            imageVector = if (isLocationCentered) {
                // 实心箭头 - 地图中心已在用户位置
                Icons.Filled.MyLocation
            } else {
                // 空心箭头 - 地图中心不在用户位置
                Icons.Outlined.LocationOn
            },
            contentDescription = if (isLocationCentered) {
                "已定位到当前位置"
            } else {
                "定位到当前位置"
            },
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
