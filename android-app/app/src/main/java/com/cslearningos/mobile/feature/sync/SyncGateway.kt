package com.cslearningos.mobile.feature.sync

import android.content.Context
import com.cslearningos.mobile.data.LearningDao

data class SyncStatusSnapshot(
    val isPaired: Boolean,
    val endpoint: String,
    val serverId: String,
    val lastSyncAt: Long,
    val scopeAreas: Set<String>,
    val includeDueReviews: Boolean,
    val serverScopes: Set<String> = emptySet(),
    val isServerConfirmed: Boolean = false
)

/**
 * ViewModel-facing boundary for sync. The default implementation wraps
 * SyncRepository/Transport/StateStore; tests substitute a fake.
 */
interface SyncGateway {
    fun statusSnapshot(): SyncStatusSnapshot
    suspend fun pair(endpoint: String, token: String, deviceName: String): SyncStatusSnapshot
    suspend fun unpair()
    suspend fun pull(): SyncReport
    suspend fun push(): SyncPushReport
    fun updateScope(areas: Set<String>, includeDueReviews: Boolean)
}

class DefaultSyncGateway(
    context: Context,
    private val dao: LearningDao,
    private val deviceName: String
) : SyncGateway {

    private val appContext = context.applicationContext
    private val store = SyncStateStore(appContext)

    override fun statusSnapshot(): SyncStatusSnapshot = snapshot()

    override suspend fun pair(endpoint: String, token: String, deviceName: String): SyncStatusSnapshot {
        val input = SyncPairing.resolvePairingInput(endpoint, token)
            ?: throw SyncException(0, "invalid_pairing_input")
        val transport = OkHttpSyncTransport(endpoint = input.endpoint, credential = "")
        val result = transport.pair(input.endpoint, input.token, deviceName)
        store.endpoint = input.endpoint
        store.credential = result.credential
        store.serverId = result.serverId
        store.deviceId = result.deviceId
        store.cursor = 0L
        store.lastAttemptUploadAt = 0L
        runCatching { refreshServerPolicy() }
            .onFailure {
                store.clearPairing()
                throw it
            }
        return snapshot()
    }

    override suspend fun unpair() {
        store.clearPairing()
    }

    override suspend fun pull(): SyncReport {
        val policy = refreshServerPolicy()
        if (SyncReadScope !in policy.scopes) {
            throw SyncException(401, "sync_read_not_allowed")
        }
        val scope = currentScope()
        return repository().pullAndApply(scope)
    }

    override suspend fun push(): SyncPushReport {
        val policy = refreshServerPolicy()
        if (SyncPushScope !in policy.scopes) {
            throw SyncException(401, "sync_push_not_allowed")
        }
        return repository().pushLocalChanges()
    }

    override fun updateScope(areas: Set<String>, includeDueReviews: Boolean) {
        store.scopeAreas = areas
        store.includeDueReviews = includeDueReviews
    }

    private fun repository(): SyncRepository {
        check(store.isPaired) { "Sync is not paired" }
        return SyncRepository(
            dao = dao,
            transport = OkHttpSyncTransport(endpoint = store.endpoint, credential = store.credential),
            store = store
        )
    }

    private fun currentScope(): SyncScope =
        SyncScope(
            areas = store.scopeAreas.toList(),
            includeDueReviews = store.includeDueReviews,
            pinnedNodeIds = emptyList()
        )

    private suspend fun refreshServerPolicy(): SyncDevicePolicy {
        check(store.isPaired) { "Sync is not paired" }
        val policy = OkHttpSyncTransport(endpoint = store.endpoint, credential = store.credential).devicePolicy()
        store.deviceId = policy.id
        store.serverScopes = policy.scopes
        return policy
    }

    private fun snapshot(): SyncStatusSnapshot =
        SyncStatusSnapshot(
            isPaired = store.isPaired,
            endpoint = store.endpoint,
            serverId = store.serverId,
            lastSyncAt = store.lastSyncAt,
            scopeAreas = store.scopeAreas,
            includeDueReviews = store.includeDueReviews,
            serverScopes = store.serverScopes,
            isServerConfirmed = store.serverScopes.isNotEmpty()
        )

    private companion object {
        const val SyncReadScope = "sync:read"
        const val SyncPushScope = "sync:push"
    }
}
