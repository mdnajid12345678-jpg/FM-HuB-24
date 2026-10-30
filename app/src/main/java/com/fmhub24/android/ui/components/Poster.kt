package com.fmhub24.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fmhub24.android.data.model.MediaItem

/** 2:3 poster card. Home rows show the poster only; grids add the title below. */
@Composable
fun PosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = false,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        RemoteImage(
            url = item.posterUrl,
            contentDescription = item.title,
            aspectRatio = 2f / 3f,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showTitle) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.releaseYear != null) {
                Text(
                    text = item.releaseYear.toString(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 16:9 thumbnail used by Continue watching and episode rows. */
@Composable
fun LandscapeThumb(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    Box(modifier = modifier) {
        RemoteImage(
            url = url,
            contentDescription = contentDescription,
            aspectRatio = 16f / 9f,
            modifier = Modifier.fillMaxWidth(),
        )
        if (progress != null && progress > 0f) {
            ThinProgress(
                progress = progress,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
