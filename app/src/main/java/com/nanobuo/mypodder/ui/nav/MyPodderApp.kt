package com.nanobuo.mypodder.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.ui.MainViewModel
import com.nanobuo.mypodder.ui.components.LocalEpisodeActions
import com.nanobuo.mypodder.ui.components.UpdateDialog
import com.nanobuo.mypodder.ui.components.LocalPlaybackHighlight
import com.nanobuo.mypodder.ui.components.PlaybackHighlight
import com.nanobuo.mypodder.ui.downloads.DownloadsScreen
import com.nanobuo.mypodder.ui.episode.EpisodeScreen
import com.nanobuo.mypodder.ui.history.HistoryScreen
import com.nanobuo.mypodder.ui.onboarding.OnboardingScreen
import com.nanobuo.mypodder.ui.library.LibraryScreen
import com.nanobuo.mypodder.ui.player.MiniPlayer
import com.nanobuo.mypodder.ui.player.PlayerScreen
import com.nanobuo.mypodder.ui.podcast.PodcastScreen
import com.nanobuo.mypodder.ui.queue.QueueScreen
import com.nanobuo.mypodder.ui.search.PreviewScreen
import com.nanobuo.mypodder.ui.search.SearchScreen
import com.nanobuo.mypodder.ui.settings.SettingsScreen
import com.nanobuo.mypodder.ui.stats.StatsScreen
import kotlin.reflect.KClass

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private data class TopLevel(val route: Any, val routeClass: KClass<*>, val icon: ImageVector, val label: Int)

private val topLevels = listOf(
    TopLevel(LibraryRoute, LibraryRoute::class, Icons.Rounded.Podcasts, R.string.nav_library),
    TopLevel(SearchRoute, SearchRoute::class, Icons.Rounded.Search, R.string.nav_search),
    TopLevel(DownloadsRoute, DownloadsRoute::class, Icons.Rounded.Download, R.string.nav_downloads),
    TopLevel(StatsRoute, StatsRoute::class, Icons.Rounded.BarChart, R.string.nav_stats),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyPodderApp(viewModel: MainViewModel, openPlayerRequests: Int) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val queueIds by viewModel.queueIds.collectAsStateWithLifecycle()

    val onPlayer = destination?.hasRoute(PlayerRoute::class) == true
    val isTopLevel = topLevels.any { t -> destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true }

    LaunchedEffect(openPlayerRequests) {
        if (openPlayerRequests > 0 && !onPlayer) nav.navigate(PlayerRoute) { launchSingleTop = true }
    }

    val tabReselect = remember { MutableSharedFlow<KClass<*>>(extraBufferCapacity = 1) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var openLatestRequests by remember { mutableIntStateOf(0) }
    val newAtStart by viewModel.newEpisodesAtStart.collectAsStateWithLifecycle()
    LaunchedEffect(newAtStart) {
        val count = newAtStart ?: return@LaunchedEffect
        // Lo stato va azzerato solo DOPO aver mostrato il messaggio: azzerarlo prima cambierebbe la
        // chiave di questo effetto, che verrebbe annullato prima di far comparire lo snackbar.
        val result = try {
            snackbar.showSnackbar(
                context.resources.getQuantityString(R.plurals.new_episodes_available, count, count),
                actionLabel = context.getString(R.string.show),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
        } finally {
            viewModel.consumeNewEpisodesAtStart()
        }
        if (result == SnackbarResult.ActionPerformed) {
            nav.navigate(LibraryRoute) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
            openLatestRequests++
        }
    }
    val needsOnboarding by viewModel.needsOnboarding.collectAsStateWithLifecycle()
    LaunchedEffect(needsOnboarding) {
        if (needsOnboarding && nav.currentDestination?.hasRoute(OnboardingRoute::class) != true) {
            nav.navigate(OnboardingRoute) { launchSingleTop = true }
        }
    }
    val updateState by viewModel.updates.state.collectAsStateWithLifecycle()
    UpdateDialog(
        state = updateState,
        onUpdate = viewModel.updates::downloadAndInstall,
        onDismiss = viewModel.updates::dismiss,
    )

    CompositionLocalProvider(
        LocalTabReselect provides tabReselect,
        LocalEpisodeActions provides viewModel,
        LocalPlaybackHighlight provides PlaybackHighlight(nowPlaying.episodeId, nowPlaying.isPlaying, queueIds),
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = nowPlaying.hasMedia && !onPlayer,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        MiniPlayer(
                            state = nowPlaying,
                            connection = viewModel.player,
                            onOpen = { nav.navigate(PlayerRoute) { launchSingleTop = true } },
                        )
                    }
                    // Senza barra di navigazione il mini-player resta sopra la barra dei gesti di sistema.
                    if (nowPlaying.hasMedia && !onPlayer && !isTopLevel) {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .navigationBarsPadding()
                                .height(12.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = isTopLevel,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        ShortNavigationBar(arrangement = ShortNavigationBarArrangement.Centered) {
                            topLevels.forEach { t ->
                                val selected = destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true
                                ShortNavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (selected) {
                                            tabReselect.tryEmit(t.routeClass)
                                            return@ShortNavigationBarItem
                                        }
                                        nav.navigate(t.route) {
                                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(t.icon, null) },
                                    label = { Text(stringResource(t.label), maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp)) },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = LibraryRoute,
                // Transizioni brevi in stile "fade through": niente dissolvenze lunghe o scatti.
                enterTransition = {
                    if (targetState.destination.hasRoute(PlayerRoute::class)) {
                        slideInVertically(tween(380, easing = EmphasizedDecelerate)) { it }
                    } else {
                        fadeIn(tween(210, delayMillis = 70, easing = LinearOutSlowInEasing)) +
                            scaleIn(tween(280, easing = EmphasizedDecelerate), initialScale = 0.96f)
                    }
                },
                exitTransition = {
                    // Sotto il player la schermata resta ferma mentre il player sale.
                    if (targetState.destination.hasRoute(PlayerRoute::class)) ExitTransition.KeepUntilTransitionsFinished
                    else fadeOut(tween(90, easing = FastOutLinearInEasing))
                },
                popEnterTransition = {
                    if (initialState.destination.hasRoute(PlayerRoute::class)) EnterTransition.None
                    else fadeIn(tween(210, delayMillis = 70, easing = LinearOutSlowInEasing)) +
                        scaleIn(tween(280, easing = EmphasizedDecelerate), initialScale = 0.96f)
                },
                popExitTransition = {
                    if (initialState.destination.hasRoute(PlayerRoute::class)) {
                        slideOutVertically(tween(300, easing = EmphasizedAccelerate)) { it }
                    } else {
                        fadeOut(tween(90, easing = FastOutLinearInEasing))
                    }
                },
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            ) {
                composable<LibraryRoute> {
                    LibraryScreen(
                        openLatestRequests = openLatestRequests,
                        onPodcast = { nav.navigate(PodcastRoute(it)) },
                        onEpisode = { nav.navigate(EpisodeRoute(it)) },
                        onSearch = { nav.navigate(SearchRoute) },
                        onSettings = { nav.navigate(SettingsRoute) },
                        onHistory = { nav.navigate(HistoryRoute) },
                    )
                }
                composable<OnboardingRoute> {
                    OnboardingScreen(onDone = {
                        nav.navigate(LibraryRoute) {
                            popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
                            launchSingleTop = true
                        }
                    })
                }
                composable<HistoryRoute> {
                    HistoryScreen(
                        onBack = { nav.popBackStack() },
                        onEpisode = { nav.navigate(EpisodeRoute(it)) },
                    )
                }
                composable<QueueRoute> {
                    QueueScreen(
                        onBack = { nav.popBackStack() },
                        onEpisode = { nav.navigate(EpisodeRoute(it)) },
                    )
                }
                composable<SearchRoute> {
                    SearchScreen(onPreview = { url, title, image -> nav.navigate(PreviewRoute(url, title, image)) })
                }
                composable<DownloadsRoute> {
                    DownloadsScreen(onEpisode = { nav.navigate(EpisodeRoute(it)) })
                }
                composable<StatsRoute> {
                    StatsScreen(onPodcast = { nav.navigate(PodcastRoute(it)) })
                }
                composable<SettingsRoute> {
                    SettingsScreen(onBack = { nav.popBackStack() })
                }
                composable<PodcastRoute> {
                    PodcastScreen(
                        onBack = { nav.popBackStack() },
                        onEpisode = { nav.navigate(EpisodeRoute(it)) },
                    )
                }
                composable<EpisodeRoute> {
                    EpisodeScreen(
                        onBack = { nav.popBackStack() },
                        onPodcast = { nav.navigate(PodcastRoute(it)) },
                    )
                }
                composable<PreviewRoute> { entry ->
                    val route = entry.toRoute<PreviewRoute>()
                    PreviewScreen(
                        initialTitle = route.title,
                        initialImage = route.imageUrl,
                        onBack = { nav.popBackStack() },
                        onSubscribed = { id ->
                            nav.navigate(PodcastRoute(id)) { popUpTo<LibraryRoute> { inclusive = false } }
                        },
                    )
                }
                composable<PlayerRoute> {
                    PlayerScreen(
                        onClose = { nav.popBackStack() },
                        // Uscendo dal player verso un'altra pagina, il player lascia la cronologia:
                        // Indietro torna alla pagina sotto il player, che si riapre solo dal mini-player.
                        onOpenQueue = {
                            nav.navigate(QueueRoute) {
                                popUpTo<PlayerRoute> { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onEpisode = {
                            nav.navigate(EpisodeRoute(it)) { popUpTo<PlayerRoute> { inclusive = true } }
                        },
                    )
                }
            }
        }
    }
}
