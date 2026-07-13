package com.cslearningos.mobile.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
}
