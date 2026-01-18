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
import me.ikate.findmy.push.GeTuiManager
import me.ikate.findmy.ui.components.PrivacyPolicyDialog
import me.ikate.findmy.ui.screen.main.MainScreen
import me.ikate.findmy.ui.theme.FindmyTheme
import me.ikate.findmy.util.NotificationHelper
import me.ikate.findmy.util.PrivacyManager

class MainActivity : ComponentActivity() {

    // 隐私政策弹窗状态
    private var showPrivacyDialog by mutableStateOf(false)

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

        // 初始化隐私合规（高德定位 SDK 要求）
        // 返回 true 表示需要显示隐私弹窗
        showPrivacyDialog = PrivacyManager.initPrivacy(this)

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

        // 绑定用户到个推（用于定向推送）
        GeTuiManager.bindUser(this)

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
                }

                MainScreen()
            }
        }
    }
}
