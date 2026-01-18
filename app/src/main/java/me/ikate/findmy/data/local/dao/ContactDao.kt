package me.ikate.findmy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.ikate.findmy.data.local.entity.ContactEntity

/**
 * 联系人数据访问对象
 */
@Dao
interface ContactDao {

    /**
     * 观察所有联系人
     */
    @Query("SELECT * FROM contacts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ContactEntity>>

    /**
     * 获取所有联系人
     */
    @Query("SELECT * FROM contacts ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ContactEntity>

    /**
     * 根据 ID 获取联系人
     */
    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: String): ContactEntity?

    /**
     * 根据目标用户 ID 获取联系人
     */
    @Query("SELECT * FROM contacts WHERE targetUserId = :targetUserId LIMIT 1")
    suspend fun getByTargetUserId(targetUserId: String): ContactEntity?

    /**
     * 根据共享状态获取联系人
     */
    @Query("SELECT * FROM contacts WHERE shareStatus = :status ORDER BY updatedAt DESC")
    suspend fun getByStatus(status: String): List<ContactEntity>

    /**
     * 插入或更新联系人
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    /**
     * 批量插入或更新联系人
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(contacts: List<ContactEntity>)

    /**
     * 更新联系人
     */
    @Update
    suspend fun update(contact: ContactEntity)

    /**
     * 删除联系人
     */
    @Delete
    suspend fun delete(contact: ContactEntity)

    /**
     * 根据 ID 删除联系人
     */
    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * 删除所有联系人
     */
    @Query("DELETE FROM contacts")
    suspend fun deleteAll()

    /**
     * 更新联系人位置和设备信息
     */
    @Query("""
        UPDATE contacts
        SET latitude = :latitude,
            longitude = :longitude,
            lastUpdateTime = :lastUpdateTime,
            isLocationAvailable = 1,
            deviceName = COALESCE(:deviceName, deviceName),
            battery = COALESCE(:battery, battery),
            updatedAt = :updatedAt
        WHERE targetUserId = :targetUserId
    """)
    suspend fun updateLocation(
        targetUserId: String,
        latitude: Double,
        longitude: Double,
        lastUpdateTime: Long,
        deviceName: String? = null,
        battery: Int? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * 更新共享状态
     */
    @Query("UPDATE contacts SET shareStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    /**
     * 清理过期的联系人
     */
    @Query("UPDATE contacts SET shareStatus = 'EXPIRED' WHERE expireTime IS NOT NULL AND expireTime < :currentTime")
    suspend fun markExpired(currentTime: Long = System.currentTimeMillis())

    /**
     * 清除联系人的位置信息
     * 用于联系人失效、暂停、拒绝等场景
     */
    @Query("""
        UPDATE contacts
        SET latitude = NULL,
            longitude = NULL,
            isLocationAvailable = 0,
            lastUpdateTime = NULL,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun clearLocation(id: String, updatedAt: Long = System.currentTimeMillis())

    /**
     * 批量清除联系人的位置信息（根据状态）
     */
    @Query("""
        UPDATE contacts
        SET latitude = NULL,
            longitude = NULL,
            isLocationAvailable = 0,
            lastUpdateTime = NULL,
            updatedAt = :updatedAt
        WHERE shareStatus = :status
    """)
    suspend fun clearLocationByStatus(status: String, updatedAt: Long = System.currentTimeMillis())

    /**
     * 根据目标用户 ID 更新暂停状态
     * 用于收到对方暂停/恢复共享通知时更新本地状态
     */
    @Query("UPDATE contacts SET isPaused = :isPaused, updatedAt = :updatedAt WHERE targetUserId = :targetUserId")
    suspend fun updatePauseStatusByTargetUserId(
        targetUserId: String,
        isPaused: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * 根据目标用户 ID 清除联系人的位置信息
     * 用于对方暂停共享时清除其位置
     */
    @Query("""
        UPDATE contacts
        SET latitude = NULL,
            longitude = NULL,
            isLocationAvailable = 0,
            lastUpdateTime = NULL,
            updatedAt = :updatedAt
        WHERE targetUserId = :targetUserId
    """)
    suspend fun clearLocationByTargetUserId(targetUserId: String, updatedAt: Long = System.currentTimeMillis())

    /**
     * 检查指定用户的共享是否已暂停
     * 用于判断是否应该响应对方的请求
     */
    @Query("SELECT isPaused FROM contacts WHERE targetUserId = :targetUserId LIMIT 1")
    suspend fun isPausedByTargetUserId(targetUserId: String): Boolean?
}
