package com.nanobuo.mypodder.data.sync

import com.nanobuo.mypodder.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class RemoteSubscription(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("feed_url") val feedUrl: String,
    val title: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

@Serializable
data class RemoteEpisodeState(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("feed_url") val feedUrl: String,
    val guid: String,
    @SerialName("position_ms") val positionMs: Long = 0,
    val completed: Boolean = false,
    @SerialName("last_played_at") val lastPlayedAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("synced_at") val syncedAt: Long = 0,
)

@Serializable
data class QueueItem(
    @SerialName("f") val feedUrl: String,
    @SerialName("g") val guid: String,
)

@Serializable
data class RemoteQueue(
    @SerialName("user_id") val userId: String? = null,
    val items: List<QueueItem> = emptyList(),
    @SerialName("updated_at") val updatedAt: Long = 0,
)

@Serializable
private data class AuthUser(val id: String, val email: String? = null)

@Serializable
private data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    val user: AuthUser? = null,
)

/** Errore HTTP di Supabase; [code] 401/403 significa sessione non più valida. */
class SupabaseException(val code: Int, message: String) : IOException(message)

/** Chiamate REST a Supabase (Auth e PostgREST), senza SDK. */
@Singleton
class SupabaseApi @Inject constructor(
    private val client: OkHttpClient,
    appJson: Json,
) {
    // Senza encodeDefaults i campi a false/0 non verrebbero inviati e l'upsert non li aggiornerebbe.
    private val json = Json(appJson) { encodeDefaults = true }
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val jsonType = "application/json".toMediaType()

    val isConfigured: Boolean get() = base.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()

    /** Link che apre l'app dopo l'accesso dall'email (va aggiunto tra i Redirect URL di Supabase). */
    val redirectUrl = "${BuildConfig.APPLICATION_ID}://login"

    // --- Auth ---

    /** Invia l'email con il codice (e il link) di accesso; crea l'account se non esiste. */
    suspend fun sendLoginEmail(email: String) {
        val url = "$base/auth/v1/otp".toHttpUrl().newBuilder().addQueryParameter("redirect_to", redirectUrl).build()
        val body = buildJsonObject {
            put("email", email)
            put("create_user", true)
        }
        call(post(url, body.toString()))
    }

    suspend fun verifyCode(email: String, code: String): SyncSession {
        val body = buildJsonObject {
            put("type", "email")
            put("email", email)
            put("token", code)
        }
        return session(call(post("$base/auth/v1/verify".toHttpUrl(), body.toString())), email)
    }

    /** Accesso immediato con email e password (impostata in precedenza da un dispositivo collegato). */
    suspend fun signInWithPassword(email: String, password: String): SyncSession {
        val url = "$base/auth/v1/token".toHttpUrl().newBuilder().addQueryParameter("grant_type", "password").build()
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
        }
        return session(call(post(url, body.toString())), email)
    }

    /** Imposta o cambia la password dell'account collegato. */
    suspend fun setPassword(token: String, password: String) {
        val body = buildJsonObject { put("password", password) }
        call(Request.Builder().url("$base/auth/v1/user").put(body.toString().toRequestBody(jsonType)).auth(token).build())
    }

    suspend fun refresh(refreshToken: String, email: String): SyncSession {
        val url = "$base/auth/v1/token".toHttpUrl().newBuilder().addQueryParameter("grant_type", "refresh_token").build()
        val body = buildJsonObject { put("refresh_token", refreshToken) }
        return session(call(post(url, body.toString())), email)
    }

    /** Completa l'accesso dai token ricevuti nel link dell'email. */
    suspend fun sessionFromTokens(accessToken: String, refreshToken: String, expiresIn: Long): SyncSession {
        val request = Request.Builder().url("$base/auth/v1/user").get().auth(accessToken).build()
        val user = json.decodeFromString<AuthUser>(call(request))
        return SyncSession(accessToken, refreshToken, expiry(expiresIn), user.id, user.email.orEmpty())
    }

    suspend fun signOut(token: String) {
        runCatching { call(post("$base/auth/v1/logout".toHttpUrl(), "{}", token)) }
    }

    suspend fun deleteAccount(token: String) {
        call(post("$base/rest/v1/rpc/delete_account".toHttpUrl(), "{}", token))
    }

    private fun session(body: String, email: String): SyncSession {
        val r = json.decodeFromString<AuthResponse>(body)
        return SyncSession(
            accessToken = r.accessToken,
            refreshToken = r.refreshToken,
            expiresAt = expiry(r.expiresIn),
            userId = r.user?.id.orEmpty(),
            email = r.user?.email ?: email,
        )
    }

    private fun expiry(expiresIn: Long) = System.currentTimeMillis() + expiresIn * 1000

    // --- Dati ---

    suspend fun subscriptions(token: String): List<RemoteSubscription> =
        json.decodeFromString(call(get(rest("subscriptions") { addQueryParameter("select", "feed_url,title,image_url,updated_at") }, token)))

    suspend fun upsertSubscriptions(token: String, rows: List<RemoteSubscription>) {
        if (rows.isNotEmpty()) upsert(token, "subscriptions", "user_id,feed_url", json.encodeToString(rows))
    }

    suspend fun deleteSubscription(token: String, feedUrl: String) {
        val url = rest("subscriptions") { addQueryParameter("feed_url", "eq.$feedUrl") }
        call(Request.Builder().url(url).delete().auth(token).build())
    }

    suspend fun upsertEpisodeStates(token: String, rows: List<RemoteEpisodeState>) {
        if (rows.isNotEmpty()) upsert(token, "episode_states", "user_id,feed_url,guid", json.encodeToString(rows))
    }

    /** Stati salvati sul server dopo [since] (ms del server), in ordine di salvataggio. */
    suspend fun episodeStatesSince(token: String, since: Long, limit: Int): List<RemoteEpisodeState> =
        json.decodeFromString(
            call(
                get(
                    rest("episode_states") {
                        addQueryParameter("synced_at", "gt.$since")
                        addQueryParameter("order", "synced_at")
                        addQueryParameter("limit", limit.toString())
                    },
                    token,
                )
            )
        )

    suspend fun episodeStatesFor(token: String, feedUrl: String): List<RemoteEpisodeState> =
        json.decodeFromString(call(get(rest("episode_states") { addQueryParameter("feed_url", "eq.$feedUrl") }, token)))

    suspend fun queue(token: String): RemoteQueue? =
        json.decodeFromString<List<RemoteQueue>>(call(get(rest("queue_state") { addQueryParameter("select", "items,updated_at") }, token)))
            .firstOrNull()

    suspend fun upsertQueue(token: String, queue: RemoteQueue) =
        upsert(token, "queue_state", "user_id", json.encodeToString(listOf(queue)))

    // --- HTTP ---

    private fun rest(table: String, params: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        "$base/rest/v1/$table".toHttpUrl().newBuilder().apply(params).build()

    private suspend fun upsert(token: String, table: String, conflict: String, body: String) {
        val url = rest(table) { addQueryParameter("on_conflict", conflict) }
        val request = Request.Builder().url(url)
            .post(body.toRequestBody(jsonType))
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .auth(token)
            .build()
        call(request)
    }

    private fun get(url: HttpUrl, token: String) = Request.Builder().url(url).get().auth(token).build()

    private fun post(url: HttpUrl, body: String, token: String? = null) =
        Request.Builder().url(url).post(body.toRequestBody(jsonType)).auth(token).build()

    private fun Request.Builder.auth(token: String?): Request.Builder = apply {
        header("apikey", BuildConfig.SUPABASE_KEY)
        cacheControl(CacheControl.FORCE_NETWORK)
        if (token != null) header("Authorization", "Bearer $token")
    }

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw SupabaseException(response.code, errorMessage(body) ?: "HTTP ${response.code}")
            body
        }
    }

    /** Messaggio leggibile dagli errori di Auth ({"msg"}) e PostgREST ({"message"}). */
    private fun errorMessage(body: String): String? = runCatching {
        val obj = json.parseToJsonElement(body) as? JsonObject ?: return null
        listOf("msg", "message", "error_description", "error").firstNotNullOfOrNull { key ->
            (obj[key] as? JsonPrimitive)?.content
        }
    }.getOrNull()
}
