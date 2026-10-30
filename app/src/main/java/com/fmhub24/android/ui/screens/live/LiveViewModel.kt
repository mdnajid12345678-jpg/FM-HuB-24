package com.fmhub24.android.ui.screens.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.android.data.local.FavoriteChannelEntity
import com.fmhub24.android.data.model.DataException
import com.fmhub24.android.data.model.HomeCategory
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.model.inCategory
import com.fmhub24.android.data.repo.CatalogRepository
import com.fmhub24.android.data.repo.SourcesRepository
import com.fmhub24.android.data.repo.WatchStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiveUiState(
    val loading: Boolean = true,
    val error: DataException? = null,
    val channels: List<MediaItem> = emptyList(),
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val watchState: WatchStateRepository,
    private val sourceRepository: SourcesRepository,
) : ViewModel() {
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<DataException?>(null)

    private val channelSources = MutableStateFlow<List<com.fmhub24.android.data.model.MediaSource>>(emptyList())
    val channelSourcesState: StateFlow<List<com.fmhub24.android.data.model.MediaSource>> = channelSources
    private val sourcesLoading = MutableStateFlow(false)
    val sourcesLoadingState: StateFlow<Boolean> = sourcesLoading

    val state: StateFlow<LiveUiState> = combine(catalog.contents, loading, error) { items, isLoading, failure ->
        LiveUiState(
            loading = isLoading,
            error = failure,
            channels = items.filter { it.isLive },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveUiState())

    val favorites: StateFlow<List<FavoriteChannelEntity>> = watchState.favoriteChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun toggleFavorite(channel: MediaItem) {
        viewModelScope.launch { watchState.toggleFavorite(channel, null) }
    }

    fun isFavorite(channelId: String): StateFlow<Boolean> = watchState.isFavorite(channelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Channel categories are built from genres, exactly like the mockup chips (spec 6.3). */
    fun categories(channels: List<MediaItem>): List<String> =
        channels.flatMap { it.genres }.distinct().sortedBy { it.lowercase() }

    /** Fresh, resolved sources for one channel (spec 5.2 rule 3). */
    fun loadSources(contentId: String) {
        viewModelScope.launch {
            sourcesLoading.value = true
            channelSources.value = runCatching {
                sourceRepository.forContent(contentId)
            }.getOrDefault(emptyList())
            sourcesLoading.value = false
        }
    }

    fun grouped(channels: List<MediaItem>, category: String): Map<String, List<MediaItem>> =
        if (category.isEmpty()) {
            mapOf("" to channels)
        } else {
            channels.filter { channel -> channel.genres.any { it.equals(category, true) } }
                .groupBy { channel -> channel.genres.firstOrNull() ?: category }
        }
}
