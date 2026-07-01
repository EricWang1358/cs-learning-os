package com.cslearningos.mobile.ui.backup

import java.io.IOException
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupImportErrorFormatterTest {
    @Test
    fun formatsInvalidJsonErrors() {
        assertEquals("invalid_json", backupImportErrorKey(JSONException("bad json")))
    }

    @Test
    fun formatsUnreadableFileErrors() {
        assertEquals("unreadable_file", backupImportErrorKey(IOException("denied")))
    }

    @Test
    fun fallsBackToUnknownKey() {
        assertEquals("unknown", backupImportErrorKey(IllegalStateException("boom")))
    }
}
