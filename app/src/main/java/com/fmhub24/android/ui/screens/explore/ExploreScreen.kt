package com.fmhub24.android.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fmhub24.android.R
import com.fmhub24.android.ui.components.CatalogFilter
import com.fmhub24.android.ui.components.ErrorState
import com.fmhub24.android.ui.components.FilterSheet
import com.fmhub24.android.ui.components.OfflineBanner
import com.fmhub24.android.ui.components.SelectChip
import com.fmhub24.android.ui.components.dataErrorMessage
import com.fmhub24.android.ui.components.rememberOnline
import com.fmhub24.android.ui.components.stringRes
import com.fmhub24.android.ui.screens.search.SearchBody

/** Explore: search field, type chips, Genre/Year/Language filter chips and a 3-column grid. */
@Composable
fun ExploreScreen(
    onOpenDetails: (String) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val recent by viewModel.recentSearches.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val languages by viewModel.languages.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val languageFailure by viewModel.languageFailure.collectAsState()
    val online by rememberOnline()
    var showSheet by remember { mutableStateOf(false) }

    val typeOptions = listOf(
        "" to stringRes(R.string.category_all),
        CatalogFilter.MOVIES to stringRes(R.string.category_movies),
        CatalogFilter.SERIES to stringRes(R.string.category_series),
        CatalogFilter.ANIME to stringRes(R.string.category_anime),
        CatalogFilter.LIVE to stringRes(R.string.section_live_tv),
    )
    val activeType = filter.types.singleOrNull() ?: ""

    Column(modifier = Modifier.fillMaxSize()) {
        if (!online) OfflineBanner()

        // Design starts Explore with a search field outlined in the accent colour.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringRes(R.string.search_hint),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showSheet = true }) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = stringRes(R.string.action_filter),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            typeOptions.forEach { (value, label) ->
                SelectChip(
                    text = label,
                    selected = value == activeType,
                    onClick = {
                        viewModel.applyFilter(
                            filter.copy(
                                types = if (value.isEmpty()) emptySet() else setOf(value),
                            ),
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChipLabel(
                label = stringRes(R.string.filter_genre),
                active = filter.genres.isNotEmpty(),
                onClick = { showSheet = true },
            )
            FilterChipLabel(
                label = stringRes(R.string.filter_year),
                active = filter.yearFrom != null || filter.yearTo != null,
                onClick = { showSheet = true },
            )
            FilterChipLabel(
                label = stringRes(R.string.filter_language),
                active = filter.languages.isNotEmpty(),
                onClick = { showSheet = true },
            )
            if (filter.isActive) {
                TextButton(onClick = { viewModel.applyFilter(CatalogFilter()) }) {
                    Text(stringRes(R.string.filter_reset), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (languageFailure && filter.languages.isNotEmpty()) {
            ErrorState(message = dataErrorMessage(null))
        } else {
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
                onOpenItem = { item ->
                    viewModel.openItem(item)
                    onOpenDetails(item.id)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showSheet) {
        FilterSheet(
            genres = genres,
            languages = languages,
            initial = filter,
            resultCount = results.size,
            onDismiss = { showSheet = false },
            onApply = { applied ->
                viewModel.applyFilter(applied)
                showSheet = false
            },
        )
    }
}

@Composable
private fun FilterChipLabel(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .border(
                width = 1.dp,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = if (active) {
                    Color(0xFFBFD8FF)
                } else {
                    Color.White.copy(alpha = 0.68f)
                },
            )
            if (active) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
