package com.nanobuo.mypodder.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nanobuo.mypodder.R
import com.nanobuo.mypodder.data.discover.Countries
import com.nanobuo.mypodder.data.discover.DiscoverRepository
import com.nanobuo.mypodder.data.discover.Topics
import com.nanobuo.mypodder.data.repo.PodcastRepository
import com.nanobuo.mypodder.data.search.SearchResult
import com.nanobuo.mypodder.data.settings.SettingsRepository
import com.nanobuo.mypodder.ui.components.Artwork
import com.nanobuo.mypodder.ui.components.Wordmark
import com.nanobuo.mypodder.ui.search.CountryPickerDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val discover: DiscoverRepository,
    private val podcasts: PodcastRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {
    val country: StateFlow<String> = settings.flow.map { it.searchCountry.ifBlank { Countries.default() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Countries.default())
    val countryIsAutomatic: StateFlow<Boolean> = settings.flow.map { it.searchCountry.isBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _topics = MutableStateFlow<Set<String>>(emptySet())
    val topics = _topics.asStateFlow()

    /** null = scelta degli argomenti; lista = proposte (vuota se non trovate). */
    private val _suggestions = MutableStateFlow<List<SearchResult>?>(null)
    val suggestions = _suggestions.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _chosen = MutableStateFlow<Set<String>>(emptySet())
    val chosen = _chosen.asStateFlow()

    fun toggleTopic(category: String) {
        _topics.value = _topics.value.let { if (category in it) it - category else it + category }
    }

    fun toggleChosen(feedUrl: String) {
        _chosen.value = _chosen.value.let { if (feedUrl in it) it - feedUrl else it + feedUrl }
    }

    fun setCountry(code: String) {
        viewModelScope.launch { settings.setSearchCountry(code) }
    }

    fun next() {
        viewModelScope.launch {
            _loading.value = true
            settings.setInterests(_topics.value)
            _suggestions.value = runCatching { discover.forTopics(_topics.value, country.value, limit = 24) }
                .getOrDefault(emptyList())
            _loading.value = false
        }
    }

    fun back() {
        _suggestions.value = null
    }

    /** Si iscrive ai podcast scelti (in background) e chiude il benvenuto. */
    fun finish(onDone: () -> Unit) {
        val feeds = _chosen.value.toList()
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            onDone()
        }
        if (feeds.isNotEmpty()) {
            appScope.launch {
                val gate = Semaphore(4)
                feeds.map { url -> async { gate.withPermit { runCatching { podcasts.subscribe(url) } } } }.awaitAll()
            }
        }
    }

    fun skip(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            onDone()
        }
    }
}

/** Primo avvio: scelta degli argomenti e proposta di podcast locali. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val chosen by viewModel.chosen.collectAsStateWithLifecycle()
    val country by viewModel.country.collectAsStateWithLifecycle()
    val automatic by viewModel.countryIsAutomatic.collectAsStateWithLifecycle()
    var pickCountry by remember { mutableStateOf(false) }
    val countryInfo = remember(country) { Countries.of(country) }

    BackHandler { if (suggestions != null) viewModel.back() else viewModel.skip(onDone) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark()
                Spacer(Modifier.weight(1f))
                AssistChip(
                    onClick = { pickCountry = true },
                    label = { Text(countryInfo.flag + "  " + countryInfo.name, maxLines = 1) },
                )
            }
            AnimatedContent(targetState = suggestions, label = "onboarding", modifier = Modifier.weight(1f)) { list ->
                if (list == null) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            stringResource(R.string.onboarding_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Topics.forEach { topic ->
                                val selected = topic.category in topics
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.toggleTopic(topic.category) },
                                    label = { Text(stringResource(topic.label)) },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Text(stringResource(R.string.onboarding_suggestions_title), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            stringResource(if (list.isEmpty()) R.string.onboarding_empty else R.string.onboarding_suggestions_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                        )
                        LazyColumn(Modifier.weight(1f)) {
                            items(list, key = { it.feedUrl }) { r ->
                                val checked = r.feedUrl in chosen
                                ListItem(
                                    headlineContent = { Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = if (r.author != null) {
                                        { Text(r.author, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    } else {
                                        null
                                    },
                                    leadingContent = { Artwork(r.imageUrl, size = 56.dp) },
                                    trailingContent = { Checkbox(checked = checked, onCheckedChange = { viewModel.toggleChosen(r.feedUrl) }) },
                                    modifier = Modifier.clickable { viewModel.toggleChosen(r.feedUrl) },
                                )
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (suggestions != null) viewModel.back() else viewModel.skip(onDone) }) {
                    Text(stringResource(if (suggestions != null) R.string.back else R.string.onboarding_skip))
                }
                Spacer(Modifier.weight(1f))
                when {
                    loading -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    suggestions == null -> Button(onClick = viewModel::next, enabled = topics.isNotEmpty()) {
                        Text(stringResource(R.string.onboarding_next))
                    }
                    else -> Button(onClick = { viewModel.finish(onDone) }) {
                        Text(
                            if (chosen.isEmpty()) stringResource(R.string.onboarding_done)
                            else stringResource(R.string.onboarding_subscribe, chosen.size)
                        )
                    }
                }
            }
        }
    }

    if (pickCountry) {
        CountryPickerDialog(
            selected = country,
            automatic = automatic,
            onSelect = {
                viewModel.setCountry(it)
                pickCountry = false
            },
            onDismiss = { pickCountry = false },
        )
    }
}
