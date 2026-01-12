package me.ikate.findmy.ui.screen.main.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

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
 * @param onSheetValueChange 面板状态变化回调
 * @param onOffsetChange 面板偏移量变化回调（用于地图 Padding 联动）
 */
@Composable
fun CustomBottomSheet(
    sheetContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    backgroundContent: @Composable BoxScope.() -> Unit = {},
    initialValue: SheetValue = SheetValue.HalfExpanded,
    onSheetValueChange: (SheetValue) -> Unit = {},
    onOffsetChange: (Float) -> Unit = {}
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        // 获取父容器最大高度 (px)
        val maxHeightPx = constraints.maxHeight.toFloat()

        // 计算三个锚点的偏移量（从屏幕底部向上的距离，单位：像素）
        val collapsedOffset = maxHeightPx * 0.06f   // 折叠态：20%
        val halfExpandedOffset = maxHeightPx * 0.35f // 半展开态：50%
        val expandedOffset = maxHeightPx * 0.85f   // 全展开态：95%

        // 当前偏移量
        val initialOffsetValue = when (initialValue) {
            SheetValue.Collapsed -> collapsedOffset
            SheetValue.HalfExpanded -> halfExpandedOffset
            SheetValue.Expanded -> expandedOffset
        }

        // 防御性检查：确保初始值不是 NaN
        val safeInitialOffset = if (initialOffsetValue.isNaN()) 0f else initialOffsetValue

        var currentOffset by remember(maxHeightPx) {
            mutableFloatStateOf(safeInitialOffset)
        }

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
                // 关键：避免被系统导航栏遮挡，增加底部内边距
                // 注意：这里不在 height 上加 padding，而是在内部布局处理，或者让 Surface 整体上移？
                // 如果是 Edge-to-Edge，align(BottomCenter) 会贴着物理屏幕底部。
                // navigationBarsPadding() 会给内容增加 padding，但 Surface 背景依然贴底。
                // 这样是正确的。
                .pointerInput(maxHeightPx) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            // 拖拽结束，根据当前位置吸附到最近的锚点
                            // 防御 NaN
                            if (currentOffset.isNaN()) {
                                currentOffset = safeInitialOffset
                                onSheetValueChange(initialValue)
                                onOffsetChange(safeInitialOffset)
                                return@detectVerticalDragGestures
                            }

                            val targetOffset = when {
                                currentOffset < (collapsedOffset + halfExpandedOffset) / 2 ->
                                    collapsedOffset

                                currentOffset < (halfExpandedOffset + expandedOffset) / 2 ->
                                    halfExpandedOffset

                                else -> expandedOffset
                            }

                            val finalOffset =
                                if (targetOffset.isNaN()) safeInitialOffset else targetOffset
                            currentOffset = finalOffset

                            // 回调状态变化
                            val newState = when (finalOffset) {
                                collapsedOffset -> SheetValue.Collapsed
                                halfExpandedOffset -> SheetValue.HalfExpanded
                                else -> SheetValue.Expanded
                            }
                            onSheetValueChange(newState)
                            // 动画结束后再通知？这里为了响应速度直接通知
                            onOffsetChange(finalOffset)
                        },
                        onVerticalDrag = { _, dragAmount ->
                            // 拖拽过程中更新偏移量
                            // dragAmount > 0 是向下，< 0 是向上
                            // newOffset = currentOffset - dragAmount
                            // 向上拖动 (dragAmount < 0) -> offset 增加
                            // 向下拖动 (dragAmount > 0) -> offset 减少

                            // 这里的 dragAmount 也有可能是 NaN? 极低概率，但防一下
                            val safeDragAmount = if (dragAmount.isNaN()) 0f else dragAmount

                            var newOffset = currentOffset - safeDragAmount
                            if (newOffset.isNaN()) {
                                newOffset = safeInitialOffset
                            }
                            newOffset = newOffset.coerceIn(
                                collapsedOffset,
                                expandedOffset
                            )
                            currentOffset = newOffset
                            onOffsetChange(newOffset)
                        }
                    )
                },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                // 内容区域避开系统导航栏
                // 这里不需要 navigationBarsPadding，因为 Surface 高度包含了导航栏区域。
                // 但是如果内容也是到底部的，会被遮挡。
                // 更好的做法是：Surface 延伸到底部，但 Column 底部留出空间。
                // 或者给 Column 加 .windowInsetsPadding(WindowInsets.navigationBars)
                // 但是注意，CustomBottomSheet 的 offset 是从物理底部算的。
            ) {
                // 拖拽手柄
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.Gray.copy(alpha = 0.3f))
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 面板内容容器
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // 占满剩余空间
                ) {
                    sheetContent()
                }
            }
        }
    }
}
