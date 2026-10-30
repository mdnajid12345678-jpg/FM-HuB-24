@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fmhub24.android.ui.screens.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.fmhub24.android.R
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.ui.screens.explore.ExploreViewModel

/** Standalone search page opened from the Home top bar (spec 6.4). */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val recent by viewModel.recentSearches.collectAsState()
    val genres by viewModel.genres.collectAsState()

    fun open(item: MediaItem) {
        viewModel.openItem(item)
        onOpenDetails(item.id)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.action_search)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )

        SearchBody(
            query = query,
            results = results,
            recent = recent,
            suggestions = viewModel.suggestions(query),
            suggestionChips = genres,
            trending = results.take(10),
            onQueryChange = viewModel::setQuery,
            onSubmit = viewModel::submitSearch,
            onRemoveRecent = viewModel::removeRecent,
            onClearRecents = viewModel::clearRecents,
            onOpenItem = ::open,
        )
    }
}
