package com.cslearningos.mobile.ui

import android.content.Context
import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.QuizItemEntity

enum class LibraryCheckedFilter {
    All,
    Checked
}

data class LibraryFolderRow(
    val areaId: String,
    val title: String,
    val nodeCount: Int,
    val checkedCount: Int,
    val dueCount: Int,
    val trackPreview: String
)

data class LibraryNodeRow(
    val id: String,
    val title: String,
    val summary: String,
    val trackLabel: String,
    val dueCount: Int,
    val checked: Boolean,
    val node: LearningNodeEntity
)

data class LibraryAreaDetail(
    val areaId: String,
    val title: String,
    val nodeCount: Int,
    val checkedCount: Int,
    val items: List<LibraryNodeRow>
)

data class LibraryOverview(
    val areaCount: Int,
    val nodeCount: Int,
    val checkedCount: Int,
    val featuredAreaLabel: String
)

data class LibraryMap(
    val areas: List<LibraryMapArea>
)

data class LibraryMapArea(
    val areaId: String,
    val label: String,
    val nodeCount: Int,
    val checkedCount: Int,
    val trackPreview: String
)

fun buildLibraryRootFolders(
    areas: List<AreaEntity>,
    nodes: List<LearningNodeEntity>,
    dueQuizzes: List<QuizItemEntity>,
    context: Context? = null
): List<LibraryFolderRow> {
    val activeNodes = nodes.activeLibraryNodes()
    val dueByNode = dueQuizzes.groupBy { it.nodeId }

    return areas
        .filter { it.deletedAt == null }
        .sortedWith(compareBy<AreaEntity> { it.order }.thenBy { displayAreaName(context, it) })
        .map { area ->
            val areaNodes = activeNodes.filter { it.areaId == area.id }
            val tracks = areaNodes.map { readableTrackLabel(context, it.track) }.distinct().sorted()
            LibraryFolderRow(
                areaId = area.id,
                title = displayAreaName(context, area),
                nodeCount = areaNodes.size,
                checkedCount = areaNodes.count { it.isChecked },
                dueCount = areaNodes.sumOf { node -> dueByNode[node.id].orEmpty().size },
                trackPreview = tracks.take(3).joinToString(", ")
            )
        }
}

fun buildLibraryAreaDetail(
    area: AreaEntity,
    nodes: List<LearningNodeEntity>,
    dueQuizzes: List<QuizItemEntity>,
    filter: LibraryCheckedFilter,
    context: Context? = null
): LibraryAreaDetail {
    val activeNodes = nodes.activeLibraryNodes()
        .filter { it.areaId == area.id }
    val filteredNodes = when (filter) {
        LibraryCheckedFilter.All -> activeNodes
        LibraryCheckedFilter.Checked -> activeNodes.filter { it.isChecked }
    }
    val dueByNode = dueQuizzes.groupBy { it.nodeId }

    return LibraryAreaDetail(
        areaId = area.id,
        title = displayAreaName(context, area),
        nodeCount = activeNodes.size,
        checkedCount = activeNodes.count { it.isChecked },
        items = filteredNodes
            .sortedWith(compareBy<LearningNodeEntity> { it.track }.thenBy { it.order }.thenBy { it.title })
            .map { node ->
                LibraryNodeRow(
                    id = node.id,
                    title = node.title,
                    summary = node.summary.ifBlank {
                        previewLibraryMarkdown(
                            markdown = node.markdownBody,
                            fallback = context?.getString(com.cslearningos.mobile.R.string.library_no_summary_yet)
                                ?: "No summary yet."
                        )
                    },
                    trackLabel = readableTrackLabel(context, node.track),
                    dueCount = dueByNode[node.id].orEmpty().size,
                    checked = node.isChecked,
                    node = node
                )
            }
    )
}

fun buildLibraryOverview(
    areas: List<AreaEntity>,
    nodes: List<LearningNodeEntity>,
    context: Context? = null
): LibraryOverview {
    val activeNodes = nodes.activeLibraryNodes()
    val featuredAreaId = activeNodes
        .groupBy { it.areaId }
        .entries
        .maxWithOrNull(
            compareBy<Map.Entry<String, List<LearningNodeEntity>>> { entry -> entry.value.count { it.isChecked } }
                .thenBy { entry -> entry.value.size }
                .thenBy { entry -> entry.key }
        )
        ?.key
    val featuredArea = areas.firstOrNull { it.id == featuredAreaId }

    return LibraryOverview(
        areaCount = areas.count { it.deletedAt == null },
        nodeCount = activeNodes.size,
        checkedCount = activeNodes.count { it.isChecked },
        featuredAreaLabel = featuredArea?.let { displayAreaName(context, it) }
            ?: (context?.getString(com.cslearningos.mobile.R.string.library_no_active_area) ?: "No active area")
    )
}

fun buildLibraryMap(
    areas: List<AreaEntity>,
    nodes: List<LearningNodeEntity>,
    context: Context? = null
): LibraryMap {
    val activeNodes = nodes.activeLibraryNodes()
    return LibraryMap(
        areas = areas
            .filter { it.deletedAt == null }
            .sortedWith(compareBy<AreaEntity> { it.order }.thenBy { displayAreaName(context, it) })
            .map { area ->
                val areaNodes = activeNodes.filter { it.areaId == area.id }
                LibraryMapArea(
                    areaId = area.id,
                    label = displayAreaName(context, area),
                    nodeCount = areaNodes.size,
                    checkedCount = areaNodes.count { it.isChecked },
                    trackPreview = areaNodes
                        .map { readableTrackLabel(context, it.track) }
                        .distinct()
                        .sorted()
                        .take(3)
                        .joinToString(", ")
                )
            }
    )
}

fun displayAreaName(context: Context?, area: AreaEntity): String =
    if (area.name == area.slug) readableAreaLabel(context, area.slug) else area.name

private fun List<LearningNodeEntity>.activeLibraryNodes(): List<LearningNodeEntity> =
    filter { it.deletedAt == null && it.visibility != "trash" }

private fun previewLibraryMarkdown(markdown: String, fallback: String): String =
    markdown
        .lineSequence()
        .map { it.trim().trimStart('#', '-', '>', ' ') }
        .firstOrNull { it.isNotBlank() && !it.startsWith(":::") }
        ?: fallback
