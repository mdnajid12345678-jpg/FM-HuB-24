package com.fmhub24.android.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.android.data.model.DataException
import com.fmhub24.android.data.DownloadDefaults
import com.fmhub24.android.data.UserPreferencesRepository
import com.fmhub24.android.data.model.EpisodeItem
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.model.MediaSource
import com.fmhub24.android.data.repo.CatalogRepository
import com.fmhub24.android.data.repo.DownloadOptions
import com.fmhub24.android.data.repo.DownloadRow
import com.fmhub24.android.data.repo.DownloadsRepository
import com.fmhub24.android.data.repo.SourcesRepository
import com.fmhub24.android.data.repo.SourceSelection
import com.fmhub24.android.data.repo.WatchStateRepository
import com.fmhub24.android.stream.DownloadAnalyzer
import com.fmhub24.android.stream.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/** State of the Download button on a details page (spec 6.5). */
sealed interface DownloadButtonState {
    data object Idle : DownloadButtonState
    data object Checking : DownloadButtonState
    data class Running(val percent: Float) : DownloadButtonState
    data object Paused : DownloadButtonState
    data object Completed : DownloadButtonState
    data object Failed : DownloadButtonState

    val isRunning: Boolean get() = this is Running || this is Checking
}

/** The open download sheet: "Checking available options" then the choices (spec 6.8). */
data class DownloadSheetState(
    val item: MediaItem,
    val episode: EpisodeItem?,
    val seasonEpisodes: List<EpisodeItem> = emptyList(),
    val phase: Phase = Phase.CHECKING,
    val options: DownloadOptions? = null,
    val analysis: DownloadAnalyzer.Analysis? = null,
    val selectedLanguage: String? = null,
    val selectedSource: MediaSource? = null,
    val selectedHeight: Int? = null,
    val selectedAudio: Set<String> = emptySet(),
    val selectedEpisodes: Set<String> = emptySet(),
    /** Content-Length estimates for direct files, keyed by server and quality. */
    val sizes: Map<String, Long> = emptyMap(),
    val starting: Boolean = false,
    val error: DataException? = null,
    val startedCount: Int = 0,
) {
    enum class Phase { CHECKING, OPTIONS, DONE, ERROR }
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadsRepository,
    private val sources: SourcesRepository,
    private val catalog: CatalogRepository,
    private val preferences: UserPreferencesRepository,
    private val watchState: WatchStateRepository,
) : ViewModel() {
    val rows: StateFlow<List<DownloadRow>> = repository.rows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val watchProgress: StateFlow<Map<String, Float>> = watchState.history
        .map { entries -> entries.associate { it.key to it.progress } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val sheet = MutableStateFlow<DownloadSheetState?>(null)
    val sheetState: StateFlow<DownloadSheetState?> = sheet

    /** Button state for one title or episode, derived from the live Media3 state. */
    fun buttonState(contentId: String, episodeKey: String? = null): StateFlow<DownloadButtonState> =
        rows.map { all ->
            val matches = all.filter {
                it.record.contentId == contentId && it.record.episodeKey == episodeKey
            }
            when {
                matches.isEmpty() -> DownloadButtonState.Idle
                matches.any { it.state?.phase == DownloadState.Phase.DOWNLOADING } ->
                    DownloadButtonState.Running(
                        matches.firstOrNull { it.state?.phase == DownloadState.Phase.DOWNLOADING }
                            ?.percent ?: 0f,
                    )
                matches.any { it.isFailed } -> DownloadButtonState.Failed
                matches.any { it.isFinished } -> DownloadButtonState.Completed
                matches.any { it.state?.phase == DownloadState.Phase.PAUSED } ->
                    DownloadButtonState.Paused
                matches.any { it.state?.phase == DownloadState.Phase.QUEUED } ->
                    DownloadButtonState.Checking
                else -> DownloadButtonState.Idle
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadButtonState.Idle)

    fun rowsForGroup(contentId: String): StateFlow<List<DownloadRow>> =
        rows.map { all -> all.filter { it.record.contentId == contentId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun isDownloaded(contentId: String, episodeKey: String?): StateFlow<Boolean> =
        rows.map { all ->
            all.any {
                it.record.contentId == contentId &&
                    it.record.episodeKey == episodeKey &&
                    (it.isFinished || it.isInProgress)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ----------------------------------------------------------------------------------------
    // Download flow
    // ----------------------------------------------------------------------------------------

    /**
     * Starts the sheet and reads a fresh source row plus, for multi-language HLS, the stream's own
     * track list, so the choices shown are real (spec 6.8).
     */
    fun openSheet(
        item: MediaItem,
        episode: EpisodeItem?,
        seasonEpisodes: List<EpisodeItem> = emptyList(),
    ) {
        sheet.value = DownloadSheetState(
            item = item,
            episode = episode,
            seasonEpisodes = seasonEpisodes,
            selectedEpisodes = seasonEpisodes.map { it.key }.toSet(),
        )
        viewModelScope.launch {
            try {
                val settings = preferences.settings.first()
                val found = sources.forTarget(item.id, episode)
                if (found.isEmpty()) {
                    sheet.value = sheet.value?.copy(phase = DownloadSheetState.Phase.ERROR)
                    return@launch
                }
                val languages = SourceSelection.languages(found)
                val language = settings.preferredAudio?.let { preferred ->
                    languages.firstOrNull { it.equals(preferred, ignoreCase = true) }
                } ?: languages.firstOrNull()
                val maxHeight = settings.defaultQuality.toIntOrNull() ?: 1080
                val chosen = SourceSelection.auto(found, language, maxHeight)
                val analysis = chosen?.takeIf { it.isHls }?.let { source ->
                    runCatching { DownloadAnalyzer.analyze(appContext(), source, headerMap(source)) }
                        .getOrNull()
                }
                sheet.value = sheet.value?.copy(
                    phase = DownloadSheetState.Phase.OPTIONS,
                    options = DownloadOptions(
                        targetId = item.id,
                        episodeKey = episode?.key,
                        sources = found,
                        hlsAnalysis = analysis,
                        freeSpaceBytes = repository.freeSpaceBytes(),
                    ),
                    analysis = analysis,
                    selectedLanguage = language,
                    selectedSource = chosen,
                    selectedHeight = settings.defaultQuality.toIntOrNull()
                        ?.let { preferred -> analysis?.video?.minByOrNull { kotlin.math.abs(it.height - preferred) }?.height }
                        ?: analysis?.video?.maxOfOrNull { it.height },
                    selectedAudio = analysis?.audio
                        ?.filter { it.supported }
                        ?.mapNotNull { it.language }
                        ?.toSet()
                        ?: emptySet(),
                    sizes = estimateSizes(found),
                )
            } catch (failure: DataException) {
                sheet.value = sheet.value?.copy(
                    phase = DownloadSheetState.Phase.ERROR,
                    error = failure,
                )
            } catch (failure: Exception) {
                sheet.value = sheet.value?.copy(
                    phase = DownloadSheetState.Phase.ERROR,
                    error = DataException.Malformed(failure.message ?: "options"),
                )
            }
        }
    }

    fun selectLanguage(language: String) {
        val current = sheet.value ?: return
        val options = current.options ?: return
        val pool = options.sources.filter { SourceSelection.matches(it, language) }
            .ifEmpty { options.sources }
        sheet.value = current.copy(
            selectedLanguage = language,
            selectedSource = SourceSelection.auto(pool, language) ?: pool.firstOrNull(),
        )
    }

    fun selectSource(source: MediaSource) {
        sheet.value = sheet.value?.copy(selectedSource = source)
    }

    fun selectHeight(height: Int?) {
        sheet.value = sheet.value?.copy(selectedHeight = height)
    }

    fun toggleAudio(language: String) {
        val current = sheet.value ?: return
        val selected = if (language in current.selectedAudio) {
            current.selectedAudio - language
        } else {
            current.selectedAudio + language
        }
        sheet.value = current.copy(selectedAudio = selected)
    }

    fun toggleEpisode(key: String) {
        val current = sheet.value ?: return
        val selected = if (key in current.selectedEpisodes) {
            current.selectedEpisodes - key
        } else {
            current.selectedEpisodes + key
        }
        sheet.value = current.copy(selectedEpisodes = selected)
    }

    fun selectAllEpisodes(select: Boolean) {
        val current = sheet.value ?: return
        sheet.value = current.copy(
            selectedEpisodes = if (select) current.seasonEpisodes.map { it.key }.toSet()
            else emptySet(),
        )
    }

    /** Reads a fresh row and hands the request to Media3 straight away (links expire fast). */
    fun start() {
        val current = sheet.value ?: return
        if (current.starting) return
        val source = current.selectedSource ?: return
        val targets: List<EpisodeItem?> =
            if (current.seasonEpisodes.isNotEmpty()) {
                current.seasonEpisodes.filter { it.key in current.selectedEpisodes }
                    .map { episode -> episode }
            } else {
                listOf(current.episode)
            }
        if (targets.isEmpty()) return

        sheet.value = current.copy(starting = true, error = null)
        viewModelScope.launch {
            try {
                var started = 0
                targets.forEach { target ->
                    val pool = sources.forTarget(current.item.id, target)
                    if (pool.isEmpty()) throw DataException.Malformed("no source")
                    val languagePool = current.selectedLanguage?.let { language ->
                        pool.filter { SourceSelection.matches(it, language) }.ifEmpty { null }
                    } ?: pool
                    val selectedQuality = current.selectedSource?.quality
                    val fresh = languagePool.firstOrNull {
                        it.serverLabel == current.selectedSource?.serverLabel &&
                            (selectedQuality == null || it.quality == selectedQuality)
                    } ?: SourceSelection.auto(
                        languagePool,
                        current.selectedLanguage,
                        current.selectedHeight ?: 1080,
                    )
                        ?: languagePool.firstOrNull()
                        ?: throw DataException.Malformed("no source")
                    val headers = fresh.headers
                    val maxHeight = if (current.seasonEpisodes.isNotEmpty()) {
                        current.selectedSource?.quality ?: current.selectedHeight
                    } else {
                        current.selectedHeight
                    }
                    repository.start(
                        item = current.item,
                        episode = target,
                        source = fresh,
                        headers = headers,
                        maxHeight = maxHeight,
                        audioLanguages = current.selectedAudio.filter { it != "Multi" },
                    )
                    started++
                }
                sheet.value = sheet.value?.copy(
                    starting = false,
                    phase = DownloadSheetState.Phase.DONE,
                    startedCount = started,
                )
            } catch (failure: DataException) {
                sheet.value = sheet.value?.copy(starting = false, error = failure, phase = DownloadSheetState.Phase.ERROR)
            } catch (failure: Exception) {
                sheet.value = sheet.value?.copy(
                    starting = false,
                    error = DataException.Malformed(failure.message ?: "start"),
                    phase = DownloadSheetState.Phase.ERROR,
                )
            }
        }
    }

    fun retry() {
        val current = sheet.value ?: return
        openSheet(current.item, current.episode, current.seasonEpisodes)
    }

    fun closeSheet() {
        sheet.value = null
    }

    fun setPaused(id: String, paused: Boolean) = repository.setPaused(id, paused)

    fun pauseAll() = repository.pauseAll()

    fun resumeAll() = repository.resumeAll()

    fun remove(id: String) {
        viewModelScope.launch { repository.remove(id) }
    }

    fun removeGroup(contentId: String) {
        viewModelScope.launch { repository.removeTarget(contentId, null) }
    }

    fun renameDownload(id: String, title: String) {
        viewModelScope.launch { repository.rename(id, title) }
    }

    fun renameSeries(contentId: String, title: String) {
        viewModelScope.launch { repository.renameSeries(contentId, title) }
    }

    fun freeSpaceBytes(): Long = repository.freeSpaceBytes()

    fun storageUsageFraction(downloadedBytes: Long): Float {
        val total = repository.totalSpaceBytes()
        if (total <= 0L) return 0f
        return (downloadedBytes.coerceAtLeast(0L).toFloat() / total).coerceIn(0f, 1f)
    }

    /** Failed rows retry through the sheet, because a fresh signed link is required (spec 5.2). */
    fun retryRow(row: DownloadRow) {
        viewModelScope.launch {
            val item = catalog.cachedContent(row.record.contentId) ?: return@launch
            val episode = catalog.cachedEpisodes(item.id)
                .firstOrNull { it.key == row.record.episodeKey }
            openSheet(item, episode)
        }
    }

    fun totalSize(rows: List<DownloadRow>): Long =
        rows.sumOf { it.sizeBytes ?: 0L }

    private fun headerMap(source: MediaSource): Map<String, String> = source.headers

    private fun appContext(): Context = context

    /** Sizes only for direct files, where Content-Length is meaningful (spec 6.8). */
    private suspend fun estimateSizes(sources: List<MediaSource>): Map<String, Long> =
        sources.filter { it.isDirectVideo }
            .take(8)
            .mapNotNull { source ->
                val size = repository.estimateSize(source.mediaUrl, source.headers)
                if (size == null) null else sourceKey(source) to size
            }
            .toMap()

    fun sourceKey(source: MediaSource): String = "${source.serverLabel}|${source.quality}"
}
