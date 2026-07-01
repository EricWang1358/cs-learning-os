package com.cslearningos.mobile.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LearningNodeEntity::class,
        ReaderQuestionEntity::class,
        CaptureSlipEntity::class,
        QuizItemEntity::class,
        ReviewStateEntity::class,
        ReviewAttemptEntity::class,
        NodeFtsEntity::class,
        QuizFtsEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun learningDao(): LearningDao

    companion object {
        private val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reader_questions` (
                        `id` TEXT NOT NULL,
                        `node_id` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `resolved_at` INTEGER,
                        `sync_status` TEXT NOT NULL,
                        `deleted_at` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `capture_slips` (
                        `id` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `topic_hint` TEXT,
                        `source_label` TEXT,
                        `linked_node_id` TEXT,
                        `status` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `revision` INTEGER NOT NULL,
                        `sync_status` TEXT NOT NULL,
                        `deleted_at` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun create(context: Context): LearningDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LearningDatabase::class.java,
                "learning-os.db"
            )
                .addMigrations(Migration1To2, Migration2To3)
                .build()
    }
}
