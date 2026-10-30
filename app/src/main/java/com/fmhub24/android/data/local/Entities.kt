package com.fmhub24.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local catalog cache. media_sources is deliberately never cached: those rows hold short-lived
 * signed links that must be read fresh at playback time (spec 5.5).
 */
@Entity(tableName = "media_contents")
data class MediaContentEntity(
    @PrimaryKey val id: String,
    val canonicalKey: String?,
    val title: String,
    val originalTitle: String?,
    val contentType: String,
    val releaseYear: Int?,
    val posterUrl: String?,
    val description: String?,
    /** Raw JSON array text, parsed when mapped to the domain model. */
    val genresJson: String?,
    val status: String?,
    val rating: Double?,
    val createdAt: String?,
    val updatedAt: String?,
)

@Entity(
    tableName = "seasons",
    primaryKeys = ["contentId", "seasonNumber"],
)
data class SeasonEntity(
    val contentId: String,
    val seasonNumber: Int,
    val name: String?,
    val sortOrder: Int?,
)

@Entity(tableName = "episodes")
data class EpisodeEntity(
    /** "$contentId|$seasonNumber|$episodeNumber" – stable, because the table may have no id. */
    @PrimaryKey val key: String,
    val contentId: String,
    val episodeId: String?,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String?,
    val description: String?,
    val posterUrl: String?,
    val sortOrder: Int?,
)

/** Watch history with resume position, stored only on the phone (spec 5.5). */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val key: String,
    val contentId: String,
    val title: String,
    val posterUrl: String?,
    val contentType: String,
    /** null for a movie. */
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val watchedAt: Long,
)

@Entity(tableName = "my_list")
data class MyListEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val posterUrl: String?,
    val contentType: String,
    val addedAt: Long,
)

@Entity(tableName = "favorite_channels")
data class FavoriteChannelEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val posterUrl: String?,
    val genresJson: String?,
    val addedAt: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)

/** Room metadata for a download started through Media3 (spec 5.5 and 6.8). */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val contentId: String,
    /** null for a movie. */
    val episodeKey: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val title: String,
    val posterUrl: String?,
    val contentType: String,
    val serverName: String,
    val language: String?,
    val quality: Int?,
    /** Bytes when known; null means "approximate" or still unknown. */
    val sizeBytes: Long?,
    val createdAt: Long,
)
