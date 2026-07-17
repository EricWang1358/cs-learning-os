package com.cslearningos.mobile.feature.review.data

import com.cslearningos.mobile.data.QuizItemEntity
import java.security.MessageDigest
import org.json.JSONObject

data class QuizOutboxPayload(
    val id: String,
    val prompt: String,
    val answer: String,
    val explanation: String,
    val area: String,
    val visibility: String,
    val revision: Long
)

object QuizOutboxCodec {
    const val SchemaVersion = 1
    fun encode(quiz: QuizItemEntity): String = JSONObject()
        .put("schemaVersion", SchemaVersion)
        .put("id", quiz.id)
        .put("prompt", quiz.prompt)
        .put("answer", quiz.answer)
        .put("explanation", quiz.explanation)
        .put("area", quiz.area)
        .put("visibility", quiz.visibility)
        .put("revision", quiz.revision)
        .toString()

    fun decode(raw: String): QuizOutboxPayload {
        val json = JSONObject(raw)
        require(json.getInt("schemaVersion") == SchemaVersion) { "Unsupported quiz outbox schema" }
        return QuizOutboxPayload(
            id = json.getString("id"),
            prompt = json.getString("prompt"),
            answer = json.getString("answer"),
            explanation = json.getString("explanation"),
            area = json.getString("area"),
            visibility = json.getString("visibility"),
            revision = json.getLong("revision")
        )
    }

    fun sha256Hex(payload: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    fun desktopBody(payload: QuizOutboxPayload): String = buildString {
        append("## Prompt\n\n")
        append(payload.prompt.trim())
        append("\n\n## Answer\n\n")
        append(payload.answer.trim())
        if (payload.explanation.isNotBlank()) {
            append("\n\n## Explanation\n\n")
            append(payload.explanation.trim())
        }
    }
}
