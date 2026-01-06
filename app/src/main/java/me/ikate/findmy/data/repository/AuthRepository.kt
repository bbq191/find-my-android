package me.ikate.findmy.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * 认证数据仓库
 * 封装 Firebase Authentication 操作
 */
class AuthRepository {

    private val auth = FirebaseAuth.getInstance()

    /**
     * 获取当前用户
     */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * 监听认证状态变化
     * 返回 Flow<FirebaseUser?>，当用户登录/登出时会发出新值
     */
    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }

        auth.addAuthStateListener(authStateListener)

        // 立即发送当前状态
        trySend(auth.currentUser)

        awaitClose { auth.removeAuthStateListener(authStateListener) }
    }

    /**
     * 匿名登录
     * 适用于快速开始使用应用，无需注册账号
     *
     * @return 登录是否成功
     */
    suspend fun signInAnonymously(): Result<FirebaseUser> {
        return try {
            val result = auth.signInAnonymously().await()
            val user = result.user ?: throw Exception("用户为空")
            Result.success(user)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "匿名登录失败", e)
            Result.failure(e)
        }
    }

    /**
     * 邮箱密码登录
     *
     * @param email 邮箱
     * @param password 密码
     * @return 登录是否成功
     */
    suspend fun signInWithEmailPassword(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("用户为空")
            Result.success(user)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "邮箱登录失败", e)
            Result.failure(e)
        }
    }

    /**
     * 邮箱密码注册
     *
     * @param email 邮箱
     * @param password 密码
     * @return 注册是否成功
     */
    suspend fun createUserWithEmailPassword(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: throw Exception("用户为空")
            Result.success(user)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "邮箱注册失败", e)
            Result.failure(e)
        }
    }

    /**
     * 登出
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * 检查是否已登录
     */
    fun isSignedIn(): Boolean {
        return auth.currentUser != null
    }

    /**
     * 获取当前用户ID
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
}
