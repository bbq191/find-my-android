package me.ikate.findmy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * FindMy 应用统一圆角样式
 * 与 Samsung S24 Ultra 的屏幕 R 角呼应
 */
object FindMyShapes {
    /** 超大圆角 - 用于 BottomSheet 顶部、全屏 Dialog */
    val ExtraLarge = RoundedCornerShape(28.dp)

    /** 大圆角 - 用于 Card、Dialog */
    val Large = RoundedCornerShape(20.dp)

    /** 中等圆角 - 用于普通卡片、输入框 */
    val Medium = RoundedCornerShape(16.dp)

    /** 小圆角 - 用于 Chip、Button */
    val Small = RoundedCornerShape(12.dp)

    /** 超小圆角 - 用于小型组件 */
    val ExtraSmall = RoundedCornerShape(8.dp)

    /** BottomSheet 顶部圆角 */
    val BottomSheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}
