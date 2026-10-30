package com.fmhub24.android.ui.screens.mylist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fmhub24.android.R
import com.fmhub24.android.data.model.ContentType
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.ui.components.EmptyState
import com.fmhub24.android.ui.components.PosterCard
import com.fmhub24.android.ui.components.stringRes
import com.fmhub24.android.ui.screens.profile.ProfileDataViewModel

/** My list (spec 6.9): a 3-column poster grid, long press removes an entry. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyListScreen(
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    viewModel: ProfileDataViewModel = hiltViewModel(),
) {
    val entries by viewModel.myList.collectAsState()
    val catalog by viewModel.catalogItems.collectAsState()

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
                text = stringRes(R.string.my_list_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (entries.isEmpty()) {
            EmptyState(
                title = stringRes(R.string.empty_my_list_title),
                detail = stringRes(R.string.empty_my_list_detail),
                icon = Icons.Outlined.Bookmark,
            )
        } else {
            // Join the stored snapshot with the catalog so the grid also works offline.
            val items = entries.map { entry ->
                catalog.firstOrNull { it.id == entry.contentId } ?: MediaItem(
                    id = entry.contentId,
                    canonicalKey = null,
                    title = entry.title,
                    originalTitle = null,
                    contentType = entry.contentType,
                    releaseYear = null,
                    posterUrl = entry.posterUrl,
                    description = null,
                    genres = emptyList(),
                    status = null,
                    rating = null,
                    createdAt = null,
                    updatedAt = null,
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    Column {
                        PosterCard(
                            item = item,
                            onClick = { onOpenDetails(item.id) },
                            showTitle = true,
                            modifier = Modifier.combinedClickable(
                                onClick = { onOpenDetails(item.id) },
                                onLongClick = { viewModel.removeFromMyList(item.id) },
                            ),
                        )
                    }
                }
            }
        }
    }
}