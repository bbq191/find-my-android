package me.ikate.findmy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import me.ikate.findmy.ui.screen.auth.AuthState
import me.ikate.findmy.ui.screen.auth.AuthViewModel
import me.ikate.findmy.ui.screen.main.MainScreen
import me.ikate.findmy.ui.theme.FindmyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FindmyTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent(
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()

    // 自动登录逻辑：如果未认证，尝试匿名登录
    LaunchedEffect(authState) {
        if (authState is AuthState.Unauthenticated) {
            authViewModel.signInAnonymously()
        }
    }

    when (authState) {
        is AuthState.Authenticated -> {
            MainScreen()
        }
        else -> {
            // 加载中或未认证时显示加载指示器
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}