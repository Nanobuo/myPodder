package com.nanobuo.mypodder.data.sync

import android.net.Uri
import com.nanobuo.mypodder.data.db.AppDatabase
import com.nanobuo.mypodder.data.repo.PodcastRepository
import com.nanobuo.mypodder.playback.PlayerConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Error(val message: String) : SyncStatus
}

sealed interface LoginState {
    data object Idle : LoginState
    data object Sending : LoginState
    data object SigningIn : LoginState
    data class CodeSent(val email: String) : LoginState
    data class Verifying(val email: String) : LoginState
    /** [email] presente se l'errore è nella verifica del codice (si resta su quel passo). */
    data class Error(val message: String, val email: String? = null, val wrongPassword: Boolean = false) : LoginState
}

/**
 * Sincronizza con Supabase iscrizioni, stato di ascolto degli episodi e coda.
 *
 * - Iscrizioni: fusione a tre vie tra quelle locali, quelle sul server e quelle presenti
 *   all'ultima sincronizzazione, così si capisce da che parte è stata aggiunta o tolta una voce.
 * - Episodi (feed + guid): per ognuno vince la modifica più recente (updated_at).
 * - Coda: un'unica lista, vince la versione modificata per ultima.
 */
@Singleton
class SyncManager @Inject constructor(
    private val api: SupabaseApi,
    private val store: SyncStore,
    db: AppDatabase,
    private val podcasts: PodcastRepository,
    private val player: PlayerConnection,
    private val appScope: CoroutineScope,
) {
    private val podcastDao = db.podcastDao()
    private val episodeDao = db.episodeDao()
    private val queueDao = db.queueDao()

    private val mutex = Mutex()
    private var pending: Job? = null

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _login = MutableStateFlow<LoginState>(LoginState.Idle)
    val login: StateFlow<LoginState> = _login.asStateFlow()

    val info = store.info
    val isAvailable: Boolean get() = api.isConfigured

    /** Avvia le sincronizzazioni automatiche quando cambiano iscrizioni o coda. */
    fun start() {
        appScope.launch {
            podcasts.podcasts.map { list -> list.map { it.feedUrl }.toSet() }
                .distinctUntilChanged().drop(1).collect { requestSync() }
        }
        appScope.launch {
            queueDao.observeIds().distinctUntilChanged().drop(1).collect { requestSync() }
        }
    }

    /** Sincronizza a breve: le richieste ravvicinate (es. più modifiche di fila) diventano una sola. */
    fun requestSync(delayMs: Long = 3_000) {
        if (store.info.value.session == null) return
        pending?.cancel()
        pending = appScope.launch {
            delay(delayMs)
            syncNow()
        }
    }

    suspend fun syncNow(): Boolean = mutex.withLock {
        if (store.session() == null) return false
        _status.value = SyncStatus.Running
        try {
            authorized { token ->
                val newFeeds = syncSubscriptions(token)
                pushStates(token)
                pullStates(token, newFeeds)
                syncQueue(token)
            }
            store.setLastSyncAt(System.currentTimeMillis())
            _status.value = SyncStatus.Idle
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _status.value = SyncStatus.Error(e.message ?: e.javaClass.simpleName)
            false
        }
    }

    // --- Accesso ---

    fun sendCode(email: String) {
        val address = email.trim()
        _login.value = LoginState.Sending
        appScope.launch {
            _login.value = runCatching { api.sendLoginEmail(address) }
                .fold({ LoginState.CodeSent(address) }, { LoginState.Error(it.message.orEmpty()) })
        }
    }

    fun verifyCode(email: String, code: String) {
        _login.value = LoginState.Verifying(email)
        appScope.launch {
            runCatching { api.verifyCode(email, code.filter(Char::isLetterOrDigit)) }
                .onSuccess { signedIn(it) }
                .onFailure { _login.value = LoginState.Error(it.message.orEmpty(), email) }
        }
    }

    fun signInWithPassword(email: String, password: String) {
        _login.value = LoginState.SigningIn
        appScope.launch {
            runCatching { api.signInWithPassword(email.trim(), password) }
                .onSuccess { signedIn(it) }
                .onFailure {
                    _login.value = LoginState.Error(it.message.orEmpty(), wrongPassword = it is SupabaseException && it.code == 400)
                }
        }
    }

    /** Imposta la password dell'account: poi si accede subito dagli altri dispositivi, senza email. */
    suspend fun setPassword(password: String): Result<Unit> = runCatching { authorized { api.setPassword(it, password) } }

    /** Link dell'email: myPodder://login#access_token=…&refresh_token=…&expires_in=… */
    fun handleLoginLink(uri: Uri) {
        val params = (uri.fragment ?: uri.query).orEmpty().split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to Uri.decode(it.substring(i + 1))
        }.toMap()
        val access = params["access_token"]
        val refresh = params["refresh_token"]
        if (access == null || refresh == null) {
            _login.value = LoginState.Error(params["error_description"]?.replace('+', ' ') ?: "Link non valido")
            return
        }
        _login.value = LoginState.Verifying("")
        appScope.launch {
            runCatching { api.sessionFromTokens(access, refresh, params["expires_in"]?.toLongOrNull() ?: 3600) }
                .onSuccess { signedIn(it) }
                .onFailure { _login.value = LoginState.Error(it.message.orEmpty()) }
        }
    }

    fun resetLogin() {
        _login.value = LoginState.Idle
    }

    private suspend fun signedIn(session: SyncSession) {
        store.clear()
        store.saveSession(session)
        _login.value = LoginState.Idle
        syncNow()
    }

    fun signOut() {
        appScope.launch {
            mutex.withLock {
                store.session()?.let { api.signOut(it.accessToken) }
                store.clear()
                _status.value = SyncStatus.Idle
            }
        }
    }

    /** Elimina account e dati sul server; quelli sul telefono restano. */
    suspend fun deleteAccount(): Boolean = mutex.withLock {
        runCatching { authorized { api.deleteAccount(it) } }
            .onSuccess {
                store.clear()
                _status.value = SyncStatus.Idle
            }
            .onFailure { _status.value = SyncStatus.Error(it.message.orEmpty()) }
            .isSuccess
    }

    /** Esegue [block] con un token valido, rinnovandolo se è scaduto o se il server lo rifiuta. */
    private suspend fun <T> authorized(block: suspend (String) -> T): T {
        var session = store.session() ?: error("Not signed in")
        if (session.expiresAt - System.currentTimeMillis() < 60_000) session = renew(session)
        return try {
            block(session.accessToken)
        } catch (e: SupabaseException) {
            if (e.code != 401) throw e
            block(renew(session).accessToken)
        }
    }

    private suspend fun renew(session: SyncSession): SyncSession = try {
        api.refresh(session.refreshToken, session.email).also { store.saveSession(it.copy(userId = it.userId.ifEmpty { session.userId })) }
    } catch (e: SupabaseException) {
        // Refresh token non più valido (es. account eliminato o sessione revocata): serve un nuovo accesso.
        if (e.code in 400..403) store.clear()
        throw e
    }

    private suspend fun userId(): String = store.session()?.userId.orEmpty()

    // --- Iscrizioni ---

    /** Restituisce i feed appena aggiunti su questo dispositivo perché presenti sul server. */
    private suspend fun syncSubscriptions(token: String): Set<String> {
        val local = podcastDao.getAll().associateBy { it.feedUrl }
        val remote = api.subscriptions(token).map { it.feedUrl }.toSet()
        val synced = store.syncedFeeds()
        val l = local.keys

        val addRemote = l - synced - remote
        val removeRemote = (synced - l).intersect(remote)
        val addLocal = remote - synced - l
        val removeLocal = synced.intersect(l) - remote

        val now = System.currentTimeMillis()
        val uid = userId()
        api.upsertSubscriptions(
            token,
            addRemote.map { url ->
                val p = local.getValue(url)
                RemoteSubscription(uid, url, p.title, p.imageUrl, now)
            },
        )
        removeRemote.forEach { api.deleteSubscription(token, it) }

        removeLocal.forEach { url -> local[url]?.let { podcasts.unsubscribe(it.id) } }
        val added = addLocal.filter { url -> runCatching { podcasts.subscribe(url) }.isSuccess }.toSet()

        val localFinal = l - removeLocal + added
        val remoteFinal = remote + addRemote - removeRemote
        store.setSyncedFeeds(localFinal.intersect(remoteFinal))
        return added
    }

    // --- Stato degli episodi ---

    private suspend fun pushStates(token: String) {
        val start = System.currentTimeMillis()
        var since = store.pushedUntil()
        val uid = userId()
        while (true) {
            val batch = episodeDao.changedSince(since, PAGE)
            if (batch.isEmpty()) break
            api.upsertEpisodeStates(
                token,
                batch.map {
                    RemoteEpisodeState(uid, it.feedUrl, it.guid, it.positionMs, it.completed, it.lastPlayedAt, it.stateUpdatedAt)
                },
            )
            since = batch.last().stateUpdatedAt
            // Mai oltre l'ora attuale: un orologio avanti su un altro dispositivo non deve far saltare modifiche.
            store.setPushedUntil(minOf(since, start))
            if (batch.size < PAGE) break
        }
    }

    private suspend fun pullStates(token: String, newFeeds: Set<String>) {
        val changed = mutableMapOf<Long, Long>()
        var since = (store.pulledUntil() - OVERLAP_MS).coerceAtLeast(0)
        var newest = store.pulledUntil()
        // Stati recenti di episodi che qui non ci sono ancora (feed non aggiornato): da riprovare.
        var heldBack: Long? = null
        val recent = System.currentTimeMillis() - HOLD_BACK_MS
        while (true) {
            val page = api.episodeStatesSince(token, since, PAGE)
            for (row in page) {
                if (!apply(row, changed) && row.updatedAt > recent && heldBack == null) heldBack = row.syncedAt
                newest = maxOf(newest, row.syncedAt)
            }
            if (page.size < PAGE) break
            since = page.last().syncedAt
        }
        store.setPulledUntil(heldBack?.let { minOf(it - 1, newest) } ?: newest)
        for (feed in newFeeds) api.episodeStatesFor(token, feed).forEach { apply(it, changed) }
        if (changed.isNotEmpty()) withContext(Dispatchers.Main) { player.onRemotePositions(changed) }
    }

    /** Applica uno stato remoto se più recente; false se l'episodio non esiste su questo dispositivo. */
    private suspend fun apply(row: RemoteEpisodeState, changed: MutableMap<Long, Long>): Boolean {
        val local = episodeDao.syncState(row.feedUrl, row.guid) ?: return false
        if (row.updatedAt > local.stateUpdatedAt) {
            episodeDao.applyRemoteState(local.id, row.positionMs, row.completed, row.lastPlayedAt, row.updatedAt)
            changed[local.id] = if (row.completed) 0 else row.positionMs
        }
        return true
    }

    // --- Coda ---

    private suspend fun syncQueue(token: String) {
        val remote = api.queue(token)
        val localAt = store.queueUpdatedAt()
        when {
            remote != null && remote.updatedAt > localAt -> {
                val ids = remote.items.mapNotNull { episodeDao.idFor(it.feedUrl, it.guid) }.distinct()
                queueDao.rewrite(ids)
                store.setQueueUpdatedAt(remote.updatedAt)
            }
            remote == null || localAt > remote.updatedAt -> {
                val at = localAt.takeIf { it > 0 } ?: System.currentTimeMillis()
                val items = episodeDao.queueKeys().map { QueueItem(it.feedUrl, it.guid) }
                api.upsertQueue(token, RemoteQueue(userId(), items, at))
                store.setQueueUpdatedAt(at)
            }
        }
    }

    private companion object {
        const val PAGE = 500
        /** Ripassa l'ultimo minuto: salvataggi concorrenti sul server possono arrivare fuori ordine. */
        const val OVERLAP_MS = 60_000L
        const val HOLD_BACK_MS = 2 * 24 * 60 * 60 * 1000L
    }
}
