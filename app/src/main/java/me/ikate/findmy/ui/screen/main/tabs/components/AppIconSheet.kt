package me.ikate.findmy.ui.screen.main.tabs.components

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.ikate.findmy.R
import me.ikate.findmy.ui.theme.FindMyShapes
import me.ikate.findmy.util.rememberHaptics

/**
 * 图标选项数据类
 */
private data class IconOption(
    val id: String,
    val name: String,
    val description: String,
    val componentName: String,
    val iconRes: Int
)

/**
 * 应用图标选择 BottomSheet
 *
 * 设计特点：
 * - 顶部预览区：模拟桌面背景，实时展示选中图标效果
 * - 图标网格：使用 LazyVerticalGrid 展示所有可选图标
 * - 选中反馈：粗圆角边框 + 对勾角标
 * - 底部宽按钮确认操作
 *
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val packageManager = context.packageManager
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 图标选项列表
    val iconOptions = remember {
        listOf(
            IconOption(
                id = "boy",
                name = "男孩",
                description = "默认卡通风格",
                componentName = ".MainActivityBoy",
                iconRes = R.mipmap.ic_launcher_boy
            ),
            IconOption(
                id = "girl",
                name = "女孩",
                description = "可爱卡通风格",
                componentName = ".MainActivityGirl",
                iconRes = R.mipmap.ic_launcher_girl
            )
        )
    }

    // 获取当前启用的图标
    var selectedIcon by remember {
        mutableStateOf(
            iconOptions.firstOrNull { option ->
                try {
                    val componentName = ComponentName(
                        context.packageName,
                        context.packageName + option.componentName
                    )
                    packageManager.getComponentEnabledSetting(componentName) ==
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } catch (_: Exception) {
                    false
                }
            } ?: iconOptions.first()
        )
    }

    // 记录初始图标，用于判断是否有变化
    val initialIcon = remember { selectedIcon }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = FindMyShapes.BottomSheetTop,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 36.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            Text(
                text = "选择应用图标",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 预览区 - 模拟桌面背景
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 图标预览（带阴影效果）
                    AsyncImage(
                        model = selectedIcon.iconRes,
                        contentDescription = "图标预览",
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(8.dp, RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                    )

                    // 应用名称标签
                    Text(
                        text = "Find My",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "桌面预览效果",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 图标选择网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.height(180.dp)
            ) {
                items(iconOptions) { option ->
                    IconOptionItem(
                        option = option,
                        isSelected = selectedIcon.id == option.id,
                        onClick = {
                            haptics.click()
                            selectedIcon = option
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 提示文字
            Text(
                text = "切换图标后，桌面图标可能需要几秒钟才能更新",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 应用按钮
            Button(
                onClick = {
                    haptics.confirm()
                    // 切换图标
                    iconOptions.forEach { opt ->
                        val componentName = ComponentName(
                            context.packageName,
                            context.packageName + opt.componentName
                        )
                        val newState = if (opt.id == selectedIcon.id) {
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        } else {
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        }
                        packageManager.setComponentEnabledSetting(
                            componentName,
                            newState,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                enabled = selectedIcon.id != initialIcon.id
            ) {
                Text(
                    text = if (selectedIcon.id != initialIcon.id) "应用图标" else "当前已选择",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 图标选项卡片
 */
@Composable
private fun IconOptionItem(
    option: IconOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            ),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(12.dp)
            ) {
                // 图标
                Box(contentAlignment = Alignment.TopEnd) {
                    AsyncImage(
                        model = option.iconRes,
                        contentDescription = option.name,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )

                    // 选中对勾
                    if (isSelected) {
                        Surface(
                            modifier = Modifier
                                .size(22.dp)
                                .padding(2.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 名称
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                // 描述
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
