package com.fmhub24.android.ui.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.android.data.model.DataException
import com.fmhub24.android.data.model.EpisodeItem
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.model.MediaSource
import com.fmhub24.android.data.model.SeasonItem
import com.fmhub24.android.data.repo.CatalogRepository
import com.fmhub24.android.data.repo.SourcesRepository
import com.fmhub24.android.data.repo.SourceSelection
import com.fmhub24.android.data.repo.WatchStateRepository
import com.fmhub24.android.data.repo.HistoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the details screens are waiting for. */
data class DetailsUiState(
    val item: MediaItem? = null,
    val loading: Boolean = true,
    val error: DataException? = null,
    val seasons: List<SeasonItem> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList(),
    val inMyList: Boolean = false,
    val history: HistoryEntry? = null,
)

/** Fresh, resolved sources plus the chosen language/server (never cached). */
data class SourcesUiState(
    val loading: Boolean = false,
    val sources: List<MediaSource> = emptyList(),
    val languages: List<String> = emptyList(),
    val languageKey: String? = null,
    val serverLabel: String? = null,
    val error: DataException? = null,
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val sources: SourcesRepository,
    private val watchState: WatchStateRepository,
) : ViewModel() {
    val contentId: String = savedState.get<String>("contentId").orEmpty()

    private val base = MutableStateFlow(DetailsUiState())
    private val sourceFlow = MutableStateFlow(SourcesUiState())
    val sourceState: StateFlow<SourcesUiState>
        get() = sourceFlow

    /** Same content_type with an overlapping genre (spec 5.3). */
    val similar: StateFlow<List<MediaItem>> = combine(base, catalog.contents) { current, items ->
        val self = current.item ?: return@combine emptyList()
        items.filter { self.isSimilarTo(it) }.take(20)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<DetailsUiState> = combine(
        base,
        watchState.isInMyList(contentId),
        watchState.history,
    ) { current, inList, history ->
        current.copy(
            inMyList = inList,
            history = history.firstOrNull { entry -> entry.contentId == contentId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsUiState())

    val item: MediaItem? get() = base.value.item

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            base.value = base.value.copy(loading = true, error = null)

            val cached = catalog.cachedContent(contentId)
            base.value = base.value.copy(item = cached)

            val contentError = catalog.ensureContent(contentId)
            base.value = base.value.copy(item = catalog.cachedContent(contentId))

            var failure = contentError
            if (failure == null) {
                val isSeries = base.value.item?.usesEpisodes == true
                val structureError = catalog.ensureStructure(contentId)
                failure = structureError
                base.value = base.value.copy(
                    seasons = catalog.cachedSeasons(contentId),
                    episodes = catalog.cachedEpisodes(contentId),
                )
                if (structureError == null && isSeries &&
                    base.value.seasons.isEmpty() && base.value.episodes.isEmpty()
                ) {
                    catalog.reloadStructure(contentId)
                    base.value = base.value.copy(
                        seasons = catalog.cachedSeasons(contentId),
                        episodes = catalog.cachedEpisodes(contentId),
                    )
                }
            }

            base.value = base.value.copy(loading = false, error = failure)
            if (base.value.item?.usesEpisodes != true) loadSources()
        }
    }

    /** Reads a fresh media_sources row for the title or one episode (spec 5.2 rule 3). */
    fun loadSources(languageKey: String? = null, episode: EpisodeItem? = null) {
        val item = base.value.item ?: return
        viewModelScope.launch {
            sourceFlow.value = SourcesUiState(loading = true, languageKey = languageKey)
            try {
                val found = sources.forTarget(item.id, episode)
                val languages = SourceSelection.languages(found)
                val chosen = languageKey ?: languages.firstOrNull()
                sourceFlow.value = SourcesUiState(
                    loading = false,
                    sources = found,
                    languages = languages,
                    languageKey = chosen,
                    serverLabel = SourceSelection.auto(found, chosen)?.serverLabel,
                )
            } catch (failure: DataException) {
                sourceFlow.value = SourcesUiState(
                    loading = false,
                    error = failure,
                    languageKey = languageKey,
                )
            } catch (failure: Exception) {
                sourceFlow.value = SourcesUiState(
                    loading = false,
                    error = DataException.Malformed(failure.message ?: "sources"),
                    languageKey = languageKey,
                )
            }
        }
    }

    fun selectLanguage(languageKey: String) {
        val current = sourceFlow.value
        sourceFlow.value = current.copy(
            languageKey = languageKey,
            serverLabel = SourceSelection.auto(current.sources, languageKey)?.serverLabel,
        )
    }

    fun selectServer(source: MediaSource) {
        sourceFlow.value = sourceFlow.value.copy(serverLabel = source.serverLabel)
    }

    /** The source to play with, or null when there is no playable row. */
    fun chosenSource(languageKey: String? = null): MediaSource? {
        val current = sourceFlow.value
        val language = languageKey ?: current.languageKey
        val pool = current.sources
        val preferred = pool.filter { SourceSelection.matches(it, language ?: "Original") }
            .ifEmpty { pool }
        val byServer = preferred.filter { it.serverLabel == current.serverLabel }
            .ifEmpty { preferred }
        return SourceSelection.auto(byServer, language) ?: byServer.firstOrNull()
    }

    /** Every playable source of the chosen language: the failure screen's "other servers". */
    fun playableServers(languageKey: String? = null): List<MediaSource> {
        val current = sourceFlow.value
        return SourceSelection.serverRows(current.sources, languageKey ?: current.languageKey)
    }

    fun toggleMyList() {
        val current = base.value.item ?: return
        viewModelScope.launch { watchState.toggleMyList(current) }
    }

    fun episodesFor(seasonNumber: Int): List<EpisodeItem> =
        base.value.episodes.filter { it.seasonNumber == seasonNumber }

    fun firstEpisode(): EpisodeItem? = base.value.episodes.minByOrNull {
        it.seasonNumber * 10_000 + it.episodeNumber
    }

    fun nextEpisodeAfter(episode: EpisodeItem): EpisodeItem? =
        base.value.episodes
            .filter {
                it.seasonNumber > episode.seasonNumber ||
                    (it.seasonNumber == episode.seasonNumber && it.episodeNumber > episode.episodeNumber)
            }
            .minByOrNull { it.seasonNumber * 10_000 + it.episodeNumber }
}
