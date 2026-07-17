package com.cslearningos.mobile.feature.sync

import org.json.JSONObject

/** Parses the desktop pairing QR payload and drives the /pair exchange. */
object SyncPairing {

    data class PairingInput(
        val endpoint: String,
        val token: String
    )

    data class PairResult(
        val deviceId: String,
        val credential: String,
        val serverId: String,
        val protocolVersion: Int
    )

    private const val PayloadScheme = "csos-sync://pair"

    /**
     * Accepts either a raw `csos-sync://pair?endpoint=...&token=...` payload
     * or separate endpoint/token fields (blank fields are taken from the
     * payload when one is pasted into either input).
     */
    fun resolvePairingInput(endpointField: String, tokenField: String): PairingInput? {
        val payload = listOf(endpointField, tokenField)
            .firstOrNull { it.trim().startsWith(PayloadScheme) }
        val fromPayload = payload?.let(::parsePayload)
        val endpoint = (endpointField.takeUnless { it.trim().startsWith(PayloadScheme) }?.trim().orEmpty())
            .ifBlank { fromPayload?.endpoint.orEmpty() }
        val token = (tokenField.takeUnless { it.trim().startsWith(PayloadScheme) }?.trim().orEmpty())
            .ifBlank { fromPayload?.token.orEmpty() }
        if (endpoint.isBlank() || token.isBlank()) return null
        return PairingInput(endpoint = endpoint.trimEnd('/'), token = token)
    }

    fun parsePayload(payload: String): PairingInput? {
        val trimmed = payload.trim()
        if (!trimmed.startsWith(PayloadScheme)) return null
        val query = trimmed.substringAfter('?', missingDelimiterValue = "")
        if (query.isBlank()) return null
        val params = query.split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=', "")
                val value = part.substringAfter('=', "")
                if (key.isBlank() || value.isBlank()) null else key to value
            }
            .toMap()
        val endpoint = params["endpoint"].orEmpty()
        val token = params["token"].orEmpty()
        if (endpoint.isBlank() || token.isBlank()) return null
        return PairingInput(endpoint = endpoint.trimEnd('/'), token = token)
    }

    fun parsePairResponse(json: JSONObject): PairResult =
        PairResult(
            deviceId = json.getString("deviceId"),
            credential = json.getString("credential"),
            serverId = json.getString("serverId"),
            protocolVersion = json.getInt("protocolVersion")
        )
}
