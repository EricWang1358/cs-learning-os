package com.cslearningos.mobile.core.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KernelTypesTest {
    @Test
    fun commandIdRejectsBlankValues() {
        assertThrows(IllegalArgumentException::class.java) { CommandId(" ") }
    }

    @Test
    fun entityRevisionRejectsNegativeValues() {
        assertThrows(IllegalArgumentException::class.java) { EntityRevision(-1L) }
        assertEquals(0L, EntityRevision(0L).value)
    }
}
