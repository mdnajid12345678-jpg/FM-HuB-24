package com.fmhub24.android.ui.screens.seeall

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fmhub24.android.R
import com.fmhub24.android.data.model.HomeCategory
import com.fmhub24.android.data.model.inCategory
import com.fmhub24.android.ui.components.ChipRow
import com.fmhub24.android.ui.components.EmptyState
import com.fmhub24.android.ui.components.ErrorState
import com.fmhub24.android.ui.components.FilterSheet
import com.fmhub24.android.ui.components.PosterCard
import com.fmhub24.android.ui.components.SelectChip
import com.fmhub24.android.ui.components.CatalogFilter
import com.fmhub24.android.ui.components.dataErrorMessage
import com.fmhub24.android.ui.components.stringRes
import com.fmhub24.android.ui.screens.explore.ExploreViewModel

private const val SORT_RECENT = "recent"
private const val SORT_NEWEST = "newest"
private const val SORT_AZ = "az"

/** "See all" grid: back arrow, sort chips, filter icon and the grid (spec 6.2). */
@Composable
fun SeeAllScreen(
    category: String,
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val all by viewModel.results.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val languages by viewModel.languages.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var sort by remember { mutableStateOf(SORT_RECENT) }
    var showSheet by remember { mutableStateOf(false) }

    val wanted = HomeCategory.entries.firstOrNull { it.name == category } ?: HomeCategory.ALL
    val items = remember(all, sort, wanted) {
        val scoped = all.filter { item ->
            when (wanted) {
                HomeCategory.ALL -> true
                HomeCategory.MOVIES -> item.contentType.inCategory(HomeCategory.MOVIES)
                HomeCategory.SERIES -> item.contentType.inCategory(HomeCategory.SERIES)
                HomeCategory.ANIME -> item.contentType.inCategory(HomeCategory.ANIME)
            }
        }
        when (sort) {
            SORT_AZ -> scoped.sortedBy { it.title.lowercase() }
            SORT_NEWEST -> scoped.sortedByDescending { it.updatedAt ?: "" }
            else -> scoped.sortedByDescending { it.createdAt ?: "" }
        }
    }

    val sortOptions = listOf(
        SORT_RECENT to stringRes(R.string.sort_recent),
        SORT_NEWEST to stringRes(R.string.sort_newest),
        SORT_AZ to stringRes(R.string.sort_az),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = stringRes(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringRes(R.string.action_see_all),
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
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
            sortOptions.forEach { (value, label) ->
                androidx.compose.material3.FilterChip(
                    selected = sort == value,
                    onClick = { sort = value },
                    label = { Text(label, fontSize = 12.sp) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            EmptyState(
                title = stringRes(R.string.empty_home_title),
                detail = stringRes(R.string.empty_home_detail),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    PosterCard(
                        item = item,
                        onClick = { onOpenDetails(item.id) },
                        showTitle = true,
                    )
                }
            }
        }
    }

    if (showSheet) {
        FilterSheet(
            genres = genres,
            languages = languages,
            initial = filter,
            resultCount = items.size,
            onDismiss = { showSheet = false },
            onApply = { applied ->
                viewModel.applyFilter(applied)
                showSheet = false
            },
        )
    }
}
