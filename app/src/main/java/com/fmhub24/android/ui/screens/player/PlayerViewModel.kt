package com.fmhub24.android.ui.screens.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.android.data.UserPreferencesRepository
import com.fmhub24.android.data.model.CodecSupport
import com.fmhub24.android.data.model.DataException
import com.fmhub24.android.data.model.ContentType
import com.fmhub24.android.data.model.EpisodeItem
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.model.MediaSource
import com.fmhub24.android.data.repo.CatalogRepository
import com.fmhub24.android.data.repo.DownloadsRepository
import com.fmhub24.android.data.repo.SourcesRepository
import com.fmhub24.android.data.repo.SourceSelection
import com.fmhub24.android.data.repo.WatchStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    val episodes: List<EpisodeItem> = emptyList(),
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
    private val preferences: UserPreferencesRepository,
    private val downloads: DownloadsRepository,
) : ViewModel() {
    val playbackSpeed = preferences.settings.map { it.playbackSpeed }
    val contentId: String = savedState.get<String>("contentId").orEmpty()
    private val episodeKey: String? = savedState.get<String>("episodeKey").orEmpty()
        .takeIf { it.isNotEmpty() }

    private val state = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = state
    private val attemptedSources = mutableSetOf<String>()
    private var handlingPlaybackError = false

    private val reportMessage = MutableStateFlow<String?>(null)
    val reportResult: StateFlow<String?> = reportMessage

    init {
        prepare()
    }

    fun prepare(languageKey: String? = null, serverLabel: String? = null) {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, failure = null, error = null)

            val storedDownload = downloads.recordFor(contentId, episodeKey)
            if (state.value.item == null && storedDownload == null) {
                catalog.ensureContent(contentId)
                val cachedItem = catalog.cachedContent(contentId)
                if (cachedItem?.usesEpisodes == true && catalog.cachedEpisodes(contentId).isEmpty()) {
                    catalog.ensureStructure(contentId)
                }
            }
            val item = catalog.cachedContent(contentId) ?: storedDownload?.let { stored ->
                MediaItem(
                    id = contentId,
                    canonicalKey = null,
                    title = if (episodeKey == null) stored.title else stored.title.substringBefore(" · S"),
                    originalTitle = null,
                    contentType = ContentType.from(stored.contentType),
                    releaseYear = null,
                    posterUrl = stored.posterUrl,
                    description = null,
                    genres = emptyList(),
                    status = null,
                    rating = null,
                    createdAt = null,
                    updatedAt = null,
                )
            }
            val episode = if (episodeKey.isNullOrEmpty()) {
                null
            } else {
                catalog.cachedEpisodes(contentId).firstOrNull { it.key == episodeKey }
                    ?: storedDownload?.let { stored ->
                        val parts = episodeKey.split('|')
                        EpisodeItem(
                            id = null,
                            contentId = contentId,
                            seasonNumber = stored.seasonNumber ?: parts.getOrNull(1)?.toIntOrNull() ?: 0,
                            episodeNumber = stored.episodeNumber ?: parts.getOrNull(2)?.toIntOrNull() ?: 0,
                            title = null,
                            description = null,
                            posterUrl = stored.posterUrl,
                            sortOrder = null,
                        )
                    }
            }
            if (item == null) {
                state.value = state.value.copy(
                    loading = false,
                    error = DataException.Malformed("content"),
                )
                return@launch
            }

            val cachedEpisodes = catalog.cachedEpisodes(contentId)
            val episodes = if (cachedEpisodes.isNotEmpty()) {
                cachedEpisodes
            } else if (item.usesEpisodes) {
                downloads.records()
                    .filter { it.contentId == contentId && it.episodeKey != null }
                    .mapNotNull { record ->
                        val key = record.episodeKey ?: return@mapNotNull null
                        val parts = key.split('|')
                        val seasonNumber = record.seasonNumber
                            ?: parts.getOrNull(1)?.toIntOrNull() ?: 0
                        val episodeNumber = record.episodeNumber
                            ?: parts.getOrNull(2)?.toIntOrNull() ?: 0
                        val marker = "S${seasonNumber}E${episodeNumber}"
                        val title = record.title.substringAfter(" · $marker", "")
                            .removePrefix(" · ").takeIf { it.isNotBlank() }
                        EpisodeItem(
                            id = null,
                            contentId = contentId,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            title = title,
                            description = null,
                            posterUrl = record.posterUrl,
                            sortOrder = null,
                        )
                    }
                    .distinctBy { it.key }
                    .sortedWith(compareBy<EpisodeItem> { it.seasonNumber }.thenBy { it.episodeNumber })
            } else emptyList()
            val playlist = if (episode != null && episodes.none { it.key == episode.key }) {
                (episodes + episode)
                    .sortedWith(compareBy<EpisodeItem> { it.seasonNumber }.thenBy { it.episodeNumber })
            } else episodes
            val history = watchState.historyFor(contentId, episode?.key)
            val next = episode?.let { current -> playlist.firstOrNull { candidate ->
                candidate.seasonNumber > current.seasonNumber ||
                    (candidate.seasonNumber == current.seasonNumber &&
                        candidate.episodeNumber > current.episodeNumber)
            } }

            try {
                val offline = downloads.offlineSource(contentId, episode?.key)
                if (offline != null) {
                    state.value = state.value.copy(
                        item = item,
                        episode = episode,
                        loading = false,
                        source = offline,
                        alternatives = listOf(offline),
                        languages = listOf(offline.language ?: "Original"),
                        languageKey = offline.language ?: "Original",
                        error = null,
                        failure = null,
                        resumePositionMs = history?.positionMs ?: 0L,
                        resumeDurationMs = history?.durationMs ?: 0L,
                        episodes = playlist,
                        nextEpisode = next,
                        retriedAfterExpiry = false,
                    )
                    return@launch
                }
                val found = sources.forTarget(contentId, episode)
                val languages = SourceSelection.languages(found)
                val preferredAudio = preferences.settings.first().preferredAudio
                val chosenLanguage = languageKey ?: state.value.languageKey
                    ?: preferredAudio?.let { preferred ->
                        languages.firstOrNull { it.equals(preferred, ignoreCase = true) }
                    } ?: languages.firstOrNull()
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
                    episodes = playlist,
                    nextEpisode = next,
                    retriedAfterExpiry = false,
                )
                attemptedSources.clear()
                chosen?.let { attemptedSources += sourceFamily(it) }
            } catch (failure: DataException) {
                state.value = state.value.copy(
                    item = item,
                    episode = episode,
                    loading = false,
                    error = failure,
                    resumePositionMs = history?.positionMs ?: 0L,
                    resumeDurationMs = history?.durationMs ?: 0L,
                    episodes = playlist,
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
                    episodes = playlist,
                    nextEpisode = next,
                )
            }
        }
    }

    /** Silently try other server/quality rows first; surface an error only when all fail. */
    fun handlePlaybackError(
        error: com.fmhub24.android.stream.StreamError,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
    ) {
        val current = state.value
        val failedSource = current.source
        if (failedSource == null || failedSource.status == "offline" || handlingPlaybackError) {
            state.value = current.copy(failure = error)
            return
        }
        handlingPlaybackError = true
        attemptedSources += sourceFamily(failedSource)
        if (positionMs > 0L) recordProgress(positionMs, durationMs)
        state.value = current.copy(
            loading = true,
            failure = null,
            resumePositionMs = positionMs.coerceAtLeast(0L),
            resumeDurationMs = durationMs.coerceAtLeast(0L),
        )
        viewModelScope.launch {
            try {
                val fresh = runCatching {
                    sources.forTarget(current.item?.id ?: contentId, current.episode)
                }.getOrDefault(emptyList())
                val alternatives = SourceSelection.serverRows(
                    fresh + current.alternatives,
                    current.languageKey,
                )
                val supported = alternatives.filter { CodecSupport.isSupported(it.codec) }
                val pool = (supported.ifEmpty { alternatives })
                    .filterNot { sourceFamily(it) in attemptedSources }
                val next = pool.sortedWith(
                    compareBy<MediaSource> {
                        when {
                            it.serverLabel != failedSource.serverLabel -> 0
                            it.quality != failedSource.quality -> 1
                            else -> 2
                        }
                    }.thenByDescending { it.quality ?: 0 },
                ).firstOrNull()
                if (next == null) {
                    state.value = state.value.copy(
                        loading = false,
                        failure = error,
                        alternatives = alternatives,
                    )
                } else {
                    attemptedSources += sourceFamily(next)
                    state.value = state.value.copy(
                        loading = false,
                        failure = null,
                        error = null,
                        source = next,
                        alternatives = alternatives,
                        retriedAfterExpiry = current.retriedAfterExpiry ||
                            error == com.fmhub24.android.stream.StreamError.EXPIRED,
                    )
                }
            } finally {
                handlingPlaybackError = false
            }
        }
    }

    private fun sourceFamily(source: MediaSource): String =
        "${source.serverLabel}|${source.quality}|${source.codec}|${source.language}"

    fun clearFailure() {
        state.value = state.value.copy(failure = null)
    }

    fun savePlaybackSpeed(speed: Float) {
        viewModelScope.launch { preferences.savePlaybackSpeed(speed) }
    }

    fun switchSource(source: MediaSource, positionMs: Long, durationMs: Long) {
        attemptedSources.clear()
        attemptedSources += sourceFamily(source)
        if (positionMs > 0L) recordProgress(positionMs, durationMs)
        state.value = state.value.copy(
            source = source,
            languageKey = SourceSelection.keyOf(source),
            loading = false,
            error = null,
            failure = null,
            resumePositionMs = positionMs.coerceAtLeast(0L),
            resumeDurationMs = durationMs.coerceAtLeast(0L),
        )
    }

    fun switchLanguage(languageKey: String, positionMs: Long, durationMs: Long) {
        attemptedSources.clear()
        val current = state.value
        if (positionMs > 0L) recordProgress(positionMs, durationMs)
        state.value = current.copy(
            loading = true,
            error = null,
            failure = null,
            resumePositionMs = positionMs.coerceAtLeast(0L),
            resumeDurationMs = durationMs.coerceAtLeast(0L),
        )
        viewModelScope.launch {
            try {
                val found = sources.forTarget(current.item?.id ?: contentId, current.episode)
                val pool = SourceSelection.forLanguage(found, languageKey)
                val chosen = SourceSelection.auto(pool, languageKey) ?: pool.firstOrNull()
                state.value = current.copy(
                    loading = false,
                    source = chosen,
                    alternatives = SourceSelection.serverRows(found, languageKey),
                    languages = SourceSelection.languages(found),
                    languageKey = languageKey,
                    error = if (chosen == null) DataException.Malformed("no source") else null,
                    failure = null,
                    resumePositionMs = positionMs.coerceAtLeast(0L),
                    resumeDurationMs = durationMs.coerceAtLeast(0L),
                )
                chosen?.let { attemptedSources += sourceFamily(it) }
            } catch (failure: Exception) {
                state.value = current.copy(
                    loading = false,
                    error = if (failure is DataException) failure
                    else DataException.Malformed(failure.message ?: "source"),
                    resumePositionMs = positionMs.coerceAtLeast(0L),
                    resumeDurationMs = durationMs.coerceAtLeast(0L),
                )
            }
        }
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

    fun onPlaybackCompleted(durationMs: Long) {
        recordProgress(durationMs, durationMs)
        val current = state.value
        val item = current.item ?: return
        viewModelScope.launch {
            if (preferences.settings.first().deleteAfterWatching) {
                downloads.removeTarget(item.id, current.episode?.key)
            }
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
