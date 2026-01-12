package me.ikate.findmy.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import me.ikate.findmy.data.model.ShareStatus

/**
 * 数据迁移工具
 * 用于修复旧版本遗留的数据问题
 */
object MigrationHelper {

    private const val TAG = "MigrationHelper"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * 修复所有共享关系的 sharedWith 字段
     *
     * 使用场景：
     * - 从旧版本升级后，devices 的 sharedWith 字段为空
     * - 用户之间有共享关系（ACCEPTED 状态），但设备未正确添加到 sharedWith
     *
     * 执行方式：
     * - 在应用启动时调用一次（仅开发/测试环境）
     * - 或在设置页面添加"修复共享关系"按钮
     */
    suspend fun fixSharedWithFields(): Result<Int> {
        val currentUid = auth.currentUser?.uid
            ?: return Result.failure(Exception("用户未登录"))

        return try {
            var fixedCount = 0

            // 1. 查询所有与我相关的 ACCEPTED 状态共享
            val mySharesSnapshot = firestore.collection("location_shares")
                .whereEqualTo("fromUid", currentUid)
                .whereEqualTo("status", ShareStatus.ACCEPTED.name)
                .get()
                .await()

            val receivedSharesSnapshot = firestore.collection("location_shares")
                .whereEqualTo("toUid", currentUid)
                .whereEqualTo("status", ShareStatus.ACCEPTED.name)
                .get()
                .await()

            // 2. 处理我分享给别人的（需要把对方 UID 添加到我的设备的 sharedWith）
            for (shareDoc in mySharesSnapshot.documents) {
                val toUid = shareDoc.getString("toUid") ?: continue

                // 查询我的所有设备
                val myDevicesSnapshot = firestore.collection("devices")
                    .whereEqualTo("ownerId", currentUid)
                    .get()
                    .await()

                for (deviceDoc in myDevicesSnapshot.documents) {
                    @Suppress("UNCHECKED_CAST")
                    val sharedWith = deviceDoc.get("sharedWith") as? List<String> ?: emptyList()

                    if (!sharedWith.contains(toUid)) {
                        deviceDoc.reference.update(
                            "sharedWith", FieldValue.arrayUnion(toUid)
                        ).await()
                        fixedCount++
                        Log.d(TAG, "✅ 已修复: 设备 ${deviceDoc.id} 添加共享对象 $toUid")
                    }
                }
            }

            // 3. 处理别人分享给我的（需要把我的 UID 添加到对方设备的 sharedWith）
            for (shareDoc in receivedSharesSnapshot.documents) {
                val fromUid = shareDoc.getString("fromUid") ?: continue

                // 查询对方的所有设备
                val theirDevicesSnapshot = firestore.collection("devices")
                    .whereEqualTo("ownerId", fromUid)
                    .get()
                    .await()

                for (deviceDoc in theirDevicesSnapshot.documents) {
                    @Suppress("UNCHECKED_CAST")
                    val sharedWith = deviceDoc.get("sharedWith") as? List<String> ?: emptyList()

                    if (!sharedWith.contains(currentUid)) {
                        deviceDoc.reference.update(
                            "sharedWith", FieldValue.arrayUnion(currentUid)
                        ).await()
                        fixedCount++
                        Log.d(TAG, "✅ 已修复: 设备 ${deviceDoc.id} 添加共享对象 $currentUid")
                    }
                }
            }

            Log.d(TAG, "🎉 修复完成: 共修复 $fixedCount 个设备的 sharedWith 字段")
            Result.success(fixedCount)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 修复失败", e)
            Result.failure(e)
        }
    }

    /**
     * 检查当前用户的共享关系是否健康
     *
     * @return Pair<正常数量, 异常数量>
     */
    suspend fun checkShareHealth(): Result<Pair<Int, Int>> {
        val currentUid = auth.currentUser?.uid
            ?: return Result.failure(Exception("用户未登录"))

        return try {
            var healthyCount = 0
            var unhealthyCount = 0

            // 查询所有 ACCEPTED 状态的共享
            val acceptedShares = firestore.collection("location_shares")
                .whereEqualTo("status", ShareStatus.ACCEPTED.name)
                .get()
                .await()

            for (shareDoc in acceptedShares.documents) {
                val fromUid = shareDoc.getString("fromUid") ?: continue
                val toUid = shareDoc.getString("toUid") ?: continue

                // 只检查与我相关的共享
                if (fromUid != currentUid && toUid != currentUid) continue

                // 检查发送者的设备是否包含接收者的 UID
                val senderDevices = firestore.collection("devices")
                    .whereEqualTo("ownerId", fromUid)
                    .limit(1)
                    .get()
                    .await()

                if (!senderDevices.isEmpty) {
                    val device = senderDevices.documents[0]
                    @Suppress("UNCHECKED_CAST")
                    val sharedWith = device.get("sharedWith") as? List<String> ?: emptyList()

                    if (sharedWith.contains(toUid)) {
                        healthyCount++
                        Log.d(TAG, "✅ 健康: 共享 ${shareDoc.id} ($fromUid → $toUid)")
                    } else {
                        unhealthyCount++
                        Log.w(TAG, "⚠️ 异常: 共享 ${shareDoc.id} ($fromUid → $toUid) 的设备 sharedWith 未包含接收者")
                    }
                }
            }

            Log.d(TAG, "健康检查完成: 正常 $healthyCount, 异常 $unhealthyCount")
            Result.success(Pair(healthyCount, unhealthyCount))
        } catch (e: Exception) {
            Log.e(TAG, "健康检查失败", e)
            Result.failure(e)
        }
    }
}
