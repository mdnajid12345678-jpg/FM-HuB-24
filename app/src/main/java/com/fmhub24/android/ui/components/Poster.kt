package com.fmhub24.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fmhub24.android.data.model.MediaItem

private val PosterShape = RoundedCornerShape(12.dp)

/** 2:3 poster card with rounded corners, a soft shadow and a press-scale animation. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "posterPress",
    )
    // One gesture modifier only: a second clickable passed in by a caller would swallow the
    // long press (that is why My list's long-press-to-remove never fired).
    val gesture = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    }
    Column(
        modifier = modifier
            .scale(scale)
            .then(gesture),
    ) {
        RemoteImage(
            url = item.posterUrl,
            contentDescription = item.title,
            aspectRatio = 2f / 3f,
            modifier = Modifier
                .fillMaxWidth()
                .clip(PosterShape),
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
    Box(modifier = modifier.clip(PosterShape)) {
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
