package com.cslearningos.mobile.ui

import androidx.test.core.app.ApplicationProvider
import com.cslearningos.mobile.data.LearningDao
import com.cslearningos.mobile.data.LearningRepository
import com.cslearningos.mobile.feature.sync.SyncGateway
import com.cslearningos.mobile.feature.sync.SyncPushReport
import com.cslearningos.mobile.feature.sync.SyncReport
import com.cslearningos.mobile.feature.sync.SyncStatusSnapshot
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearningViewModelSyncTest {

    private fun viewModel(gateway: SyncGateway): LearningViewModel {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        return LearningViewModel(
            application = application,
            repository = LearningRepository(proxyDao()),
            initialState = LearningUiState(),
            syncGateway = gateway
        )
    }

    @Test
    fun pairSuccessMarksStatePaired() = runTest {
        val gateway = FakeSyncGateway()
        val vm = viewModel(gateway)

        vm.pairSync("http://desktop:8000", "token")
        advanceUntilIdle()

        val sync = vm.state.value.sync
        assertTrue(sync.isPaired)
        assertEquals("http://desktop:8000", sync.endpoint)
        assertFalse(sync.busy)
        assertNull(sync.error)
        assertEquals("http://desktop:8000", gateway.lastPairEndpoint)
    }

    @Test
    fun pairFailureSurfacesErrorWithoutMarkingPaired() = runTest {
        val gateway = FakeSyncGateway(failOnPair = true)
        val vm = viewModel(gateway)

        vm.pairSync("http://desktop:8000", "bad-token")
        advanceUntilIdle()

        val sync = vm.state.value.sync
        assertFalse(sync.isPaired)
        assertFalse(sync.busy)
        assertNotNull(sync.error)
    }

    @Test
    fun pullSuccessStoresReport() = runTest {
        val gateway = FakeSyncGateway(paired = true)
        val vm = viewModel(gateway)

        vm.pullSyncNow()
        advanceUntilIdle()

        val sync = vm.state.value.sync
        assertNotNull(sync.lastPullReport)
        assertEquals(3, sync.lastPullReport?.totalApplied)
        assertTrue(gateway.pullCalled)
    }

    @Test
    fun uploadSuccessStoresPushReport() = runTest {
        val gateway = FakeSyncGateway(paired = true)
        val vm = viewModel(gateway)

        vm.uploadSyncNow()
        advanceUntilIdle()

        val sync = vm.state.value.sync
        assertNotNull(sync.lastPushReport)
        assertEquals(2, sync.lastPushReport?.totalUploaded)
    }

    @Test
    fun unpairResetsSyncState() = runTest {
        val gateway = FakeSyncGateway(paired = true)
        val vm = viewModel(gateway)

        vm.unpairSync()
        advanceUntilIdle()

        val sync = vm.state.value.sync
        assertFalse(sync.isPaired)
        assertTrue(gateway.unpairCalled)
    }

    @Test
    fun updateScopeDelegatesToGateway() = runTest {
        val gateway = FakeSyncGateway(paired = true)
        val vm = viewModel(gateway)

        vm.updateSyncScope(setOf("algorithms", "systems"), includeDueReviews = true)
        advanceUntilIdle()

        assertEquals(setOf("algorithms", "systems"), gateway.snapshot.scopeAreas)
        assertTrue(vm.state.value.sync.includeDueReviews)
    }

    private class FakeSyncGateway(
        paired: Boolean = false,
        private val failOnPair: Boolean = false
    ) : SyncGateway {
        var snapshot = SyncStatusSnapshot(
            isPaired = paired,
            endpoint = if (paired) "http://desktop:8000" else "",
            serverId = if (paired) "srv" else "",
            lastSyncAt = 0L,
            scopeAreas = emptySet(),
            includeDueReviews = false
        )
        var lastPairEndpoint: String? = null
        var pullCalled = false
        var unpairCalled = false

        override fun statusSnapshot(): SyncStatusSnapshot = snapshot

        override suspend fun pair(endpoint: String, token: String, deviceName: String): SyncStatusSnapshot {
            if (failOnPair) throw com.cslearningos.mobile.feature.sync.SyncException(401, "unauthorized")
            lastPairEndpoint = endpoint
            snapshot = snapshot.copy(isPaired = true, endpoint = endpoint, serverId = "srv")
            return snapshot
        }

        override suspend fun unpair() {
            unpairCalled = true
            snapshot = snapshot.copy(isPaired = false, endpoint = "", serverId = "")
        }

        override suspend fun pull(): SyncReport {
            pullCalled = true
            return SyncReport(pulledNodes = 2, pulledQuizzes = 1, cursor = 9, serverId = "srv")
        }

        override suspend fun push(): SyncPushReport =
            SyncPushReport(uploadedAttempts = 2)

        override fun updateScope(areas: Set<String>, includeDueReviews: Boolean) {
            snapshot = snapshot.copy(scopeAreas = areas, includeDueReviews = includeDueReviews)
        }
    }

    private fun proxyDao(): LearningDao =
        Proxy.newProxyInstance(
            LearningDao::class.java.classLoader,
            arrayOf(LearningDao::class.java)
        ) { _, method, _ ->
            when {
                method.returnType == Boolean::class.javaPrimitiveType -> false
                method.returnType == Int::class.javaPrimitiveType -> 0
                method.returnType == Long::class.javaPrimitiveType -> 0L
                method.name.startsWith("observe") || method.name.startsWith("due") -> flowOf(emptyList<Any>())
                else -> null
            }
        } as LearningDao
}
