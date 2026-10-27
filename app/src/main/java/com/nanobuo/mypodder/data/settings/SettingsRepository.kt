package com.nanobuo.mypodder.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Come segnalare i nuovi episodi nella libreria. */
enum class NewBadgeStyle { COUNT, DOT, NONE }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val skipBackSec: Int = 10,
    val skipForwardSec: Int = 30,
    val speed: Float = 1f,
    val wifiOnly: Boolean = true,
    val deleteAfterPlayed: Boolean = false,
    val autoDownloadCount: Int = 3,
    val refreshHours: Int = 6,
    val addNewToQueue: Boolean = true,
    val shakeToExtend: Boolean = true,
    val sleepMinutes: Int = 30,
    val libraryGrid: Boolean = true,
    val showRemaining: Boolean = true,
    val newBadge: NewBadgeStyle = NewBadgeStyle.DOT,
    val coverColors: Boolean = false,
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheck: Long = 0,
    /** Paese per ricerca e classifiche (codice ISO); vuoto = automatico. */
    val searchCountry: String = "",
    val showTopCharts: Boolean = true,
    val onboardingDone: Boolean = false,
    val interests: Set<String> = emptySet(),
    /** Ultimo aggiornamento completo dei feed (manuale, automatico o all'apertura). */
    val lastFullRefresh: Long = 0,
    /** Ultima volta che l'app è stata aperta: base per contare gli episodi usciti nel frattempo. */
    val lastVisit: Long = 0,
    /** Cartella scelta sulla microSD (URI dell'albero); vuoto = memoria del telefono. */
    val downloadTreeUri: String = "",
    /** Ultima attività del player su questo dispositivo (ascolto, pausa, cambio episodio). */
    val lastPlayerActivity: Long = 0,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    appScope: CoroutineScope,
) {
    private object Keys {
        val theme = intPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val skipBack = intPreferencesKey("skip_back")
        val skipForward = intPreferencesKey("skip_forward")
        val speed = floatPreferencesKey("speed")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val deleteAfterPlayed = booleanPreferencesKey("delete_after_played")
        val autoDownloadCount = intPreferencesKey("auto_download_count")
        val refreshHours = intPreferencesKey("refresh_hours")
        val addNewToQueue = booleanPreferencesKey("add_new_to_queue")
        val shakeToExtend = booleanPreferencesKey("shake_to_extend")
        val sleepMinutes = intPreferencesKey("sleep_minutes")
        val lastEpisode = longPreferencesKey("last_episode")
        val libraryGrid = booleanPreferencesKey("library_grid")
        val showRemaining = booleanPreferencesKey("show_remaining")
        val newBadge = intPreferencesKey("new_badge")
        val coverColors = booleanPreferencesKey("cover_colors")
        val autoCheckUpdates = booleanPreferencesKey("auto_check_updates")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val searchCountry = stringPreferencesKey("search_country")
        val showTopCharts = booleanPreferencesKey("show_top_charts")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val interests = stringSetPreferencesKey("interests")
        val lastFullRefresh = longPreferencesKey("last_full_refresh")
        val lastVisit = longPreferencesKey("last_visit")
        val downloadTreeUri = stringPreferencesKey("download_tree_uri")
        val lastPlayerActivity = longPreferencesKey("last_player_activity")
    }

    private val defaults = AppSettings()

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = ThemeMode.entries.getOrElse(p[Keys.theme] ?: 0) { ThemeMode.SYSTEM },
            dynamicColor = p[Keys.dynamicColor] ?: defaults.dynamicColor,
            skipBackSec = p[Keys.skipBack] ?: defaults.skipBackSec,
            skipForwardSec = p[Keys.skipForward] ?: defaults.skipForwardSec,
            speed = p[Keys.speed] ?: defaults.speed,
            wifiOnly = p[Keys.wifiOnly] ?: defaults.wifiOnly,
            deleteAfterPlayed = p[Keys.deleteAfterPlayed] ?: defaults.deleteAfterPlayed,
            autoDownloadCount = p[Keys.autoDownloadCount] ?: defaults.autoDownloadCount,
            refreshHours = p[Keys.refreshHours] ?: defaults.refreshHours,
            addNewToQueue = p[Keys.addNewToQueue] ?: defaults.addNewToQueue,
            shakeToExtend = p[Keys.shakeToExtend] ?: defaults.shakeToExtend,
            sleepMinutes = p[Keys.sleepMinutes] ?: defaults.sleepMinutes,
            libraryGrid = p[Keys.libraryGrid] ?: defaults.libraryGrid,
            showRemaining = p[Keys.showRemaining] ?: defaults.showRemaining,
            coverColors = p[Keys.coverColors] ?: defaults.coverColors,
            autoCheckUpdates = p[Keys.autoCheckUpdates] ?: defaults.autoCheckUpdates,
            lastUpdateCheck = p[Keys.lastUpdateCheck] ?: defaults.lastUpdateCheck,
            searchCountry = p[Keys.searchCountry] ?: defaults.searchCountry,
            showTopCharts = p[Keys.showTopCharts] ?: defaults.showTopCharts,
            onboardingDone = p[Keys.onboardingDone] ?: defaults.onboardingDone,
            interests = p[Keys.interests] ?: defaults.interests,
            lastFullRefresh = p[Keys.lastFullRefresh] ?: defaults.lastFullRefresh,
            lastVisit = p[Keys.lastVisit] ?: defaults.lastVisit,
            downloadTreeUri = p[Keys.downloadTreeUri] ?: defaults.downloadTreeUri,
            lastPlayerActivity = p[Keys.lastPlayerActivity] ?: defaults.lastPlayerActivity,
            newBadge = p[Keys.newBadge]?.let { NewBadgeStyle.entries.getOrNull(it) } ?: defaults.newBadge,
        )
    }

    /** Valore sempre disponibile in modo sincrono (es. per il player). */
    val state: StateFlow<AppSettings> = flow.stateIn(appScope, SharingStarted.Eagerly, defaults)

    suspend fun current(): AppSettings = flow.first()

    val lastEpisodeId: Flow<Long?> = context.dataStore.data.map { it[Keys.lastEpisode] }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setTheme(mode: ThemeMode) = set(Keys.theme, mode.ordinal)
    suspend fun setDynamicColor(v: Boolean) = set(Keys.dynamicColor, v)
    suspend fun setSkipBack(v: Int) = set(Keys.skipBack, v)
    suspend fun setSkipForward(v: Int) = set(Keys.skipForward, v)
    suspend fun setSpeed(v: Float) = set(Keys.speed, v)
    suspend fun setWifiOnly(v: Boolean) = set(Keys.wifiOnly, v)
    suspend fun setDeleteAfterPlayed(v: Boolean) = set(Keys.deleteAfterPlayed, v)
    suspend fun setAutoDownloadCount(v: Int) = set(Keys.autoDownloadCount, v)
    suspend fun setRefreshHours(v: Int) = set(Keys.refreshHours, v)
    suspend fun setAddNewToQueue(v: Boolean) = set(Keys.addNewToQueue, v)
    suspend fun setShakeToExtend(v: Boolean) = set(Keys.shakeToExtend, v)
    suspend fun setSleepMinutes(v: Int) = set(Keys.sleepMinutes, v)
    suspend fun setLastEpisode(id: Long) = set(Keys.lastEpisode, id)
    suspend fun setLibraryGrid(v: Boolean) = set(Keys.libraryGrid, v)
    suspend fun setShowRemaining(v: Boolean) = set(Keys.showRemaining, v)
    suspend fun setNewBadge(v: NewBadgeStyle) = set(Keys.newBadge, v.ordinal)
    suspend fun setCoverColors(v: Boolean) = set(Keys.coverColors, v)
    suspend fun setAutoCheckUpdates(v: Boolean) = set(Keys.autoCheckUpdates, v)
    suspend fun setLastUpdateCheck(v: Long) = set(Keys.lastUpdateCheck, v)
    suspend fun setSearchCountry(v: String) = set(Keys.searchCountry, v)
    suspend fun setShowTopCharts(v: Boolean) = set(Keys.showTopCharts, v)
    suspend fun setOnboardingDone(v: Boolean) = set(Keys.onboardingDone, v)
    suspend fun setInterests(v: Set<String>) = set(Keys.interests, v)
    suspend fun setLastFullRefresh(v: Long) = set(Keys.lastFullRefresh, v)
    suspend fun setLastVisit(v: Long) = set(Keys.lastVisit, v)
    suspend fun setDownloadTreeUri(v: String) = set(Keys.downloadTreeUri, v)
    suspend fun setLastPlayerActivity(v: Long) = set(Keys.lastPlayerActivity, v)
}
