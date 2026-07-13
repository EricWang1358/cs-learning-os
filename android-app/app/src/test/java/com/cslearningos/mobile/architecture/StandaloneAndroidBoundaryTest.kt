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

        val requiredProjects = listOf(
            ":core:kernel",
            ":domain:assistant",
            ":feature:assistant:api",
            ":feature:assistant:impl",
            ":adapter:model-openai"
        )
        requiredProjects.forEach { projectPath ->
            assertTrue("Missing $projectPath", settings.contains("include \"$projectPath\""))
        }
        assertTrue(
            root.resolve(
                "build-logic/src/main/groovy/com/cslearningos/buildlogic/KotlinLibraryConventionPlugin.groovy"
            ).isFile
        )

        root.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.name in setOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts") &&
                    file.invariantSeparatorsPath.contains("/build/").not() &&
                    file.invariantSeparatorsPath.contains("/.gradle/").not()
            }
            .forEach { gradleFile ->
                val text = gradleFile.readText(Charsets.UTF_8)
                val relativePath = gradleFile.relativeTo(root).invariantSeparatorsPath
                assertFalse("Parent traversal in ${gradleFile.path}", text.contains("../"))
                assertFalse("Parent traversal in ${gradleFile.path}", text.contains("..\\"))
                assertFalse("Parent traversal in ${gradleFile.path}", text.contains("parentFile"))
                text.lineSequence()
                    .filter { line -> Regex("\\b(from|file|files|includeBuild)\\s*\\(").containsMatchIn(line) }
                    .forEach { line ->
                        assertTrue(
                            "Unapproved local Gradle input in $relativePath: ${line.trim()}",
                            isApprovedLocalInput(relativePath, line.trim())
                        )
                    }
            }
    }

    private fun isApprovedLocalInput(relativePath: String, line: String): Boolean = when (relativePath) {
        "settings.gradle" -> line == "includeBuild(\"build-logic\")"
        "app/build.gradle" ->
            line == "def starterAssetsDir = file(\"\$buildDir/generated/starter-assets\")" ||
                line == "from(\"\$rootDir/starter-content\") {" ||
                line == "implementation files("
        else -> false
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
