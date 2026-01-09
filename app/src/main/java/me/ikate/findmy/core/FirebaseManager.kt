package me.ikate.findmy.core

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Firebase 初始化管理器
 * 负责初始化 Firebase 服务（Firestore、FCM 等）
 *
 * 注意：使用前需要从 Firebase Console 下载 google-services.json 文件
 * 并放置在 app/ 目录下
 */
object FirebaseManager {

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
                // TODO: 将 Token 上传到服务器
                android.util.Log.d("FirebaseManager", "FCM Token: $token")
            } else {
                android.util.Log.e("FirebaseManager", "获取 FCM Token 失败", task.exception)
            }
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
