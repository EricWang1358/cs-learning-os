package com.cslearningos.mobile.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cslearningos.graph.data.GraphSchemaV9
import com.cslearningos.graph.data.KgDaoProvider
import com.cslearningos.graph.data.KgEdgeDao
import com.cslearningos.graph.data.KgEdgeEntity
import com.cslearningos.graph.data.KgMasteryDao
import com.cslearningos.graph.data.KgMasteryEntity
import com.cslearningos.graph.data.KgMasteryEventEntity
import com.cslearningos.graph.data.KgProposalDao
import com.cslearningos.graph.data.KgProposalEntity
import com.cslearningos.graph.data.KgQuestionDao
import com.cslearningos.graph.data.KgQuestionEntity
import com.cslearningos.graph.data.MIGRATION_8_9
import com.cslearningos.mobile.feature.assistant.data.AssistantConversationEntity

@Database(
    entities = [
        AreaEntity::class,
        LearningNodeEntity::class,
        ReaderQuestionEntity::class,
        CaptureSlipEntity::class,
        QuizItemEntity::class,
        ReviewStateEntity::class,
        ReviewAttemptEntity::class,
        NodeFtsEntity::class,
        QuizFtsEntity::class,
        AssistantConversationEntity::class,
        ProcessedCommandEntity::class,
        ReplicationOutboxEntity::class,
        KgQuestionEntity::class,
        KgEdgeEntity::class,
        KgProposalEntity::class,
        KgMasteryEntity::class,
        KgMasteryEventEntity::class,
        BiteCardEntity::class
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class LearningDatabase : RoomDatabase(), KgDaoProvider {
    abstract fun learningDao(): LearningDao

    // KgDaoProvider — KnowledgeGraph v9 DAOs (Room generates the implementations).
    abstract override fun kgQuestionDao(): KgQuestionDao
    abstract override fun kgEdgeDao(): KgEdgeDao
    abstract override fun kgProposalDao(): KgProposalDao
    abstract override fun kgMasteryDao(): KgMasteryDao

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

        private val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `learning_nodes` ADD COLUMN `area` TEXT NOT NULL DEFAULT 'questions'")
                db.execSQL("ALTER TABLE `learning_nodes` ADD COLUMN `track` TEXT NOT NULL DEFAULT 'general'")
                db.execSQL("ALTER TABLE `learning_nodes` ADD COLUMN `order` INTEGER NOT NULL DEFAULT 1000")
                db.execSQL("ALTER TABLE `learning_nodes` ADD COLUMN `summary` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `learning_nodes` ADD COLUMN `visibility` TEXT NOT NULL DEFAULT 'support'")
                db.execSQL("ALTER TABLE `learning_nodes` ADD COLUMN `is_starter` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `quiz_items` ADD COLUMN `area` TEXT NOT NULL DEFAULT 'questions'")
                db.execSQL("ALTER TABLE `quiz_items` ADD COLUMN `track` TEXT NOT NULL DEFAULT 'general'")
                db.execSQL("ALTER TABLE `quiz_items` ADD COLUMN `visibility` TEXT NOT NULL DEFAULT 'practice'")
                db.execSQL("ALTER TABLE `quiz_items` ADD COLUMN `is_starter` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `areas` (
                        `id` TEXT NOT NULL,
                        `slug` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `order` INTEGER NOT NULL DEFAULT 1000,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `deleted_at` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE `learning_nodes` ADD COLUMN `area_id` TEXT NOT NULL DEFAULT 'questions'")
                db.execSQL("ALTER TABLE `learning_nodes` ADD COLUMN `is_checked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `areas` (`id`, `slug`, `name`, `order`, `created_at`, `updated_at`, `deleted_at`)
                    SELECT `area`, `area`, `area`, 1000, MIN(`created_at`), MAX(`updated_at`), NULL
                    FROM `learning_nodes`
                    GROUP BY `area`
                    """.trimIndent()
                )
                db.execSQL("UPDATE `learning_nodes` SET `area_id` = `area` WHERE `area_id` = ''")
            }
        }

        private val Migration5To6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `assistant_conversations` (
                        `id` TEXT NOT NULL,
                        `messages_json` TEXT NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        internal val Migration6To7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `processed_commands` (
                        `command_id` TEXT NOT NULL,
                        `command_type` TEXT NOT NULL,
                        `request_fingerprint` TEXT NOT NULL,
                        `result_type` TEXT NOT NULL,
                        `result_payload_json` TEXT NOT NULL,
                        `processed_at` INTEGER NOT NULL,
                        PRIMARY KEY(`command_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `replication_outbox` (
                        `change_id` TEXT NOT NULL,
                        `command_id` TEXT NOT NULL,
                        `aggregate_type` TEXT NOT NULL,
                        `aggregate_id` TEXT NOT NULL,
                        `operation` TEXT NOT NULL,
                        `base_revision` INTEGER,
                        `new_revision` INTEGER NOT NULL,
                        `domain_schema_version` INTEGER NOT NULL,
                        `payload_json` TEXT NOT NULL,
                        `payload_hash` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`change_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_replication_outbox_command_id`
                    ON `replication_outbox` (`command_id`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_replication_outbox_state_created_at`
                    ON `replication_outbox` (`state`, `created_at`)
                    """.trimIndent()
                )
            }
        }

        internal val Migration7To8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `learning_nodes` ADD COLUMN `base_revision` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `quiz_items` ADD COLUMN `base_revision` INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val Migration9To10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bite_cards` (
                        `id` INTEGER NOT NULL,
                        `source_type` TEXT NOT NULL,
                        `source_id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `area` TEXT NOT NULL,
                        `difficulty` TEXT NOT NULL,
                        `prompt` TEXT NOT NULL,
                        `answer` TEXT NOT NULL,
                        `hint` TEXT NOT NULL,
                        `explanation_json` TEXT NOT NULL,
                        `question_type` TEXT NOT NULL,
                        `options_json` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `sync_status` TEXT NOT NULL DEFAULT 'clean',
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
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
                .addMigrations(
                    Migration1To2,
                    Migration2To3,
                    Migration3To4,
                    Migration4To5,
                    Migration5To6,
                    Migration6To7,
                    Migration7To8,
                    MIGRATION_8_9,
                    Migration9To10
                )
                // Fresh installs (no migration path): Room auto-generates wrong
                // full-unique indexes from the mirror annotations; this callback
                // replaces them with the RFC §3.3 partial-unique originals.
                .addCallback(GraphSchemaV9.freshInstallCallback)
                .build()
    }
}
