package com.cslearningos.mobile.ui

import com.cslearningos.mobile.data.LearningNodeEntity
import com.cslearningos.mobile.data.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryModelsTest {
    @Test
    fun libraryGroupsNodesByAreaTrackAndOrderWithoutRevisionJargon() {
        val groups = buildLibraryGroups(
            listOf(
                node("gdb", "GDB", "cs-fundamentals", "gdb-debugging", 20),
                node("leaq", "leaq", "cs-fundamentals", "x86-64-assembly", 30),
                node("binary", "Binary Search", "algorithms", "search-patterns", 10)
            )
        )

        assertEquals(listOf("algorithms", "cs-fundamentals"), groups.map { it.area })
        assertEquals("search-patterns", groups[0].tracks.single().track)
        assertEquals(listOf("gdb-debugging", "x86-64-assembly"), groups[1].tracks.map { it.track })
        assertEquals("Binary Search", groups[0].tracks.single().nodes.single().title)
        assertEquals("Order 10", groups[0].tracks.single().nodes.single().meta)
    }

    @Test
    fun libraryOverviewExplainsDesktopCompatibleStructureBeforeCards() {
        val overview = buildLibraryOverview(
            listOf(
                node("gdb", "GDB", "cs-fundamentals", "gdb-debugging", 20),
                node("leaq", "leaq", "cs-fundamentals", "x86-64-assembly", 30),
                node("binary", "Binary Search", "algorithms", "search-patterns", 10)
            )
        )

        assertEquals(2, overview.areaCount)
        assertEquals(3, overview.trackCount)
        assertEquals(3, overview.nodeCount)
        assertEquals("CS Fundamentals", overview.featuredAreaLabel)
        assertEquals("Area -> Track -> Ordered nodes", overview.structureLabel)
    }

    private fun node(id: String, title: String, area: String, track: String, order: Int) =
        LearningNodeEntity(
            id = id,
            title = title,
            markdownBody = "# $title",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            lastReadAt = null,
            revision = 1L,
            syncStatus = SyncStatus.clean,
            deletedAt = null,
            area = area,
            track = track,
            order = order,
            summary = "Summary for $title",
            visibility = "core",
            isStarter = true
        )
}
