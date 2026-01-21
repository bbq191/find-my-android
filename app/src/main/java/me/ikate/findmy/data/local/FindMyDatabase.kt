package me.ikate.findmy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.ikate.findmy.data.local.dao.ContactDao
import me.ikate.findmy.data.local.dao.DeviceDao
import me.ikate.findmy.data.local.dao.GeofenceDao
import me.ikate.findmy.data.local.dao.PendingMessageDao
import me.ikate.findmy.data.local.entity.ContactEntity
import me.ikate.findmy.data.local.entity.DeviceEntity
import me.ikate.findmy.data.local.entity.GeofenceEntity
import me.ikate.findmy.data.local.entity.GeofenceEventEntity
import me.ikate.findmy.data.local.entity.PendingMessageEntity

/**
 * FindMy 本地数据库
 * 使用 Room 进行数据持久化
 */
@Database(
    entities = [
        DeviceEntity::class,
        PendingMessageEntity::class,
        ContactEntity::class,
        GeofenceEntity::class,
        GeofenceEventEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class FindMyDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao
    abstract fun pendingMessageDao(): PendingMessageDao
    abstract fun contactDao(): ContactDao
    abstract fun geofenceDao(): GeofenceDao

    companion object {
        private const val DATABASE_NAME = "findmy_db"

        @Volatile
        private var INSTANCE: FindMyDatabase? = null

        /**
         * 版本 2 -> 3 迁移
         * 添加 geofences 和 geofence_events 表
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建 geofences 表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `geofences` (
                        `id` TEXT NOT NULL,
                        `contactId` TEXT NOT NULL,
                        `contactName` TEXT NOT NULL,
                        `locationName` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `radiusMeters` REAL NOT NULL,
                        `triggerType` TEXT NOT NULL DEFAULT 'ENTER',
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `isOneTime` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                // 创建 geofences 表的索引
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_geofences_contactId`
                    ON `geofences` (`contactId`)
                """.trimIndent())

                // 创建 geofence_events 表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `geofence_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `geofenceId` TEXT NOT NULL,
                        `contactId` TEXT NOT NULL,
                        `contactName` TEXT NOT NULL,
                        `locationName` TEXT NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `isNotified` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`geofenceId`) REFERENCES `geofences`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 创建 geofence_events 表的索引
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_geofence_events_geofenceId`
                    ON `geofence_events` (`geofenceId`)
                """.trimIndent())
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_geofence_events_contactId`
                    ON `geofence_events` (`contactId`)
                """.trimIndent())
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS `index_geofence_events_timestamp`
                    ON `geofence_events` (`timestamp`)
                """.trimIndent())
            }
        }

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
                .addMigrations(MIGRATION_2_3)
                // 未来版本升级时添加新迁移: .addMigrations(MIGRATION_2_3, MIGRATION_3_4, ...)
                // 仅当没有对应迁移脚本时才破坏性重建（开发阶段兜底）
                .fallbackToDestructiveMigration(false)
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
