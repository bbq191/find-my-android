package me.ikate.findmy.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import me.ikate.findmy.ui.screen.main.components.LightPreset

/**
 * CompositionLocal 用于在组件树中传递主题颜色
 */
val LocalMapThemeColors = compositionLocalOf { MapTheme.Day }

/**
 * 地图主题颜色系统
 * 根据光照预设提供对应的界面颜色
 */
@Stable
data class MapThemeColors(
    val primary: Color,           // 主色调
    val primaryVariant: Color,    // 主色调变体
    val surface: Color,           // 表面颜色（卡片、面板）
    val surfaceVariant: Color,    // 表面变体
    val background: Color,        // 背景色
    val onPrimary: Color,         // 主色调上的文字
    val onSurface: Color,         // 表面上的文字
    val onSurfaceVariant: Color,  // 次要文字
    val border: Color,            // 边框颜色
    val buttonBackground: Color,  // 按钮背景
    val buttonIcon: Color,        // 按钮图标
    val accent: Color,            // 强调色
    val isDark: Boolean           // 是否深色主题
)

object MapTheme {

    /**
     * 浅色主题（白天）- 明亮清新
     * 自动应用腾讯地图白浅样式 (styleId=1)
     */
    val Day = MapThemeColors(
        primary = Color(0xFF007AFF),
        primaryVariant = Color(0xFF0056B3),
        surface = Color.White,
        surfaceVariant = Color(0xFFF8F9FA),
        background = Color(0xFFF5F5F5),
        onPrimary = Color.White,
        onSurface = Color(0xFF1C1C1E),
        onSurfaceVariant = Color(0xFF8E8E93),
        border = Color(0xFFE5E5EA),
        buttonBackground = Color.White,
        buttonIcon = Color(0xFF007AFF),
        accent = Color(0xFF34C759),
        isDark = false
    )

    /**
     * 深色主题（夜间）- 深色蓝调
     * 自动应用腾讯地图墨渊样式 (styleId=2)
     */
    val Night = MapThemeColors(
        primary = Color(0xFF0A84FF),
        primaryVariant = Color(0xFF0066CC),
        surface = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFF2C2C2E),
        background = Color(0xFF1C1C1E),
        onPrimary = Color.White,
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFF8E8E93),
        border = Color(0xFF38383A),
        buttonBackground = Color(0xFF2C2C2E),
        buttonIcon = Color(0xFF0A84FF),
        accent = Color(0xFF30D158),
        isDark = true
    )

    /**
     * 根据光照预设获取主题颜色
     * - AUTO: 根据当前时间自动选择 Day 或 Night
     * - LIGHT: Day 主题
     * - DARK: Night 主题
     */
    fun getColors(preset: LightPreset): MapThemeColors {
        val effectivePreset = LightPreset.getEffectivePreset(preset)
        return when (effectivePreset) {
            LightPreset.LIGHT -> Day
            LightPreset.DARK -> Night
            LightPreset.AUTO -> Day // 不会到达这里，AUTO 会被 getEffectivePreset 转换
        }
    }
}

/**
 * 动画过渡的主题颜色
 * 当光照预设改变时，颜色会平滑过渡
 */
@Composable
fun animatedMapThemeColors(preset: LightPreset): MapThemeColors {
    val targetColors = MapTheme.getColors(preset)
    val animationSpec = tween<Color>(durationMillis = 500)

    val primary by animateColorAsState(targetColors.primary, animationSpec, label = "primary")
    val primaryVariant by animateColorAsState(targetColors.primaryVariant, animationSpec, label = "primaryVariant")
    val surface by animateColorAsState(targetColors.surface, animationSpec, label = "surface")
    val surfaceVariant by animateColorAsState(targetColors.surfaceVariant, animationSpec, label = "surfaceVariant")
    val background by animateColorAsState(targetColors.background, animationSpec, label = "background")
    val onPrimary by animateColorAsState(targetColors.onPrimary, animationSpec, label = "onPrimary")
    val onSurface by animateColorAsState(targetColors.onSurface, animationSpec, label = "onSurface")
    val onSurfaceVariant by animateColorAsState(targetColors.onSurfaceVariant, animationSpec, label = "onSurfaceVariant")
    val border by animateColorAsState(targetColors.border, animationSpec, label = "border")
    val buttonBackground by animateColorAsState(targetColors.buttonBackground, animationSpec, label = "buttonBackground")
    val buttonIcon by animateColorAsState(targetColors.buttonIcon, animationSpec, label = "buttonIcon")
    val accent by animateColorAsState(targetColors.accent, animationSpec, label = "accent")

    return MapThemeColors(
        primary = primary,
        primaryVariant = primaryVariant,
        surface = surface,
        surfaceVariant = surfaceVariant,
        background = background,
        onPrimary = onPrimary,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        border = border,
        buttonBackground = buttonBackground,
        buttonIcon = buttonIcon,
        accent = accent,
        isDark = targetColors.isDark
    )
}
