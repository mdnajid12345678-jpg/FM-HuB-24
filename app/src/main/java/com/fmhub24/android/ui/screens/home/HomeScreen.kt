package com.fmhub24.android.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.fmhub24.android.R
import com.fmhub24.android.data.model.HomeCategory
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.ui.components.EmptyState
import com.fmhub24.android.ui.components.ErrorState
import com.fmhub24.android.ui.components.OfflineBanner
import com.fmhub24.android.ui.components.dataErrorMessage
import com.fmhub24.android.ui.components.rememberOnline
import com.fmhub24.android.ui.components.stringRes

private val HomeBackground = Color(0xFF08130F)
private val HomeSurface = Color(0xFF14221D)
private val HomeAccent = Color(0xFFF4B84A)
private val HomeMuted = Color(0xFFAAB8B0)

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

    Column(Modifier.fillMaxSize().background(HomeBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "home-header") { HomeHeader(onOpenSearch) }
            item(key = "home-categories") {
                CategorySelector(
                    selected = selected,
                    onSelected = { category = it.name },
                )
            }
            if (!online) item(key = "offline-banner") { OfflineBanner() }

            if (state.loading && state.items.isEmpty()) {
                item(key = "home-loading") {
                    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(color = HomeAccent)
                    }
                }
            } else if (state.error != null && state.items.isEmpty()) {
                item(key = "home-error") {
                    ErrorState(
                        message = dataErrorMessage(state.error),
                        onRetry = viewModel::refresh,
                    )
                }
            } else {
                if (state.error != null) {
                    item(key = "home-inline-error") {
                        ErrorState(message = dataErrorMessage(state.error), onRetry = viewModel::refresh)
                    }
                }
                if (featured != null) {
                    item(key = "home-featured") {
                        FeaturedHero(
                            item = featured,
                            onClick = {
                                if (featured.isLive) onOpenChannel(featured.id)
                                else onOpenDetails(featured.id)
                            },
                        )
                    }
                }
                if (recent.isNotEmpty()) {
                    item(key = "home-new-this-week") {
                        PosterCarousel(
                            title = stringRes(R.string.section_new_this_week),
                            items = recent,
                            onOpenDetails = onOpenDetails,
                            onSeeAll = { onSeeAll(HomeCategory.ALL) },
                        )
                    }
                }
                rows.forEach { row ->
                    item(key = "home-section-${row.titleRes}") {
                        val seeAllCategory = when (row.titleRes) {
                            R.string.section_movies -> HomeCategory.MOVIES
                            R.string.section_series -> HomeCategory.SERIES
                            R.string.section_anime -> HomeCategory.ANIME
                            else -> selected
                        }
                        PosterCarousel(
                            title = stringRes(row.titleRes),
                            items = row.items,
                            onOpenDetails = { id ->
                                if (row.items.firstOrNull { it.id == id }?.isLive == true) onOpenChannel(id)
                                else onOpenDetails(id)
                            },
                            onSeeAll = { onSeeAll(seeAllCategory) },
                        )
                    }
                }
                if (state.items.isEmpty() && state.error == null) {
                    item(key = "home-empty") {
                        EmptyState(
                            title = stringRes(R.string.empty_home_title),
                            detail = stringRes(R.string.empty_home_detail),
                            icon = Icons.Outlined.Movie,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(onOpenSearch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildAnnotatedString {
                pushStyle(SpanStyle(color = Color(0xFFF2F5F2), fontWeight = FontWeight.ExtraBold))
                append("FMHuB")
                pop()
                pushStyle(SpanStyle(color = HomeAccent, fontWeight = FontWeight.ExtraBold))
                append("24")
                pop()
            },
            fontSize = 25.sp,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.weight(1f),
        )
        Surface(
            onClick = onOpenSearch,
            shape = CircleShape,
            color = HomeSurface,
            contentColor = HomeMuted,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringRes(R.string.action_search), fontSize = 14.sp, color = HomeMuted)
            }
        }
    }
}

@Composable
private fun CategorySelector(
    selected: HomeCategory,
    onSelected: (HomeCategory) -> Unit,
) {
    val categories = listOf(
        HomeCategory.ALL to stringRes(R.string.category_all),
        HomeCategory.MOVIES to stringRes(R.string.category_movies),
        HomeCategory.SERIES to stringRes(R.string.category_series),
        HomeCategory.ANIME to stringRes(R.string.category_anime),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(CircleShape)
            .background(HomeSurface)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        categories.forEach { (category, label) ->
            val active = selected == category
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(if (active) HomeAccent else Color.Transparent)
                    .clickable { onSelected(category) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (active) Color(0xFF17130B) else HomeMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FeaturedHero(item: MediaItem, onClick: () -> Unit) {
    val metadata = buildList {
        addAll(item.genres.take(2))
        item.releaseYear?.let { add(it.toString()) }
    }.joinToString(" · ")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .aspectRatio(1.9f)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF1C7765), Color(0xFFB99347), Color(0xFF263B31))),
            )
            .clickable(onClick = onClick),
    ) {
        if (!item.posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0x22101713),
                            0.42f to Color(0x33101713),
                            1f to Color(0xEE08100D),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 92.dp, bottom = 22.dp),
        ) {
            Text(
                stringRes(R.string.home_featured),
                color = HomeAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (metadata.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    metadata,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(
            onClick = onClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 20.dp)
                .size(62.dp),
            shape = CircleShape,
            color = HomeAccent,
            contentColor = Color(0xFF17130B),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = stringRes(R.string.action_play),
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

@Composable
private fun PosterCarousel(
    title: String,
    items: List<MediaItem>,
    onOpenDetails: (String) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = Color(0xFFEAF0EC),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringRes(R.string.action_see_all),
                color = HomeAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onSeeAll).padding(6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { item ->
                HomePosterCard(
                    item = item,
                    onClick = { onOpenDetails(item.id) },
                    modifier = Modifier.width(100.dp),
                )
            }
        }
    }
}

@Composable
private fun HomePosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.76f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF286B54), Color(0xFF34404C), Color(0xFF9D552F))),
                ),
        ) {
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = item.title,
            color = Color(0xFFE7ECE9),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
