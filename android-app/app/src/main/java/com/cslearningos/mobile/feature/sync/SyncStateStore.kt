package com.cslearningos.mobile.feature.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * Sync pairing state: endpoint, credential, server identity, cursor, and the
 * scope fingerprint the cursor is bound to. v1 keeps the credential in the
 * app-private SharedPreferences sandbox; move to Keystore-backed storage
 * before exposing sync beyond trusted networks.
 */
class SyncStateStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var endpoint: String
        get() = prefs.getString(KEY_ENDPOINT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ENDPOINT, value).apply()

    var credential: String
        get() = prefs.getString(KEY_CREDENTIAL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CREDENTIAL, value).apply()

    var serverId: String
        get() = prefs.getString(KEY_SERVER_ID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SERVER_ID, value).apply()

    var cursor: Long
        get() = prefs.getLong(KEY_CURSOR, 0L)
        set(value) = prefs.edit().putLong(KEY_CURSOR, value).apply()

    var scopeFingerprint: String
        get() = prefs.getString(KEY_SCOPE_FINGERPRINT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SCOPE_FINGERPRINT, value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_AT, value).apply()

    var lastAttemptUploadAt: Long
        get() = prefs.getLong(KEY_LAST_ATTEMPT_UPLOAD_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_ATTEMPT_UPLOAD_AT, value).apply()

    val isPaired: Boolean
        get() = endpoint.isNotBlank() && credential.isNotBlank()

    fun clearPairing() {
        prefs.edit()
            .remove(KEY_CREDENTIAL)
            .remove(KEY_SERVER_ID)
            .remove(KEY_CURSOR)
            .remove(KEY_SCOPE_FINGERPRINT)
            .remove(KEY_LAST_SYNC_AT)
            .remove(KEY_LAST_ATTEMPT_UPLOAD_AT)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "cs_learning_sync"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_CREDENTIAL = "credential"
        const val KEY_SERVER_ID = "server_id"
        const val KEY_CURSOR = "cursor"
        const val KEY_SCOPE_FINGERPRINT = "scope_fingerprint"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_LAST_ATTEMPT_UPLOAD_AT = "last_attempt_upload_at"
    }
}
