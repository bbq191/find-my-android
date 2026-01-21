package me.ikate.findmy.ui.screen.main.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.ikate.findmy.ui.theme.MapTheme
import me.ikate.findmy.ui.theme.MapThemeColors

/**
 * 定位按钮组件
 * 显示在地图右上角，用于控制地图中心定位到用户位置
 * 仿 iOS Find My 风格：统一使用箭头图标，通过实心/空心区分状态
 *
 * @param isLocationCentered 地图中心是否在用户位置（true = 实心箭头，false = 空心箭头）
 * @param onClick 点击事件回调
 * @param themeColors 主题颜色
 * @param modifier 修饰符
 */
@Composable
fun LocationButton(
    isLocationCentered: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    themeColors: MapThemeColors = MapTheme.Day
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        containerColor = themeColors.buttonBackground,
        contentColor = themeColors.buttonIcon,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Icon(
            imageVector = if (isLocationCentered) {
                Icons.Filled.NearMe  // 实心箭头：已定位
            } else {
                Icons.Outlined.NearMe  // 空心箭头：未定位
            },
            contentDescription = if (isLocationCentered) {
                "已定位到当前位置"
            } else {
                "定位到当前位置"
            },
            tint = if (isLocationCentered) {
                themeColors.buttonIcon
            } else {
                themeColors.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp)
        )
    }
}
