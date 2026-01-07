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
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.LocationShare
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.data.model.ShareStatus
import me.ikate.findmy.data.model.User
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
     * 仅保存 UID 和 创建时间
     */
    suspend fun syncCurrentUser(): Result<Unit> {
        val currentUser = auth.currentUser ?: return Result.failure(Exception("未登录"))

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

            Log.d(TAG, "用户信息同步成功: ${currentUser.uid}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "用户信息同步失败", e)
            Result.failure(e)
        }
    }

    /**
     * 获取当前用户信息
     */
    suspend fun getCurrentUser(): User? {
        val currentUser = auth.currentUser ?: return null
        
        // 尝试从 Firestore 获取完整信息
        return try {
            val doc = usersCollection.document(currentUser.uid).get().await()
            if (doc.exists()) {
                User(
                    uid = doc.getString("uid") ?: currentUser.uid,
                    createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                )
            } else {
                // 如果 Firestore 中不存在，返回 Auth 中的基本信息
                User(
                    uid = currentUser.uid
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取当前用户失败", e)
            null
        }
    }

    // ====================================================================
    // 位置共享管理
    // ====================================================================

    /**
     * 发起位置共享
     * @param targetUid 目标用户 UID
     */
    suspend fun createLocationShare(
        targetUid: String,
        duration: ShareDuration
    ): Result<String> {
        val currentUser = auth.currentUser ?: return Result.failure(Exception("未登录"))
        val currentUid = currentUser.uid

        return try {
            // 计算过期时间
            val expireTime = when (duration) {
                ShareDuration.ONE_HOUR -> System.currentTimeMillis() + duration.durationMillis!!
                ShareDuration.END_OF_DAY -> ShareDuration.calculateEndOfDay()
                ShareDuration.INDEFINITELY -> null
            }

            val shareData = hashMapOf(
                "fromUid" to currentUid,
                "toUid" to targetUid,
                "status" to ShareStatus.PENDING.name,
                "expireTime" to expireTime,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val docRef = sharesCollection.add(shareData).await()
            Log.d(TAG, "位置共享创建成功: ${docRef.id}, 目标UID: $targetUid")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "创建位置共享失败", e)
            Result.failure(e)
        }
    }

    /**
     * 接受位置共享
     */
    suspend fun acceptLocationShare(shareId: String): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: return Result.failure(Exception("未登录"))

        return try {
            // 更新共享状态
            sharesCollection.document(shareId).update(
                mapOf(
                    "status" to ShareStatus.ACCEPTED.name,
                    "toUid" to currentUid, // 确保 toUid 是当前用户
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
        // 虽然拒绝不需要UID校验，但为了保持一致性检查登录
        if (auth.currentUser == null) return Result.failure(Exception("未登录"))

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

    /**
     * 暂停位置共享
     */
    suspend fun pauseLocationShare(shareId: String): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: return Result.failure(Exception("未登录"))

        return try {
            val shareDoc = sharesCollection.document(shareId).get().await()
            val fromUid = shareDoc.getString("fromUid")

            // 只有发送者可以暂停
            if (fromUid != currentUid) {
                return Result.failure(Exception("只有位置发送方可以暂停共享"))
            }

            sharesCollection.document(shareId).update("isPaused", true).await()
            Log.d(TAG, "暂停共享成功: $shareId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "暂停共享失败: $shareId", e)
            Result.failure(e)
        }
    }

    /**
     * 恢复位置共享
     */
    suspend fun resumeLocationShare(shareId: String, duration: ShareDuration): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: return Result.failure(Exception("未登录"))

        return try {
            val shareDoc = sharesCollection.document(shareId).get().await()
            val fromUid = shareDoc.getString("fromUid")

            // 只有发送者可以恢复
            if (fromUid != currentUid) {
                return Result.failure(Exception("只有位置发送方可以恢复共享"))
            }

            // 计算新的过期时间
            val expireTime = when (duration) {
                ShareDuration.ONE_HOUR -> System.currentTimeMillis() + duration.durationMillis!!
                ShareDuration.END_OF_DAY -> ShareDuration.calculateEndOfDay()
                ShareDuration.INDEFINITELY -> null
            }

            sharesCollection.document(shareId).update(
                mapOf(
                    "isPaused" to false,
                    "expireTime" to expireTime
                )
            ).await()
            Log.d(TAG, "恢复共享成功: $shareId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "恢复共享失败: $shareId", e)
            Result.failure(e)
        }
    }

    /**
     * 绑定联系人（设置别名）
     */
    suspend fun bindContact(shareId: String, name: String, photoUrl: String?): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: return Result.failure(Exception("未登录"))

        return try {
            val shareDoc = sharesCollection.document(shareId).get().await()
            val fromUid = shareDoc.getString("fromUid")
            val toUid = shareDoc.getString("toUid")

            val updates = mutableMapOf<String, Any>()

            if (toUid == currentUid) {
                // 我是接收者，给发送者(fromUid)设置备注
                updates["receiverAliasName"] = name
                if (photoUrl != null) updates["receiverAliasAvatar"] = photoUrl
            } else if (fromUid == currentUid) {
                // 我是发送者，给接收者(toUid)设置备注
                updates["senderAliasName"] = name
                if (photoUrl != null) updates["senderAliasAvatar"] = photoUrl
            } else {
                return Result.failure(Exception("无权修改此共享记录"))
            }

            sharesCollection.document(shareId).update(updates).await()
            Log.d(TAG, "联系人绑定成功: $shareId, alias=$name")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "联系人绑定失败", e)
            Result.failure(e)
        }
    }

    // ====================================================================
    // 联系人列表查询
    // ====================================================================

    /**
     * 实时监听我的联系人列表
     */
    fun observeMyContacts(): Flow<List<Contact>> = callbackFlow {
        val currentUid = auth.currentUser?.uid

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
     * 合并两个共享列表为联系人列表 (优化版：按好友 UID 合并)
     */
    private suspend fun mergeContactLists(
        iShareList: List<LocationShare>,
        theyShareList: List<LocationShare>
    ): List<Contact> {
        val currentUid = auth.currentUser?.uid ?: return emptyList()
        
        // 临时存储好友 UID -> 共享信息对
        // Key: 对方 UID, Value: (我发出的共享, 对方发出的共享)
        val userShareMap = mutableMapOf<String, Pair<LocationShare?, LocationShare?>>()

        // 1. 处理我分享给别人的
        iShareList.forEach { share ->
            val otherUid = share.toUid ?: return@forEach
            val current = userShareMap[otherUid]
            userShareMap[otherUid] = Pair(share, current?.second)
        }

        // 2. 处理别人分享给我的
        theyShareList.forEach { share ->
            val otherUid = share.fromUid
            val current = userShareMap[otherUid]
            userShareMap[otherUid] = Pair(current?.first, share)
        }

        // 3. 构建最终的联系人对象
        return userShareMap.map { (otherUid, sharePair) ->
            val myShare = sharePair.first
            val theirShare = sharePair.second

            // 确定最终的方向
            val direction = when {
                myShare != null && theirShare != null -> ShareDirection.MUTUAL
                myShare != null -> ShareDirection.I_SHARE_TO_THEM
                else -> ShareDirection.THEY_SHARE_TO_ME
            }

            // 确定显示名称和头像
            // 优先顺序：接收者备注 > 发送者备注 > UID
            val name = myShare?.senderAliasName ?: theirShare?.receiverAliasName ?: "用户 ${otherUid.take(4)}"
            val avatar = myShare?.senderAliasAvatar ?: theirShare?.receiverAliasAvatar

            // 获取位置信息 (来自对方发出的共享)
            var location: LatLng? = null
            var lastUpdate: Long? = null
            var isLocationAvailable = false
            var isPaused = false

            if (theirShare != null) {
                isPaused = theirShare.isPaused
                if (theirShare.status == ShareStatus.ACCEPTED && !isPaused) {
                    val deviceSnapshot = devicesCollection
                        .whereEqualTo("ownerId", otherUid)
                        .limit(1)
                        .get()
                        .await()

                    deviceSnapshot.documents.firstOrNull()?.let { deviceDoc ->
                        val geoPoint = deviceDoc.getGeoPoint("location")
                        location = geoPoint?.let { 
                            CoordinateConverter.wgs84ToGcj02(it.latitude, it.longitude) 
                        }
                        lastUpdate = deviceDoc.getTimestamp("lastUpdateTime")?.toDate()?.time
                        isLocationAvailable = location != null
                    }
                }
            }

            // 如果是我发出的，记录我的共享 ID 用于操作
            // 如果只有对方分享给我，则记录对方的 ID 用于接受/拒绝
            val contactId = myShare?.id ?: theirShare?.id ?: ""

            Contact(
                id = contactId,
                email = "",
                name = name,
                avatarUrl = avatar,
                shareStatus = (myShare ?: theirShare)?.status ?: ShareStatus.PENDING,
                shareDirection = direction,
                expireTime = myShare?.expireTime ?: theirShare?.expireTime,
                location = location,
                lastUpdateTime = lastUpdate,
                isLocationAvailable = isLocationAvailable,
                isPaused = myShare?.isPaused ?: false // 这里的暂停状态显示为“我是否暂停了共享”
            )
        }
    }

    /**
     * 解析 LocationShare 文档
     */
    private fun parseLocationShare(id: String, data: Map<String, Any>): LocationShare {
        return LocationShare(
            id = id,
            fromUid = data["fromUid"] as? String ?: "",
            toUid = data["toUid"] as? String,
            status = try {
                ShareStatus.valueOf(data["status"] as? String ?: "PENDING")
            } catch (e: Exception) {
                ShareStatus.PENDING
            },
            expireTime = data["expireTime"] as? Long,
            createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis(),
            acceptedAt = (data["acceptedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time,
            receiverAliasName = data["receiverAliasName"] as? String,
            receiverAliasAvatar = data["receiverAliasAvatar"] as? String,
            senderAliasName = data["senderAliasName"] as? String,
            senderAliasAvatar = data["senderAliasAvatar"] as? String,
            isPaused = data["isPaused"] as? Boolean ?: false
        )
    }
}