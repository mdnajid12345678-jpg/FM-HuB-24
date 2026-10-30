package com.fmhub24.android.ui.screens.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fmhub24.android.R
import com.fmhub24.android.ui.components.EmptyState
import com.fmhub24.android.ui.components.LandscapeThumb
import com.fmhub24.android.ui.components.stringRes
import com.fmhub24.android.ui.screens.profile.ProfileDataViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** History (spec 6.9): grouped by Today, Yesterday and so on, with Remove and Clear. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: ProfileDataViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsState()
    var menuKey by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

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
                text = stringRes(R.string.history_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (history.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(
                        Icons.Outlined.DeleteSweep,
                        contentDescription = stringRes(R.string.clear_history),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (history.isEmpty()) {
            EmptyState(
                title = stringRes(R.string.empty_history_title),
                detail = stringRes(R.string.empty_history_detail),
                icon = Icons.Outlined.History,
            )
        } else {
            val grouped = history.groupBy { entry -> dayLabel(entry.watchedAt) }
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                grouped.forEach { (label, entries) ->
                    item(key = "header-$label") {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(entries, key = { it.key }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { /* opening is handled by the caller's route */ }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.width(120.dp)) {
                                LandscapeThumb(
                                    url = entry.posterUrl,
                                    contentDescription = entry.title,
                                    progress = entry.progress,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (entry.isEpisode) {
                                    Text(
                                        text = entry.episodeLabel,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { menuKey = entry.key }) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    menuKey?.let { key ->
        DropdownMenu(expanded = true, onDismissRequest = { menuKey = null }) {
            DropdownMenuItem(
                text = { Text(stringRes(R.string.remove_item)) },
                onClick = {
                    viewModel.removeHistory(key)
                    menuKey = null
                },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringRes(R.string.clear_history)) },
            text = { Text(stringRes(R.string.clear_history_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    confirmClear = false
                }) { Text(stringRes(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringRes(R.string.action_cancel))
                }
            },
        )
    }
}

@androidx.compose.runtime.Composable
private fun dayLabel(timestamp: Long): String {
    val todayLabel = stringRes(R.string.history_today)
    val yesterdayLabel = stringRes(R.string.history_yesterday)
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = Calendar.getInstance()
    return when {
        sameDay(calendar, now) -> todayLabel
        isYesterday(calendar, now) -> yesterdayLabel
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(java.util.Date(timestamp))
    }
}

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun isYesterday(calendar: Calendar, today: Calendar): Boolean {
    val copy = today.clone() as Calendar
    copy.add(Calendar.DAY_OF_YEAR, -1)
    return sameDay(calendar, copy)
}
