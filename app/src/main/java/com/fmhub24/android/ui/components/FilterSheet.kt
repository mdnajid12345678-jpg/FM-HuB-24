package com.fmhub24.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fmhub24.android.R
import com.fmhub24.android.data.model.ContentType
import com.fmhub24.android.data.model.MediaItem

/** Filter values shared by Explore and See all (spec 6.4). */
data class CatalogFilter(
    val types: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val languages: Set<String> = emptySet(),
) {
    val isActive: Boolean
        get() = types.isNotEmpty() || genres.isNotEmpty() ||
            yearFrom != null || yearTo != null || languages.isNotEmpty()

    fun reset() = CatalogFilter()

    /**
     * @param languageIndex languages per title, or null while the index is still being read —
     *   the language clause is then skipped instead of rejecting every title.
     */
    fun matches(
        item: MediaItem,
        languageIndex: Map<String, Set<String>>? = null,
    ): Boolean {
        if (types.isNotEmpty() && typeOf(item.contentType) !in types) return false
        if (genres.isNotEmpty() && item.genres.none { genre -> genres.any { it.equals(genre, true) } }) {
            return false
        }
        val year = item.releaseYear
        if (yearFrom != null && (year == null || year < yearFrom)) return false
        if (yearTo != null && (year == null || year > yearTo)) return false
        if (languages.isNotEmpty() && languageIndex != null) {
            val known = languageIndex[item.id] ?: return false
            if (known.none { language -> languages.any { it.equals(language, true) } }) return false
        }
        return true
    }

    companion object {
        const val MOVIES = "movies"
        const val SERIES = "series"
        const val ANIME = "anime"
        const val LIVE = "live"
        const val DOCUMENTARY = "documentary"
        const val OTHER = "other"

        fun typeOf(contentType: ContentType): String = when (contentType) {
            ContentType.MOVIE, ContentType.ANIME_MOVIE -> MOVIES
            ContentType.SERIES, ContentType.ASIAN_DRAMA, ContentType.CARTOON -> SERIES
            ContentType.ANIME_SERIES, ContentType.OVA -> ANIME
            ContentType.LIVE -> LIVE
            ContentType.DOCUMENTARY -> DOCUMENTARY
            ContentType.OTHER -> OTHER
        }
    }
}

/** Bottom sheet: Type, Genre (multi), Year range, Language, Reset and "Show N results". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    genres: List<String>,
    languages: List<String>,
    initial: CatalogFilter,
    countFor: (CatalogFilter) -> Int,
    onDismiss: () -> Unit,
    onApply: (CatalogFilter) -> Unit,
    languagesLoading: Boolean = false,
) {
    var types by remember { mutableStateOf(initial.types) }
    var selectedGenres by remember { mutableStateOf(initial.genres) }
    var selectedLanguages by remember { mutableStateOf(initial.languages) }
    var yearFrom by remember { mutableStateOf(initial.yearFrom?.toString() ?: "") }
    var yearTo by remember { mutableStateOf(initial.yearTo?.toString() ?: "") }

    val typeOptions = listOf(
        CatalogFilter.MOVIES to stringRes(R.string.category_movies),
        CatalogFilter.SERIES to stringRes(R.string.category_series),
        CatalogFilter.ANIME to stringRes(R.string.category_anime),
        CatalogFilter.LIVE to stringRes(R.string.section_live_tv),
        CatalogFilter.DOCUMENTARY to stringRes(R.string.section_documentary),
        CatalogFilter.OTHER to stringRes(R.string.type_other),
    )

    fun buildFilter() = CatalogFilter(
        types = types,
        genres = selectedGenres,
        yearFrom = yearFrom.toIntOrNull(),
        yearTo = yearTo.toIntOrNull(),
        languages = selectedLanguages,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringRes(R.string.action_filter),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                androidx.compose.material3.TextButton(onClick = {
                    types = emptySet()
                    selectedGenres = emptySet()
                    selectedLanguages = emptySet()
                    yearFrom = ""
                    yearTo = ""
                }) {
                    Text(stringRes(R.string.filter_reset), fontSize = 12.sp)
                }
            }

            FilterGroup(title = stringRes(R.string.filter_type)) {
                typeOptions.forEach { (value, label) ->
                    SelectChip(
                        text = label,
                        selected = value in types,
                        onClick = {
                            types = if (value in types) types - value else types + value
                        },
                    )
                }
            }

            if (genres.isNotEmpty()) {
                FilterGroup(title = stringRes(R.string.filter_genre)) {
                    genres.forEach { genre ->
                        SelectChip(
                            text = genre,
                            selected = selectedGenres.any { it.equals(genre, true) },
                            onClick = {
                                selectedGenres = if (selectedGenres.any { it.equals(genre, true) }) {
                                    selectedGenres.filterNot { it.equals(genre, true) }.toSet()
                                } else {
                                    selectedGenres + genre
                                }
                            },
                        )
                    }
                }
            }

            FilterGroup(title = stringRes(R.string.filter_year)) {
                YearField(stringRes(R.string.year_from), yearFrom) { yearFrom = it }
                YearField(stringRes(R.string.year_to), yearTo) { yearTo = it }
            }

            if (languages.isNotEmpty()) {
                FilterGroup(title = stringRes(R.string.filter_language)) {
                    languages.forEach { language ->
                        SelectChip(
                            text = language,
                            selected = selectedLanguages.any { it.equals(language, true) },
                            onClick = {
                                selectedLanguages = if (selectedLanguages.any { it.equals(language, true) }) {
                                    selectedLanguages.filterNot { it.equals(language, true) }.toSet()
                                } else {
                                    selectedLanguages + language
                                }
                            },
                        )
                    }
                }
            }

            if (languages.isEmpty()) {
                Text(
                    text = stringRes(
                        if (languagesLoading) {
                            R.string.filter_languages_loading
                        } else {
                            R.string.filter_languages_none
                        },
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))
            // Recomputed on every tick so the count follows the chips as they are toggled.
            PrimaryButton(
                text = stringRes(R.string.filter_show_results, countFor(buildFilter())),
                onClick = { onApply(buildFilter()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun YearField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() }.take(4)) },
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(160.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

private fun Modifier.widthFallback(dp: androidx.compose.ui.unit.Dp): Modifier = this
