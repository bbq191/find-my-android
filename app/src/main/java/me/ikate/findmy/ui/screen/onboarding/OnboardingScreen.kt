package me.ikate.findmy.ui.screen.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import me.ikate.findmy.util.DeviceAdminHelper
import me.ikate.findmy.util.OnboardingPreferences

/**
 * 首次启动向导步骤
 */
enum class OnboardingStep(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isRequired: Boolean = true
) {
    WELCOME(
        title = "欢迎使用 Find My",
        description = "定位您的设备、与家人共享位置、\n在设备丢失时保护您的隐私",
        icon = Icons.Default.Explore,
        isRequired = true
    ),
    LOCATION_PERMISSION(
        title = "位置权限",
        description = "为了在地图上显示您的位置，\n我们需要访问您的位置信息",
        icon = Icons.Default.LocationOn,
        isRequired = true
    ),
    BACKGROUND_LOCATION(
        title = "后台定位",
        description = "为了让家人在您关闭屏幕时也能找到您，\n请允许始终访问位置",
        icon = Icons.Default.MyLocation,
        isRequired = true
    ),
    BATTERY_OPTIMIZATION(
        title = "电池优化",
        description = "为了防止系统误杀导致位置停止更新，\n请将本应用设为「无限制」",
        icon = Icons.Default.BatteryFull,
        isRequired = false
    ),
    NOTIFICATION_PERMISSION(
        title = "通知权限",
        description = "为了接收家人的位置请求和地理围栏提醒，\n请允许发送通知",
        icon = Icons.Default.Notifications,
        isRequired = false
    ),
    DEVICE_ADMIN(
        title = "设备管理员",
        description = "为了支持远程锁定和擦除功能，\n请激活设备管理员权限",
        icon = Icons.Default.AdminPanelSettings,
        isRequired = false
    ),
    COMPLETE(
        title = "设置完成",
        description = "您已完成所有设置，\n现在可以开始使用 Find My 了",
        icon = Icons.Default.Check,
        isRequired = true
    )
}

/**
 * 首次启动向导屏幕
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(OnboardingStep.WELCOME.ordinal) }
    val steps = OnboardingStep.entries

    // 权限状态
    val locationPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val backgroundLocationPermissionState = rememberPermissionState(
        permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )

    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
    } else null

    // 电池优化状态
    var isBatteryOptimizationDisabled by remember {
        mutableStateOf(
            (context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        )
    }

    // 设备管理员状态
    var isDeviceAdminActive by remember { mutableStateOf(DeviceAdminHelper.isAdminActive(context)) }

    // Device Admin 激活启动器
    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDeviceAdminActive = DeviceAdminHelper.isAdminActive(context)
    }

    // 电池优化设置启动器
    val batterySettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isBatteryOptimizationDisabled = (context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    }

    // 保存进度
    LaunchedEffect(currentStep) {
        OnboardingPreferences.setLastCompletedStep(context, currentStep)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 进度指示器
            if (currentStep > 0 && currentStep < steps.size - 1) {
                LinearProgressIndicator(
                    progress = { currentStep.toFloat() / (steps.size - 1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(36.dp))
            }

            // 步骤内容（带动画）
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                    } else {
                        slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                    }
                },
                modifier = Modifier.weight(1f),
                label = "step_content"
            ) { step ->
                val currentStepEnum = steps[step]
                OnboardingStepContent(
                    step = currentStepEnum,
                    isLocationGranted = locationPermissionState.allPermissionsGranted,
                    isBackgroundLocationGranted = backgroundLocationPermissionState.status.isGranted,
                    isBatteryOptimizationDisabled = isBatteryOptimizationDisabled,
                    isNotificationGranted = notificationPermissionState?.status?.isGranted ?: true,
                    isDeviceAdminActive = isDeviceAdminActive
                )
            }

            // 底部按钮
            OnboardingButtons(
                currentStep = currentStep,
                totalSteps = steps.size,
                currentStepEnum = steps[currentStep],
                isLocationGranted = locationPermissionState.allPermissionsGranted,
                isBackgroundLocationGranted = backgroundLocationPermissionState.status.isGranted,
                isBatteryOptimizationDisabled = isBatteryOptimizationDisabled,
                isNotificationGranted = notificationPermissionState?.status?.isGranted ?: true,
                isDeviceAdminActive = isDeviceAdminActive,
                onRequestLocationPermission = {
                    locationPermissionState.launchMultiplePermissionRequest()
                },
                onRequestBackgroundLocation = {
                    // 后台定位需要跳转设置页
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                },
                onRequestBatteryOptimization = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    batterySettingsLauncher.launch(intent)
                },
                onRequestNotificationPermission = {
                    notificationPermissionState?.launchPermissionRequest()
                },
                onRequestDeviceAdmin = {
                    val intent = DeviceAdminHelper.createActivationIntent(context)
                    deviceAdminLauncher.launch(intent)
                },
                onNext = {
                    if (currentStep < steps.size - 1) {
                        currentStep++
                    }
                },
                onSkip = {
                    if (currentStep < steps.size - 1) {
                        currentStep++
                    }
                },
                onComplete = {
                    OnboardingPreferences.setOnboardingCompleted(context)
                    onComplete()
                }
            )
        }
    }
}

@Composable
private fun OnboardingStepContent(
    step: OnboardingStep,
    isLocationGranted: Boolean,
    isBackgroundLocationGranted: Boolean,
    isBatteryOptimizationDisabled: Boolean,
    isNotificationGranted: Boolean,
    isDeviceAdminActive: Boolean
) {
    val isStepCompleted = when (step) {
        OnboardingStep.WELCOME -> true
        OnboardingStep.LOCATION_PERMISSION -> isLocationGranted
        OnboardingStep.BACKGROUND_LOCATION -> isBackgroundLocationGranted
        OnboardingStep.BATTERY_OPTIMIZATION -> isBatteryOptimizationDisabled
        OnboardingStep.NOTIFICATION_PERMISSION -> isNotificationGranted
        OnboardingStep.DEVICE_ADMIN -> isDeviceAdminActive
        OnboardingStep.COMPLETE -> true
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 图标
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = if (isStepCompleted)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isStepCompleted && step != OnboardingStep.WELCOME && step != OnboardingStep.COMPLETE)
                        Icons.Default.Check else step.icon,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = if (isStepCompleted)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 标题
        Text(
            text = step.title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 描述
        Text(
            text = step.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )

        // 状态提示
        if (isStepCompleted && step != OnboardingStep.WELCOME && step != OnboardingStep.COMPLETE) {
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "已完成",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 可选标签
        if (!step.isRequired && step != OnboardingStep.COMPLETE) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "（可选）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun OnboardingButtons(
    currentStep: Int,
    totalSteps: Int,
    currentStepEnum: OnboardingStep,
    isLocationGranted: Boolean,
    isBackgroundLocationGranted: Boolean,
    isBatteryOptimizationDisabled: Boolean,
    isNotificationGranted: Boolean,
    isDeviceAdminActive: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestDeviceAdmin: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit
) {
    val isStepCompleted = when (currentStepEnum) {
        OnboardingStep.WELCOME -> true
        OnboardingStep.LOCATION_PERMISSION -> isLocationGranted
        OnboardingStep.BACKGROUND_LOCATION -> isBackgroundLocationGranted
        OnboardingStep.BATTERY_OPTIMIZATION -> isBatteryOptimizationDisabled
        OnboardingStep.NOTIFICATION_PERMISSION -> isNotificationGranted
        OnboardingStep.DEVICE_ADMIN -> isDeviceAdminActive
        OnboardingStep.COMPLETE -> true
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (currentStepEnum) {
            OnboardingStep.WELCOME -> {
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("开始设置")
                }
            }

            OnboardingStep.LOCATION_PERMISSION -> {
                if (!isLocationGranted) {
                    Button(
                        onClick = onRequestLocationPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("授予位置权限")
                    }
                } else {
                    Button(
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("继续")
                    }
                }
            }

            OnboardingStep.BACKGROUND_LOCATION -> {
                if (!isBackgroundLocationGranted) {
                    Button(
                        onClick = onRequestBackgroundLocation,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("去设置")
                    }
                    Text(
                        text = "请在设置中选择「始终允许」",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Button(
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("继续")
                    }
                }
            }

            OnboardingStep.BATTERY_OPTIMIZATION -> {
                if (!isBatteryOptimizationDisabled) {
                    Button(
                        onClick = onRequestBatteryOptimization,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("关闭电池优化")
                    }
                }
                if (isStepCompleted) {
                    Button(
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("继续")
                    }
                } else {
                    TextButton(onClick = onSkip) {
                        Text("跳过")
                    }
                }
            }

            OnboardingStep.NOTIFICATION_PERMISSION -> {
                if (!isNotificationGranted) {
                    Button(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("授予通知权限")
                    }
                }
                if (isStepCompleted) {
                    Button(
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("继续")
                    }
                } else {
                    TextButton(onClick = onSkip) {
                        Text("跳过")
                    }
                }
            }

            OnboardingStep.DEVICE_ADMIN -> {
                if (!isDeviceAdminActive) {
                    Button(
                        onClick = onRequestDeviceAdmin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("激活设备管理员")
                    }
                }
                if (isStepCompleted) {
                    Button(
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("继续")
                    }
                } else {
                    TextButton(onClick = onSkip) {
                        Text("跳过")
                    }
                }
            }

            OnboardingStep.COMPLETE -> {
                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("开始使用")
                }
            }
        }

        // 步骤指示器
        if (currentStep > 0 && currentStep < totalSteps - 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(totalSteps - 2) { index -> // 排除欢迎页和完成页
                    val stepIndex = index + 1
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (stepIndex <= currentStep)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}
