package com.cslearningos.mobile.content.room

import com.cslearningos.mobile.content.domain.ContentAreaRef
import com.cslearningos.mobile.content.domain.ContentNode
import com.cslearningos.mobile.content.domain.NodeId
import com.cslearningos.mobile.core.kernel.EntityRevision
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentNodeCodecTest {
    @Test
    fun encodeThenDecodePreservesTheContentNode() {
        assertEquals(node(), ContentNodeCodec.decode(ContentNodeCodec.encode(node())))
    }

    @Test
    fun encodingAndHashAreDeterministic() {
        val firstPayload = ContentNodeCodec.encode(node())
        val secondPayload = ContentNodeCodec.encode(node())

        assertEquals(firstPayload, secondPayload)
        assertEquals(ContentNodeCodec.sha256Hex(firstPayload), ContentNodeCodec.sha256Hex(secondPayload))
    }

    @Test
    fun titleBodyAndAreaChangesEachChangeTheHash() {
        val originalHash = hashOf(node())

        assertFalse(originalHash == hashOf(node().copy(title = "Changed title")))
        assertFalse(originalHash == hashOf(node().copy(markdownBody = "Changed body")))
        assertFalse(originalHash == hashOf(node().copy(area = ContentAreaRef("area-9", "algorithms"))))
    }

    @Test
    fun payloadContainsNoPersistenceOnlyFields() {
        val payload = ContentNodeCodec.encode(node())

        assertFalse(payload.contains("syncStatus"))
        assertFalse(payload.contains("lastReadAt"))
    }

    @Test
    fun nullDeletedAtRoundTrips() {
        val decoded = ContentNodeCodec.decode(ContentNodeCodec.encode(node().copy(deletedAt = null)))

        assertNull(decoded.deletedAt)
    }

    @Test
    fun missingDeletedAtIsRejectedEvenThoughItsValueMayBeNull() {
        val objectPayload = JSONObject(ContentNodeCodec.encode(node()))
        objectPayload.remove("deletedAt")
        val payload = objectPayload.toString()

        try {
            ContentNodeCodec.decode(payload)
            throw AssertionError("Expected missing deletedAt to be rejected")
        } catch (exception: IllegalArgumentException) {
            assertTrue(exception.message.orEmpty().contains("deletedAt"))
        }
    }

    @Test
    fun unsupportedSchemaVersionIsRejected() {
        val payload = ContentNodeCodec.encode(node()).replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":2")

        try {
            ContentNodeCodec.decode(payload)
            throw AssertionError("Expected unsupported schema version to be rejected")
        } catch (exception: IllegalArgumentException) {
            assertTrue(exception.message.orEmpty().contains("schemaVersion"))
        }
    }

    private fun hashOf(node: ContentNode): String =
        ContentNodeCodec.sha256Hex(ContentNodeCodec.encode(node))

    private fun node() = ContentNode(
        id = NodeId("node-7"),
        title = "Memory ordering",
        markdownBody = "# Memory ordering\n\nAcquire and release.",
        createdAt = 100L,
        updatedAt = 200L,
        revision = EntityRevision(8L),
        deletedAt = 300L,
        area = ContentAreaRef(id = "area-2", slug = "concurrency"),
        track = "systems",
        order = 11,
        summary = "Ordering guarantees.",
        visibility = "core",
        isStarter = true,
        isChecked = true
    )
}
