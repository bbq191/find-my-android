package me.ikate.findmy.data.repository

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import me.ikate.findmy.data.model.*
import me.ikate.findmy.util.CoordinateConverter

/**
 * 联系人和位置共享数据仓库
 * 封装 Firestore 数据访问，提供用户索引、位置共享关系的管理
 */
class ContactRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val usersCollection = firestore.collection("users")
    private val sharesCollection = firestore.collection("location_shares")
    private val devicesCollection = firestore.collection("devices")

    companion object {
        private const val TAG = "ContactRepository"
    }

    // ====================================================================
    // 用户管理
    // ====================================================================

    /**
     * 同步当前用户信息到 Firestore
     * 应在用户登录后调用（邮箱登录/注册时）
     */
    suspend fun syncCurrentUser(): Result<Unit> {
        val currentUser = auth.currentUser ?: return Result.failure(Exception("未登录"))

        // 匿名用户不同步
        if (currentUser.isAnonymous) {
            Log.d(TAG, "匿名用户不需要同步到 users 集合")
            return Result.success(Unit)
        }

        return try {
            // 获取 FCM Token
            val fcmToken = try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
            } catch (e: Exception) {
                Log.w(TAG, "获取 FCM Token 失败", e)
                null
            }

            val userData = hashMapOf(
                "uid" to currentUser.uid,
                "email" to (currentUser.email ?: ""),
                "displayName" to (currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: ""),
                "photoUrl" to (currentUser.photoUrl?.toString() ?: ""),
                "createdAt" to FieldValue.serverTimestamp()
            )

            // 使用 set(..., SetOptions.merge()) 来更新基本信息，避免覆盖其他字段
            usersCollection.document(currentUser.uid)
                .set(userData, com.google.firebase.firestore.SetOptions.merge())
                .await()

            // 如果获取到了 Token，追加到 fcmTokens 数组
            if (!fcmToken.isNullOrEmpty()) {
                usersCollection.document(currentUser.uid)
                    .update("fcmTokens", FieldValue.arrayUnion(fcmToken))
                    .await()
            }

            Log.d(TAG, "用户信息同步成功: ${currentUser.email}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "用户信息同步失败", e)
            Result.failure(e)
        }
    }

    /**
     * 根据邮箱搜索用户
     */
    suspend fun searchUserByEmail(email: String): Result<User?> {
        return try {
            val snapshot = usersCollection
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()

            val user = snapshot.documents.firstOrNull()?.let { doc ->
                User(
                    uid = doc.getString("uid") ?: "",
                    email = doc.getString("email") ?: "",
                    displayName = doc.getString("displayName"),
                    photoUrl = doc.getString("photoUrl"),
                    createdAt = doc.getLong("createdAt") ?: 0L
                )
            }

            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "搜索用户失败: $email", e)
            Result.failure(e)
        }
    }

    // ====================================================================
    // 位置共享管理
    // ====================================================================

    /**
     * 发起位置共享
     */
    suspend fun createLocationShare(
        toEmail: String,
        duration: ShareDuration
    ): Result<String> {
        val currentUser = auth.currentUser ?: return Result.failure(Exception("未登录"))
        val currentUid = currentUser.uid

        return try {
            // 确定目标 UID
            val targetUid = if (toEmail.equals(currentUser.email, ignoreCase = true)) {
                // 优化：如果是发给自己，直接使用当前 UID，无需网络查询，避免大小写或权限问题
                Log.d(TAG, "检测到自我分享，直接使用当前 UID")
                currentUid
            } else {
                // 查询其他用户
                val result = searchUserByEmail(toEmail)
                // 记录搜索失败的具体原因（权限/网络等），方便调试
                result.onFailure { e -> Log.w(TAG, "搜索用户出错: $toEmail", e) }
                
                // 获取用户对象，如果失败或不存在则为 null
                result.getOrNull()?.uid
            }

            // 计算过期时间
            val expireTime = when (duration) {
                ShareDuration.ONE_HOUR -> System.currentTimeMillis() + duration.durationMillis!!
                ShareDuration.END_OF_DAY -> ShareDuration.calculateEndOfDay()
                ShareDuration.INDEFINITELY -> null
            }

            val shareData = hashMapOf(
                "fromUid" to currentUid,
                "toEmail" to toEmail,
                "toUid" to targetUid,
                "status" to ShareStatus.PENDING.name,
                "expireTime" to expireTime,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val docRef = sharesCollection.add(shareData).await()
            Log.d(TAG, "位置共享创建成功: ${docRef.id}, 目标邮箱: $toEmail, 目标UID: $targetUid")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "创建位置共享失败", e)
            Result.failure(e)
        }
    }

    /**
     * 接受位置共享
     * 1. 更新共享状态为 ACCEPTED
     * 2. 将当前用户添加到分享者所有设备的 sharedWith 数组
     */
    suspend fun acceptLocationShare(shareId: String): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: return Result.failure(Exception("未登录"))

        return try {
            // 更新共享状态
            sharesCollection.document(shareId).update(
                mapOf(
                    "status" to ShareStatus.ACCEPTED.name,
                    "toUid" to currentUid,
                    "acceptedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 获取分享者的 UID
            val shareDoc = sharesCollection.document(shareId).get().await()
            val fromUid = shareDoc.getString("fromUid") ?: return Result.failure(Exception("数据错误: fromUid 为空"))

            // 查询分享者的所有设备,添加当前用户到 sharedWith
            val devicesSnapshot = devicesCollection.whereEqualTo("ownerId", fromUid).get().await()
            devicesSnapshot.documents.forEach { deviceDoc ->
                deviceDoc.reference.update(
                    "sharedWith", FieldValue.arrayUnion(currentUid)
                ).await()
            }

            Log.d(TAG, "接受位置共享成功: $shareId, 已添加到 ${devicesSnapshot.size()} 个设备的 sharedWith")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "接受位置共享失败: $shareId", e)
            Result.failure(e)
        }
    }

    /**
     * 拒绝位置共享
     */
    suspend fun rejectLocationShare(shareId: String): Result<Unit> {
        return try {
            sharesCollection.document(shareId).update(
                "status", ShareStatus.REJECTED.name
            ).await()

            Log.d(TAG, "拒绝位置共享成功: $shareId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "拒绝位置共享失败: $shareId", e)
            Result.failure(e)
        }
    }

    /**
     * 停止共享（删除共享关系）
     * 1. 从设备的 sharedWith 中移除对方
     * 2. 删除共享记录
     */
    suspend fun stopSharing(shareId: String): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: return Result.failure(Exception("未登录"))

        return try {
            val shareDoc = sharesCollection.document(shareId).get().await()
            val fromUid = shareDoc.getString("fromUid")
            val toUid = shareDoc.getString("toUid")

            // 从设备的 sharedWith 中移除
            if (fromUid == currentUid && toUid != null) {
                // 我停止分享给对方
                val devicesSnapshot = devicesCollection.whereEqualTo("ownerId", currentUid).get().await()
                devicesSnapshot.documents.forEach { deviceDoc ->
                    deviceDoc.reference.update(
                        "sharedWith", FieldValue.arrayRemove(toUid)
                    ).await()
                }
                Log.d(TAG, "已从 ${devicesSnapshot.size()} 个设备的 sharedWith 中移除用户: $toUid")
            }

            // 删除共享记录
            sharesCollection.document(shareId).delete().await()
            Log.d(TAG, "停止共享成功: $shareId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "停止共享失败: $shareId", e)
            Result.failure(e)
        }
    }

    // ====================================================================
    // 联系人列表查询
    // ====================================================================

    /**
     * 实时监听我的联系人列表
     * 包括：我分享给谁的 + 谁分享给我的
     * 注意: Firestore 不支持 OR 查询,需要两次查询后合并
     */
    fun observeMyContacts(): Flow<List<Contact>> = callbackFlow {
        val currentUid = auth.currentUser?.uid
        val currentEmail = auth.currentUser?.email

        if (currentUid == null) {
            Log.w(TAG, "用户未登录,返回空联系人列表")
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        // 由于 Firestore 不支持 OR 查询,使用标志位触发合并
        var iShareList: List<LocationShare> = emptyList()
        var theyShareList: List<LocationShare> = emptyList()

        // 监听1: 我分享给别人的 (fromUid == currentUid)
        val listener1 = sharesCollection
            .whereEqualTo("fromUid", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "监听我分享的失败", error)
                    return@addSnapshotListener
                }

                iShareList = snapshot?.documents?.mapNotNull { doc ->
                    parseLocationShare(doc.id, doc.data ?: return@mapNotNull null)
                } ?: emptyList()

                // 合并两个列表并发送
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val contacts = mergeContactLists(iShareList, theyShareList)
                    trySend(contacts)
                }
            }

        // 监听2: 别人分享给我的 (toUid == currentUid)
        val listener2 = sharesCollection
            .whereEqualTo("toUid", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "监听分享给我的失败", error)
                    return@addSnapshotListener
                }

                theyShareList = snapshot?.documents?.mapNotNull { doc ->
                    parseLocationShare(doc.id, doc.data ?: return@mapNotNull null)
                } ?: emptyList()

                // 合并两个列表并发送
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val contacts = mergeContactLists(iShareList, theyShareList)
                    trySend(contacts)
                }
            }

        awaitClose {
            listener1.remove()
            listener2.remove()
        }
    }

    /**
     * 合并两个共享列表为联系人列表
     */
    private suspend fun mergeContactLists(
        iShareList: List<LocationShare>,
        theyShareList: List<LocationShare>
    ): List<Contact> {
        // 使用 Map 进行去重合并，Key 为 shareId
        val contactMap = mutableMapOf<String, Contact>()

        // 1. 先处理我分享给别人的 (Priority: Low)
        iShareList.forEach { share ->
            // 查询用户信息获取名称
            val userName = share.toEmail.substringBefore("@")

            val contact = Contact(
                id = share.id,
                email = share.toEmail,
                name = userName,
                shareStatus = share.status,
                shareDirection = ShareDirection.I_SHARE_TO_THEM,
                expireTime = share.expireTime,
                isLocationAvailable = false
            )
            contactMap[share.id] = contact
        }

        // 2. 后处理别人分享给我的 (Priority: High)
        // 如果是自我分享 (id 相同)，这里会覆盖上面的条目
        // 这很重要，因为我们需要保留 "THEY_SHARE_TO_ME" 状态以便用户可以点击 "接受"
        theyShareList.forEach { share ->
            // 获取分享者的邮箱和名称
            val fromUser = getUserByUid(share.fromUid)
            val userName = fromUser?.displayName ?: fromUser?.email?.substringBefore("@") ?: "未知用户"
            val userEmail = fromUser?.email ?: ""

            // 获取分享者的设备位置 (如果已接受)
            var location: LatLng? = null
            var lastUpdateTime: Long? = null
            if (share.status == ShareStatus.ACCEPTED) {
                val deviceSnapshot = devicesCollection
                    .whereEqualTo("ownerId", share.fromUid)
                    .limit(1)
                    .get()
                    .await()

                deviceSnapshot.documents.firstOrNull()?.let { deviceDoc ->
                    val geoPoint = deviceDoc.getGeoPoint("location")
                    // Firebase中存储的是WGS-84坐标（GPS原始坐标）
                    // 需要转换为GCJ-02以匹配Google Maps在中国的底图
                    location = geoPoint?.let { 
                        CoordinateConverter.wgs84ToGcj02(it.latitude, it.longitude) 
                    }
                    lastUpdateTime = deviceDoc.getTimestamp("lastUpdateTime")?.toDate()?.time
                }
            }

            val contact = Contact(
                id = share.id,
                email = userEmail,
                name = userName,
                shareStatus = share.status,
                shareDirection = ShareDirection.THEY_SHARE_TO_ME,
                expireTime = share.expireTime,
                location = location,
                lastUpdateTime = lastUpdateTime,
                isLocationAvailable = location != null
            )
            contactMap[share.id] = contact
        }

        return contactMap.values.toList()
    }

    /**
     * 解析 LocationShare 文档
     */
    private fun parseLocationShare(id: String, data: Map<String, Any>): LocationShare {
        return LocationShare(
            id = id,
            fromUid = data["fromUid"] as? String ?: "",
            toEmail = data["toEmail"] as? String ?: "",
            toUid = data["toUid"] as? String,
            status = try {
                ShareStatus.valueOf(data["status"] as? String ?: "PENDING")
            } catch (e: Exception) {
                ShareStatus.PENDING
            },
            expireTime = data["expireTime"] as? Long,
            createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
            acceptedAt = (data["acceptedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
        )
    }

    /**
     * 根据 UID 查询用户信息
     */
    private suspend fun getUserByUid(uid: String): User? {
        return try {
            val doc = usersCollection.document(uid).get().await()
            if (doc.exists()) {
                User(
                    uid = doc.getString("uid") ?: uid,
                    email = doc.getString("email") ?: "",
                    displayName = doc.getString("displayName"),
                    photoUrl = doc.getString("photoUrl"),
                    createdAt = doc.getLong("createdAt") ?: 0L
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询用户失败: $uid", e)
            null
        }
    }
}
