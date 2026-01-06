package me.ikate.findmy.ui.screen.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ikate.findmy.data.repository.AuthRepository

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
class AuthViewModel : ViewModel() {

    private val authRepository = AuthRepository()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // 会话确认状态：用户是否在当前会话中已经点击过登录
    // 用于确保匿名用户每次打开应用都需要重新点击登录按钮
    private val _hasConfirmedEntry = MutableStateFlow(false)
    val hasConfirmedEntry: StateFlow<Boolean> = _hasConfirmedEntry.asStateFlow()

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
                _authState.value = if (user != null) {
                    AuthState.Authenticated(user)
                } else {
                    AuthState.Unauthenticated
                }
            }
        }
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
                    _authState.value = AuthState.Authenticated(user)
                    _hasConfirmedEntry.value = true  // 标记用户已确认进入
                },
                onFailure = { error ->
                    _authState.value = AuthState.Error(error.message ?: "登录失败")
                }
            )
        }
    }

    /**
     * 邮箱密码注册
     */
    fun signUpWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.createUserWithEmailPassword(email, password)
            result.fold(
                onSuccess = { user ->
                    _authState.value = AuthState.Authenticated(user)
                    _hasConfirmedEntry.value = true  // 标记用户已确认进入
                },
                onFailure = { error ->
                    _authState.value = AuthState.Error(error.message ?: "注册失败")
                }
            )
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
