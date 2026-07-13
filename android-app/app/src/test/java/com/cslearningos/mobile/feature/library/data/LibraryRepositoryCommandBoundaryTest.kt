package com.cslearningos.mobile.feature.library.data

import com.cslearningos.mobile.content.application.ContentCommandPort
import com.cslearningos.mobile.content.application.ContentCommandResult
import com.cslearningos.mobile.content.domain.ContentAreaRef
import com.cslearningos.mobile.content.domain.ContentNode
import com.cslearningos.mobile.content.domain.NodeId
import com.cslearningos.mobile.core.kernel.EntityRevision
import com.cslearningos.mobile.data.LearningDao
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryRepositoryCommandBoundaryTest {
    @Test
    fun legacyCreateWithoutAreaUsesDefaultAreaWithoutDaoWrites() = runTest {
        val areaWrites = mutableListOf<String>()
        var receivedAreaId: String? = null
        val dao = Proxy.newProxyInstance(
            LearningDao::class.java.classLoader,
            arrayOf(LearningDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "observeAreas", "observeNodes", "observeTrashNodes" -> flowOf(emptyList<Any>())
                "getArea", "getAreaBySlug", "getNode" -> null
                "getAllAreas" -> emptyList<Any>()
                "upsertArea" -> areaWrites += method.name
                else -> error("Unexpected DAO call: ${method.name}")
            }
        } as LearningDao
        val repository = LibraryRepository(
            dao = dao,
            contentCommands = ContentCommandPort {
                receivedAreaId = it.areaId
                ContentCommandResult.Success(
                    ContentNode(
                        id = NodeId(it.nodeId.value),
                        title = it.title,
                        markdownBody = it.markdownBody,
                        createdAt = it.occurredAt,
                        updatedAt = it.occurredAt,
                        revision = EntityRevision(1L),
                        deletedAt = null,
                        area = ContentAreaRef(id = "questions", slug = "questions"),
                        track = "general",
                        order = 1_000,
                        summary = "",
                        visibility = "support",
                        isStarter = false,
                        isChecked = false
                    )
                )
            }
        )

        val result = repository.saveNode(
            id = null,
            title = "New node",
            markdownBody = "# New node",
            areaId = null,
            now = 100L
        )

        assertEquals("questions", receivedAreaId)
        assertEquals("questions", result.areaId)
        assertEquals(emptyList<String>(), areaWrites)
    }
}
