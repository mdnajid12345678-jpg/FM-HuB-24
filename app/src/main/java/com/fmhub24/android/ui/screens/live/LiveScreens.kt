@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fmhub24.android.ui.screens.live

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fmhub24.android.R
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.ui.components.EmptyState
import com.fmhub24.android.ui.components.ErrorState
import com.fmhub24.android.ui.components.LoadingState
import com.fmhub24.android.ui.components.OfflineBanner
import com.fmhub24.android.ui.components.RemoteImage
import com.fmhub24.android.ui.components.SelectChip
import com.fmhub24.android.ui.components.LiveBadge
import com.fmhub24.android.ui.components.dataErrorMessage
import com.fmhub24.android.ui.components.rememberOnline
import com.fmhub24.android.ui.components.stringRes

/** Live TV tab (spec 6.3): chips, a featured channel with a LIVE badge and grids per category. */
@Composable
fun LiveScreen(
    onOpenChannel: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFavorites: () -> Unit,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val online by rememberOnline()
    var category by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!online) OfflineBanner()

        TopAppBar(
            title = {
                Text(
                    text = stringRes(R.string.live_title),
                    fontWeight = FontWeight.SemiBold,
                )
            },
            actions = {
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = stringRes(R.string.action_search),
                    )
                }
                IconButton(onClick = onOpenFavorites) {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = stringRes(R.string.favorites_title),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )

        when {
            state.loading && state.channels.isEmpty() -> LoadingState()
            state.error != null && state.channels.isEmpty() -> ErrorState(
                message = dataErrorMessage(state.error),
                onRetry = viewModel::refresh,
            )
            else -> {
                val categories = remember(state.channels) { viewModel.categories(state.channels) }
                val grouped = remember(state.channels, category) {
                    viewModel.grouped(state.channels, category)
                }
                val featured = state.channels.firstOrNull { !it.posterUrl.isNullOrBlank() }
                    ?: state.channels.firstOrNull()

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SelectChip(
                                text = stringRes(R.string.category_all),
                                selected = category.isEmpty(),
                                onClick = { category = "" },
                            )
                            categories.forEach { value ->
                                SelectChip(
                                    text = value,
                                    selected = category == value,
                                    onClick = { category = value },
                                )
                            }
                        }
                    }

                    if (featured != null) {
                        item {
                            FeaturedChannel(
                                channel = featured,
                                onClick = { onOpenChannel(featured.id) },
                            )
                        }
                    }

                    if (state.channels.isEmpty()) {
                        item {
                            EmptyState(
                                title = stringRes(R.string.empty_live_title),
                                detail = stringRes(R.string.empty_live_detail),
                                icon = Icons.Outlined.LiveTv,
                            )
                        }
                    }

                    grouped.forEach { (group, channels) ->
                        if (group.isNotEmpty()) {
                            item {
                                Text(
                                    text = group,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                        item {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((((channels.size + 2) / 3) * 132).dp),
                                userScrollEnabled = false,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                            ) {
                                items(channels, key = { it.id }) { channel ->
                                    ChannelTile(channel = channel, onClick = { onOpenChannel(channel.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedChannel(channel: MediaItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(170.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        RemoteImage(
            url = channel.posterUrl,
            contentDescription = channel.title,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
        ) {
            LiveBadge()
            Spacer(Modifier.height(8.dp))
            Text(
                text = channel.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            channel.genres.take(2).joinToString(" · ").takeIf { it.isNotBlank() }?.let { labels ->
                Text(text = labels, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
            }
        }
    }
}

/** 1:1 channel tile with a small LIVE dot. */
@Composable
private fun ChannelTile(channel: MediaItem, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box {
            RemoteImage(
                url = channel.posterUrl,
                contentDescription = channel.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color(0xFFFF5C6C), RoundedCornerShape(50))
                    .width(7.dp)
                    .height(7.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = channel.title,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Favorite channels: a grid with an Add tile (spec 6.3). */
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsState()
    val channels by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = stringRes(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringRes(R.string.favorites_title),
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }

        if (favorites.isEmpty()) {
            EmptyState(
                title = stringRes(R.string.empty_favorites_title),
                detail = stringRes(R.string.empty_favorites_detail),
                icon = Icons.Outlined.StarBorder,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(favorites, key = { it.contentId }) { favorite ->
                    Column(
                        modifier = Modifier.clickable { onOpenChannel(favorite.contentId) },
                    ) {
                        RemoteImage(
                            url = favorite.posterUrl,
                            contentDescription = favorite.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp),
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = favorite.title,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                item {
                    Surface(
                        onClick = onBack,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(88.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = stringRes(R.string.add_channel),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringRes(R.string.add_channel),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.nothingPlaceholder(): Modifier = this
