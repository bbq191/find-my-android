package me.ikate.findmy.data.repository

import android.content.Context
import android.util.Log
import com.mapbox.geojson.Point
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.ikate.findmy.data.local.FindMyDatabase
import me.ikate.findmy.data.local.entity.ContactEntity
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.data.model.ShareStatus
import me.ikate.findmy.data.model.User
import me.ikate.findmy.data.remote.mqtt.LocationMqttService

/**
 * 联系人和位置共享数据仓库
 * 使用 Room 本地存储 + MQTT 同步（替代 Firestore）
 */
class ContactRepository(private val context: Context) {

    private val contactDao = FindMyDatabase.getInstance(context).contactDao()
    private val mqttService: LocationMqttService by lazy {
        DeviceRepository.getMqttService(context)
    }

    // 获取当前用户 ID
    private fun getCurrentUid(): String = AuthRepository.getUserId(context)

    // 获取当前用户名称
    private fun getCurrentUserName(): String {
        return context.getSharedPreferences("findmy_prefs", Context.MODE_PRIVATE)
            .getString("user_display_name", null) ?: "用户 ${getCurrentUid().take(6)}"
    }

    companion object {
        private const val TAG = "ContactRepository"
    }

    // ====================================================================
    // 用户管理
    // ====================================================================

    /**
     * 同步当前用户信息（本地保存）
     */
    suspend fun syncCurrentUser(): Result<Unit> {
        val currentUid = getCurrentUid()
        Log.d(TAG, "用户信息同步成功: $currentUid")
        return Result.success(Unit)
    }

    /**
     * 根据邮箱查找用户 UID
     * 注意：离线模式下无法查找远程用户
     */
    suspend fun findUserByEmail(email: String): String? {
        // 在本地联系人中查找
        val contacts = contactDao.getAll()
        return contacts.find { it.email == email }?.targetUserId
    }

    /**
     * 获取当前用户信息
     */
    suspend fun getCurrentUser(): User? {
        val currentUid = getCurrentUid()
        return User(uid = currentUid)
    }

    // ====================================================================
    // 位置共享管理
    // ====================================================================

    /**
     * 发起位置共享
     * 通过 MQTT 发送共享请求
     */
    suspend fun createLocationShare(
        targetInput: String,
        duration: ShareDuration
    ): Result<String> {
        val currentUid = getCurrentUid()

        return try {
            // 检查是否已存在该联系人（根据 targetUserId）
            val existingContact = contactDao.getByTargetUserId(targetInput)
            if (existingContact != null) {
                // 如果是被对方移除的状态，允许重新发起共享（先删除旧记录）
                if (existingContact.shareStatus == ShareStatus.REMOVED.name) {
                    contactDao.deleteById(existingContact.id)
                    Log.d(TAG, "已删除被移除的旧联系人记录，准备重新发起共享")
                } else {
                    // 其他状态返回相应的错误信息
                    val errorMsg = when (existingContact.shareStatus) {
                        ShareStatus.PENDING.name -> "该联系人的共享请求正在等待处理"
                        ShareStatus.ACCEPTED.name -> "该联系人已在列表中"
                        ShareStatus.REJECTED.name -> "该联系人已拒绝共享，请稍后再试"
                        ShareStatus.EXPIRED.name -> "该联系人的共享已过期，请删除后重新添加"
                        else -> "该联系人已存在"
                    }
                    return Result.failure(Exception(errorMsg))
                }
            }

            // 不能添加自己
            if (targetInput == currentUid) {
                return Result.failure(Exception("不能添加自己为联系人"))
            }

            // 生成共享 ID
            val shareId = "share_${currentUid}_${System.currentTimeMillis()}"

            // 计算过期时间
            val expireTime = when (duration) {
                ShareDuration.ONE_HOUR -> System.currentTimeMillis() + duration.durationMillis!!
                ShareDuration.END_OF_DAY -> ShareDuration.calculateEndOfDay()
                ShareDuration.INDEFINITELY -> null
            }

            // 创建本地联系人记录
            val contactEntity = ContactEntity(
                id = shareId,
                email = if (targetInput.contains("@")) targetInput else "",
                name = "用户 ${targetInput.take(8)}",
                shareStatus = ShareStatus.PENDING.name,
                shareDirection = ShareDirection.I_SHARE_TO_THEM.name,
                expireTime = expireTime,
                targetUserId = targetInput,
                createdAt = System.currentTimeMillis()
            )

            contactDao.upsert(contactEntity)
            Log.d(TAG, "位置共享创建成功: $shareId, 目标: $targetInput")

            // 通过 MQTT 发送共享请求给目标用户
            val sendResult = mqttService.publishShareRequest(
                targetUserId = targetInput,
                shareId = shareId,
                senderId = currentUid,
                senderName = getCurrentUserName(),
                senderEmail = null,
                expireTime = expireTime
            )

            if (sendResult.isSuccess) {
                Log.d(TAG, "邀请请求已通过 MQTT 发送")
            } else {
                Log.w(TAG, "邀请请求发送失败（已加入离线队列）")
            }

            Result.success(shareId)
        } catch (e: Exception) {
            Log.e(TAG, "创建位置共享失败", e)
            Result.failure(e)
        }
    }

    /**
     * 接受位置共享（本地操作 + 发送 MQTT 响应）
     * 用于用户主动接受邀请
     */
    suspend fun acceptLocationShare(shareId: String): Result<Unit> {
        return try {
            val contact = contactDao.getById(shareId)
            contactDao.updateStatus(shareId, ShareStatus.ACCEPTED.name)
            Log.d(TAG, "接受位置共享成功: $shareId")

            // 通过 MQTT 通知对方已接受
            contact?.targetUserId?.let { targetUserId ->
                val sendResult = mqttService.publishShareResponse(
                    targetUserId = targetUserId,
                    shareId = shareId,
                    responderId = getCurrentUid(),
                    responderName = getCurrentUserName(),
                    accepted = true
                )
                if (sendResult.isSuccess) {
                    Log.d(TAG, "接受响应已通过 MQTT 发送")
                } else {
                    Log.w(TAG, "接受响应发送失败（已加入离线队列）")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "接受位置共享失败: $shareId", e)
            Result.failure(e)
        }
    }

    /**
     * 更新共享状态（仅本地操作，不发送 MQTT）
     * 用于收到对方的响应后更新本地状态
     */
    suspend fun updateShareStatusLocally(shareId: String, status: ShareStatus): Result<Unit> {
        return try {
            val contact = contactDao.getById(shareId)

            // 如果对方接受了我的邀请，且当前是单向共享（我分享给对方），则更新为双向共享
            if (status == ShareStatus.ACCEPTED &&
                contact?.shareDirection == ShareDirection.I_SHARE_TO_THEM.name) {
                contactDao.upsert(
                    contact.copy(
                        shareStatus = status.name,
                        shareDirection = ShareDirection.MUTUAL.name,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "本地状态更新成功: $shareId -> $status, shareDirection -> MUTUAL")
            } else {
                contactDao.updateStatus(shareId, status.name)
                Log.d(TAG, "本地状态更新成功: $shareId -> $status")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "本地状态更新失败: $shareId", e)
            Result.failure(e)
        }
    }

    /**
     * 删除联系人（仅本地操作，不发送 MQTT）
     * 用于收到对方停止共享通知后删除本地记录
     */
    suspend fun deleteContactLocally(shareId: String): Result<Unit> {
        return try {
            contactDao.deleteById(shareId)
            Log.d(TAG, "本地联系人删除成功: $shareId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "本地联系人删除失败: $shareId", e)
            Result.failure(e)
        }
    }

    /**
     * 标记联系人为已被移除状态（仅本地操作）
     * 用于收到对方移除通知后更新本地状态，显示"已被移出"
     */
    suspend fun markContactAsRemoved(targetUserId: String): Result<Unit> {
        return try {
            val contact = contactDao.getByTargetUserId(targetUserId)
            if (contact != null) {
                contactDao.upsert(
                    contact.copy(
                        shareStatus = ShareStatus.REMOVED.name,
                        isLocationAvailable = false,
                        latitude = null,
                        longitude = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "联系人已标记为移除状态: $targetUserId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "标记联系人移除状态失败: $targetUserId", e)
            Result.failure(e)
        }
    }

    /**
     * 拒绝位置共享
     */
    suspend fun rejectLocationShare(shareId: String): Result<Unit> {
        return try {
            val contact = contactDao.getById(shareId)
            contactDao.updateStatus(shareId, ShareStatus.REJECTED.name)
            // 清除位置信息
            contactDao.clearLocation(shareId)
            Log.d(TAG, "拒绝位置共享成功: $shareId")

            // 通过 MQTT 通知对方已拒绝
            contact?.targetUserId?.let { targetUserId ->
                val sendResult = mqttService.publishShareResponse(
                    targetUserId = targetUserId,
                    shareId = shareId,
                    responderId = getCurrentUid(),
                    responderName = getCurrentUserName(),
                    accepted = false
                )
                if (sendResult.isSuccess) {
                    Log.d(TAG, "拒绝响应已通过 MQTT 发送")
                } else {
                    Log.w(TAG, "拒绝响应发送失败（已加入离线队列）")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "拒绝位置共享失败: $shareId", e)
            Result.failure(e)
        }
    }

    /**
     * 停止共享（删除共享关系）
     * 会发送移除通知给对方，对方会显示"已被移出"状态
     */
    suspend fun stopSharing(shareId: String): Result<Unit> {
        return try {
            // 先获取联系人信息（在删除之前）
            val contact = contactDao.getById(shareId)

            // 删除本地记录
            contactDao.deleteById(shareId)
            Log.d(TAG, "停止共享成功: $shareId")

            // 通过 MQTT 通知对方已被移除
            contact?.targetUserId?.let { targetUserId ->
                val sendResult = mqttService.publishShareRemove(
                    targetUserId = targetUserId,
                    shareId = shareId,
                    responderId = getCurrentUid(),
                    responderName = getCurrentUserName()
                )
                if (sendResult.isSuccess) {
                    Log.d(TAG, "移除通知已发送给: $targetUserId")
                } else {
                    Log.w(TAG, "移除通知发送失败（已加入离线队列）")
                }
            }

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
        return try {
            val contact = contactDao.getById(shareId) ?: return Result.failure(Exception("未找到共享记录"))
            contactDao.upsert(contact.copy(isPaused = true, updatedAt = System.currentTimeMillis()))
            // 清除位置信息
            contactDao.clearLocation(shareId)
            Log.d(TAG, "暂停共享成功: $shareId")

            // 通过 MQTT 通知对方共享已暂停
            contact.targetUserId?.let { targetUserId ->
                val sendResult = mqttService.publishSharePause(
                    targetUserId = targetUserId,
                    senderId = getCurrentUid(),
                    senderName = getCurrentUserName(),
                    isPaused = true
                )
                if (sendResult.isSuccess) {
                    Log.d(TAG, "暂停共享通知已发送给: $targetUserId")
                } else {
                    Log.w(TAG, "暂停共享通知发送失败（已加入离线队列）")
                }
            }

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
        return try {
            val contact = contactDao.getById(shareId) ?: return Result.failure(Exception("未找到共享记录"))

            val expireTime = when (duration) {
                ShareDuration.ONE_HOUR -> System.currentTimeMillis() + duration.durationMillis!!
                ShareDuration.END_OF_DAY -> ShareDuration.calculateEndOfDay()
                ShareDuration.INDEFINITELY -> null
            }

            contactDao.upsert(
                contact.copy(
                    isPaused = false,
                    expireTime = expireTime,
                    shareStatus = ShareStatus.ACCEPTED.name,
                    updatedAt = System.currentTimeMillis()
                )
            )
            Log.d(TAG, "恢复共享成功: $shareId")

            // 通过 MQTT 通知对方共享已恢复
            contact.targetUserId?.let { targetUserId ->
                val sendResult = mqttService.publishSharePause(
                    targetUserId = targetUserId,
                    senderId = getCurrentUid(),
                    senderName = getCurrentUserName(),
                    isPaused = false
                )
                if (sendResult.isSuccess) {
                    Log.d(TAG, "恢复共享通知已发送给: $targetUserId")
                } else {
                    Log.w(TAG, "恢复共享通知发送失败（已加入离线队列）")
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
        return try {
            val contact = contactDao.getById(shareId) ?: return Result.failure(Exception("未找到共享记录"))
            contactDao.upsert(
                contact.copy(
                    name = name,
                    avatarUrl = photoUrl,
                    updatedAt = System.currentTimeMillis()
                )
            )
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
     */
    suspend fun cleanupExpiredShares(): Result<Int> {
        return try {
            contactDao.markExpired()
            // 清除所有过期联系人的位置信息
            contactDao.clearLocationByStatus(ShareStatus.EXPIRED.name)
            // 清除所有被拒绝联系人的位置信息
            contactDao.clearLocationByStatus(ShareStatus.REJECTED.name)
            Log.d(TAG, "清理过期共享完成")
            Result.success(0)
        } catch (e: Exception) {
            Log.e(TAG, "清理过期共享失败", e)
            Result.failure(e)
        }
    }

    /**
     * 实时监听我的联系人列表
     */
    fun observeMyContacts(): Flow<List<Contact>> {
        return contactDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * 获取所有联系人
     */
    suspend fun getAllContacts(): List<Contact> {
        return contactDao.getAll().map { it.toDomain() }
    }

    /**
     * 更新联系人位置（从 MQTT 消息接收）
     */
    suspend fun updateContactLocation(
        targetUserId: String,
        location: Point,
        lastUpdateTime: Long,
        deviceName: String? = null,
        battery: Int? = null
    ) {
        try {
            val contact = contactDao.getByTargetUserId(targetUserId)
            if (contact != null) {
                contactDao.upsert(
                    contact.copy(
                        latitude = location.latitude(),
                        longitude = location.longitude(),
                        lastUpdateTime = lastUpdateTime,
                        isLocationAvailable = true,
                        deviceName = deviceName ?: contact.deviceName,
                        battery = battery ?: contact.battery,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "联系人位置更新: $targetUserId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新联系人位置失败", e)
        }
    }

    /**
     * 添加联系人（从 MQTT 接收共享请求时调用）
     */
    suspend fun addContactFromShare(
        shareId: String,
        fromUserId: String,
        fromUserName: String,
        expireTime: Long?
    ) {
        try {
            // 检查是否已存在该用户的共享记录
            val existingContact = contactDao.getByTargetUserId(fromUserId)
            if (existingContact != null) {
                // 如果已存在，更新为新的共享请求
                Log.d(TAG, "收到重复共享请求，更新现有记录: $fromUserId (状态: ${existingContact.shareStatus})")
                contactDao.upsert(
                    existingContact.copy(
                        id = shareId,
                        name = fromUserName, // 更新名称
                        shareStatus = ShareStatus.PENDING.name,
                        shareDirection = ShareDirection.THEY_SHARE_TO_ME.name,
                        expireTime = expireTime,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                return
            }

            // 不存在则新建
            val contactEntity = ContactEntity(
                id = shareId,
                name = fromUserName,
                shareStatus = ShareStatus.PENDING.name,
                shareDirection = ShareDirection.THEY_SHARE_TO_ME.name,
                expireTime = expireTime,
                targetUserId = fromUserId,
                createdAt = System.currentTimeMillis()
            )
            contactDao.upsert(contactEntity)
            Log.d(TAG, "收到共享请求，已添加联系人: $fromUserId")
        } catch (e: Exception) {
            Log.e(TAG, "添加联系人失败", e)
        }
    }

    /**
     * 检查是否应该响应来自指定用户的请求
     * 返回 true 表示应该响应，false 表示应该忽略
     *
     * 检查条件：
     * 1. 该用户必须在联系人列表中
     * 2. 共享状态必须是 ACCEPTED
     * 3. 我没有暂停与该用户的共享
     */
    suspend fun shouldRespondToRequest(requesterUid: String): Boolean {
        return try {
            val contact = contactDao.getByTargetUserId(requesterUid) ?: return false

            // 检查共享状态是否为 ACCEPTED
            if (contact.shareStatus != ShareStatus.ACCEPTED.name) {
                Log.d(TAG, "忽略请求: 共享状态不是 ACCEPTED (${contact.shareStatus})")
                return false
            }

            // 检查是否暂停了共享
            if (contact.isPaused) {
                Log.d(TAG, "忽略请求: 共享已暂停")
                return false
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "检查请求响应条件失败", e)
            false
        }
    }
}
