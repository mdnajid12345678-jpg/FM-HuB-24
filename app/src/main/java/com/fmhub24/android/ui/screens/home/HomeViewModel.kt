package com.fmhub24.android.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.android.data.model.DataException
import com.fmhub24.android.data.model.HomeCategory
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.model.inCategory
import com.fmhub24.android.data.repo.CatalogRepository
import com.fmhub24.android.data.repo.HistoryEntry
import com.fmhub24.android.data.repo.WatchStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of the home screen: its title and the titles it shows. */
data class HomeRow(val titleRes: Int, val items: List<MediaItem>)

data class HomeUiState(
    val loading: Boolean = true,
    val error: DataException? = null,
    val items: List<MediaItem> = emptyList(),
    val continueWatching: List<HistoryEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    watchState: WatchStateRepository,
) : ViewModel() {
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<DataException?>(null)

    val state: StateFlow<HomeUiState> = combine(
        catalog.contents,
        watchState.history,
        loading,
        error,
    ) { items, history, isLoading, failure ->
        HomeUiState(
            loading = isLoading,
            error = failure,
            items = items,
            continueWatching = history.filter { entry -> items.any { it.id == entry.contentId } },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            error.value = catalog.refresh()
            loading.value = false
        }
    }

    /** Featured banner: the most recently updated title that has a poster (spec 5.3). */
    fun featured(items: List<MediaItem>): MediaItem? =
        items.filter { !it.posterUrl.isNullOrBlank() }
            .maxByOrNull { it.updatedAt ?: it.createdAt ?: "" }

    /** Row called "Recently added" because there is no popularity data (spec 5.3). */
    fun recentlyAdded(items: List<MediaItem>): List<MediaItem> =
        items.sortedByDescending { it.createdAt ?: "" }.take(20)

    fun rows(items: List<MediaItem>, category: HomeCategory): List<HomeRow> {
        val rows = mutableListOf<HomeRow>()
        val movies = items.filter { it.contentType.inCategory(HomeCategory.MOVIES) }
        val series = items.filter { it.contentType.inCategory(HomeCategory.SERIES) }
        val anime = items.filter { it.contentType.inCategory(HomeCategory.ANIME) }
        val live = items.filter { it.contentType.inCategory(HomeCategory.ALL) && it.isLive }
        val docs = items.filter { !it.isLive && !it.contentType.inCategory(HomeCategory.MOVIES) &&
            !it.contentType.inCategory(HomeCategory.SERIES) && !it.contentType.inCategory(HomeCategory.ANIME) }

        if (category == HomeCategory.ALL || category == HomeCategory.MOVIES) {
            if (movies.isNotEmpty()) rows += HomeRow(com.fmhub24.android.R.string.section_movies, movies)
        }
        if (category == HomeCategory.ALL || category == HomeCategory.SERIES) {
            if (series.isNotEmpty()) rows += HomeRow(com.fmhub24.android.R.string.section_series, series)
        }
        if (category == HomeCategory.ALL || category == HomeCategory.ANIME) {
            if (anime.isNotEmpty()) rows += HomeRow(com.fmhub24.android.R.string.section_anime, anime)
        }
        if (category == HomeCategory.ALL) {
            if (live.isNotEmpty()) rows += HomeRow(com.fmhub24.android.R.string.section_live_tv, live)
            if (docs.isNotEmpty()) rows += HomeRow(com.fmhub24.android.R.string.section_documentary, docs)
        }
        return rows
    }
}
