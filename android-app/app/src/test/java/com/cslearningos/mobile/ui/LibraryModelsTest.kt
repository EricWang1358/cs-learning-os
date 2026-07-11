package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.AreaEntity
import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.QuizItemEntity
import com.cslearningos.mobile.data.QuizSource
import com.cslearningos.mobile.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryModelsTest {
    @Test
    fun libraryRootFoldersShowEmptyAreaAndInlineCounts() {
        val folders = buildLibraryRootFolders(
            areas = listOf(
                area("algorithms", "Algorithms", 10),
                area("systems", "Systems", 20),
                area("empty-lab", "Empty Lab", 30)
            ),
            nodes = listOf(
                node("binary", "Binary Search", "algorithms", "search-patterns", 10, isChecked = true),
                node("paging", "Paging", "systems", "virtual-memory", 20)
            ),
            dueQuizzes = listOf(
                quiz("quiz-1", "binary", "algorithms"),
                quiz("quiz-2", "paging", "systems"),
                quiz("quiz-3", "paging", "systems")
            )
        )

        assertEquals(listOf("Algorithms", "Systems", "Empty Lab"), folders.map { it.title })
        assertEquals(1, folders[0].nodeCount)
        assertEquals(1, folders[0].checkedCount)
        assertEquals(2, folders[1].dueCount)
        assertEquals(0, folders[2].nodeCount)
        assertTrue(folders[2].trackPreview.isBlank())
    }

    @Test
    fun libraryAreaDetailShowsDirectNodeRowsAndCheckedFilter() {
        val detail = buildLibraryAreaDetail(
            area = area("systems", "Systems", 20),
            nodes = listOf(
                node("paging", "Paging", "systems", "virtual-memory", 20, isChecked = true),
                node("tlb", "TLB", "systems", "virtual-memory", 30),
                node("cache", "Cache", "systems", "cache", 10, isChecked = true),
                node("binary", "Binary Search", "algorithms", "search-patterns", 10, isChecked = true)
            ),
            dueQuizzes = listOf(
                quiz("quiz-1", "paging", "systems"),
                quiz("quiz-2", "tlb", "systems")
            ),
            filter = LibraryCheckedFilter.Checked
        )

        assertEquals("Systems", detail.title)
        assertEquals(listOf("Cache", "Paging"), detail.items.map { it.title })
        assertEquals(listOf("Cache", "Virtual Memory"), detail.items.map { it.trackLabel })
        assertEquals(2, detail.checkedCount)
        assertEquals(1, detail.items.last().dueCount)
    }

    @Test
    fun libraryRootModelContainsAreaFoldersOnly() {
        val root = buildLibraryRootModel(
            areas = listOf(
                area("algorithms", "Algorithms", 10),
                area("systems", "Systems", 20)
            ),
            nodes = listOf(
                node("binary", "Binary Search", "algorithms", "search-patterns", 10),
                node("paging", "Paging", "systems", "virtual-memory", 20, isChecked = true)
            ),
            dueQuizzes = emptyList()
        )

        assertEquals(listOf("Algorithms", "Systems"), root.folders.map { it.title })
        assertEquals(false, root.hasOverviewPanel)
        assertEquals(false, root.hasAreaMapPanel)
    }

    @Test
    fun libraryCardActionsExcludeTapEquivalentOpenActions() {
        assertEquals(
            listOf(LibraryFolderCardAction.Edit, LibraryFolderCardAction.Delete),
            libraryFolderCardActions()
        )
        assertEquals(
            listOf(LibraryNodeCardAction.Check, LibraryNodeCardAction.Edit, LibraryNodeCardAction.Move),
            libraryNodeCardActions()
        )
    }

    private fun area(id: String, name: String, order: Int) =
        AreaEntity(
            id = id,
            slug = id,
            name = name,
            order = order,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            deletedAt = null
        )

    private fun node(
        id: String,
        title: String,
        areaId: String,
        track: String,
        order: Int,
        isChecked: Boolean = false
    ) = LearningNodeEntity(
        id = id,
        title = title,
        markdownBody = "# $title",
        createdAt = 1_000L,
        updatedAt = 1_000L,
        lastReadAt = null,
        revision = 1L,
        syncStatus = SyncStatus.clean,
        deletedAt = null,
        area = areaId,
        areaId = areaId,
        track = track,
        order = order,
        summary = "Summary for $title",
        visibility = "core",
        isStarter = true,
        isChecked = isChecked
    )

    private fun quiz(id: String, nodeId: String, area: String) =
        QuizItemEntity(
            id = id,
            nodeId = nodeId,
            prompt = "Prompt $id",
            answer = "Answer $id",
            explanation = "",
            source = QuizSource.manual,
            sourceAnchor = null,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = area,
            track = "general",
            visibility = "practice",
            isStarter = false
        )
}
