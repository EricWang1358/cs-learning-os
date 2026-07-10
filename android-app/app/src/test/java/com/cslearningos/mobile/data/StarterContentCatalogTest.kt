package com.cslearningos.mobile.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterContentCatalogTest {
    @Test
    fun demoCatalogProvidesACompleteCrossAreaLearningBaseline() {
        val contentRoot = findContentDemoRoot()
        val pack = StarterContentImporter.fromMarkdownAssets(
            nodeFiles = markdownAssets(contentRoot.resolve("nodes"), contentRoot),
            quizFiles = markdownAssets(contentRoot.resolve("quizzes"), contentRoot),
            now = 1_000L
        )

        assertTrue("Starter pack needs a meaningful study library", pack.nodes.size >= 12)
        assertTrue("Starter pack needs enough daily review prompts", pack.quizzes.size >= 10)
        assertEquals(pack.quizzes.size, pack.reviewStates.size)

        val nodeAreas = pack.nodes.groupBy { it.area }
        assertTrue("Algorithms needs several connected learning nodes", nodeAreas.getValue("algorithms").size >= 4)
        assertTrue("CS fundamentals needs several connected learning nodes", nodeAreas.getValue("cs-fundamentals").size >= 5)
        assertTrue("Projects needs an end-to-end practice node", nodeAreas.getValue("projects").isNotEmpty())
        assertTrue("Abilities needs a reusable learning workflow", nodeAreas.getValue("abilities").isNotEmpty())

        val nodeTitles = pack.nodes.map { it.title }.toSet()
        assertTrue("Graph traversal is a core interview topic", "Graph Traversal" in nodeTitles)
        assertTrue("HTTP requests are a core systems topic", "HTTP Request Lifecycle" in nodeTitles)
        assertTrue("Database indexes are a core systems topic", "Database Indexes" in nodeTitles)

        assertTrue("Every review prompt must have a useful answer", pack.quizzes.all { it.answer.length >= 80 })
        assertTrue("Every review prompt must explain the answer", pack.quizzes.all { it.explanation.length >= 80 })
    }

    private fun findContentDemoRoot(): File {
        val workingDirectory = System.getProperty("user.dir")
            ?: error("The test process did not expose its working directory")
        var directory = File(workingDirectory).absoluteFile
        repeat(MaxDirectorySearchDepth) {
            val candidate = directory.resolve("content-demo")
            if (candidate.isDirectory) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate the repository content-demo directory")
    }

    private fun markdownAssets(directory: File, contentRoot: File): List<MarkdownAsset> =
        directory.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .map { file ->
                MarkdownAsset(
                    path = file.relativeTo(contentRoot).invariantSeparatorsPath,
                    text = file.readText()
                )
            }
            .toList()

    private companion object {
        const val MaxDirectorySearchDepth = 4
    }
}
