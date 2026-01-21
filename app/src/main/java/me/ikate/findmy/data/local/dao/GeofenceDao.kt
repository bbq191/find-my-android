package me.ikate.findmy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.ikate.findmy.data.local.entity.GeofenceEntity
import me.ikate.findmy.data.local.entity.GeofenceEventEntity

/**
 * 电子围栏数据访问对象
 */
@Dao
interface GeofenceDao {

    // ==================== 围栏配置操作 ====================

    /**
     * 观察所有激活的围栏
     */
    @Query("SELECT * FROM geofences WHERE isActive = 1 ORDER BY createdAt DESC")
    fun observeActiveGeofences(): Flow<List<GeofenceEntity>>

    /**
     * 观察所有围栏
     */
    @Query("SELECT * FROM geofences ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GeofenceEntity>>

    /**
     * 获取所有围栏
     */
    @Query("SELECT * FROM geofences ORDER BY createdAt DESC")
    suspend fun getAll(): List<GeofenceEntity>

    /**
     * 获取所有激活的围栏
     */
    @Query("SELECT * FROM geofences WHERE isActive = 1 ORDER BY createdAt DESC")
    suspend fun getActiveGeofences(): List<GeofenceEntity>

    /**
     * 根据 ID 获取围栏
     */
    @Query("SELECT * FROM geofences WHERE id = :id")
    suspend fun getById(id: String): GeofenceEntity?

    /**
     * 根据联系人 ID 获取围栏
     */
    @Query("SELECT * FROM geofences WHERE contactId = :contactId LIMIT 1")
    suspend fun getByContactId(contactId: String): GeofenceEntity?

    /**
     * 观察指定联系人的围栏
     */
    @Query("SELECT * FROM geofences WHERE contactId = :contactId LIMIT 1")
    fun observeByContactId(contactId: String): Flow<GeofenceEntity?>

    /**
     * 插入或更新围栏
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(geofence: GeofenceEntity)

    /**
     * 批量插入或更新围栏
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(geofences: List<GeofenceEntity>)

    /**
     * 更新围栏
     */
    @Update
    suspend fun update(geofence: GeofenceEntity)

    /**
     * 删除围栏
     */
    @Delete
    suspend fun delete(geofence: GeofenceEntity)

    /**
     * 根据 ID 删除围栏
     */
    @Query("DELETE FROM geofences WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * 根据联系人 ID 删除围栏
     */
    @Query("DELETE FROM geofences WHERE contactId = :contactId")
    suspend fun deleteByContactId(contactId: String)

    /**
     * 删除所有围栏
     */
    @Query("DELETE FROM geofences")
    suspend fun deleteAll()

    /**
     * 更新围栏激活状态
     */
    @Query("UPDATE geofences SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateActiveStatus(id: String, isActive: Boolean, updatedAt: Long = System.currentTimeMillis())

    /**
     * 检查联系人是否已设置围栏
     */
    @Query("SELECT EXISTS(SELECT 1 FROM geofences WHERE contactId = :contactId)")
    suspend fun hasGeofenceForContact(contactId: String): Boolean

    // ==================== 围栏事件操作 ====================

    /**
     * 观察指定围栏的所有事件
     */
    @Query("SELECT * FROM geofence_events WHERE geofenceId = :geofenceId ORDER BY timestamp DESC")
    fun observeEventsByGeofenceId(geofenceId: String): Flow<List<GeofenceEventEntity>>

    /**
     * 观察指定联系人的所有事件
     */
    @Query("SELECT * FROM geofence_events WHERE contactId = :contactId ORDER BY timestamp DESC")
    fun observeEventsByContactId(contactId: String): Flow<List<GeofenceEventEntity>>

    /**
     * 观察所有事件（按时间倒序）
     */
    @Query("SELECT * FROM geofence_events ORDER BY timestamp DESC")
    fun observeAllEvents(): Flow<List<GeofenceEventEntity>>

    /**
     * 观察最近的事件（限制数量）
     */
    @Query("SELECT * FROM geofence_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int = 50): Flow<List<GeofenceEventEntity>>

    /**
     * 获取指定围栏的所有事件
     */
    @Query("SELECT * FROM geofence_events WHERE geofenceId = :geofenceId ORDER BY timestamp DESC")
    suspend fun getEventsByGeofenceId(geofenceId: String): List<GeofenceEventEntity>

    /**
     * 获取指定联系人的所有事件
     */
    @Query("SELECT * FROM geofence_events WHERE contactId = :contactId ORDER BY timestamp DESC")
    suspend fun getEventsByContactId(contactId: String): List<GeofenceEventEntity>

    /**
     * 获取未通知的事件
     */
    @Query("SELECT * FROM geofence_events WHERE isNotified = 0 ORDER BY timestamp ASC")
    suspend fun getUnnotifiedEvents(): List<GeofenceEventEntity>

    /**
     * 插入事件
     */
    @Insert
    suspend fun insertEvent(event: GeofenceEventEntity): Long

    /**
     * 标记事件为已通知
     */
    @Query("UPDATE geofence_events SET isNotified = 1 WHERE id = :eventId")
    suspend fun markEventAsNotified(eventId: Long)

    /**
     * 批量标记事件为已通知
     */
    @Query("UPDATE geofence_events SET isNotified = 1 WHERE id IN (:eventIds)")
    suspend fun markEventsAsNotified(eventIds: List<Long>)

    /**
     * 删除指定围栏的所有事件
     */
    @Query("DELETE FROM geofence_events WHERE geofenceId = :geofenceId")
    suspend fun deleteEventsByGeofenceId(geofenceId: String)

    /**
     * 删除指定联系人的所有事件
     */
    @Query("DELETE FROM geofence_events WHERE contactId = :contactId")
    suspend fun deleteEventsByContactId(contactId: String)

    /**
     * 删除所有事件
     */
    @Query("DELETE FROM geofence_events")
    suspend fun deleteAllEvents()

    /**
     * 清理过期事件（保留最近 N 天）
     */
    @Query("DELETE FROM geofence_events WHERE timestamp < :cutoffTime")
    suspend fun deleteEventsOlderThan(cutoffTime: Long)

    /**
     * 获取指定围栏最近一次事件
     */
    @Query("SELECT * FROM geofence_events WHERE geofenceId = :geofenceId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastEventForGeofence(geofenceId: String): GeofenceEventEntity?

    /**
     * 统计指定联系人的事件数量
     */
    @Query("SELECT COUNT(*) FROM geofence_events WHERE contactId = :contactId")
    suspend fun countEventsByContactId(contactId: String): Int
}
