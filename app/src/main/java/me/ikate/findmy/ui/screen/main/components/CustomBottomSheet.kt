package me.ikate.findmy.ui.screen.main.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import me.ikate.findmy.ui.theme.FindMyShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.ikate.findmy.util.rememberHaptics

/**
 * 底部面板的三种状态
 */
enum class SheetValue {
    /** 折叠态 - 20% 屏幕高度 */
    Collapsed,

    /** 半展开态 - 约 50% 屏幕高度（默认） */
    HalfExpanded,

    /** 全展开态 - 约 95% 屏幕高度 */
    Expanded
}

/**
 * 自定义三态底部面板
 * 实现可拖拽的三种状态：Collapsed、HalfExpanded、Expanded
 *
 * @param sheetContent 面板内容
 * @param modifier 修饰符
 * @param backgroundContent 背景内容（通常是地图）
 * @param initialValue 初始状态（默认为半展开）
 * @param maxExpandedFraction 最大展开比例（0-1），默认 0.75 即 75% 屏幕高度
 * @param onSheetValueChange 面板状态变化回调
 * @param onOffsetChange 面板偏移量变化回调（用于地图 Padding 联动）
 */
@Composable
fun CustomBottomSheet(
    sheetContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    backgroundContent: @Composable BoxScope.() -> Unit = {},
    initialValue: SheetValue = SheetValue.HalfExpanded,
    maxExpandedFraction: Float = 0.75f,
    onSheetValueChange: (SheetValue) -> Unit = {},
    onOffsetChange: (Float) -> Unit = {}
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    BoxWithConstraints(modifier = modifier) {
        // 获取父容器最大高度 (px)
        val maxHeightPx = constraints.maxHeight.toFloat()

        // 计算三个锚点的偏移量（从屏幕底部向上的距离，单位：像素）
        val collapsedOffset = maxHeightPx * 0.06f   // 折叠态：6%
        val halfExpandedOffset = maxHeightPx * 0.35f // 半展开态：35%
        val expandedOffset = maxHeightPx * maxExpandedFraction.coerceIn(0.4f, 0.95f) // 全展开态：受限于参数

        // 当前偏移量
        val initialOffsetValue = when (initialValue) {
            SheetValue.Collapsed -> collapsedOffset
            SheetValue.HalfExpanded -> halfExpandedOffset
            SheetValue.Expanded -> expandedOffset
        }

        // 防御性检查：确保初始值不是 NaN
        val safeInitialOffset = if (initialOffsetValue.isNaN()) 0f else initialOffsetValue

        // 使用 Animatable 实现弹簧动画
        val offsetAnimatable = remember(maxHeightPx) {
            Animatable(safeInitialOffset)
        }
        val currentOffset = offsetAnimatable.value

        // 初始化时通知偏移量
        LaunchedEffect(currentOffset) {
            if (!currentOffset.isNaN()) {
                onOffsetChange(currentOffset)
            }
        }

        // 背景内容（地图）
        Box(modifier = Modifier.fillMaxSize()) {
            backgroundContent()
        }

        // 确保传递给 height 的值有效且不为 0，避免除零错误
        val sheetHeightDp =
            if (currentOffset.isNaN() || currentOffset <= 1f) 1.dp else with(density) { currentOffset.toDp() }

        // 底部面板
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeightDp)
                .align(Alignment.BottomCenter)
                .pointerInput(maxHeightPx) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            // 拖拽结束，根据当前位置吸附到最近的锚点（带弹簧动画）
                            val currentValue = offsetAnimatable.value
                            if (currentValue.isNaN()) {
                                scope.launch {
                                    offsetAnimatable.snapTo(safeInitialOffset)
                                }
                                onSheetValueChange(initialValue)
                                onOffsetChange(safeInitialOffset)
                                return@detectVerticalDragGestures
                            }

                            val targetOffset = when {
                                currentValue < (collapsedOffset + halfExpandedOffset) / 2 ->
                                    collapsedOffset

                                currentValue < (halfExpandedOffset + expandedOffset) / 2 ->
                                    halfExpandedOffset

                                else -> expandedOffset
                            }

                            val finalOffset =
                                if (targetOffset.isNaN()) safeInitialOffset else targetOffset

                            val newState = when (finalOffset) {
                                collapsedOffset -> SheetValue.Collapsed
                                halfExpandedOffset -> SheetValue.HalfExpanded
                                else -> SheetValue.Expanded
                            }

                            // 使用弹簧动画吸附到锚点
                            scope.launch {
                                offsetAnimatable.animateTo(
                                    targetValue = finalOffset,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                // 吸附完成时触发震动：模拟抽屉关上的"咔哒"感
                                haptics.gestureEnd()
                            }
                            onSheetValueChange(newState)
                        },
                        onVerticalDrag = { _, dragAmount ->
                            val safeDragAmount = if (dragAmount.isNaN()) 0f else dragAmount

                            var newOffset = offsetAnimatable.value - safeDragAmount
                            if (newOffset.isNaN()) {
                                newOffset = safeInitialOffset
                            }
                            newOffset = newOffset.coerceIn(
                                collapsedOffset,
                                expandedOffset
                            )
                            // 拖拽时直接更新，无动画
                            scope.launch {
                                offsetAnimatable.snapTo(newOffset)
                            }
                        }
                    )
                },
            shape = FindMyShapes.BottomSheetTop, // 统一 28dp 超大圆角
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp, // 增强阴影
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                // 拖拽手柄 - Material 3 风格
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 面板内容容器
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    sheetContent()
                }
            }
        }
    }
}
