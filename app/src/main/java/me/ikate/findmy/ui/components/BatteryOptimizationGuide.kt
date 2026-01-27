package me.ikate.findmy.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.ikate.findmy.util.SamsungDeviceDetector

private const val TAG = "BatteryOptimization"

/**
 * 电池优化引导对话框
 *
 * 引导用户关闭系统电池优化以确保后台服务稳定运行
 * 设计风格与 PermissionGuideDialog 保持一致
 */
@Composable
fun BatteryOptimizationGuideDialog(
    onDismiss: () -> Unit,
    onDontShowAgain: () -> Unit = {}
) {
    val context = LocalContext.current
    val isIgnoringBatteryOptimizations = remember {
        isBatteryOptimizationDisabled(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isIgnoringBatteryOptimizations)
                    Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isIgnoringBatteryOptimizations)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                text = "电池优化设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 当前状态说明
                Text(
                    text = if (isIgnoringBatteryOptimizations)
                        "电池优化已关闭，后台服务运行正常。"
                    else "为确保定位服务稳定运行，请关闭电池优化并将应用加入不休眠列表。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 主设置卡片
                if (!isIgnoringBatteryOptimizations) {
                    BatterySettingCard(
                        title = "关闭电池优化",
                        description = "允许应用在后台持续运行，确保位置共享稳定",
                        onClick = { openBatteryOptimizationSettings(context) }
                    )
                }

                // 厂商特殊设置
                val manufacturer = Build.MANUFACTURER.lowercase()
                val manufacturerSteps = getManufacturerSteps(manufacturer)

                if (manufacturerSteps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${getManufacturerDisplayName(manufacturer)} 额外设置",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    ManufacturerStepsCard(
                        manufacturer = manufacturer,
                        steps = manufacturerSteps,
                        onOpenSettings = { openManufacturerSettings(context, manufacturer) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "⚠️ 设置完成后请返回应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("完成")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDontShowAgain()
                    onDismiss()
                }
            ) {
                Text("不再提示")
            }
        }
    )
}

/**
 * 电池设置卡片
 */
@Composable
private fun BatterySettingCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BatteryFull,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }

            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.height(36.dp)
            ) {
                Text("去设置", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * 厂商特殊步骤卡片
 */
@Composable
private fun ManufacturerStepsCard(
    manufacturer: String,
    steps: List<String>,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            steps.forEachIndexed { index, step ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // 快捷跳转按钮（仅三星设备）
            if (manufacturer == "samsung") {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("打开电池设置")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 获取厂商显示名称
 */
private fun getManufacturerDisplayName(manufacturer: String): String {
    return when (manufacturer) {
        "samsung" -> "三星"
        "xiaomi" -> "小米"
        "huawei" -> "华为"
        "oppo" -> "OPPO"
        "vivo" -> "vivo"
        "oneplus" -> "一加"
        else -> manufacturer.replaceFirstChar { it.uppercase() }
    }
}

/**
 * 获取厂商特殊设置步骤
 */
private fun getManufacturerSteps(manufacturer: String): List<String> {
    return when (manufacturer) {
        "xiaomi" -> listOf(
            "设置 → 应用设置 → 应用管理 → FindMy",
            "开启「自启动」权限",
            "省电策略选择「无限制」"
        )
        "huawei" -> listOf(
            "设置 → 应用 → 应用启动管理",
            "找到 FindMy，关闭自动管理",
            "手动开启「允许自启动」「允许关联启动」「允许后台活动」"
        )
        "oppo" -> listOf(
            "设置 → 电池 → 更多电池设置",
            "关闭「睡眠待机优化」",
            "设置 → 应用管理 → FindMy → 允许自启动"
        )
        "vivo" -> listOf(
            "设置 → 电池 → 后台高耗电",
            "允许 FindMy 后台高耗电",
            "设置 → 更多设置 → 应用程序 → 自启动管理 → 允许 FindMy"
        )
        "samsung" -> getSamsungOptimizationSteps()
        "oneplus" -> listOf(
            "设置 → 电池 → 电池优化",
            "选择 FindMy → 不优化",
            "设置 → 应用 → 应用管理 → FindMy → 允许自启动"
        )
        else -> emptyList()
    }
}

/**
 * 获取三星设备优化步骤（针对 OneUI 6.x 增强版）
 */
private fun getSamsungOptimizationSteps(): List<String> {
    val oneUIVersion = SamsungDeviceDetector.getOneUIVersion()
    val isOneUI6Plus = oneUIVersion?.isAtLeast(6, 0) == true

    return if (isOneUI6Plus) {
        listOf(
            "设置 → 应用程序 → FindMy → 电池",
            "选择「无限制」(而非「已优化」)",
            "返回 → 电池 → 后台使用限制",
            "确认 FindMy 不在「深度睡眠」列表中"
        )
    } else {
        listOf(
            "应用程序 → FindMy → 电池 → 无限制",
            "电池 → 后台使用限制 → 不休眠的应用 → 添加 FindMy"
        )
    }
}

/**
 * 打开厂商特殊设置页面
 */
private fun openManufacturerSettings(context: Context, manufacturer: String) {
    when (manufacturer) {
        "samsung" -> openSamsungBatterySettings(context)
        else -> openBatteryOptimizationSettings(context)
    }
}

/**
 * 打开三星电池设置页面
 */
private fun openSamsungBatterySettings(context: Context) {
    val intents = listOf(
        // OneUI 6.x 电池设置
        Intent().setComponent(
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            )
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        // OneUI 5.x 电池设置
        Intent().setComponent(
            ComponentName(
                "com.samsung.android.sm",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            )
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        // 通用电池设置
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    for (intent in intents) {
        try {
            context.startActivity(intent)
            Log.d(TAG, "成功打开三星电池设置: ${intent.component}")
            return
        } catch (e: Exception) {
            Log.w(TAG, "尝试打开三星电池设置失败: ${intent.component}", e)
        }
    }

    // 所有尝试都失败，回退到标准设置
    openBatteryOptimizationSettings(context)
}

/**
 * 检查是否已关闭电池优化
 */
fun isBatteryOptimizationDisabled(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * 打开电池优化设置
 */
fun openBatteryOptimizationSettings(context: Context) {
    // 尝试直接请求忽略电池优化（会弹出系统对话框）
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        Log.d(TAG, "成功打开电池优化请求对话框")
        return
    } catch (e: Exception) {
        Log.w(TAG, "打开电池优化请求对话框失败", e)
    }

    // 降级为打开电池优化列表页面
    try {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        Log.d(TAG, "成功打开电池优化列表页面")
        return
    } catch (e: Exception) {
        Log.w(TAG, "打开电池优化列表页面失败", e)
    }

    // 最后回退到应用详情页面
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        Log.d(TAG, "回退到应用详情页面")
    } catch (e: Exception) {
        Log.e(TAG, "无法打开任何设置页面", e)
    }
}

/**
 * 紧凑型电池优化提示卡片
 * 用于设置页面或首页提示
 */
@Composable
fun BatteryOptimizationCard(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val isIgnoringBatteryOptimizations = remember {
        isBatteryOptimizationDisabled(context)
    }

    // 如果已关闭优化则不显示
    if (isIgnoringBatteryOptimizations) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        ),
        onClick = onOpenSettings
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BatteryFull,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "优化后台运行",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "关闭电池优化以确保定位稳定",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
