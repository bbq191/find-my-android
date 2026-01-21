package me.ikate.findmy.data.remote.firestore

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import me.ikate.findmy.data.repository.AuthRepository
import me.ikate.findmy.util.DeviceIdProvider

/**
 * 远程指令仓库
 *
 * 负责与 Firestore commands 集合交互:
 * - 读取待执行的指令
 * - 更新指令执行状态
 * - 上报设备状态
 *
 * 集合路径: users/{uid}/devices/{deviceId}/commands/
 */
class CommandRepository(private val context: Context) {

    companion object {
        private const val TAG = "CommandRepository"

        // Firestore 集合路径
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_DEVICES = "devices"
        private const val COLLECTION_COMMANDS = "commands"

        // 单例
        @Volatile
        private var instance: CommandRepository? = null

        fun getInstance(context: Context): CommandRepository {
            return instance ?: synchronized(this) {
                instance ?: CommandRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private val deviceId: String by lazy {
        DeviceIdProvider.getDeviceId(context)
    }

    /**
     * 获取当前用户的 UID
     * 用于构建 Firestore 路径
     */
    private fun getCurrentUid(): String? {
        return AuthRepository.getUserId(context)
    }

    /**
     * 获取指令集合引用
     */
    private fun getCommandsCollection(uid: String) = firestore
        .collection(COLLECTION_USERS)
        .document(uid)
        .collection(COLLECTION_DEVICES)
        .document(deviceId)
        .collection(COLLECTION_COMMANDS)

    /**
     * 获取设备文档引用
     */
    private fun getDeviceDocument(uid: String) = firestore
        .collection(COLLECTION_USERS)
        .document(uid)
        .collection(COLLECTION_DEVICES)
        .document(deviceId)

    /**
     * 获取所有待执行的指令
     *
     * @return 待执行指令列表
     */
    suspend fun getPendingCommands(): List<CommandEntity> {
        val uid = getCurrentUid()
        if (uid == null) {
            Log.w(TAG, "用户未登录，无法获取指令")
            return emptyList()
        }

        return try {
            val snapshot = getCommandsCollection(uid)
                .whereEqualTo("status", CommandStatus.PENDING.value)
                .orderBy("created_at", Query.Direction.ASCENDING)
                .get()
                .await()

            val commands = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CommandEntity::class.java)
            }

            Log.d(TAG, "获取到 ${commands.size} 条待执行指令")
            commands
        } catch (e: Exception) {
            Log.e(TAG, "获取待执行指令失败", e)
            emptyList()
        }
    }

    /**
     * 获取最新的待执行指令
     *
     * @return 最新的待执行指令，没有则返回 null
     */
    suspend fun getLatestPendingCommand(): CommandEntity? {
        val uid = getCurrentUid()
        if (uid == null) {
            Log.w(TAG, "用户未登录，无法获取指令")
            return null
        }

        return try {
            val snapshot = getCommandsCollection(uid)
                .whereEqualTo("status", CommandStatus.PENDING.value)
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            val command = snapshot.documents.firstOrNull()?.toObject(CommandEntity::class.java)
            if (command != null) {
                Log.d(TAG, "获取到最新指令: ${command.type}")
            }
            command
        } catch (e: Exception) {
            Log.e(TAG, "获取最新指令失败", e)
            null
        }
    }

    /**
     * 监听待执行指令变化
     *
     * @return 指令列表的 Flow
     */
    fun observePendingCommands(): Flow<List<CommandEntity>> = callbackFlow {
        val uid = getCurrentUid()
        if (uid == null) {
            Log.w(TAG, "用户未登录，无法监听指令")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listenerRegistration = getCommandsCollection(uid)
            .whereEqualTo("status", CommandStatus.PENDING.value)
            .orderBy("created_at", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "监听指令变化失败", error)
                    return@addSnapshotListener
                }

                val commands = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(CommandEntity::class.java)
                } ?: emptyList()

                Log.d(TAG, "指令变化: ${commands.size} 条待执行")
                trySend(commands)
            }

        awaitClose {
            listenerRegistration.remove()
            Log.d(TAG, "停止监听指令变化")
        }
    }

    /**
     * 更新指令状态为执行中
     *
     * @param cmdId 指令 ID
     */
    suspend fun markAsExecuting(cmdId: String): Boolean {
        return updateCommandStatus(cmdId, CommandStatus.EXECUTING, null)
    }

    /**
     * 更新指令状态为已执行
     *
     * @param cmdId 指令 ID
     * @param response 执行结果数据
     */
    suspend fun markAsExecuted(cmdId: String, response: Map<String, Any>? = null): Boolean {
        return updateCommandStatus(cmdId, CommandStatus.EXECUTED, response)
    }

    /**
     * 更新指令状态为失败
     *
     * @param cmdId 指令 ID
     * @param errorMessage 错误信息
     */
    suspend fun markAsFailed(cmdId: String, errorMessage: String): Boolean {
        val response = mapOf("error" to errorMessage)
        return updateCommandStatus(cmdId, CommandStatus.FAILED, response)
    }

    /**
     * 更新指令状态
     */
    private suspend fun updateCommandStatus(
        cmdId: String,
        status: CommandStatus,
        response: Map<String, Any>?
    ): Boolean {
        val uid = getCurrentUid()
        if (uid == null) {
            Log.w(TAG, "用户未登录，无法更新指令状态")
            return false
        }

        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to status.value,
                "executed_at" to Timestamp.now()
            )
            if (response != null) {
                updates["device_response"] = response
            }

            getCommandsCollection(uid)
                .document(cmdId)
                .update(updates)
                .await()

            Log.d(TAG, "指令 $cmdId 状态更新为 ${status.value}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "更新指令状态失败: $cmdId", e)
            false
        }
    }

    /**
     * 上报设备位置
     *
     * @param latitude 纬度
     * @param longitude 经度
     * @param accuracy 精度
     * @param address 地址
     */
    suspend fun reportLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        address: String? = null
    ): Boolean {
        val uid = getCurrentUid()
        if (uid == null) {
            Log.w(TAG, "用户未登录，无法上报位置")
            return false
        }

        return try {
            val locationData = mapOf(
                "last_location" to mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "accuracy" to accuracy,
                    "address" to address,
                    "timestamp" to Timestamp.now()
                ),
                "online" to true,
                "last_seen" to Timestamp.now()
            )

            getDeviceDocument(uid)
                .set(locationData, SetOptions.merge())
                .await()

            Log.d(TAG, "位置已上报: ($latitude, $longitude)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "上报位置失败", e)
            false
        }
    }

    /**
     * 上报丢失模式状态
     *
     * @param enabled 是否启用
     * @param message 丢失消息
     * @param phoneNumber 联系电话
     */
    suspend fun reportLostModeStatus(
        enabled: Boolean,
        message: String? = null,
        phoneNumber: String? = null
    ): Boolean {
        val uid = getCurrentUid()
        if (uid == null) {
            Log.w(TAG, "用户未登录，无法上报丢失模式状态")
            return false
        }

        return try {
            val lostModeData = mutableMapOf<String, Any>(
                "enabled" to enabled
            )
            if (enabled) {
                lostModeData["enabled_at"] = Timestamp.now()
                message?.let { lostModeData["message"] = it }
                phoneNumber?.let { lostModeData["phone_number"] = it }
            }

            val updates = mapOf(
                "lost_mode" to lostModeData,
                "last_seen" to Timestamp.now()
            )

            getDeviceDocument(uid)
                .set(updates, SetOptions.merge())
                .await()

            Log.d(TAG, "丢失模式状态已上报: enabled=$enabled")
            true
        } catch (e: Exception) {
            Log.e(TAG, "上报丢失模式状态失败", e)
            false
        }
    }

    /**
     * 上报响铃状态
     *
     * @param triggered 是否触发 (true=开始响铃, false=停止响铃)
     */
    suspend fun reportRingStatus(triggered: Boolean): Boolean {
        val uid = getCurrentUid()
        if (uid == null) {
            Log.w(TAG, "用户未登录，无法上报响铃状态")
            return false
        }

        return try {
            val ringData = if (triggered) {
                mapOf("triggered_at" to Timestamp.now())
            } else {
                mapOf("stopped_at" to Timestamp.now())
            }

            val updates = mapOf(
                "last_ring" to ringData,
                "last_seen" to Timestamp.now()
            )

            getDeviceDocument(uid)
                .set(updates, SetOptions.merge())
                .await()

            Log.d(TAG, "响铃状态已上报: triggered=$triggered")
            true
        } catch (e: Exception) {
            Log.e(TAG, "上报响铃状态失败", e)
            false
        }
    }

    /**
     * 更新设备在线状态
     */
    suspend fun updateOnlineStatus(online: Boolean): Boolean {
        val uid = getCurrentUid()
        if (uid == null) {
            Log.w(TAG, "用户未登录，无法更新在线状态")
            return false
        }

        return try {
            val updates = mapOf(
                "online" to online,
                "last_seen" to Timestamp.now()
            )

            getDeviceDocument(uid)
                .set(updates, SetOptions.merge())
                .await()

            Log.d(TAG, "在线状态已更新: online=$online")
            true
        } catch (e: Exception) {
            Log.e(TAG, "更新在线状态失败", e)
            false
        }
    }
}
