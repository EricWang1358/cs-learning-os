package com.cslearningos.mobile.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandaloneAndroidBoundaryTest {
    @Test
    fun buildInputsStayInsideAndroidRoot() {
        val root = androidRoot()
        val appBuild = root.resolve("app/build.gradle").readText(Charsets.UTF_8)
        val settings = root.resolve("settings.gradle").readText(Charsets.UTF_8)

        assertFalse(appBuild.contains("../content-demo"))
        assertTrue(appBuild.contains("\$rootDir/starter-content"))
        assertFalse(settings.contains("../app"))
        assertFalse(settings.contains("../backend"))
        assertTrue(root.resolve("starter-content/index.md").isFile)
        assertTrue(root.resolve("docs/architecture.md").isFile)
        assertTrue(root.resolve("docs/data-recovery.md").isFile)
    }

    private fun androidRoot(): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(6) {
            if (current.resolve("settings.gradle").isFile && current.resolve("app").isDirectory) {
                return current
            }
            current = current.parentFile ?: error("Reached filesystem root while locating Android root")
        }
        error("Could not locate standalone Android root from ${System.getProperty("user.dir")}")
    }
}
