package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.LearningNodeEntity

data class LibraryNodeSummary(
    val id: String,
    val title: String,
    val summary: String,
    val meta: String,
    val node: LearningNodeEntity
)

data class LibraryTrackGroup(
    val track: String,
    val nodes: List<LibraryNodeSummary>
)

data class LibraryAreaGroup(
    val area: String,
    val tracks: List<LibraryTrackGroup>
)

data class LibraryOverview(
    val areaCount: Int,
    val trackCount: Int,
    val nodeCount: Int,
    val featuredAreaLabel: String,
    val structureLabel: String = "Area -> Track -> Ordered nodes"
)

data class LibraryMap(
    val areas: List<LibraryMapArea>
)

data class LibraryMapArea(
    val area: String,
    val label: String,
    val nodeCount: Int,
    val trackCount: Int,
    val trackPreview: String,
    val collapsed: Boolean
)

fun buildLibraryOverview(nodes: List<LearningNodeEntity>): LibraryOverview {
    val activeNodes = nodes.filter { it.deletedAt == null && it.visibility != "trash" }
    val groups = buildLibraryGroups(activeNodes)
    val featuredArea = groups.maxByOrNull { area -> area.tracks.sumOf { it.nodes.size } }?.area

    return LibraryOverview(
        areaCount = groups.size,
        trackCount = groups.sumOf { it.tracks.size },
        nodeCount = activeNodes.size,
        featuredAreaLabel = featuredArea?.let(::readableAreaLabel) ?: "No active area"
    )
}

fun buildLibraryGroups(nodes: List<LearningNodeEntity>): List<LibraryAreaGroup> =
    nodes
        .filter { it.deletedAt == null && it.visibility != "trash" }
        .groupBy { it.area }
        .toSortedMap()
        .map { (area, areaNodes) ->
            LibraryAreaGroup(
                area = area,
                tracks = areaNodes
                    .groupBy { it.track }
                    .toSortedMap()
                    .map { (track, trackNodes) ->
                        LibraryTrackGroup(
                            track = track,
                            nodes = trackNodes
                                .sortedWith(compareBy<LearningNodeEntity> { it.order }.thenBy { it.title })
                                .map { node ->
                                    LibraryNodeSummary(
                                        id = node.id,
                                        title = node.title,
                                        summary = node.summary.ifBlank { previewLibraryMarkdown(node.markdownBody) },
                                        meta = "Order ${node.order}",
                                        node = node
                                    )
                                }
                        )
                    }
            )
        }

fun buildLibraryMap(nodes: List<LearningNodeEntity>, collapsedAreas: Set<String>): LibraryMap =
    LibraryMap(
        areas = buildLibraryGroups(nodes).map { area ->
            val tracks = area.tracks.map { readableTrackLabel(it.track) }
            LibraryMapArea(
                area = area.area,
                label = readableAreaLabel(area.area),
                nodeCount = area.tracks.sumOf { it.nodes.size },
                trackCount = area.tracks.size,
                trackPreview = tracks.take(3).joinToString(", ").ifBlank { "No tracks" },
                collapsed = area.area in collapsedAreas
            )
        }
    )

fun readableAreaLabel(area: String): String =
    when (area) {
        "cs-fundamentals" -> "CS Fundamentals"
        "algorithms" -> "Algorithms"
        "projects" -> "Projects"
        "abilities" -> "Abilities"
        "tools" -> "Tools"
        "questions" -> "Questions"
        else -> area.split('-', '_').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
    }

fun readableTrackLabel(track: String): String =
    track.split('-', '_').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

private fun previewLibraryMarkdown(markdown: String): String =
    markdown
        .lineSequence()
        .map { it.trim().trimStart('#', '-', '>', ' ') }
        .firstOrNull { it.isNotBlank() && !it.startsWith(":::") }
        ?: "No summary yet."
