package me.ikate.findmy.ui.screen.auth

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ikate.findmy.data.repository.AuthRepository
import me.ikate.findmy.data.repository.ContactRepository
import java.util.concurrent.TimeUnit

/**
 * 认证状态
 */
sealed class AuthState {
    data object Loading : AuthState()
    data object Unauthenticated : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * AuthViewModel - 管理用户认证状态
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository()
    private val contactRepository = ContactRepository()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // 会话确认状态：用户是否在当前会话中已经点击过登录
    // 用于确保匿名用户每次打开应用都需要重新点击登录按钮
    private val _hasConfirmedEntry = MutableStateFlow(false)
    val hasConfirmedEntry: StateFlow<Boolean> = _hasConfirmedEntry.asStateFlow()

    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_LAST_ACTIVE = "last_active_timestamp"
        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L
    }

    init {
        // 监听认证状态变化
        observeAuthState()
    }

    /**
     * 监听认证状态
     */
    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { user ->
                if (user != null) {
                    checkAutoLoginValidity(user)
                } else {
                    _authState.value = AuthState.Unauthenticated
                    _hasConfirmedEntry.value = false
                }
            }
        }
    }

    /**
     * 检查自动登录有效期
     * 如果超过7天未活跃，强制登出；否则更新活跃时间并自动进入
     */
    private fun checkAutoLoginValidity(user: FirebaseUser) {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastActiveTime = prefs.getLong(KEY_LAST_ACTIVE, 0L)
        val currentTime = System.currentTimeMillis()

        if (lastActiveTime > 0 && (currentTime - lastActiveTime > SEVEN_DAYS_MS)) {
            // 超过7天未活跃，强制登出
            android.util.Log.d("AuthViewModel", "自动登录失效，超过7天未活跃")
            signOut()
        } else {
            // 有效期内，更新活跃时间并允许进入
            // 即使是第一次登录（lastActiveTime == 0），也认为是有效，并初始化时间
            updateLastActiveTime()
            _authState.value = AuthState.Authenticated(user)
            
            // 如果是已登录用户（非首次启动），直接标记为确认进入，实现自动登录
            // 注意：这里我们假设如果 Firebase 有用户且在有效期内，就是"自动登录"场景
            _hasConfirmedEntry.value = true
        }
    }

    /**
     * 更新最后活跃时间
     */
    private fun updateLastActiveTime() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_ACTIVE, System.currentTimeMillis()).apply()
    }

    /**
     * 匿名登录
     */
    fun signInAnonymously() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInAnonymously()
            result.fold(
                onSuccess = { user ->
                    updateLastActiveTime()
                    _authState.value = AuthState.Authenticated(user)
                    _hasConfirmedEntry.value = true  // 标记用户已确认进入
                    android.util.Log.d("AuthViewModel", "匿名登录成功: ${user.uid}")
                },
                onFailure = { error ->
                    _authState.value = AuthState.Error(error.message ?: "登录失败")
                    android.util.Log.e("AuthViewModel", "匿名登录失败", error)
                }
            )
        }
    }

    /**
     * 邮箱密码登录
     */
    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithEmailPassword(email, password)
            result.fold(
                onSuccess = { user ->
                    updateLastActiveTime()
                    _authState.value = AuthState.Authenticated(user)
                    _hasConfirmedEntry.value = true  // 标记用户已确认进入

                    // 同步用户信息到 Firestore users 集合
                    contactRepository.syncCurrentUser()
                },
                onFailure = { error ->
                    val friendlyMessage = mapAuthErrorToMessage(error, isLogin = true)
                    _authState.value = AuthState.Error(friendlyMessage)
                }
            )
        }
    }

    /**
     * 邮箱密码注册
     */
    fun signUpWithEmail(email: String, password: String) {
        viewModelScope.launch {
            if (password.length < 6) {
                _authState.value = AuthState.Error("密码长度至少需要6位")
                return@launch
            }

            _authState.value = AuthState.Loading
            val result = authRepository.createUserWithEmailPassword(email, password)
            result.fold(
                onSuccess = { user ->
                    updateLastActiveTime()
                    _authState.value = AuthState.Authenticated(user)
                    _hasConfirmedEntry.value = true  // 标记用户已确认进入

                    // 同步用户信息到 Firestore users 集合
                    contactRepository.syncCurrentUser()
                },
                onFailure = { error ->
                    val friendlyMessage = mapAuthErrorToMessage(error, isLogin = false)
                    _authState.value = AuthState.Error(friendlyMessage)
                }
            )
        }
    }

    /**
     * 将 Firebase 异常转换为用户友好的错误信息
     */
    private fun mapAuthErrorToMessage(error: Throwable, isLogin: Boolean): String {
        // 尝试获取异常消息或类名进行匹配
        // 注意：具体的异常类型取决于 Firebase SDK 版本，这里做通用匹配
        val message = error.message ?: ""
        
        return when {
            // 用户不存在
            message.contains("The user may have been deleted") ||
            message.contains("There is no user record") ||
            error is com.google.firebase.auth.FirebaseAuthInvalidUserException -> {
                if (isLogin) "账号不存在，请先注册" else "账号异常"
            }
            
            // 密码错误
            message.contains("password is invalid") || 
            message.contains("supplied auth credential is incorrect") ||
            error is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> {
                 if (isLogin) "账号或密码错误" else "邮箱格式不正确"
            }
            
            // 邮箱已存在
            message.contains("The email address is already in use") ||
            error is com.google.firebase.auth.FirebaseAuthUserCollisionException -> {
                "该邮箱已被注册"
            }
            
            // 邮箱格式错误
            message.contains("The email address is badly formatted") -> {
                "邮箱格式不正确"
            }
            
            // 网络问题
            message.contains("network") || 
            message.contains("connection") -> {
                "网络连接失败，请检查网络"
            }
            
            else -> "操作失败: ${error.localizedMessage}"
        }
    }

    /**
     * 登出
     */
    fun signOut() {
        authRepository.signOut()
    }

    /**
     * 清除错误状态
     */
    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }
}
