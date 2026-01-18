package me.ikate.findmy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.ikate.findmy.data.local.entity.DeviceEntity

/**
 * 设备数据访问对象
 */
@Dao
interface DeviceDao {

    /**
     * 插入或更新设备
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: DeviceEntity)

    /**
     * 批量插入或更新设备
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(devices: List<DeviceEntity>)

    /**
     * 更新设备
     */
    @Update
    suspend fun update(device: DeviceEntity)

    /**
     * 删除设备
     */
    @Delete
    suspend fun delete(device: DeviceEntity)

    /**
     * 根据 ID 删除设备
     */
    @Query("DELETE FROM devices WHERE id = :deviceId")
    suspend fun deleteById(deviceId: String)

    /**
     * 根据 ID 获取设备
     */
    @Query("SELECT * FROM devices WHERE id = :deviceId")
    suspend fun getById(deviceId: String): DeviceEntity?

    /**
     * 获取所有设备（Flow 实时观察）
     */
    @Query("SELECT * FROM devices ORDER BY lastUpdateTime DESC")
    fun observeAll(): Flow<List<DeviceEntity>>

    /**
     * 获取当前用户拥有的设备
     */
    @Query("SELECT * FROM devices WHERE ownerId = :ownerId ORDER BY lastUpdateTime DESC")
    fun observeByOwner(ownerId: String): Flow<List<DeviceEntity>>

    /**
     * 获取与当前用户共享的设备
     * @param userId 当前用户 ID，检查 sharedWith 字段是否包含该 ID
     */
    @Query("SELECT * FROM devices WHERE sharedWith LIKE '%' || :userId || '%' ORDER BY lastUpdateTime DESC")
    fun observeSharedWith(userId: String): Flow<List<DeviceEntity>>

    /**
     * 获取当前用户可见的所有设备（自己的 + 共享给自己的）
     */
    @Query("""
        SELECT * FROM devices
        WHERE ownerId = :userId OR sharedWith LIKE '%' || :userId || '%'
        ORDER BY lastUpdateTime DESC
    """)
    fun observeVisibleDevices(userId: String): Flow<List<DeviceEntity>>

    /**
     * 获取所有设备（一次性查询）
     */
    @Query("SELECT * FROM devices ORDER BY lastUpdateTime DESC")
    suspend fun getAll(): List<DeviceEntity>

    /**
     * 清空所有设备数据
     */
    @Query("DELETE FROM devices")
    suspend fun deleteAll()

    /**
     * 将所有设备标记为离线
     */
    @Query("UPDATE devices SET isOnline = 0")
    suspend fun markAllOffline()

    /**
     * 将指定设备标记为离线
     */
    @Query("UPDATE devices SET isOnline = 0 WHERE id = :deviceId")
    suspend fun markOffline(deviceId: String)

    /**
     * 更新设备位置
     */
    @Query("""
        UPDATE devices
        SET latitude = :latitude, longitude = :longitude,
            bearing = :bearing, lastUpdateTime = :updateTime, isOnline = 1
        WHERE id = :deviceId
    """)
    suspend fun updateLocation(
        deviceId: String,
        latitude: Double,
        longitude: Double,
        bearing: Float,
        updateTime: Long = System.currentTimeMillis()
    )
}
