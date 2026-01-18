package me.ikate.findmy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareStatus
import me.ikate.findmy.data.model.pointOf

/**
 * 联系人实体（Room 数据库表）
 * 用于本地持久化联系人和共享关系
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val id: String,
    val email: String = "",
    val name: String,
    val avatarUrl: String? = null,

    // 共享关系
    val shareStatus: String = ShareStatus.PENDING.name,
    val shareDirection: String = ShareDirection.MUTUAL.name,
    val expireTime: Long? = null,
    val targetUserId: String? = null,

    // 位置信息
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastUpdateTime: Long? = null,
    val isLocationAvailable: Boolean = false,
    val isPaused: Boolean = false,
    val deviceName: String? = null,
    val battery: Int? = null,

    // 元数据
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {

    /**
     * 转换为领域模型
     */
    fun toDomain(): Contact = Contact(
        id = id,
        email = email,
        name = name,
        avatarUrl = avatarUrl,
        shareStatus = try {
            ShareStatus.valueOf(shareStatus)
        } catch (e: Exception) {
            ShareStatus.PENDING
        },
        shareDirection = try {
            ShareDirection.valueOf(shareDirection)
        } catch (e: Exception) {
            ShareDirection.MUTUAL
        },
        expireTime = expireTime,
        targetUserId = targetUserId,
        location = if (latitude != null && longitude != null) {
            pointOf(latitude, longitude)
        } else null,
        lastUpdateTime = lastUpdateTime,
        isLocationAvailable = isLocationAvailable,
        isPaused = isPaused,
        deviceName = deviceName,
        battery = battery
    )

    companion object {
        /**
         * 从领域模型创建实体
         */
        fun fromDomain(contact: Contact): ContactEntity = ContactEntity(
            id = contact.id,
            email = contact.email,
            name = contact.name,
            avatarUrl = contact.avatarUrl,
            shareStatus = contact.shareStatus.name,
            shareDirection = contact.shareDirection.name,
            expireTime = contact.expireTime,
            targetUserId = contact.targetUserId,
            latitude = contact.location?.latitude(),
            longitude = contact.location?.longitude(),
            lastUpdateTime = contact.lastUpdateTime,
            isLocationAvailable = contact.isLocationAvailable,
            isPaused = contact.isPaused,
            deviceName = contact.deviceName,
            battery = contact.battery,
            updatedAt = System.currentTimeMillis()
        )
    }
}
