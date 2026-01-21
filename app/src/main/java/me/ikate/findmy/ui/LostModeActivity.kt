package me.ikate.findmy.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import me.ikate.findmy.service.LostModeService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ikate.findmy.ui.theme.FindmyTheme

/**
 * 丢失模式全屏 Activity
 * 显示丢失信息和联系方式
 *
 * 特性：
 * - 显示在锁屏之上
 * - 禁用返回键
 * - 当用户按 HOME 键离开后，服务会重新显示此界面
 * - 只有通过身份验证或远程解锁才能关闭
 */
class LostModeActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_PHONE = "phone"

        // 远程关闭丢失模式的广播 Action
        const val ACTION_CLOSE_LOST_MODE = "me.ikate.findmy.ACTION_CLOSE_LOST_MODE"

        fun createIntent(
            context: Context,
            message: String,
            phoneNumber: String
        ): Intent {
            return Intent(context, LostModeActivity::class.java).apply {
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_PHONE, phoneNumber)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
        }
    }

    private var message: String = "此设备已丢失"
    private var phoneNumber: String = ""

    // 关闭丢失模式的广播接收器
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_CLOSE_LOST_MODE) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 禁用返回键，必须通过身份验证才能退出
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 不执行任何操作，禁用返回键
            }
        })

        // 设置窗口标志：显示在锁屏之上、保持屏幕常亮
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // FLAG_KEEP_SCREEN_ON 未被弃用，始终添加
        // 旧版 API 需要使用已弃用的标志
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        message = intent.getStringExtra(EXTRA_MESSAGE) ?: "此设备已丢失"
        phoneNumber = intent.getStringExtra(EXTRA_PHONE) ?: ""

        // 注册关闭广播接收器（Android 14+ 需要指定导出标志）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(closeReceiver, IntentFilter(ACTION_CLOSE_LOST_MODE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeReceiver, IntentFilter(ACTION_CLOSE_LOST_MODE))
        }

        setContent {
            FindmyTheme(darkTheme = true) {
                LostModeScreen(
                    message = message,
                    phoneNumber = phoneNumber,
                    onCallClick = { dialPhone(phoneNumber) },
                    onDismissClick = { launchAuth() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 更新显示内容
        message = intent.getStringExtra(EXTRA_MESSAGE) ?: message
        phoneNumber = intent.getStringExtra(EXTRA_PHONE) ?: phoneNumber
    }

    override fun onResume() {
        super.onResume()
        // 检查丢失模式是否已关闭，如果已关闭则自动退出
        if (!LostModeService.isEnabled(this)) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(closeReceiver)
        } catch (e: Exception) {
            // 忽略
        }
    }

    /**
     * 当用户按 HOME 键时调用
     * 不阻止，但服务会在屏幕解锁后重新显示此界面
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 服务会监听 ACTION_USER_PRESENT 并重新显示界面
    }

    private fun dialPhone(phone: String) {
        if (phone.isNotBlank()) {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phone")
            }
            startActivity(intent)
        }
    }

    private fun launchAuth() {
        val intent = Intent(this, LostModeAuthActivity::class.java)
        startActivity(intent)
    }

}

@Composable
private fun LostModeScreen(
    message: String,
    phoneNumber: String,
    onCallClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 锁定图标
            Surface(
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(50.dp),
                color = Color(0xFF1E88E5).copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFF1E88E5)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 标题
            Text(
                text = "此设备已丢失",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 消息
            Text(
                text = message,
                fontSize = 16.sp,
                color = Color(0xFFCCCCCC),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 电话号码
            if (phoneNumber.isNotBlank()) {
                Text(
                    text = "联系电话",
                    fontSize = 14.sp,
                    color = Color(0xFF888888)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = phoneNumber,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 拨打电话按钮
                Button(
                    onClick = onCallClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "拨打电话",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // 关闭按钮
            OutlinedButton(
                onClick = onDismissClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF888888)
                )
            ) {
                Text(
                    text = "我是机主",
                    fontSize = 14.sp
                )
            }
        }
    }
}
