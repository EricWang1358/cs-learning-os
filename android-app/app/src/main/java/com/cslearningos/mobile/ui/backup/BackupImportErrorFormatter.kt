package com.cslearningos.mobile.ui.backup

import java.io.IOException
import org.json.JSONException

fun backupImportErrorKey(error: Throwable): String =
    when (error) {
        is JSONException -> "invalid_json"
        is IOException -> "unreadable_file"
        else -> "unknown"
    }
