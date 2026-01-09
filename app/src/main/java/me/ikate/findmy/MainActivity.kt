package me.ikate.findmy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import me.ikate.findmy.ui.screen.main.MainScreen
import me.ikate.findmy.ui.theme.FindmyTheme
import me.ikate.findmy.util.NotificationHelper

class MainActivity : ComponentActivity() {

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

        setContent {
            FindmyTheme {
                MainScreen()
            }
        }
    }
}