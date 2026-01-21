package me.ikate.findmy.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import me.ikate.findmy.data.local.FindMyDatabase
import me.ikate.findmy.data.local.entity.GeofenceEntity
import me.ikate.findmy.data.local.entity.GeofenceEventEntity
import me.ikate.findmy.data.model.Geofence
import me.ikate.findmy.data.model.GeofenceEvent
import me.ikate.findmy.data.model.GeofenceEventType
import me.ikate.findmy.data.model.GeofenceTriggerType
import me.ikate.findmy.data.model.latLngOf
import java.util.UUID

/**
 * 电子围栏数据仓库
 * 负责围栏配置的本地存储和云端同步
 *
 * 架构：端侧计算，云端同步
 * - 本地使用 Room 存储围栏配置
 * - 云端使用 Firestore 同步围栏配置
 * - FCM 用于通知对方同步围栏
 */
class GeofenceRepository(private val context: Context) {

    companion object {
        private const val TAG = "GeofenceRepository"

        // Firestore 集合路径
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_GEOFENCES = "geofences"

        // 事件保留天数
        private const val EVENT_RETENTION_DAYS = 30L
    }

    private val geofenceDao = FindMyDatabase.getInstance(context).geofenceDao()
    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Firestore 监听器
    private var geofenceListener: ListenerRegistration? = null

    // 获取当前用户 ID
    private fun getCurrentUid(): String = AuthRepository.getUserId(context)

    // ==================== 围栏配置操作 ====================

    /**
     * 创建电子围栏
     *
     * @param contactId 被监控的联系人ID
     * @param contactName 联系人名称
     * @param locationName 位置名称（如"家"）
     * @param center 围栏中心点 (GCJ-02)
     * @param radiusMeters 半径（米），建议最小200米
     * @param triggerType 触发类型
     * @param isOneTime 是否一次性触发
     * @return 创建的围栏
     */
    suspend fun createGeofence(
        contactId: String,
        contactName: String,
        locationName: String,
        center: LatLng,
        radiusMeters: Float,
        triggerType: GeofenceTriggerType = GeofenceTriggerType.ENTER,
        isOneTime: Boolean = false
    ): Result<Geofence> {
        return try {
            // 生成唯一ID
            val geofenceId = "geofence_${contactId}_${System.currentTimeMillis()}"

            val geofence = Geofence(
                id = geofenceId,
                contactId = contactId,
                contactName = contactName,
                locationName = locationName,
                center = center,
                radiusMeters = radiusMeters.coerceAtLeast(200f), // 最小200米
                triggerType = triggerType,
                isActive = true,
                isOneTime = isOneTime
            )

            // 先删除该联系人的旧围栏（每个联系人只能有一个围栏）
            geofenceDao.deleteByContactId(contactId)

            // 保存到本地
            geofenceDao.upsert(GeofenceEntity.fromDomain(geofence))

            // 同步到云端
            syncGeofenceToCloud(geofence)

            Log.d(TAG, "围栏创建成功: $geofenceId, 联系人: $contactName, 位置: $locationName")
            Result.success(geofence)
        } catch (e: Exception) {
            Log.e(TAG, "创建围栏失败", e)
            Result.failure(e)
        }
    }

    /**
     * 更新围栏配置
     */
    suspend fun updateGeofence(geofence: Geofence): Result<Unit> {
        return try {
            val updatedGeofence = geofence.copy(updatedAt = System.currentTimeMillis())
            geofenceDao.upsert(GeofenceEntity.fromDomain(updatedGeofence))
            syncGeofenceToCloud(updatedGeofence)
            Log.d(TAG, "围栏更新成功: ${geofence.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "更新围栏失败", e)
            Result.failure(e)
        }
    }

    /**
     * 删除围栏
     */
    suspend fun deleteGeofence(geofenceId: String): Result<Unit> {
        return try {
            geofenceDao.deleteById(geofenceId)
            deleteGeofenceFromCloud(geofenceId)
            Log.d(TAG, "围栏删除成功: $geofenceId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "删除围栏失败", e)
            Result.failure(e)
        }
    }

    /**
     * 删除指定联系人的围栏
     */
    suspend fun deleteGeofenceByContactId(contactId: String): Result<Unit> {
        return try {
            val geofence = geofenceDao.getByContactId(contactId)
            if (geofence != null) {
                geofenceDao.deleteByContactId(contactId)
                deleteGeofenceFromCloud(geofence.id)
            }
            Log.d(TAG, "联系人围栏删除成功: $contactId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "删除联系人围栏失败", e)
            Result.failure(e)
        }
    }

    /**
     * 切换围栏激活状态
     */
    suspend fun toggleGeofenceActive(geofenceId: String, isActive: Boolean): Result<Unit> {
        return try {
            geofenceDao.updateActiveStatus(geofenceId, isActive)
            val geofence = geofenceDao.getById(geofenceId)?.toDomain()
            if (geofence != null) {
                syncGeofenceToCloud(geofence.copy(isActive = isActive))
            }
            Log.d(TAG, "围栏状态切换: $geofenceId -> $isActive")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "切换围栏状态失败", e)
            Result.failure(e)
        }
    }

    /**
     * 获取指定联系人的围栏
     */
    suspend fun getGeofenceByContactId(contactId: String): Geofence? {
        return geofenceDao.getByContactId(contactId)?.toDomain()
    }

    /**
     * 获取所有激活的围栏
     */
    suspend fun getActiveGeofences(): List<Geofence> {
        return geofenceDao.getActiveGeofences().map { it.toDomain() }
    }

    /**
     * 观察所有围栏
     */
    fun observeAllGeofences(): Flow<List<Geofence>> {
        return geofenceDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * 观察激活的围栏
     */
    fun observeActiveGeofences(): Flow<List<Geofence>> {
        return geofenceDao.observeActiveGeofences().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * 观察指定联系人的围栏
     */
    fun observeGeofenceByContactId(contactId: String): Flow<Geofence?> {
        return geofenceDao.observeByContactId(contactId).map { it?.toDomain() }
    }

    /**
     * 检查联系人是否已设置围栏
     */
    suspend fun hasGeofenceForContact(contactId: String): Boolean {
        return geofenceDao.hasGeofenceForContact(contactId)
    }

    // ==================== 围栏事件操作 ====================

    /**
     * 记录围栏触发事件
     */
    suspend fun recordGeofenceEvent(
        geofence: Geofence,
        eventType: GeofenceEventType,
        triggerLocation: LatLng
    ): GeofenceEvent {
        val event = GeofenceEvent(
            geofenceId = geofence.id,
            contactId = geofence.contactId,
            contactName = geofence.contactName,
            locationName = geofence.locationName,
            eventType = eventType,
            latitude = triggerLocation.latitude,
            longitude = triggerLocation.longitude
        )

        val eventId = geofenceDao.insertEvent(GeofenceEventEntity.fromDomain(event))
        Log.d(TAG, "围栏事件记录: ${event.eventType} at ${event.locationName}")

        // 如果是一次性围栏，触发后删除
        if (geofence.isOneTime) {
            deleteGeofence(geofence.id)
            Log.d(TAG, "一次性围栏已触发并删除: ${geofence.id}")
        }

        return event.copy(id = eventId)
    }

    /**
     * 标记事件为已通知
     */
    suspend fun markEventAsNotified(eventId: Long) {
        geofenceDao.markEventAsNotified(eventId)
    }

    /**
     * 获取未通知的事件
     */
    suspend fun getUnnotifiedEvents(): List<GeofenceEvent> {
        return geofenceDao.getUnnotifiedEvents().map { it.toDomain() }
    }

    /**
     * 观察所有事件
     */
    fun observeAllEvents(): Flow<List<GeofenceEvent>> {
        return geofenceDao.observeAllEvents().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * 观察最近事件
     */
    fun observeRecentEvents(limit: Int = 50): Flow<List<GeofenceEvent>> {
        return geofenceDao.observeRecentEvents(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * 观察指定联系人的事件
     */
    fun observeEventsByContactId(contactId: String): Flow<List<GeofenceEvent>> {
        return geofenceDao.observeEventsByContactId(contactId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * 清理过期事件
     */
    suspend fun cleanupOldEvents() {
        val cutoffTime = System.currentTimeMillis() - (EVENT_RETENTION_DAYS * 24 * 60 * 60 * 1000)
        geofenceDao.deleteEventsOlderThan(cutoffTime)
        Log.d(TAG, "已清理 $EVENT_RETENTION_DAYS 天前的围栏事件")
    }

    // ==================== 云端同步 ====================

    /**
     * 同步围栏到云端 (Firestore)
     */
    private suspend fun syncGeofenceToCloud(geofence: Geofence) {
        try {
            val currentUid = getCurrentUid()
            val docRef = firestore
                .collection(COLLECTION_USERS)
                .document(currentUid)
                .collection(COLLECTION_GEOFENCES)
                .document(geofence.id)

            val data = hashMapOf(
                "id" to geofence.id,
                "contactId" to geofence.contactId,
                "contactName" to geofence.contactName,
                "locationName" to geofence.locationName,
                "latitude" to geofence.center.latitude,
                "longitude" to geofence.center.longitude,
                "radiusMeters" to geofence.radiusMeters,
                "triggerType" to geofence.triggerType.name,
                "isActive" to geofence.isActive,
                "isOneTime" to geofence.isOneTime,
                "createdAt" to geofence.createdAt,
                "updatedAt" to geofence.updatedAt
            )

            docRef.set(data).await()
            Log.d(TAG, "围栏已同步到云端: ${geofence.id}")
        } catch (e: Exception) {
            Log.e(TAG, "同步围栏到云端失败", e)
        }
    }

    /**
     * 从云端删除围栏
     */
    private suspend fun deleteGeofenceFromCloud(geofenceId: String) {
        try {
            val currentUid = getCurrentUid()
            firestore
                .collection(COLLECTION_USERS)
                .document(currentUid)
                .collection(COLLECTION_GEOFENCES)
                .document(geofenceId)
                .delete()
                .await()
            Log.d(TAG, "围栏已从云端删除: $geofenceId")
        } catch (e: Exception) {
            Log.e(TAG, "从云端删除围栏失败", e)
        }
    }

    /**
     * 从云端拉取围栏配置并同步到本地
     */
    suspend fun syncFromCloud(): Result<Int> {
        return try {
            val currentUid = getCurrentUid()
            val snapshot = firestore
                .collection(COLLECTION_USERS)
                .document(currentUid)
                .collection(COLLECTION_GEOFENCES)
                .get()
                .await()

            val geofences = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    GeofenceEntity(
                        id = data["id"] as? String ?: doc.id,
                        contactId = data["contactId"] as? String ?: "",
                        contactName = data["contactName"] as? String ?: "",
                        locationName = data["locationName"] as? String ?: "",
                        latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                        longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                        radiusMeters = (data["radiusMeters"] as? Number)?.toFloat() ?: 200f,
                        triggerType = data["triggerType"] as? String ?: GeofenceTriggerType.ENTER.name,
                        isActive = data["isActive"] as? Boolean ?: true,
                        isOneTime = data["isOneTime"] as? Boolean ?: false,
                        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "解析围栏文档失败: ${doc.id}", e)
                    null
                }
            }

            geofenceDao.upsertAll(geofences)
            Log.d(TAG, "从云端同步了 ${geofences.size} 个围栏")
            Result.success(geofences.size)
        } catch (e: Exception) {
            Log.e(TAG, "从云端同步围栏失败", e)
            Result.failure(e)
        }
    }

    /**
     * 开始监听云端围栏变化
     */
    fun startListeningCloudChanges() {
        val currentUid = getCurrentUid()
        geofenceListener?.remove()

        geofenceListener = firestore
            .collection(COLLECTION_USERS)
            .document(currentUid)
            .collection(COLLECTION_GEOFENCES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "监听云端围栏变化失败", error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    scope.launch {
                        try {
                            val data = change.document.data
                            val geofence = GeofenceEntity(
                                id = data["id"] as? String ?: change.document.id,
                                contactId = data["contactId"] as? String ?: "",
                                contactName = data["contactName"] as? String ?: "",
                                locationName = data["locationName"] as? String ?: "",
                                latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                                longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                                radiusMeters = (data["radiusMeters"] as? Number)?.toFloat() ?: 200f,
                                triggerType = data["triggerType"] as? String ?: GeofenceTriggerType.ENTER.name,
                                isActive = data["isActive"] as? Boolean ?: true,
                                isOneTime = data["isOneTime"] as? Boolean ?: false,
                                createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            )

                            when (change.type) {
                                com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                    geofenceDao.upsert(geofence)
                                    Log.d(TAG, "云端围栏变化已同步: ${geofence.id}")
                                }
                                com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                    geofenceDao.deleteById(geofence.id)
                                    Log.d(TAG, "云端围栏删除已同步: ${geofence.id}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "处理云端围栏变化失败", e)
                        }
                    }
                }
            }
    }

    /**
     * 停止监听云端变化
     */
    fun stopListeningCloudChanges() {
        geofenceListener?.remove()
        geofenceListener = null
    }

    /**
     * 清理资源
     */
    fun destroy() {
        stopListeningCloudChanges()
    }
}
