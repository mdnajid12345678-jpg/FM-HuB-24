package com.fmhub24.android.ui.screens.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.android.data.model.DataException
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.repo.CatalogRepository
import com.fmhub24.android.data.repo.SourcesRepository
import com.fmhub24.android.data.repo.WatchStateRepository
import com.fmhub24.android.ui.components.CatalogFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val watchState: WatchStateRepository,
    private val sources: SourcesRepository,
) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(CatalogFilter())

    private val languageIndex = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    private val languageIndexLoaded = MutableStateFlow(false)
    private val languageIndexLoading = MutableStateFlow(false)
    private val languageIndexError = MutableStateFlow(false)

    val recentSearches: StateFlow<List<String>> = watchState.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val catalogItems = catalog.contents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Hits the provider returned for the last submitted query; never written to the cache. */
    private val remoteResults = MutableStateFlow<List<MediaItem>>(emptyList())
    private val remoteQuery = MutableStateFlow("")
    private var searchJob: Job? = null

    /** The cached catalog plus whatever the provider found for the submitted query. */
    private val searchableItems: StateFlow<List<MediaItem>> =
        combine(catalogItems, remoteResults) { local, remote ->
            if (remote.isEmpty()) local else (local + remote).distinctBy { it.id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everything that matches the text and the filter sheet. */
    val results: StateFlow<List<MediaItem>> = combine(
        searchableItems,
        query,
        filter,
        languageIndex,
        languageIndexLoaded,
    ) { items, text, active, index, loaded ->
        val needle = text.trim()
        // Until the language index is in memory the language clause is skipped rather than
        // hiding every title (the chips would look like a filter that matches nothing).
        val languages = if (loaded) index else null
        items.filter { item ->
            (needle.isEmpty() || item.title.contains(needle, true) ||
                item.originalTitle?.contains(needle, true) == true) &&
                active.matches(item, languages)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val genres: StateFlow<List<String>> = catalogItems
        .map { items -> items.flatMap { it.genres }.distinct().sortedBy { it.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val years: StateFlow<List<Int>> = catalogItems
        .map { items -> items.mapNotNull { it.releaseYear }.distinct().sortedDescending() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Languages actually present in media_sources, fetched only when the filter is used. */
    val languages: StateFlow<List<String>> = languageIndex
        .map { index -> index.values.flatten().distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val languageFailure: StateFlow<Boolean> = languageIndexError

    val languageLoading: StateFlow<Boolean> = languageIndexLoading

    /** Prefix matches used as suggestions while typing. */
    fun suggestions(text: String, limit: Int = 6): List<MediaItem> {
        val needle = text.trim()
        if (needle.length < 2) return emptyList()
        return catalogItems.value
            .filter { it.title.contains(needle, true) || it.originalTitle?.contains(needle, true) == true }
            .sortedBy { !it.title.startsWith(needle, true) }
            .take(limit)
    }

    fun setQuery(value: String) {
        query.value = value
        // Provider hits belong to the query that produced them: typing something else drops them
        // instead of mixing the previous search into the new one.
        if (!value.trim().equals(remoteQuery.value, ignoreCase = true)) clearRemoteResults()
    }

    fun submitSearch() {
        val text = query.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch { watchState.recordSearch(text) }
        searchProvider(text)
    }

    /**
     * Runs only on submit, so typing does not send a request per character. Results are held in
     * memory and merged into [results]; opening one caches it through the details screen.
     */
    private fun searchProvider(text: String) {
        if (text.length < MIN_REMOTE_QUERY) return
        if (text.equals(remoteQuery.value, ignoreCase = true) && remoteResults.value.isNotEmpty()) return
        remoteQuery.value = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            remoteResults.value = try {
                catalog.searchProvider(text)
            } catch (error: DataException) {
                emptyList()
            } catch (error: Exception) {
                emptyList()
            }
        }
    }

    private fun clearRemoteResults() {
        searchJob?.cancel()
        remoteQuery.value = ""
        if (remoteResults.value.isNotEmpty()) remoteResults.value = emptyList()
    }

    fun openItem(item: MediaItem) {
        viewModelScope.launch { watchState.recordSearch(item.title) }
    }

    fun removeRecent(value: String) {
        viewModelScope.launch { watchState.removeSearch(value) }
    }

    fun clearRecents() {
        viewModelScope.launch { watchState.clearSearchHistory() }
    }

    fun applyFilter(value: CatalogFilter) {
        filter.value = value
        if (value.languages.isNotEmpty()) refreshLanguages()
    }

    /** How many titles a not-yet-applied filter would leave: the sheet's button count. */
    fun countFor(candidate: CatalogFilter): Int {
        val index = if (languageIndexLoaded.value) languageIndex.value else null
        return catalogItems.value.count { candidate.matches(it, index) }
    }

    /** Opens the language section before the user can reach for it: the chips come from the index. */
    fun onFilterSheetOpen() {
        if (languageIndex.value.isEmpty()) refreshLanguages()
    }

    /** Retries the language index after the filter reported a failure. */
    fun retryLanguages() {
        refreshLanguages()
    }

    /**
     * Reads the language of each title from media_sources in one request. The index stays in
     * memory only: media_sources must never be cached (spec 5.5).
     */
    private fun refreshLanguages() {
        val ids = catalogItems.value.map { it.id }.take(500)
        if (ids.isEmpty() || languageIndexLoading.value) return
        viewModelScope.launch {
            languageIndexLoading.value = true
            try {
                languageIndex.value = sources.languageIndex(ids)
                languageIndexLoaded.value = true
                languageIndexError.value = false
            } catch (error: DataException) {
                languageIndex.value = emptyMap()
                languageIndexLoaded.value = false
                languageIndexError.value = true
            } catch (error: Exception) {
                languageIndex.value = emptyMap()
                languageIndexLoaded.value = false
                languageIndexError.value = true
            } finally {
                languageIndexLoading.value = false
            }
        }
    }

    private companion object {
        /** Matches CatalogRepository.MIN_REMOTE_QUERY: one character is not a search. */
        const val MIN_REMOTE_QUERY = 2
    }
}
