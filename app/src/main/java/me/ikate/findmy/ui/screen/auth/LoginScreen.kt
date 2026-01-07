package me.ikate.findmy.ui.screen.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 登录界面
 * 支持匿名登录和邮箱密码登录
 */
@Composable
fun LoginScreen(
    viewModel: AuthViewModel = viewModel(),
    onLoginSuccess: () -> Unit = {}
) {
    val authState by viewModel.authState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isSignUpMode by remember { mutableStateOf(false) }

    // 监听认证状态，登录成功后跳转
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onLoginSuccess()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo 和标题
            Text(
                text = "Find My",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isSignUpMode) "创建新账号" else "定位你的设备",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (!isSignUpMode) {
                // 匿名登录按钮（推荐）
                Button(
                    onClick = { viewModel.signInAnonymously() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = authState !is AuthState.Loading
                ) {
                    if (authState is AuthState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("快速开始（匿名登录）")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 分割线
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "或",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 邮箱输入框
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("邮箱") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = "邮箱")
                },
                singleLine = true,
                enabled = authState !is AuthState.Loading
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 密码输入框
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密码") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = "密码")
                },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            if (isPasswordVisible) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff,
                            contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码"
                        )
                    }
                },
                visualTransformation = if (isPasswordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                singleLine = true,
                enabled = authState !is AuthState.Loading
            )

            // 注册模式下的确认密码框
            if (isSignUpMode) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("确认密码") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = "确认密码")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = authState !is AuthState.Loading,
                    isError = confirmPassword.isNotEmpty() && confirmPassword != password,
                    supportingText = {
                        if (confirmPassword.isNotEmpty() && confirmPassword != password) {
                            Text("密码不一致")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 登录/注册按钮
            Button(
                onClick = {
                    if (isSignUpMode) {
                        if (password == confirmPassword) {
                            viewModel.signUpWithEmail(email, password)
                        }
                    } else {
                        viewModel.signInWithEmail(email, password)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = authState !is AuthState.Loading &&
                        email.isNotBlank() && password.isNotBlank() &&
                        (!isSignUpMode || (confirmPassword.isNotBlank() && password == confirmPassword))
            ) {
                if (authState is AuthState.Loading && isSignUpMode) {
                     CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isSignUpMode) "注册" else "登录")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 切换登录/注册模式
            TextButton(
                onClick = { isSignUpMode = !isSignUpMode },
                enabled = authState !is AuthState.Loading
            ) {
                Text(
                    if (isSignUpMode) "已有账号？点击登录"
                    else "没有账号？点击注册"
                )
            }

            // 错误提示
            if (authState is AuthState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = (authState as AuthState.Error).message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
