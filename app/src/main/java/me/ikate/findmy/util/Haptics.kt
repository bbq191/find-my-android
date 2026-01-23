package me.ikate.findmy.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * 震动反馈工具类
 *
 * 针对 Samsung S24 Ultra X轴线性马达优化
 * 提供精细化的触觉反馈，让应用有"精密仪器"的高级感
 *
 * 使用场景分类：
 * 1. click() - 轻触反馈：Tab切换、Chip点击、普通按钮
 * 2. tick() - 刻度反馈：Slider滑动、列表刻度
 * 3. success() - 成功反馈：BottomSheet吸附、关键操作确认
 * 4. error() - 错误反馈：操作失败、拒绝
 * 5. longPress() - 长按反馈：地图长按选点
 * 6. confirm() - 确认反馈：播放声音等远程控制指令
 */
object Haptics {

    /**
     * 1. 轻触反馈 (用于 Tab切换, Chip点击, 普通按钮)
     * 效果：清脆的点击感
     */
    fun click(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /**
     * 2. 刻度/选择反馈 (用于 Slider滑动, 列表刻度)
     * 效果：模拟齿轮/时钟的刻度感
     */
    fun tick(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /**
     * 3. 成功/吸附反馈 (用于 BottomSheet吸附, 关键操作确认)
     * 效果：沉稳的确认感
     */
    fun success(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /**
     * 4. 错误/拒绝反馈 (用于 操作失败, 错误提示)
     * 效果：快速的两次震动
     */
    fun error(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            // 低版本使用 LONG_PRESS 作为替代
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /**
     * 5. 长按反馈 (用于 地图长按选点)
     * 效果：沉稳的震动，提示"已锁定"
     */
    fun longPress(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /**
     * 6. 确认反馈 (用于 播放声音等远程控制指令)
     * 效果：较强的震动，明确的"发送成功"反馈
     */
    fun confirm(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /**
     * 7. 拖拽开始反馈 (用于 BottomSheet开始拖拽)
     * 效果：轻微的开始感
     */
    fun dragStart(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.DRAG_START)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /**
     * 8. 手势结束反馈 (用于 BottomSheet吸附完成)
     * 效果：模拟抽屉关上的"咔哒"感
     */
    fun gestureEnd(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        } else {
            success(view)
        }
    }
}

/**
 * Compose 中方便使用的 Haptics 扩展
 */
class ComposeHaptics(
    private val view: View,
    private val hapticFeedback: HapticFeedback
) {
    /** 轻触反馈 */
    fun click() = Haptics.click(view)

    /** 刻度反馈 */
    fun tick() = Haptics.tick(view)

    /** 成功反馈 */
    fun success() = Haptics.success(view)

    /** 错误反馈 */
    fun error() = Haptics.error(view)

    /** 长按反馈 */
    fun longPress() = Haptics.longPress(view)

    /** 确认反馈 */
    fun confirm() = Haptics.confirm(view)

    /** 拖拽开始反馈 */
    fun dragStart() = Haptics.dragStart(view)

    /** 手势结束反馈 */
    fun gestureEnd() = Haptics.gestureEnd(view)

    /** 使用系统标准的 performHapticFeedback */
    fun perform(type: HapticFeedbackType) = hapticFeedback.performHapticFeedback(type)
}

/**
 * 在 Compose 中获取 Haptics 实例
 */
@Composable
fun rememberHaptics(): ComposeHaptics {
    val view = LocalView.current
    val hapticFeedback = LocalHapticFeedback.current
    return remember(view, hapticFeedback) {
        ComposeHaptics(view, hapticFeedback)
    }
}
