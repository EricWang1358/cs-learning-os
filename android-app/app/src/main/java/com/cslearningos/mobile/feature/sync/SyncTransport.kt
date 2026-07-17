package com.cslearningos.mobile.feature.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SyncException(val statusCode: Int, message: String) : Exception(message)

interface SyncTransport {
    suspend fun health(): SyncHealth
    suspend fun manifest(cursor: Long, serverId: String, scope: SyncScope): SyncManifest
    suspend fun pull(entityType: String, ids: List<String>, scope: SyncScope): List<SyncRecord>
    suspend fun pushAttempts(items: List<JSONObject>): List<SyncReceipt>
    suspend fun pushCaptures(items: List<JSONObject>): List<SyncReceipt>
    suspend fun pushReaderQuestions(items: List<JSONObject>): List<SyncReceipt>
    suspend fun pair(endpoint: String, token: String, deviceName: String): SyncPairing.PairResult
}

class OkHttpSyncTransport(
    private val endpoint: String,
    private val credential: String,
    private val client: OkHttpClient = defaultClient()
) : SyncTransport {

    override suspend fun health(): SyncHealth =
        parseSyncHealth(get("/api/sync/v1/health"))

    override suspend fun manifest(cursor: Long, serverId: String, scope: SyncScope): SyncManifest =
        parseSyncManifest(
            post(
                "/api/sync/v1/manifest",
                JSONObject()
                    .put("cursor", cursor)
                    .put("serverId", serverId)
                    .put("scope", scope.toJson())
            )
        )

    override suspend fun pull(entityType: String, ids: List<String>, scope: SyncScope): List<SyncRecord> {
        val response = post(
            "/api/sync/v1/pull",
            JSONObject()
                .put("entityType", entityType)
                .put("ids", JSONArray(ids))
                .put("scope", scope.toJson())
        )
        val records = response.optJSONArray("records") ?: return emptyList()
        return buildList {
            for (index in 0 until records.length()) {
                parseSyncRecord(records.getJSONObject(index))?.let(::add)
            }
        }
    }

    override suspend fun pushAttempts(items: List<JSONObject>): List<SyncReceipt> =
        pushEvents("/api/sync/v1/push/attempts", items)

    override suspend fun pushCaptures(items: List<JSONObject>): List<SyncReceipt> =
        pushEvents("/api/sync/v1/push/captures", items)

    override suspend fun pushReaderQuestions(items: List<JSONObject>): List<SyncReceipt> =
        pushEvents("/api/sync/v1/push/reader-questions", items)

    override suspend fun pair(endpoint: String, token: String, deviceName: String): SyncPairing.PairResult =
        SyncPairing.parsePairResponse(
            postTo(
                endpoint = endpoint,
                path = "/api/sync/v1/pair",
                body = JSONObject()
                    .put("token", token)
                    .put("device_name", deviceName),
                authenticated = false
            )
        )

    private suspend fun pushEvents(path: String, items: List<JSONObject>): List<SyncReceipt> {
        if (items.isEmpty()) return emptyList()
        val payload = JSONObject().put("items", JSONArray(items))
        return parseSyncReceipts(post(path, payload))
    }

    private suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${endpoint.trimEnd('/')}$path")
            .get()
            .header("Authorization", "Bearer $credential")
            .build()
        execute(request)
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${endpoint.trimEnd('/')}$path")
            .post(body.toString().toRequestBody(JsonMediaType))
            .header("Authorization", "Bearer $credential")
            .build()
        execute(request)
    }

    private suspend fun postTo(
        endpoint: String,
        path: String,
        body: JSONObject,
        authenticated: Boolean
    ): JSONObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("${endpoint.trimEnd('/')}$path")
            .post(body.toString().toRequestBody(JsonMediaType))
        if (authenticated && credential.isNotBlank()) {
            builder.header("Authorization", "Bearer $credential")
        }
        execute(builder.build())
    }

    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SyncException(response.code, "Sync request failed (${response.code})")
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    companion object {
        private val JsonMediaType = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
