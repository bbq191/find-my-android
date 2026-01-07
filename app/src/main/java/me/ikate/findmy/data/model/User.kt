package me.ikate.findmy.data.model

/**
 * 用户索引数据模型
 * Firestore 集合: users
 * Document ID: uid (Auth UID)
 */
data class User(
    val uid: String,
    val email: String,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
