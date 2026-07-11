package com.cslearningos.mobile.ui

import android.content.Context
import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.QuizItemEntity

enum class LibraryCheckedFilter {
    All,
    Checked
}

enum class LibraryFolderCardAction {
    Open,
    Edit,
    Delete
}

enum class LibraryNodeCardAction {
    Read,
    Check,
    Edit,
    Move
}

enum class LibraryAreaTreeAction {
    NewNode,
    Edit,
    More
}

enum class LibraryNodeTreeAction {
    OpenReader
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

data class LibraryTrashNodeRow(
    val id: String,
    val title: String,
    val originalAreaId: String,
    val deletedAt: Long?,
    val node: LearningNodeEntity
)

sealed interface LibraryTreeRow {
    data class Area(val folder: LibraryFolderRow) : LibraryTreeRow
    data class Trash(
        val nodeCount: Int,
        val items: List<LibraryTrashNodeRow>
    ) : LibraryTreeRow
}

data class LibraryAreaDetail(
    val areaId: String,
    val title: String,
    val nodeCount: Int,
    val checkedCount: Int,
    val items: List<LibraryNodeRow>
)

data class LibraryRootModel(
    val folders: List<LibraryFolderRow>,
    val hasOverviewPanel: Boolean = false,
    val hasAreaMapPanel: Boolean = false
)

fun buildLibraryRootModel(
    areas: List<AreaEntity>,
    nodes: List<LearningNodeEntity>,
    dueQuizzes: List<QuizItemEntity>,
    context: Context? = null
): LibraryRootModel =
    LibraryRootModel(
        folders = buildLibraryRootFolders(areas, nodes, dueQuizzes, context)
    )

fun libraryFolderCardActions(): List<LibraryFolderCardAction> =
    listOf(LibraryFolderCardAction.Open, LibraryFolderCardAction.Edit, LibraryFolderCardAction.Delete)

fun libraryNodeCardActions(): List<LibraryNodeCardAction> =
    listOf(LibraryNodeCardAction.Read, LibraryNodeCardAction.Check, LibraryNodeCardAction.Edit, LibraryNodeCardAction.Move)

fun libraryAreaTreeActions(): List<LibraryAreaTreeAction> =
    listOf(LibraryAreaTreeAction.NewNode, LibraryAreaTreeAction.Edit, LibraryAreaTreeAction.More)

fun libraryNodeTreeActions(): List<LibraryNodeTreeAction> = listOf(LibraryNodeTreeAction.OpenReader)

fun buildLibraryTreeRows(
    areas: List<AreaEntity>,
    nodes: List<LearningNodeEntity>,
    trashNodes: List<LearningNodeEntity>,
    dueQuizzes: List<QuizItemEntity>,
    context: Context? = null
): List<LibraryTreeRow> =
    buildLibraryRootFolders(areas, nodes, dueQuizzes, context)
        .map(LibraryTreeRow::Area)
        .plus(
            LibraryTreeRow.Trash(
                nodeCount = trashNodes.size,
                items = trashNodes
                    .sortedByDescending { it.deletedAt ?: it.updatedAt }
                    .map { node ->
                        LibraryTrashNodeRow(
                            id = node.id,
                            title = node.title,
                            originalAreaId = node.areaId.ifBlank { node.area },
                            deletedAt = node.deletedAt,
                            node = node
                        )
                    }
            )
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
