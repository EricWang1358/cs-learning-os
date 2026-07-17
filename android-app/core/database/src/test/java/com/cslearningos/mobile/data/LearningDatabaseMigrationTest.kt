package com.cslearningos.mobile.data

import androidx.room.testing.MigrationTestHelper
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearningDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LearningDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration6To7PreservesNodeAndCreatesEmptyOperationalTables() {
        helper.createDatabase("phase-2a-migration", 6).apply {
            execSQL("INSERT INTO areas(id,slug,name,`order`,created_at,updated_at,deleted_at) VALUES('systems','systems','Systems',1,1000,1000,NULL)")
            execSQL("INSERT INTO learning_nodes(id,title,markdown_body,created_at,updated_at,last_read_at,revision,sync_status,deleted_at,area,area_id,track,`order`,summary,visibility,is_starter,is_checked) VALUES('node-1','Paging','# Paging',1000,1000,NULL,4,'clean',NULL,'systems','systems','memory',1,'Paging','support',0,0)")
            close()
        }

        helper.runMigrationsAndValidate(
            "phase-2a-migration",
            7,
            true,
            LearningDatabase.Migration6To7
        ).use { db ->
            db.query("SELECT revision FROM learning_nodes WHERE id='node-1'").use {
                assertTrue(it.moveToFirst())
                assertEquals(4L, it.getLong(0))
            }
            db.query("SELECT COUNT(*) FROM processed_commands").use {
                assertTrue(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
            db.query("SELECT COUNT(*) FROM replication_outbox").use {
                assertTrue(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
        }
    }

    @Test
    fun migration7To8AddsBaseRevisionWithZeroBaseline() {
        helper.createDatabase("sync-migration", 7).apply {
            execSQL("INSERT INTO learning_nodes(id,title,markdown_body,created_at,updated_at,last_read_at,revision,sync_status,deleted_at,area,area_id,track,`order`,summary,visibility,is_starter,is_checked) VALUES('node-1','Paging','# Paging',1000,1000,NULL,4,'clean',NULL,'systems','systems','memory',1,'Paging','support',0,0)")
            execSQL("INSERT INTO quiz_items(id,node_id,prompt,answer,explanation,source,source_anchor,created_at,updated_at,revision,sync_status,deleted_at,area,track,visibility,is_starter) VALUES('quiz-1',NULL,'Q','A','E','manual',NULL,1000,1000,2,'clean',NULL,'systems','memory','practice',0)")
            close()
        }

        helper.runMigrationsAndValidate(
            "sync-migration",
            8,
            true,
            LearningDatabase.Migration7To8
        ).use { db ->
            db.query("SELECT base_revision FROM learning_nodes WHERE id='node-1'").use {
                assertTrue(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
            db.query("SELECT base_revision FROM quiz_items WHERE id='quiz-1'").use {
                assertTrue(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
        }
    }

    @Test
    fun restoreBackupReplacesCanonicalDataAndClearsV7OperationalRecords() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            LearningDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val dao = database.learningDao()
            dao.upsertNode(node(id = "stale-node", title = "Stale"))
            dao.insertProcessedCommand(
                ProcessedCommandEntity(
                    commandId = "command-1",
                    commandType = "content.node.save",
                    requestFingerprint = "fingerprint",
                    resultType = "saved",
                    resultPayloadJson = "{}",
                    processedAt = 1_000
                )
            )
            dao.insertOutbox(
                ReplicationOutboxEntity(
                    changeId = "change-1",
                    commandId = "command-1",
                    aggregateType = "content_node",
                    aggregateId = "stale-node",
                    operation = "upsert",
                    baseRevision = null,
                    newRevision = 1,
                    domainSchemaVersion = 1,
                    payloadJson = "{}",
                    payloadHash = "hash",
                    state = "pending",
                    createdAt = 1_000
                )
            )

            dao.restoreBackup(
                areas = emptyList(),
                nodes = listOf(node(id = "restored-node", title = "Restored")),
                quizzes = emptyList(),
                states = emptyList(),
                attempts = emptyList(),
                questions = emptyList(),
                captureSlips = emptyList(),
                nodeFts = emptyList(),
                quizFts = emptyList()
            )

            assertEquals(listOf("restored-node"), dao.getAllNodes().map(LearningNodeEntity::id))
            assertEquals(0, rowCount(database, "processed_commands"))
            assertEquals(0, rowCount(database, "replication_outbox"))
        } finally {
            database.close()
        }
    }

    private fun node(id: String, title: String) = LearningNodeEntity(
        id = id,
        title = title,
        markdownBody = "#$title",
        createdAt = 1_000,
        updatedAt = 1_000,
        lastReadAt = null,
        revision = 1,
        syncStatus = SyncStatus.dirty,
        deletedAt = null
    )

    private fun rowCount(database: LearningDatabase, table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
