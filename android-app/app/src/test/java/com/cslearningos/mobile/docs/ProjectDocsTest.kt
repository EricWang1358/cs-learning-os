package com.cslearningos.mobile.docs

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectDocsTest {
    @Test
    fun androidDocsCoverArchitectureAndRecoveryWithoutMojibake() {
        val root = androidRoot()
        val readme = root.resolve("README.md").readText(Charsets.UTF_8)
        val architecture = root.resolve("docs/architecture.md").readText(Charsets.UTF_8)
        val dataRecovery = root.resolve("docs/data-recovery.md").readText(Charsets.UTF_8)

        listOf(readme, architecture, dataRecovery).forEach { text ->
            assertFalse(text.contains('\uFFFD'))
        }

        assertTrue(readme.contains("Network permission policy"))
        assertTrue(readme.contains("Advanced commands"))
        assertTrue(architecture.contains("independently buildable"))
        assertTrue(dataRecovery.contains("Backup schema v1"))
    }

    private fun androidRoot(): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(6) {
            if (
                current.resolve("settings.gradle").isFile &&
                current.resolve("app").isDirectory &&
                current.resolve("README.md").isFile
            ) {
                return current
            }
            current = current.parentFile ?: error("Reached filesystem root while locating Android root")
        }
        error("Could not locate standalone Android root from ${System.getProperty("user.dir")}")
    }
}
