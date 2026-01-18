package me.ikate.findmy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import me.ikate.findmy.data.local.dao.ContactDao
import me.ikate.findmy.data.local.dao.DeviceDao
import me.ikate.findmy.data.local.dao.PendingMessageDao
import me.ikate.findmy.data.local.entity.ContactEntity
import me.ikate.findmy.data.local.entity.DeviceEntity
import me.ikate.findmy.data.local.entity.PendingMessageEntity

/**
 * FindMy 本地数据库
 * 使用 Room 进行数据持久化
 */
@Database(
    entities = [
        DeviceEntity::class,
        PendingMessageEntity::class,
        ContactEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class FindMyDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao
    abstract fun pendingMessageDao(): PendingMessageDao
    abstract fun contactDao(): ContactDao

    companion object {
        private const val DATABASE_NAME = "findmy_db"

        @Volatile
        private var INSTANCE: FindMyDatabase? = null

        /**
         * 获取数据库单例
         */
        fun getInstance(context: Context): FindMyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): FindMyDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                FindMyDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration(true)
                .build()
        }

        /**
         * 关闭数据库（用于测试或清理）
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
