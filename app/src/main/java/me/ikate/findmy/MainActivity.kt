package me.ikate.findmy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import me.ikate.findmy.ui.components.PrivacyPolicyDialog
import me.ikate.findmy.ui.screen.main.MainScreen
import me.ikate.findmy.ui.screen.onboarding.OnboardingScreen
import me.ikate.findmy.ui.theme.FindmyTheme
import me.ikate.findmy.util.NotificationHelper
import me.ikate.findmy.util.OnboardingPreferences
import me.ikate.findmy.util.PrivacyManager

class MainActivity : ComponentActivity() {

    // 隐私政策弹窗状态
    private var showPrivacyDialog by mutableStateOf(false)

    // 首次启动向导状态
    private var showOnboarding by mutableStateOf(false)

    // 通知权限请求启动器（Android 13+）
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                android.util.Log.d("MainActivity", "通知权限已授予")
            } else {
                android.util.Log.w("MainActivity", "通知权限被拒绝")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 设置 120Hz 高刷新率
        setHighRefreshRate()

        // 初始化隐私合规（腾讯定位 SDK 要求）
        // 返回 true 表示需要显示隐私弹窗
        showPrivacyDialog = PrivacyManager.initPrivacy(this)

        // 检查是否需要显示首次启动向导
        showOnboarding = !OnboardingPreferences.isOnboardingCompleted(this)

        // 初始化通知渠道
        NotificationHelper.createNotificationChannels(this)

        // 请求通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // FCM Token 会在 FCMMessagingService 中自动获取和保存

        setContent {
            FindmyTheme {
                // 隐私政策弹窗
                if (showPrivacyDialog) {
                    PrivacyPolicyDialog(
                        onAgree = {
                            PrivacyManager.setPrivacyAgreed(this@MainActivity, true)
                            showPrivacyDialog = false
                        },
                        onDisagree = {
                            // 用户不同意，退出应用
                            finish()
                        }
                    )
                } else if (showOnboarding) {
                    // 首次启动向导
                    OnboardingScreen(
                        onComplete = {
                            showOnboarding = false
                        }
                    )
                } else {
                    // 主界面
                    MainScreen()
                }
            }
        }
    }

    /**
     * 设置屏幕刷新率为 120Hz
     * 适用于 Samsung Galaxy S24 Ultra 等支持高刷新率的设备
     */
    private fun setHighRefreshRate() {
        window.attributes = window.attributes.apply {
            preferredRefreshRate = 120f
        }
    }
}
