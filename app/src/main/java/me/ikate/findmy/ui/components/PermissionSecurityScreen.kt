package me.ikate.findmy.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import me.ikate.findmy.util.DeviceAdminHelper
import me.ikate.findmy.util.PermissionStatusChecker

/**
 * 权限与安全页面
 * 整合所有权限状态的统一管理入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSecurityScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 权限状态
    var permissionStatus by remember {
        mutableStateOf(PermissionStatusChecker.checkAllPermissions(context))
    }

    // 监听生命周期，返回时刷新权限状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionStatus = PermissionStatusChecker.checkAllPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 计算已开启的权限数量
    val enabledCount = listOf(
        permissionStatus.hasLocationPermission,
        permissionStatus.hasBackgroundLocationPermission,
        permissionStatus.hasBatteryOptimizationDisabled,
        permissionStatus.hasNotificationPermission,
        permissionStatus.hasDeviceAdminActive
    ).count { it }
    val totalCount = 5
    val progress by animateFloatAsState(
        targetValue = enabledCount.toFloat() / totalCount,
        label = "progress"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "权限与安全",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        permissionStatus = PermissionStatusChecker.checkAllPermissions(context)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 权限状态概览卡片
            PermissionOverviewCard(
                enabledCount = enabledCount,
                totalCount = totalCount,
                progress = progress,
                hasAllPermissions = permissionStatus.hasAllPermissions
            )

            // 必要权限区域
            Text(
                text = "必要权限",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            // 位置权限
            PermissionItemCard(
                icon = Icons.Default.LocationOn,
                title = "位置权限",
                description = "获取设备位置以便共享给联系人",
                isEnabled = permissionStatus.hasLocationPermission,
                isCritical = true,
                onEnableClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )

            // 后台定位权限
            PermissionItemCard(
                icon = Icons.Default.MyLocation,
                title = "后台定位",
                description = "允许应用在后台持续更新位置",
                isEnabled = permissionStatus.hasBackgroundLocationPermission,
                isCritical = true,
                onEnableClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )

            // 可选权限区域
            Text(
                text = "推荐权限",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            // 电池优化
            PermissionItemCard(
                icon = Icons.Default.BatterySaver,
                title = "忽略电池优化",
                description = "防止系统休眠时中断定位服务",
                isEnabled = permissionStatus.hasBatteryOptimizationDisabled,
                isCritical = false,
                onEnableClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )

            // 通知权限
            PermissionItemCard(
                icon = Icons.Default.Notifications,
                title = "通知权限",
                description = "接收位置更新和设备查找通知",
                isEnabled = permissionStatus.hasNotificationPermission,
                isCritical = false,
                onEnableClick = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    }
                    context.startActivity(intent)
                }
            )

            // 设备管理员
            PermissionItemCard(
                icon = Icons.Default.PhonelinkLock,
                title = "设备管理员",
                description = "启用丢失模式的锁屏和数据擦除功能",
                isEnabled = permissionStatus.hasDeviceAdminActive,
                isCritical = false,
                onEnableClick = {
                    val intent = DeviceAdminHelper.createActivationIntent(context)
                    context.startActivity(intent)
                }
            )

            // 底部说明
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "关于权限",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "必要权限用于核心功能正常运行，推荐权限可以提升使用体验。" +
                                    "您可以随时在系统设置中管理这些权限。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 权限概览卡片
 */
@Composable
private fun PermissionOverviewCard(
    enabledCount: Int,
    totalCount: Int,
    progress: Float,
    hasAllPermissions: Boolean
) {
    val progressColor by animateColorAsState(
        targetValue = when {
            hasAllPermissions -> MaterialTheme.colorScheme.primary
            enabledCount >= 3 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.error
        },
        label = "progressColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (hasAllPermissions) "权限状态良好" else "部分权限未开启",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$enabledCount / $totalCount 项权限已开启",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 圆形进度指示
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(56.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 6.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        color = progressColor,
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = progressColor
                    )
                }
            }

            if (!hasAllPermissions) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "开启所有推荐权限可以获得最佳使用体验",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 权限项卡片
 */
@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    isEnabled: Boolean,
    isCritical: Boolean,
    onEnableClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            isEnabled -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            isCritical -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "containerColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isEnabled, onClick = onEnableClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (isEnabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else if (isCritical) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (isEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else if (isCritical) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 标题和描述
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (isCritical && !isEnabled) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "必要",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 状态指示
            if (isEnabled) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "已开启",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            } else {
                TextButton(onClick = onEnableClick) {
                    Text(
                        text = "开启",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * 获取权限状态摘要文本
 */
fun getPermissionStatusSummary(status: PermissionStatusChecker.PermissionStatus): String {
    val enabledCount = listOf(
        status.hasLocationPermission,
        status.hasBackgroundLocationPermission,
        status.hasBatteryOptimizationDisabled,
        status.hasNotificationPermission,
        status.hasDeviceAdminActive
    ).count { it }

    return if (status.hasAllPermissions) {
        "所有权限已开启"
    } else {
        "$enabledCount/5 项权限已开启"
    }
}
