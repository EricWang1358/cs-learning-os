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

    var deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var scopeAreas: Set<String>
        get() = prefs.getStringSet(KEY_SCOPE_AREAS, emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_SCOPE_AREAS, value).apply()

    var serverScopes: Set<String>
        get() = prefs.getStringSet(KEY_SERVER_SCOPES, emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_SERVER_SCOPES, value).apply()

    var includeDueReviews: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_DUE, false)
        set(value) = prefs.edit().putBoolean(KEY_INCLUDE_DUE, value).apply()

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
            .remove(KEY_DEVICE_ID)
            .remove(KEY_SCOPE_AREAS)
            .remove(KEY_SERVER_SCOPES)
            .remove(KEY_INCLUDE_DUE)
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
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SCOPE_AREAS = "scope_areas"
        const val KEY_SERVER_SCOPES = "server_scopes"
        const val KEY_INCLUDE_DUE = "include_due_reviews"
    }
}
