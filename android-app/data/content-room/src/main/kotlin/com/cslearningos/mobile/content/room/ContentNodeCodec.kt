package com.cslearningos.mobile.content.room

import com.cslearningos.mobile.content.domain.ContentAreaRef
import com.cslearningos.mobile.content.domain.ContentNode
import com.cslearningos.mobile.content.domain.NodeId
import com.cslearningos.mobile.core.kernel.EntityRevision
import java.security.MessageDigest
import org.json.JSONObject

object ContentNodeCodec {
    const val SchemaVersion = 1

    private val fieldOrder = listOf(
        "schemaVersion",
        "id",
        "title",
        "markdownBody",
        "createdAt",
        "updatedAt",
        "revision",
        "deletedAt",
        "areaId",
        "areaSlug",
        "track",
        "order",
        "summary",
        "visibility",
        "isStarter",
        "isChecked"
    )

    fun encode(node: ContentNode): String {
        val objectPayload = JSONObject()
            .put("schemaVersion", SchemaVersion)
            .put("id", node.id.value)
            .put("title", node.title)
            .put("markdownBody", node.markdownBody)
            .put("createdAt", node.createdAt)
            .put("updatedAt", node.updatedAt)
            .put("revision", node.revision.value)
            .put("deletedAt", node.deletedAt ?: JSONObject.NULL)
            .put("areaId", node.area.id)
            .put("areaSlug", node.area.slug)
            .put("track", node.track)
            .put("order", node.order)
            .put("summary", node.summary)
            .put("visibility", node.visibility)
            .put("isStarter", node.isStarter)
            .put("isChecked", node.isChecked)

        return fieldOrder.joinToString(prefix = "{", postfix = "}", separator = ",") { field ->
            "\"$field\":${jsonValue(objectPayload.get(field))}"
        }
    }

    fun decode(payload: String): ContentNode {
        val objectPayload = JSONObject(payload)
        val version = objectPayload.requiredInt("schemaVersion")
        require(version == SchemaVersion) { "Unsupported content node schemaVersion: $version" }

        return ContentNode(
            id = NodeId(objectPayload.requiredString("id")),
            title = objectPayload.requiredString("title"),
            markdownBody = objectPayload.requiredString("markdownBody"),
            createdAt = objectPayload.requiredLong("createdAt"),
            updatedAt = objectPayload.requiredLong("updatedAt"),
            revision = EntityRevision(objectPayload.requiredLong("revision")),
            deletedAt = objectPayload.nullableLong("deletedAt"),
            area = ContentAreaRef(
                id = objectPayload.requiredString("areaId"),
                slug = objectPayload.requiredString("areaSlug")
            ),
            track = objectPayload.requiredString("track"),
            order = objectPayload.requiredInt("order"),
            summary = objectPayload.requiredString("summary"),
            visibility = objectPayload.requiredString("visibility"),
            isStarter = objectPayload.requiredBoolean("isStarter"),
            isChecked = objectPayload.requiredBoolean("isChecked")
        )
    }

    fun sha256Hex(payload: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }

    private fun JSONObject.requiredString(name: String): String {
        requirePresent(name)
        return getString(name)
    }

    private fun JSONObject.requiredLong(name: String): Long {
        requirePresent(name)
        return getLong(name)
    }

    private fun JSONObject.requiredInt(name: String): Int {
        requirePresent(name)
        return getInt(name)
    }

    private fun JSONObject.requiredBoolean(name: String): Boolean {
        requirePresent(name)
        return getBoolean(name)
    }

    private fun JSONObject.nullableLong(name: String): Long? {
        require(has(name)) { "Missing required content node field: $name" }
        return if (isNull(name)) null else getLong(name)
    }

    private fun JSONObject.requirePresent(name: String) {
        require(has(name) && !isNull(name)) { "Missing required content node field: $name" }
    }

    private fun jsonValue(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is String -> jsonString(value)
        is Boolean, is Number -> value.toString()
        else -> error("Unsupported content node value type: ${value::class.java.name}")
    }

    private fun jsonString(value: String): String = buildString(value.length + 2) {
        append('\"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '\"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('\"')
    }
}
