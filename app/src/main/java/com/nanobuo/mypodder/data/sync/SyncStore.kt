package com.nanobuo.mypodder.data.sync

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

// File a parte (escluso dai backup): sessione e cursori valgono solo su questo dispositivo.
private val Context.syncStore by preferencesDataStore(name = "sync")

data class SyncSession(
    val accessToken: String,
    val refreshToken: String,
    /** Scadenza dell'access token (ms). */
    val expiresAt: Long,
    val userId: String,
    val email: String,
)

data class SyncInfo(
    val session: SyncSession? = null,
    val lastSyncAt: Long = 0,
)

@Singleton
class SyncStore @Inject constructor(
    @ApplicationContext private val context: Context,
    appScope: CoroutineScope,
) {
    private object Keys {
        val accessToken = stringPreferencesKey("access_token")
        val refreshToken = stringPreferencesKey("refresh_token")
        val expiresAt = longPreferencesKey("expires_at")
        val userId = stringPreferencesKey("user_id")
        val email = stringPreferencesKey("email")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val pushedUntil = longPreferencesKey("pushed_until")
        val pulledUntil = longPreferencesKey("pulled_until")
        val queueUpdatedAt = longPreferencesKey("queue_updated_at")
        val syncedFeeds = stringSetPreferencesKey("synced_feeds")
    }

    private val data = context.syncStore.data

    val info: StateFlow<SyncInfo> = data.map { p ->
        val token = p[Keys.accessToken]
        SyncInfo(
            session = token?.let {
                SyncSession(
                    accessToken = it,
                    refreshToken = p[Keys.refreshToken].orEmpty(),
                    expiresAt = p[Keys.expiresAt] ?: 0,
                    userId = p[Keys.userId].orEmpty(),
                    email = p[Keys.email].orEmpty(),
                )
            },
            lastSyncAt = p[Keys.lastSyncAt] ?: 0,
        )
    }.stateIn(appScope, SharingStarted.Eagerly, SyncInfo())

    suspend fun session(): SyncSession? = data.first().let { p ->
        val token = p[Keys.accessToken] ?: return null
        SyncSession(token, p[Keys.refreshToken].orEmpty(), p[Keys.expiresAt] ?: 0, p[Keys.userId].orEmpty(), p[Keys.email].orEmpty())
    }

    suspend fun saveSession(s: SyncSession) = context.syncStore.edit {
        it[Keys.accessToken] = s.accessToken
        it[Keys.refreshToken] = s.refreshToken
        it[Keys.expiresAt] = s.expiresAt
        it[Keys.userId] = s.userId
        it[Keys.email] = s.email
    }

    /** Esce dall'account e dimentica cursori e stato: il prossimo accesso riparte da una fusione. */
    suspend fun clear() = context.syncStore.edit { it.clear() }

    private suspend fun long(key: Preferences.Key<Long>) = data.first()[key] ?: 0

    suspend fun pushedUntil() = long(Keys.pushedUntil)
    suspend fun pulledUntil() = long(Keys.pulledUntil)
    suspend fun queueUpdatedAt() = long(Keys.queueUpdatedAt)
    suspend fun syncedFeeds(): Set<String> = data.first()[Keys.syncedFeeds].orEmpty()

    suspend fun setPushedUntil(v: Long) = context.syncStore.edit { it[Keys.pushedUntil] = v }
    suspend fun setPulledUntil(v: Long) = context.syncStore.edit { it[Keys.pulledUntil] = v }
    suspend fun setQueueUpdatedAt(v: Long) = context.syncStore.edit { it[Keys.queueUpdatedAt] = v }
    suspend fun setSyncedFeeds(v: Set<String>) = context.syncStore.edit { it[Keys.syncedFeeds] = v }
    suspend fun setLastSyncAt(v: Long) = context.syncStore.edit { it[Keys.lastSyncAt] = v }

    /** Da chiamare a ogni modifica della coda fatta dall'utente su questo dispositivo. */
    suspend fun markQueueChanged() = setQueueUpdatedAt(System.currentTimeMillis())
}
