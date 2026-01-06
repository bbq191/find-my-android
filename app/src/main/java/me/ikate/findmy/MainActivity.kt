package me.ikate.findmy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import me.ikate.findmy.ui.screen.auth.AuthState
import me.ikate.findmy.ui.screen.auth.AuthViewModel
import me.ikate.findmy.ui.screen.auth.LoginScreen
import me.ikate.findmy.ui.screen.main.MainScreen
import me.ikate.findmy.ui.theme.FindmyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FindmyTheme {
                AppNavigation()
            }
        }
    }
}

/**
 * 应用导航 - 根据认证状态和会话确认状态显示不同界面
 *
 * 逻辑：
 * - 匿名用户每次打开应用都需要先看到登录页，点击"匿名登录"后才能进入主界面
 * - 已注册用户（邮箱登录）直接进入主界面
 * - 使用 hasConfirmedEntry 状态跟踪用户是否在当前会话中已点击登录
 */
@Composable
fun AppNavigation(
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val hasConfirmedEntry by authViewModel.hasConfirmedEntry.collectAsState()

    when (authState) {
        is AuthState.Authenticated -> {
            // 用户已通过认证（匿名或注册用户）
            if (hasConfirmedEntry) {
                // 已确认进入（点击过登录按钮），显示主界面
                MainScreen()
            } else {
                // 未确认进入（应用刚启动），显示登录界面
                LoginScreen(viewModel = authViewModel)
            }
        }
        is AuthState.Unauthenticated, is AuthState.Error -> {
            // 未登录或错误，显示登录界面
            LoginScreen(viewModel = authViewModel)
        }
        is AuthState.Loading -> {
            // 加载中，可以显示启动画面
            // 暂时不显示任何内容，等待认证状态确定
        }
    }
}