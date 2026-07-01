package com.cslearningos.mobile.data

import android.content.res.AssetManager

data class MarkdownAsset(
    val path: String,
    val text: String
)

data class StarterContentPackage(
    val nodes: List<LearningNodeEntity>,
    val quizzes: List<QuizItemEntity>,
    val reviewStates: List<ReviewStateEntity>
)

object StarterContentImporter {
    fun fromAssets(assetManager: AssetManager, now: Long = System.currentTimeMillis()): StarterContentPackage {
        val nodeFiles = assetManager.readMarkdownAssets("nodes")
        val quizFiles = assetManager.readMarkdownAssets("quizzes")
        return fromMarkdownAssets(nodeFiles = nodeFiles, quizFiles = quizFiles, now = now)
    }

    fun fromMarkdownAssets(
        nodeFiles: List<MarkdownAsset>,
        quizFiles: List<MarkdownAsset>,
        now: Long
    ): StarterContentPackage {
        val nodes = nodeFiles.map { asset ->
            val parsed = MarkdownFrontMatter.parse(asset.text)
            LearningNodeEntity(
                id = "starter:node:${asset.path.removePrefix("nodes/").removeSuffix(".md")}",
                title = parsed.title ?: firstMarkdownHeading(parsed.body) ?: titleFromPath(asset.path),
                markdownBody = parsed.body,
                createdAt = now,
                updatedAt = now,
                lastReadAt = null,
                revision = 1L,
                syncStatus = SyncStatus.clean,
                deletedAt = null,
                area = parsed.metadata["area"] ?: areaFromPath(asset.path),
                track = parsed.metadata["track"] ?: "general",
                order = parsed.metadata["order"]?.toIntOrNull() ?: 1000,
                summary = parsed.metadata["summary"].orEmpty(),
                visibility = parsed.metadata["visibility"] ?: "core",
                isStarter = true
            )
        }

        val quizzes = quizFiles.mapNotNull { asset ->
            val parsed = MarkdownFrontMatter.parse(asset.text)
            val prompt = sectionAfterHeading(parsed.body, "Prompt") ?: return@mapNotNull null
            val answer = sectionAfterHeading(parsed.body, "Answer") ?: return@mapNotNull null
            QuizItemEntity(
                id = "starter:quiz:${asset.path.removePrefix("quizzes/").removeSuffix(".md")}",
                nodeId = null,
                prompt = prompt,
                answer = answer,
                explanation = sectionAfterHeading(parsed.body, "Explanation").orEmpty(),
                source = QuizSource.manual,
                sourceAnchor = asset.path,
                createdAt = now,
                updatedAt = now,
                revision = 1L,
                syncStatus = SyncStatus.clean,
                deletedAt = null,
                area = parsed.metadata["area"] ?: areaFromPath(asset.path),
                track = parsed.metadata["track"] ?: "general",
                visibility = parsed.metadata["visibility"] ?: "practice",
                isStarter = true
            )
        }

        return StarterContentPackage(
            nodes = nodes,
            quizzes = quizzes,
            reviewStates = quizzes.map { quiz ->
                ReviewStateEntity(
                    quizId = quiz.id,
                    ease = 2.5,
                    intervalDays = 0,
                    dueAt = now,
                    lastResult = ReviewResult.again,
                    attemptCount = 0,
                    updatedAt = now
                )
            }
        )
    }

    private fun AssetManager.readMarkdownAssets(root: String): List<MarkdownAsset> =
        listMarkdownPaths(root).map { path ->
            MarkdownAsset(path = path, text = open(path).bufferedReader().use { it.readText() })
        }

    private fun AssetManager.listMarkdownPaths(path: String): List<String> {
        val children = list(path)?.toList().orEmpty()
        if (children.isEmpty()) {
            return if (path.endsWith(".md")) listOf(path) else emptyList()
        }
        return children.flatMap { child -> listMarkdownPaths("$path/$child") }
    }

    private fun firstMarkdownHeading(markdown: String): String? =
        markdown.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun titleFromPath(path: String): String =
        path.substringAfterLast('/').removeSuffix(".md").split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercaseChar() } }

    private fun areaFromPath(path: String): String =
        path.removePrefix("nodes/")
            .removePrefix("quizzes/")
            .substringBefore('/')
            .takeIf { it.isNotBlank() && it != path }
            ?: "questions"

    private fun sectionAfterHeading(markdown: String, heading: String): String? {
        val lines = markdown.lines()
        val start = lines.indexOfFirst { it.trim().equals("## $heading", ignoreCase = true) }
        if (start < 0) return null
        return lines.drop(start + 1)
            .takeWhile { !it.trim().startsWith("## ") }
            .joinToString("\n")
            .trim()
            .takeIf { it.isNotBlank() }
    }
}
