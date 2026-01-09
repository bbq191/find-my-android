package me.ikate.findmy.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/**
 * 认证数据仓库
 * 封装 Firebase Authentication 操作
 *
 * 使用设备的 Android ID 作为固定凭据，确保同一设备卸载重装后 UID 不变
 */
class AuthRepository(private val context: Context? = null) {

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
     * 获取设备的 Android ID
     */
    @SuppressLint("HardwareIds")
    private fun getAndroidId(): String? {
        return context?.let {
            Settings.Secure.getString(it.contentResolver, Settings.Secure.ANDROID_ID)
        }
    }

    /**
     * 基于 Android ID 生成固定的邮箱和密码
     * 确保同一设备每次使用相同的凭据
     */
    private fun generateDeviceCredentials(): Pair<String, String>? {
        val androidId = getAndroidId() ?: return null

        // 使用 Android ID 生成固定的邮箱
        val email = "device_${androidId}@findmy.local"

        // 使用 Android ID + 固定盐值生成密码（SHA-256）
        val password = hashString("$androidId:findmy:secret:salt")

        return Pair(email, password)
    }

    /**
     * SHA-256 哈希
     */
    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 基于设备 ID 登录
     * 如果账号不存在则自动创建，确保同一设备 UID 永久不变
     *
     * @return 登录是否成功
     */
    suspend fun signInWithDeviceId(): Result<FirebaseUser> {
        return try {
            val credentials = generateDeviceCredentials()
                ?: return Result.failure(Exception("无法获取设备 ID"))

            val (email, password) = credentials

            // 尝试登录
            val result = try {
                auth.signInWithEmailAndPassword(email, password).await()
            } catch (e: Exception) {
                android.util.Log.d("AuthRepository", "账号不存在，创建新账号: $email")
                // 如果登录失败（账号不存在），则创建账号
                auth.createUserWithEmailAndPassword(email, password).await()
            }

            val user = result.user ?: throw Exception("用户为空")
            android.util.Log.d("AuthRepository", "设备登录成功: ${user.uid}")
            Result.success(user)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "设备登录失败", e)
            Result.failure(e)
        }
    }

    /**
     * 匿名登录（已废弃，保留兼容性）
     * 建议使用 signInWithDeviceId() 确保 UID 不变
     *
     * @return 登录是否成功
     */
    @Deprecated("使用 signInWithDeviceId() 代替，确保同设备 UID 不变")
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
