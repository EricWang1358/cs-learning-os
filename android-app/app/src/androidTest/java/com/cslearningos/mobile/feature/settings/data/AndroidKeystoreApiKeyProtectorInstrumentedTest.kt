package com.cslearningos.mobile.feature.settings.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreApiKeyProtectorInstrumentedTest {
    @Test
    fun encryptsAndDecryptsUsingAndroidKeystore() {
        val protector = AndroidKeystoreApiKeyProtector()

        val envelope = protector.encrypt("sk-instrumented-test")

        assertEquals("sk-instrumented-test", protector.decrypt(envelope))
    }
}
