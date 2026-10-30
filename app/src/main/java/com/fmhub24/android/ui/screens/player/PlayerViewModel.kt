package com.fmhub24.android.ui.screens.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.android.data.model.DataException
import com.fmhub24.android.data.model.EpisodeItem
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.model.MediaSource
import com.fmhub24.android.data.repo.CatalogRepository
import com.fmhub24.android.data.repo.SourcesRepository
import com.fmhub24.android.data.repo.SourceSelection
import com.fmhub24.android.data.repo.WatchStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Playback readiness: sources are read fresh, then the player is built (spec 5.2 rule 3). */
data class PlayerUiState(
    val item: MediaItem? = null,
    val episode: EpisodeItem? = null,
    val loading: Boolean = true,
    val source: MediaSource? = null,
    val alternatives: List<MediaSource> = emptyList(),
    val languages: List<String> = emptyList(),
    val languageKey: String? = null,
    val error: DataException? = null,
    val failure: com.fmhub24.android.stream.StreamError? = null,
    val retriedAfterExpiry: Boolean = false,
    val resumePositionMs: Long = 0L,
    val resumeDurationMs: Long = 0L,
    val nextEpisode: EpisodeItem? = null,
) {
    val isSeries: Boolean get() = episode != null
    val title: String get() = item?.title.orEmpty()
    val subtitle: String
        get() = episode?.let { "S${it.seasonNumber} E${it.episodeNumber}" }.orEmpty()
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val catalog: CatalogRepository,
    private val sources: SourcesRepository,
    private val watchState: WatchStateRepository,
) : ViewModel() {
    val contentId: String = savedState.get<String>("contentId").orEmpty()
    private val episodeKey: String? = savedState.get<String>("episodeKey").orEmpty()
        .takeIf { it.isNotEmpty() }

    private val state = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = state

    private val reportMessage = MutableStateFlow<String?>(null)
    val reportResult: StateFlow<String?> = reportMessage

    init {
        prepare()
    }

    fun prepare(languageKey: String? = null, serverLabel: String? = null) {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, failure = null, error = null)

            if (state.value.item == null) {
                catalog.ensureContent(contentId)
                catalog.ensureStructure(contentId)
            }
            val item = catalog.cachedContent(contentId)
            val episode = if (episodeKey.isNullOrEmpty()) {
                null
            } else {
                catalog.cachedEpisodes(contentId).firstOrNull { it.key == episodeKey }
            }
            if (item == null) {
                state.value = state.value.copy(
                    loading = false,
                    error = DataException.Malformed("content"),
                )
                return@launch
            }

            val history = watchState.historyFor(contentId, episode?.key)
            val next = episode?.let { catalog.cachedEpisodes(contentId).firstOrNull { candidate ->
                candidate.seasonNumber > episode.seasonNumber ||
                    (candidate.seasonNumber == episode.seasonNumber &&
                        candidate.episodeNumber > episode.episodeNumber)
            } }

            try {
                val found = sources.forTarget(contentId, episode)
                val languages = SourceSelection.languages(found)
                val chosenLanguage = languageKey ?: state.value.languageKey ?: languages.firstOrNull()
                val pool = chosenLanguage?.let { lang ->
                    found.filter { SourceSelection.matches(it, lang) }.ifEmpty { null }
                } ?: found
                val preferred = serverLabel?.let { label ->
                    pool.filter { it.serverLabel == label }.ifEmpty { null }
                }
                val chosen = SourceSelection.auto(
                    preferred ?: pool,
                    chosenLanguage,
                ) ?: (preferred ?: pool).firstOrNull()

                state.value = state.value.copy(
                    item = item,
                    episode = episode,
                    loading = false,
                    source = chosen,
                    alternatives = SourceSelection.serverRows(found, chosenLanguage),
                    languages = languages,
                    languageKey = chosenLanguage,
                    error = if (found.isEmpty()) DataException.Malformed("no source") else null,
                    resumePositionMs = history?.positionMs ?: 0L,
                    resumeDurationMs = history?.durationMs ?: 0L,
                    nextEpisode = next,
                    retriedAfterExpiry = false,
                )
            } catch (failure: DataException) {
                state.value = state.value.copy(
                    item = item,
                    episode = episode,
                    loading = false,
                    error = failure,
                    resumePositionMs = history?.positionMs ?: 0L,
                    resumeDurationMs = history?.durationMs ?: 0L,
                    nextEpisode = next,
                )
            } catch (failure: Exception) {
                state.value = state.value.copy(
                    item = item,
                    episode = episode,
                    loading = false,
                    error = DataException.Malformed(failure.message ?: "source"),
                    resumePositionMs = history?.positionMs ?: 0L,
                    resumeDurationMs = history?.durationMs ?: 0L,
                    nextEpisode = next,
                )
            }
        }
    }

    /** A stream failure: read one fresh row and retry, otherwise show the failure screen (7). */
    fun handlePlaybackError(error: com.fmhub24.android.stream.StreamError) {
        val current = state.value
        if (current.retriedAfterExpiry ||
            error != com.fmhub24.android.stream.StreamError.EXPIRED
        ) {
            state.value = current.copy(failure = error)
            return
        }
        state.value = current.copy(retriedAfterExpiry = true, failure = null)
        prepare(languageKey = current.languageKey, serverLabel = current.source?.serverLabel)
    }

    fun clearFailure() {
        state.value = state.value.copy(failure = null)
    }

    fun switchSource(serverLabel: String) {
        prepare(languageKey = state.value.languageKey, serverLabel = serverLabel)
    }

    fun switchLanguage(languageKey: String) {
        prepare(languageKey = languageKey, serverLabel = null)
    }

    fun recordProgress(positionMs: Long, durationMs: Long) {
        val current = state.value
        val item = current.item ?: return
        if (positionMs <= 0) return
        viewModelScope.launch {
            watchState.recordProgress(
                item = item,
                episode = current.episode,
                positionMs = positionMs,
                durationMs = durationMs,
            )
        }
    }

    /** Spec 6.10: reporting has no backend yet, so the app says so instead of pretending. */
    fun sendReport(reason: String) {
        reportMessage.value = reason
    }

    fun dismissReport() {
        reportMessage.value = null
    }

    fun shareText(): String {
        val current = state.value
        val title = current.item?.title ?: return ""
        return context.getString(com.fmhub24.android.R.string.share_text_title, title)
    }
}
