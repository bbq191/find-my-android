package me.ikate.findmy.ui.screen.main.tabs.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.ikate.findmy.BuildConfig
import me.ikate.findmy.R
import me.ikate.findmy.ui.theme.FindMyShapes
import me.ikate.findmy.util.rememberHaptics

/**
 * 关于页面 BottomSheet
 *
 * 设计特点（品牌海报式）：
 * - Logo 英雄区：大尺寸 App Logo + 阴影
 * - 版本徽章：灰色胶囊包裹
 * - Slogan：精致小字替代功能列表
 * - 功能菜单：带箭头的点击项
 * - 版权信息：底部极小灰色字体
 *
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val currentIconRes = remember { getCurrentAppIconRes(context) }

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
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo 英雄区
            AsyncImage(
                model = currentIconRes,
                contentDescription = "应用图标",
                modifier = Modifier
                    .size(88.dp)
                    .shadow(12.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 应用名称
            Text(
                text = context.getString(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 版本徽章
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Slogan
            Text(
                text = "连接你我，守护安全",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 功能菜单
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    // 去评分
                    AboutMenuItem(
                        icon = Icons.Default.RateReview,
                        title = "去评分",
                        onClick = {
                            haptics.click()
                            openAppStore(context)
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // 检查更新
                    AboutMenuItem(
                        icon = Icons.Default.Refresh,
                        title = "检查更新",
                        onClick = {
                            haptics.click()
                            // TODO: 实现检查更新逻辑
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // 访问官网
                    AboutMenuItem(
                        icon = Icons.Default.Web,
                        title = "访问项目主页",
                        onClick = {
                            haptics.click()
                            openUrl(context, "https://github.com/nichem/findmy")
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 版权信息
            Text(
                text = "© 2026 Neurone Studio. All rights reserved.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 关于页面菜单项
 */
@Composable
private fun AboutMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 获取当前启用的应用图标资源
 */
private fun getCurrentAppIconRes(context: Context): Int {
    val packageManager = context.packageManager
    val packageName = context.packageName

    return try {
        val girlComponent = ComponentName(packageName, "$packageName.MainActivityGirl")
        if (packageManager.getComponentEnabledSetting(girlComponent) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        ) {
            R.mipmap.ic_launcher_girl
        } else {
            R.mipmap.ic_launcher_boy
        }
    } catch (_: Exception) {
        R.mipmap.ic_launcher_boy
    }
}

/**
 * 打开应用商店
 */
private fun openAppStore(context: Context) {
    try {
        // 优先尝试打开三星应用商店
        val samsungIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("samsungapps://ProductDetail/${context.packageName}")
            setPackage("com.sec.android.app.samsungapps")
        }
        if (samsungIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(samsungIntent)
            return
        }

        // 回退到 Google Play
        val playIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=${context.packageName}")
        }
        if (playIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(playIntent)
            return
        }

        // 最后使用网页链接
        openUrl(context, "https://play.google.com/store/apps/details?id=${context.packageName}")
    } catch (_: Exception) {
        // 忽略异常
    }
}

/**
 * 打开 URL
 */
private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {
        // 忽略异常
    }
}
