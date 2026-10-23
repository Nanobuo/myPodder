package com.nanobuo.mypodder.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object LibraryRoute
@Serializable data object QueueRoute
@Serializable data object SearchRoute
@Serializable data object DownloadsRoute
@Serializable data object StatsRoute
@Serializable data object SettingsRoute
@Serializable data object PlayerRoute
@Serializable data object HistoryRoute
@Serializable data object OnboardingRoute
@Serializable data class PodcastRoute(val id: Long)
@Serializable data class EpisodeRoute(val id: Long)
@Serializable data class PreviewRoute(val feedUrl: String, val title: String? = null, val imageUrl: String? = null)
