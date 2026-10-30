package com.fmhub24.android.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.repo.CatalogRepository
import com.fmhub24.android.data.repo.HistoryEntry
import com.fmhub24.android.data.repo.MyListEntry
import com.fmhub24.android.data.repo.WatchStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** My list and History data plus their edits (spec 6.9). */
@HiltViewModel
class ProfileDataViewModel @Inject constructor(
    private val watchState: WatchStateRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {
    val history: StateFlow<List<HistoryEntry>> = watchState.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myList: StateFlow<List<MyListEntry>> = watchState.myList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Full catalog items for the My list grid, joined on the cached ids. */
    val catalogItems: StateFlow<List<MediaItem>> = catalog.contents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeHistory(key: String) {
        viewModelScope.launch { watchState.removeHistory(key) }
    }

    fun clearHistory() {
        viewModelScope.launch { watchState.clearHistory() }
    }

    fun removeFromMyList(contentId: String) {
        viewModelScope.launch { watchState.removeFromMyList(contentId) }
    }
}