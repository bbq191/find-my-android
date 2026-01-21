package me.ikate.findmy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.ikate.findmy.data.local.entity.PendingMessageEntity

/**
 * 待发送消息数据访问对象
 * 用于离线消息队列管理
 */
@Dao
interface PendingMessageDao {

    /**
     * 插入待发送消息
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: PendingMessageEntity): Long

    /**
     * 更新消息状态
     */
    @Update
    suspend fun update(message: PendingMessageEntity)

    /**
     * 删除消息
     */
    @Delete
    suspend fun delete(message: PendingMessageEntity)

    /**
     * 根据 ID 删除消息
     */
    @Query("DELETE FROM pending_messages WHERE id = :messageId")
    suspend fun deleteById(messageId: Long)

    /**
     * 获取所有待发送消息（按创建时间排序）
     */
    @Query("SELECT * FROM pending_messages WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingMessages(): List<PendingMessageEntity>

    /**
     * 获取待发送消息数量
     */
    @Query("SELECT COUNT(*) FROM pending_messages WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int

    /**
     * 观察待发送消息数量（实时）
     */
    @Query("SELECT COUNT(*) FROM pending_messages WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    /**
     * 将消息标记为发送中
     */
    @Query("UPDATE pending_messages SET status = 'SENDING' WHERE id = :messageId")
    suspend fun markSending(messageId: Long)

    /**
     * 将消息标记为已发送
     */
    @Query("UPDATE pending_messages SET status = 'SENT' WHERE id = :messageId")
    suspend fun markSent(messageId: Long)

    /**
     * 将消息标记为失败并增加重试次数
     */
    @Query("""
        UPDATE pending_messages
        SET status = CASE
            WHEN retryCount + 1 >= maxRetries THEN 'FAILED'
            ELSE 'PENDING'
        END,
        retryCount = retryCount + 1
        WHERE id = :messageId
    """)
    suspend fun markFailedAndRetry(messageId: Long)

    /**
     * 删除已发送的消息
     */
    @Query("DELETE FROM pending_messages WHERE status = 'SENT'")
    suspend fun deleteSentMessages()

    /**
     * 删除过期消息（超过指定时间）
     */
    @Query("DELETE FROM pending_messages WHERE createdAt < :expiryTime")
    suspend fun deleteExpiredMessages(expiryTime: Long)

    /**
     * 清空所有消息
     */
    @Query("DELETE FROM pending_messages")
    suspend fun deleteAll()

    /**
     * 重置所有发送中的消息为待发送状态（用于恢复）
     */
    @Query("UPDATE pending_messages SET status = 'PENDING' WHERE status = 'SENDING'")
    suspend fun resetSendingToPending()

    /**
     * 删除重试次数超过指定次数的失败消息
     */
    @Query("DELETE FROM pending_messages WHERE status = 'FAILED' OR retryCount >= :maxRetries")
    suspend fun deleteFailedMessages(maxRetries: Int)

    /**
     * 获取失败消息数量
     */
    @Query("SELECT COUNT(*) FROM pending_messages WHERE status = 'FAILED'")
    suspend fun getFailedCount(): Int

    /**
     * 获取所有消息（用于调试）
     */
    @Query("SELECT * FROM pending_messages ORDER BY createdAt DESC")
    suspend fun getAllMessages(): List<PendingMessageEntity>
}
