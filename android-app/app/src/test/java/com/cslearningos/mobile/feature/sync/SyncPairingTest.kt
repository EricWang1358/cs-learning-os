package com.cslearningos.mobile.feature.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncPairingTest {

    @Test
    fun parsesFullPayloadFromEitherField() {
        val payload = "csos-sync://pair?endpoint=http://192.168.1.5:8000&token=tok123&server=srv"
        val fromEndpoint = SyncPairing.resolvePairingInput(payload, "")
        val fromToken = SyncPairing.resolvePairingInput("", payload)
        checkNotNull(fromEndpoint)
        checkNotNull(fromToken)
        assertEquals("http://192.168.1.5:8000", fromEndpoint!!.endpoint)
        assertEquals("tok123", fromEndpoint.token)
        assertEquals(fromEndpoint, fromToken)
    }

    @Test
    fun acceptsSeparateEndpointAndToken() {
        val input = SyncPairing.resolvePairingInput("http://desktop:8000/", "abc")
        checkNotNull(input)
        assertEquals("http://desktop:8000", input!!.endpoint)
        assertEquals("abc", input.token)
    }

    @Test
    fun payloadTokenFillsMissingTokenField() {
        val input = SyncPairing.resolvePairingInput(
            "csos-sync://pair?endpoint=http://desktop:8000&token=xyz",
            ""
        )
        checkNotNull(input)
        assertEquals("xyz", input!!.token)
    }

    @Test
    fun rejectsIncompleteInput() {
        assertNull(SyncPairing.resolvePairingInput("", "only-a-token"))
        assertNull(SyncPairing.resolvePairingInput("http://desktop:8000", ""))
        assertNull(SyncPairing.resolvePairingInput("csos-sync://pair?endpoint=http://x", ""))
        assertNull(SyncPairing.parsePayload("https://example.com"))
    }
}
