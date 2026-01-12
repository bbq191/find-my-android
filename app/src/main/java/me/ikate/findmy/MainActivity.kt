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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.ikate.findmy.ui.screen.main.MainScreen
import me.ikate.findmy.ui.theme.FindmyTheme
import me.ikate.findmy.util.MigrationHelper
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

        // 🔧 自动修复 sharedWith 字段（仅执行一次）
        runMigrationIfNeeded()

        setContent {
            FindmyTheme {
                MainScreen()
            }
        }
    }

    /**
     * 运行数据迁移（仅在首次启动或版本升级时执行）
     */
    private fun runMigrationIfNeeded() {
        val prefs = getSharedPreferences("migration", MODE_PRIVATE)
        val migrationVersion = prefs.getInt("migration_version", 0)
        val currentMigrationVersion = 1 // 每次有新迁移时递增

        if (migrationVersion < currentMigrationVersion) {
            lifecycleScope.launch {
                try {
                    android.util.Log.d("MainActivity", "开始执行数据迁移...")

                    // 执行修复
                    val result = MigrationHelper.fixSharedWithFields()
                    result.fold(
                        onSuccess = { count ->
                            android.util.Log.d("MainActivity", "✅ 数据迁移完成: 修复了 $count 个设备")
                            // 标记迁移完成
                            prefs.edit().putInt("migration_version", currentMigrationVersion).apply()
                        },
                        onFailure = { e ->
                            android.util.Log.e("MainActivity", "❌ 数据迁移失败", e)
                        }
                    )
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "数据迁移异常", e)
                }
            }
        }
    }
}