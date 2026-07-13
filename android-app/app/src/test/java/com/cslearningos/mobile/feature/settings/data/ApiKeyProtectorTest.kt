package com.cslearningos.mobile.feature.settings.data

import android.util.Base64
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiKeyProtectorTest {
    @Test
    fun androidKeystoreProtectorRoundTripsVersionedTwelveByteIvEnvelope() {
        val protector = protector()
        val plainText = "sk-secret"

        val envelope = protector.encrypt(plainText)
        val payload = Base64.decode(envelope, Base64.NO_WRAP)

        assertEquals(1, payload[0].toInt())
        assertEquals(1 + 12 + plainText.toByteArray(Charsets.UTF_8).size + 16, payload.size)
        assertEquals(plainText, protector.decrypt(envelope))
    }

    @Test
    fun androidKeystoreProtectorRejectsTamperedCiphertext() {
        val protector = protector()
        val payload = Base64.decode(protector.encrypt("sk-secret"), Base64.NO_WRAP)
        payload[payload.lastIndex] = (payload.last().toInt() xor 1).toByte()

        assertNull(protector.decrypt(Base64.encodeToString(payload, Base64.NO_WRAP)))
    }

    private fun protector(): AndroidKeystoreApiKeyProtector =
        AndroidKeystoreApiKeyProtector {
            SecretKeySpec(ByteArray(32) { index -> index.toByte() }, "AES")
        }
}
