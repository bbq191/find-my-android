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
                "email" to (currentUser.email ?: ""),
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
     * 根据邮箱查找用户 UID
     */
    suspend fun findUserByEmail(email: String): String? {
        return try {
            val snapshot = usersCollection
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                snapshot.documents[0].getString("uid")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "查找用户失败: $email", e)
            null
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
     * @param targetInput 目标用户 UID 或 邮箱
     */
    suspend fun createLocationShare(
        targetInput: String,
        duration: ShareDuration
    ): Result<String> {
        val currentUser = auth.currentUser ?: return Result.failure(Exception("未登录"))
        val currentUid = currentUser.uid

        return try {
            // 尝试解析目标 UID
            val targetUid = if (targetInput.contains("@")) {
                findUserByEmail(targetInput)
                    ?: return Result.failure(Exception("未找到该邮箱对应的用户"))
            } else {
                targetInput
            }

            if (targetUid == currentUid) {
                return Result.failure(Exception("不能给自己分享位置"))
            }

            // 检查是否已存在共享关系（双向检查）
            val existingShare = sharesCollection
                .whereEqualTo("fromUid", currentUid)
                .whereEqualTo("toUid", targetUid)
                .limit(1)
                .get()
                .await()

            if (!existingShare.isEmpty) {
                val status = existingShare.documents[0].getString("status")
                return when (status) {
                    ShareStatus.PENDING.name -> Result.failure(Exception("已发送邀请，等待对方接受"))
                    ShareStatus.ACCEPTED.name -> Result.failure(Exception("已经在共享位置，可以使用暂停/恢复功能"))
                    ShareStatus.EXPIRED.name -> Result.failure(Exception("共享已过期，请先删除后重新邀请"))
                    ShareStatus.REJECTED.name -> Result.failure(Exception("对方已拒绝，请先删除后重新邀请"))
                    else -> Result.failure(Exception("已存在共享关系"))
                }
            }

            // 检查反向共享（对方是否已邀请我）
            val reverseShare = sharesCollection
                .whereEqualTo("fromUid", targetUid)
                .whereEqualTo("toUid", currentUid)
                .limit(1)
                .get()
                .await()

            if (!reverseShare.isEmpty) {
                val status = reverseShare.documents[0].getString("status")
                if (status == ShareStatus.PENDING.name) {
                    return Result.failure(Exception("对方已邀请您，请在联系人列表中接受邀请"))
                } else if (status == ShareStatus.ACCEPTED.name) {
                    return Result.failure(Exception("已经在共享位置，可以使用暂停/恢复功能"))
                }
            }

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
     * B 接受 A 的邀请后，自动创建反向共享（B → A），实现双向位置共享
     */
    suspend fun acceptLocationShare(shareId: String): Result<Unit> {
        val currentUid = auth.currentUser?.uid ?: return Result.failure(Exception("未登录"))

        return try {
            // 获取原始共享记录
            val shareDoc = sharesCollection.document(shareId).get().await()
            val fromUid = shareDoc.getString("fromUid")
                ?: return Result.failure(Exception("数据错误: fromUid 为空"))
            val expireTime = shareDoc.getLong("expireTime")

            // 1. 更新原共享状态为 ACCEPTED
            sharesCollection.document(shareId).update(
                mapOf(
                    "status" to ShareStatus.ACCEPTED.name,
                    "toUid" to currentUid,
                    "acceptedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 2. 查询分享者的所有设备，添加当前用户到 sharedWith
            val devicesSnapshot = devicesCollection.whereEqualTo("ownerId", fromUid).get().await()
            devicesSnapshot.documents.forEach { deviceDoc ->
                deviceDoc.reference.update(
                    "sharedWith", FieldValue.arrayUnion(currentUid)
                ).await()
            }

            // 3. 自动创建反向共享（B → A），使用相同的过期时间
            // 先检查是否已存在反向共享，避免重复创建
            val existingReverseShare = sharesCollection
                .whereEqualTo("fromUid", currentUid)
                .whereEqualTo("toUid", fromUid)
                .limit(1)
                .get()
                .await()

            if (existingReverseShare.isEmpty) {
                val reverseShareData = hashMapOf(
                    "fromUid" to currentUid,
                    "toUid" to fromUid,
                    "status" to ShareStatus.ACCEPTED.name,  // 直接设置为 ACCEPTED
                    "expireTime" to expireTime,  // 使用相同的过期时间
                    "createdAt" to FieldValue.serverTimestamp(),
                    "acceptedAt" to FieldValue.serverTimestamp()
                )

                sharesCollection.add(reverseShareData).await()
                Log.d(TAG, "自动创建反向共享: $currentUid → $fromUid")

                // 将 A 添加到 B 的设备的 sharedWith 列表
                val myDevicesSnapshot = devicesCollection.whereEqualTo("ownerId", currentUid).get().await()
                myDevicesSnapshot.documents.forEach { deviceDoc ->
                    deviceDoc.reference.update(
                        "sharedWith", FieldValue.arrayUnion(fromUid)
                    ).await()
                }
            } else {
                Log.d(TAG, "反向共享已存在，跳过创建")
            }

            Log.d(
                TAG,
                "接受位置共享成功: $shareId, 已自动建立双向共享"
            )
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
            val shareDoc = sharesCollection.document(shareId).get().await()
            val fromUid = shareDoc.getString("fromUid")

            sharesCollection.document(shareId).update(
                "status", ShareStatus.REJECTED.name
            ).await()

            // 模拟发送拒绝通知
            if (fromUid != null) {
                sendRejectNotification(fromUid)
            }

            Log.d(TAG, "拒绝位置共享成功: $shareId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "拒绝位置共享失败: $shareId", e)
            Result.failure(e)
        }
    }

    /**
     * 模拟发送拒绝通知
     * 在实际生产环境中，这应该通过 Cloud Functions 实现
     */
    private suspend fun sendRejectNotification(targetUid: String) {
        try {
            val userDoc = usersCollection.document(targetUid).get().await()

            @Suppress("UNCHECKED_CAST")
            val fcmTokens = userDoc.get("fcmTokens") as? List<String>
            if (!fcmTokens.isNullOrEmpty()) {
                Log.i(
                    TAG,
                    ">>> [模拟推送] 向用户 $targetUid 发送通知: 您的位置共享请求已被拒绝 (Tokens: ${fcmTokens.size})"
                )
            } else {
                Log.w(TAG, ">>> [模拟推送] 用户 $targetUid 没有注册 FCM Token，无法发送通知")
            }
        } catch (e: Exception) {
            Log.e(TAG, "发送拒绝通知失败", e)
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
                val devicesSnapshot =
                    devicesCollection.whereEqualTo("ownerId", currentUid).get().await()
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
            val toUid = shareDoc.getString("toUid")

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

            val updateData = mapOf(
                "isPaused" to false,
                "expireTime" to expireTime,
                "status" to ShareStatus.ACCEPTED.name  // 恢复时将状态从 EXPIRED 改回 ACCEPTED
            )

            // 1. 更新当前共享记录 (A → B)
            sharesCollection.document(shareId).update(updateData).await()
            Log.d(TAG, "恢复共享成功: $shareId")

            // 2. 同时更新反向共享记录 (B → A)，确保双方状态一致
            if (toUid != null) {
                val reverseShareSnapshot = sharesCollection
                    .whereEqualTo("fromUid", toUid)
                    .whereEqualTo("toUid", fromUid)
                    .limit(1)
                    .get()
                    .await()

                if (!reverseShareSnapshot.isEmpty) {
                    val reverseShareId = reverseShareSnapshot.documents[0].id
                    sharesCollection.document(reverseShareId).update(updateData).await()
                    Log.d(TAG, "同时恢复反向共享: $reverseShareId")
                } else {
                    Log.w(TAG, "未找到反向共享记录，可能是单向共享")
                }
            }

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
     * 清理过期的位置共享
     * 删除所有已过期的共享记录（包括 PENDING 和 ACCEPTED 状态）
     */
    suspend fun cleanupExpiredShares(): Result<Int> {
        val currentUid = auth.currentUser?.uid ?: return Result.failure(Exception("未登录"))
        val now = System.currentTimeMillis()

        return try {
            var deletedCount = 0

            // 查询所有与我相关的共享（我发出的或发给我的）
            val mySharesSnapshot = sharesCollection
                .whereEqualTo("fromUid", currentUid)
                .get()
                .await()

            val receivedSharesSnapshot = sharesCollection
                .whereEqualTo("toUid", currentUid)
                .get()
                .await()

            val allShares = mySharesSnapshot.documents + receivedSharesSnapshot.documents

            for (doc in allShares) {
                val expireTime = doc.getLong("expireTime")
                val status = doc.getString("status")

                // 检查是否过期
                if (expireTime != null && expireTime < now) {
                    // 如果是 PENDING 状态且超时 24 小时，直接删除
                    if (status == ShareStatus.PENDING.name) {
                        val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                        if (now - createdAt > 24 * 60 * 60 * 1000) { // 24 小时
                            doc.reference.delete().await()
                            deletedCount++
                            Log.d(TAG, "删除超时的 PENDING 共享: ${doc.id}")
                        }
                    } else {
                        // ACCEPTED 状态的过期共享，标记为 EXPIRED
                        doc.reference.update("status", ShareStatus.EXPIRED.name).await()
                        Log.d(TAG, "标记共享为 EXPIRED: ${doc.id}")
                    }
                }
            }

            Log.d(TAG, "清理完成: 删除 $deletedCount 个超时的 PENDING 共享")
            Result.success(deletedCount)
        } catch (e: Exception) {
            Log.e(TAG, "清理过期共享失败", e)
            Result.failure(e)
        }
    }

    /**
     * 实时监听我的联系人列表
     * 🔄 优化：同时监听 shares 和 devices 集合，确保位置实时更新
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

        // 🔄 监听3: 监听所有与我共享的设备位置更新
        // 当任何共享联系人的设备位置更新时，触发联系人列表刷新
        val listener3 = devicesCollection
            .whereArrayContains("sharedWith", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "监听共享设备失败", error)
                    return@addSnapshotListener
                }

                // 设备位置更新，触发重新合并
                Log.d(TAG, "🔄 检测到 ${snapshot?.size() ?: 0} 个共享设备更新，触发联系人列表刷新")
                snapshot?.documents?.forEach { doc ->
                    val ownerId = doc.getString("ownerId")
                    val location = doc.getGeoPoint("location")
                    val updateTime = doc.getTimestamp("lastUpdateTime")
                    Log.d(TAG, "  📱 设备: ${doc.id}, 所有者: $ownerId, 位置: $location, 更新时间: $updateTime")
                }

                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val contacts = mergeContactLists(iShareList, theyShareList)
                    Log.d(TAG, "🔄 合并后联系人列表: ${contacts.size} 个联系人")
                    contacts.forEach { contact ->
                        Log.d(TAG, "  👤 ${contact.name}: location=${contact.location}, lastUpdate=${contact.lastUpdateTime}")
                    }
                    trySend(contacts)
                }
            }

        awaitClose {
            listener1.remove()
            listener2.remove()
            listener3.remove()
        }
    }

    /**
     * 合并两个共享列表为联系人列表 (优化版：按好友 UID 合并)
     */
    private suspend fun mergeContactLists(
        iShareList: List<LocationShare>,
        theyShareList: List<LocationShare>
    ): List<Contact> {

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
            val name = myShare?.senderAliasName ?: theirShare?.receiverAliasName
            ?: "用户 ${otherUid.take(4)}"
            val avatar = myShare?.senderAliasAvatar ?: theirShare?.receiverAliasAvatar

            // 获取位置信息 (来自对方发出的共享)
            var location: LatLng? = null
            var lastUpdate: Long? = null
            var isLocationAvailable = false
            var deviceName: String? = null
            var battery: Int? = null

            if (theirShare != null) {
                if (theirShare.status == ShareStatus.ACCEPTED && !theirShare.isPaused) {
                    Log.d(TAG, "🔍 查询用户 $otherUid 的设备位置...")
                    val deviceSnapshot = devicesCollection
                        .whereEqualTo("ownerId", otherUid)
                        .orderBy("lastUpdateTime", com.google.firebase.firestore.Query.Direction.DESCENDING)  // 按更新时间降序排序
                        .limit(1)  // 获取最近更新的设备
                        .get()
                        .await()

                    if (deviceSnapshot.isEmpty) {
                        Log.w(TAG, "  ⚠️ 未找到用户 $otherUid 的设备")
                    } else {
                        Log.d(TAG, "  ✅ 找到 ${deviceSnapshot.size()} 个设备")
                    }

                    deviceSnapshot.documents.firstOrNull()?.let { deviceDoc ->
                        val geoPoint = deviceDoc.getGeoPoint("location")
                        val sharedWith = deviceDoc.get("sharedWith") as? List<*>
                        Log.d(TAG, "  📍 设备 ${deviceDoc.id}: location=$geoPoint, sharedWith=$sharedWith")

                        location = geoPoint?.let {
                            CoordinateConverter.wgs84ToGcj02(it.latitude, it.longitude)
                        }
                        lastUpdate = deviceDoc.getTimestamp("lastUpdateTime")?.toDate()?.time
                        isLocationAvailable = location != null
                        deviceName = deviceDoc.getString("name")
                        battery = deviceDoc.getLong("battery")?.toInt()
                    }
                }
            }

            // 如果是我发出的，记录我的共享 ID 用于操作
            // 如果只有对方分享给我，则记录对方的 ID 用于接受/拒绝
            val contactId = myShare?.id ?: theirShare?.id ?: ""

            // 关键修正: isPaused 应该反映"我是否暂停了给对方的共享"
            // 只有当 myShare 存在且我暂停了它时，isPaused 为 true
            val amIPaused = myShare?.isPaused == true

            Contact(
                id = contactId,
                email = "",
                name = name,
                avatarUrl = avatar,
                shareStatus = (myShare ?: theirShare)?.status ?: ShareStatus.PENDING,
                shareDirection = direction,
                expireTime = myShare?.expireTime ?: theirShare?.expireTime,
                targetUserId = otherUid, // 保存目标用户的 UID，用于位置请求
                location = location,
                lastUpdateTime = lastUpdate,
                isLocationAvailable = isLocationAvailable,
                isPaused = amIPaused,
                deviceName = deviceName,
                battery = battery
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
            } catch (_: Exception) {
                ShareStatus.PENDING
            },
            expireTime = data["expireTime"] as? Long,
            createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
                ?: System.currentTimeMillis(),
            acceptedAt = (data["acceptedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time,
            receiverAliasName = data["receiverAliasName"] as? String,
            receiverAliasAvatar = data["receiverAliasAvatar"] as? String,
            senderAliasName = data["senderAliasName"] as? String,
            senderAliasAvatar = data["senderAliasAvatar"] as? String,
            isPaused = data["isPaused"] as? Boolean ?: false
        )
    }
}