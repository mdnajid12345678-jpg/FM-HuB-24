package com.fmhub24.android.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fmhub24.android.R
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.model.ContentType
import com.fmhub24.android.ui.components.EmptyState
import com.fmhub24.android.ui.components.PosterCard
import com.fmhub24.android.ui.components.RemoteImage
import com.fmhub24.android.ui.components.SelectChip
import com.fmhub24.android.ui.components.stringRes

/**
 * The search experience of spec 6.4: recent searches with per-item removal and Clear all,
 * suggestions with the typed part highlighted plus an arrow to fill the field, a 3-column grid,
 * and an honest no-results block that still offers something to watch.
 */
@Composable
fun SearchBody(
    query: String,
    results: List<MediaItem>,
    recent: List<String>,
    suggestions: List<MediaItem>,
    suggestionChips: List<String>,
    trending: List<MediaItem>,
    /** Chips and filters rendered directly under the search field (Explore). */
    header: (@Composable () -> Unit)? = null,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onOpenItem: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    matchResultsAsList: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val showRecents = focused && query.isBlank() && recent.isNotEmpty()
    val showChips = focused && query.isBlank()
    val showSuggestions = query.isNotBlank() && suggestions.isNotEmpty()
    val showNoResults = query.isNotBlank() && results.isEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (matchResultsAsList) 18.dp else 16.dp)
                .onFocusChanged { state -> focused = state.isFocused },
            placeholder = { Text(stringRes(R.string.search_hint), fontSize = 13.sp) },
            singleLine = true,
            shape = if (matchResultsAsList) RoundedCornerShape(28.dp) else RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            leadingIcon = if (matchResultsAsList) {
                {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else null,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Outlined.Clear,
                            contentDescription = stringRes(R.string.action_clear),
                        )
                    }
                } else if (!matchResultsAsList) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = stringRes(R.string.action_search),
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (matchResultsAsList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )

        Spacer(Modifier.height(12.dp))

        header?.invoke()

        if (showRecents) {
            RecentSearches(
                recent = recent,
                onRemove = onRemoveRecent,
                onClearAll = onClearRecents,
                onPick = { onQueryChange(it) },
            )
        } else if (showChips && suggestionChips.isNotEmpty()) {
            SuggestionChips(chips = suggestionChips, onPick = { onQueryChange(it) })
        }

        if (matchResultsAsList && query.isNotBlank()) {
            SearchMatchResults(query = query, results = results, onOpenItem = onOpenItem)
        } else if (showSuggestions) {
            SuggestionList(
                suggestions = suggestions,
                query = query,
                onFill = { onQueryChange(it) },
                onOpen = onOpenItem,
            )
        } else if (matchResultsAsList) {
            Spacer(Modifier.weight(1f))
        } else if (showNoResults) {
            NoResults(query = query, trending = trending, onOpenItem = onOpenItem)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(results, key = { it.id }) { item ->
                    PosterCard(
                        item = item,
                        onClick = { onOpenItem(item) },
                        showTitle = false,
                    )
                }
                if (results.isEmpty() && query.isBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            title = stringRes(R.string.empty_home_title),
                            detail = stringRes(R.string.empty_home_detail),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearches(
    recent: List<String>,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onPick: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringRes(R.string.recent_searches),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringRes(R.string.clear_search_history),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onClearAll),
            )
        }
        Spacer(Modifier.height(6.dp))
        recent.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(entry) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = entry,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { onRemove(entry) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringRes(R.string.remove_query),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionChips(chips: List<String>, onPick: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringRes(R.string.suggestions_label),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEach { chip -> SelectChip(text = chip, selected = false, onClick = { onPick(chip) }) }
        }
    }
}

/** Suggestions with the typed part highlighted and an arrow that fills the field. */
@Composable
private fun SuggestionList(
    suggestions: List<MediaItem>,
    query: String,
    onFill: (String) -> Unit,
    onOpen: (MediaItem) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringRes(R.string.suggestions_label),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        suggestions.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(item) }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = highlight(query = query, text = item.title),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { onFill(item.title) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Outlined.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoResults(query: String, trending: List<MediaItem>, onOpenItem: (MediaItem) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringRes(R.string.empty_search_message, query),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringRes(R.string.empty_search_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (trending.isNotEmpty()) {
            Text(
                text = stringRes(R.string.section_recently_added),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(trending.size) { index ->
                    val item = trending[index]
                    PosterCard(
                        item = item,
                        onClick = { onOpenItem(item) },
                        modifier = Modifier.width(110.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun highlight(query: String, text: String): AnnotatedString {
    val needle = query.trim()
    val accent = MaterialTheme.colorScheme.primary
    return AnnotatedString.Builder().apply {
        if (needle.isEmpty() || !text.contains(needle, true)) {
            append(text)
        } else {
            val index = text.indexOf(needle, ignoreCase = true)
            append(text.take(index))
            withStyle(SpanStyle(color = accent)) {
                append(text.substring(index, index + needle.length))
            }
            append(text.substring(index + needle.length))
        }
    }.toAnnotatedString()
}

@Composable
private fun SearchMatchResults(
    query: String,
    results: List<MediaItem>,
    onOpenItem: (MediaItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = stringRes(R.string.search_match_count, results.size),
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 15.dp, bottom = 10.dp),
        )
        if (results.isEmpty()) {
            NoResults(query = query, trending = emptyList(), onOpenItem = onOpenItem)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(results, key = { it.id }) { item ->
                    SearchMatchRow(item = item, query = query, onClick = { onOpenItem(item) })
                }
            }
        }
    }
}

@Composable
private fun SearchMatchRow(item: MediaItem, query: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = item.posterUrl,
            contentDescription = item.title,
            modifier = Modifier.width(54.dp),
            aspectRatio = 2f / 3f,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlight(query = query, text = item.title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            val metadata = listOfNotNull(
                contentTypeLabel(item.contentType),
                item.releaseYear?.toString(),
            ).joinToString(" · ")
            Text(
                text = metadata,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Outlined.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun contentTypeLabel(type: ContentType): String = when (type) {
    ContentType.MOVIE, ContentType.ANIME_MOVIE -> stringRes(R.string.category_movies)
    ContentType.SERIES, ContentType.ASIAN_DRAMA, ContentType.CARTOON -> stringRes(R.string.section_series)
    ContentType.ANIME_SERIES, ContentType.OVA -> stringRes(R.string.section_anime)
    ContentType.DOCUMENTARY -> stringRes(R.string.section_documentary)
    ContentType.LIVE -> stringRes(R.string.section_live_tv)
    ContentType.OTHER -> stringRes(R.string.type_other)
}
