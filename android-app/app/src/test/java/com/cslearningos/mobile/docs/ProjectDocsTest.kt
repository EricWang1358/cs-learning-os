package com.cslearningos.mobile.docs

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectDocsTest {
    @Test
    fun startHereDocsCoverRunPathsAndDataSafetyWithoutMojibake() {
        val root = repoRoot()
        val readme = root.resolve("README.md").readText(Charsets.UTF_8)
        val firstRun = root.resolve("docs/first-run.md").readText(Charsets.UTF_8)
        val dataRecovery = root.resolve("docs/data-recovery.md").readText(Charsets.UTF_8)
        val androidReadme = root.resolve("android-app/README.md").readText(Charsets.UTF_8)
        val migration = root.resolve("docs/android-migration.md").readText(Charsets.UTF_8)
        val appReadme = root.resolve("app/README.md").readText(Charsets.UTF_8)
        val design = root.resolve("app/DESIGN.md").readText(Charsets.UTF_8)

        listOf(readme, firstRun, dataRecovery, androidReadme, migration, appReadme).forEach { text ->
            assertFalse(text.contains('\uFFFD'))
            assertFalse(text.contains("鈥"))
            assertFalse(text.contains("涓"))
        }

        assertTrue(readme.contains("Desktop beta"))
        assertTrue(readme.contains("Android beta"))
        assertTrue(readme.contains("Developer setup"))
        assertTrue(readme.contains("Data safety"))
        assertTrue(firstRun.contains("five minutes"))
        assertTrue(dataRecovery.contains("Restore is a full replacement"))
        assertTrue(dataRecovery.contains("Delete forever is only recoverable from backup"))
        assertTrue(androidReadme.contains("Network permission policy"))
        assertTrue(androidReadme.contains("Advanced commands"))
        assertTrue(migration.contains("No blanket network permission"))
        assertTrue(appReadme.contains("Frontend guide"))
        assertFalse(appReadme.contains("This template provides"))
        assertFalse(design.contains("README.md"))
    }

    private fun repoRoot(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        repeat(5) {
            if (current.resolve("android-app").isDirectory && current.resolve("app").isDirectory) return current
            current = current.parentFile
        }
        error("Could not locate repo root from ${System.getProperty("user.dir")}")
    }
}
