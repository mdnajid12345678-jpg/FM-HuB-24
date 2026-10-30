@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fmhub24.android.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.fmhub24.android.R
import com.fmhub24.android.data.model.HomeCategory
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.ui.components.ChipRow
import com.fmhub24.android.ui.components.EmptyState
import com.fmhub24.android.ui.components.ErrorState
import com.fmhub24.android.ui.components.LandscapeThumb
import com.fmhub24.android.ui.components.LoadingState
import com.fmhub24.android.ui.components.OfflineBanner
import com.fmhub24.android.ui.components.PosterCard
import com.fmhub24.android.ui.components.SectionHeader
import com.fmhub24.android.ui.components.ThinProgress
import com.fmhub24.android.ui.components.dataErrorMessage
import com.fmhub24.android.ui.components.rememberOnline
import com.fmhub24.android.ui.components.stringRes

@Composable
fun HomeScreen(
    onSeeAll: (HomeCategory) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenLive: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val online by rememberOnline()
    var category by remember { mutableStateOf(HomeCategory.ALL.name) }
    val selected = HomeCategory.entries.firstOrNull { it.name == category } ?: HomeCategory.ALL
    val featured = remember(state.items) { viewModel.featured(state.items) }
    val recent = remember(state.items) { viewModel.recentlyAdded(state.items) }
    val rows = remember(state.items, selected) { viewModel.rows(state.items, selected) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!online) OfflineBanner()

        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.app_name),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            },
            actions = {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.action_search))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )

        when {
            state.loading && state.items.isEmpty() -> LoadingState()
            state.error != null && state.items.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ErrorState(
                    message = dataErrorMessage(state.error),
                    onRetry = viewModel::refresh,
                )
            }
            else -> HomeContent(
                state = state,
                featured = featured,
                recent = recent,
                rows = rows,
                selectedCategory = selected,
                onCategorySelected = { category = it.name },
                onSeeAll = onSeeAll,
                onOpenDetails = onOpenDetails,
                onOpenChannel = onOpenChannel,
                onRefresh = viewModel::refresh,
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    featured: MediaItem?,
    recent: List<MediaItem>,
    rows: List<HomeRow>,
    selectedCategory: HomeCategory,
    onCategorySelected: (HomeCategory) -> Unit,
    onSeeAll: (HomeCategory) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val categoryOptions = listOf(
        HomeCategory.ALL.name to stringRes(R.string.category_all),
        HomeCategory.MOVIES.name to stringRes(R.string.category_movies),
        HomeCategory.SERIES.name to stringRes(R.string.category_series),
        HomeCategory.ANIME.name to stringRes(R.string.category_anime),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            ChipRow(
                options = categoryOptions,
                selected = selectedCategory.name,
                onSelect = { value ->
                    onCategorySelected(
                        HomeCategory.entries.firstOrNull { it.name == value } ?: HomeCategory.ALL,
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (state.error != null) {
            item { ErrorState(message = dataErrorMessage(state.error), onRetry = onRefresh) }
        }

        if (featured != null) {
            item {
                FeaturedBanner(
                    item = featured,
                    onClick = {
                        if (featured.isLive) onOpenChannel(featured.id) else onOpenDetails(featured.id)
                    },
                )
            }
        }

        // Continue watching: design shows a two-column grid of landscape thumbnails, each with a
        // progress bar under the frame.
        if (state.continueWatching.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader(title = stringRes(R.string.section_continue_watching))
                    Spacer(Modifier.height(10.dp))
                    state.continueWatching.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            pair.forEach { entry ->
                                val item = state.items.firstOrNull { it.id == entry.contentId }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            if (item != null) onOpenDetails(item.id)
                                        },
                                ) {
                                    LandscapeThumb(
                                        url = entry.posterUrl,
                                        contentDescription = entry.title,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(Modifier.height(5.dp))
                                    ThinProgress(progress = entry.progress ?: 0f)
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        text = entry.title,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item {
                PosterGrid(
                    title = stringRes(R.string.section_recently_added),
                    items = recent,
                    onOpenDetails = onOpenDetails,
                    onSeeAll = { onSeeAll(HomeCategory.ALL) },
                )
            }
        }

        rows.forEach { row ->
            item {
                PosterRow(
                    title = stringRes(row.titleRes),
                    items = row.items,
                    onOpenDetails = onOpenDetails,
                    onSeeAll = { onSeeAll(selectedCategory) },
                )
            }
        }

        if (state.items.isEmpty() && state.error == null) {
            item {
                EmptyState(
                    title = stringRes(R.string.empty_home_title),
                    detail = stringRes(R.string.empty_home_detail),
                    icon = Icons.Outlined.Movie,
                )
            }
        }
    }
}

/** Design `.ban` + `.pg`: a flat accent banner with the title left and a play glyph right. */
@Composable
private fun FeaturedBanner(item: MediaItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(88.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.isLive) {
            com.fmhub24.android.ui.components.LiveBadge()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = item.title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Outlined.PlayArrow,
            contentDescription = stringRes(R.string.action_play),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** Design `.pg`: a three-column poster grid under a bold heading. */
@Composable
private fun PosterGrid(
    title: String,
    items: List<MediaItem>,
    onOpenDetails: (String) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = title, onSeeAll = onSeeAll)
        Spacer(Modifier.height(10.dp))
        items.take(6).chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { item ->
                    PosterCard(
                        item = item,
                        onClick = { onOpenDetails(item.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PosterRow(
    title: String,
    items: List<MediaItem>,
    onOpenDetails: (String) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column {
        SectionHeader(
            title = title,
            modifier = Modifier.padding(horizontal = 16.dp),
            onSeeAll = onSeeAll,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                PosterCard(
                    item = item,
                    onClick = { onOpenDetails(item.id) },
                    modifier = Modifier.width(116.dp),
                )
            }
        }
    }
}
