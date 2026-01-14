package me.ikate.findmy.core

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Firebase 初始化管理器
 * 负责初始化 Firebase 服务（Firestore、FCM 等）
 *
 * 注意：使用前需要从 Firebase Console 下载 google-services.json 文件
 * 并放置在 app/ 目录下
 */
object FirebaseManager {

    private const val TAG = "FirebaseManager"

    /**
     * 初始化 Firebase
     * 应在 Application onCreate 中调用
     *
     * @param application Application 实例
     */
    fun initialize(application: Application) {
        // 初始化 Firebase
        FirebaseApp.initializeApp(application)

        // 请求 FCM Token（用于推送通知）
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                android.util.Log.d(TAG, "FCM Token 获取成功: $token")

                // 保存 Token 到 Firestore
                saveTokenToFirestore(token)
            } else {
                android.util.Log.e(TAG, "获取 FCM Token 失败", task.exception)
            }
        }
    }

    /**
     * 保存 FCM Token 到 Firestore
     * 支持多设备（使用 arrayUnion）
     */
    private fun saveTokenToFirestore(token: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && !currentUser.isAnonymous) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(currentUser.uid)
                .update("fcmTokens", FieldValue.arrayUnion(token))
                .addOnSuccessListener {
                    android.util.Log.d(TAG, "✅ FCM Token 已保存到 Firestore")
                }
                .addOnFailureListener { e ->
                    android.util.Log.w(TAG, "⚠️ 保存 FCM Token 失败，尝试创建文档", e)
                    // 如果 users 文档不存在，创建它
                    val userData = hashMapOf(
                        "uid" to currentUser.uid,
                        "email" to (currentUser.email ?: ""),
                        "fcmTokens" to listOf(token),
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("users").document(currentUser.uid)
                        .set(userData)
                        .addOnSuccessListener {
                            android.util.Log.d(TAG, "✅ 用户文档已创建，FCM Token 已保存")
                        }
                        .addOnFailureListener { e2 ->
                            android.util.Log.e(TAG, "❌ 创建用户文档失败", e2)
                        }
                }
        } else {
            android.util.Log.w(TAG, "⚠️ 用户未登录，跳过保存 FCM Token")
        }
    }

    /**
     * 订阅 FCM 主题
     * 用于接收特定主题的推送通知
     *
     * @param topic 主题名称
     */
    fun subscribeToTopic(topic: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    android.util.Log.d("FirebaseManager", "已订阅主题: $topic")
                } else {
                    android.util.Log.e("FirebaseManager", "订阅主题失败: $topic", task.exception)
                }
            }
    }

    /**
     * 取消订阅 FCM 主题
     *
     * @param topic 主题名称
     */
    fun unsubscribeFromTopic(topic: String) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    android.util.Log.d("FirebaseManager", "已取消订阅主题: $topic")
                } else {
                    android.util.Log.e(
                        "FirebaseManager",
                        "取消订阅主题失败: $topic",
                        task.exception
                    )
                }
            }
    }
}
