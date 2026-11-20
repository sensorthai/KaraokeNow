package com.karaoketv.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        QueueItemEntity::class,
        SongCacheEntity::class,
        SearchHistoryEntity::class,
        FavoriteSongEntity::class,
        FavoriteArtistEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class KaraokeDatabase : RoomDatabase() {
    abstract fun karaokeDao(): KaraokeDao

    companion object {
        @Volatile
        private var INSTANCE: KaraokeDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favorites` (
                        `videoId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artistName` TEXT NOT NULL,
                        `styleTag` TEXT NOT NULL,
                        `thumbnailUrl` TEXT NOT NULL,
                        `channelTitle` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`videoId`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `queue_items` ADD COLUMN `songCode` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `song_cache` ADD COLUMN `songCode` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `favorites` ADD COLUMN `songCode` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favorite_artists` (
                        `artistName` TEXT NOT NULL,
                        `styleHint` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`artistName`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `song_cache` ADD COLUMN `eraTag` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): KaraokeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KaraokeDatabase::class.java,
                    "karaoke_tv.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
