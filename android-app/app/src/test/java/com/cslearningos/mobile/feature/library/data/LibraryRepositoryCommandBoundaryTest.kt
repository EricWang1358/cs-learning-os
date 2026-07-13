package com.cslearningos.mobile.feature.library.data

import com.cslearningos.mobile.content.application.ContentCommandFailure
import com.cslearningos.mobile.content.application.ContentCommandPort
import com.cslearningos.mobile.content.application.ContentCommandResult
import com.cslearningos.mobile.data.LearningDao
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRepositoryCommandBoundaryTest {
    @Test
    fun legacyCreateWithoutAreaDelegatesBeforeTouchingDao() = runTest {
        val areaWrites = mutableListOf<String>()
        val dao = Proxy.newProxyInstance(
            LearningDao::class.java.classLoader,
            arrayOf(LearningDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "observeAreas", "observeNodes", "observeTrashNodes" -> flowOf(emptyList<Any>())
                "getArea", "getAreaBySlug" -> null
                "getAllAreas" -> emptyList<Any>()
                "upsertArea" -> areaWrites += method.name
                else -> error("Unexpected DAO call: ${method.name}")
            }
        } as LearningDao
        val repository = LibraryRepository(
            dao = dao,
            contentCommands = ContentCommandPort {
                ContentCommandResult.Failure(ContentCommandFailure.Validation("create.missing_area"))
            }
        )

        val result = runCatching {
            repository.saveNode(
                id = null,
                title = "New node",
                markdownBody = "# New node",
                areaId = null,
                now = 100L
            )
        }

        assertTrue(result.isFailure)
        assertEquals(emptyList<String>(), areaWrites)
    }
}
