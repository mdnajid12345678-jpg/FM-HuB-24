package com.fmhub24.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    // ISO-8601 timestamps sort correctly as plain text, so no datetime() conversion is needed.
    @Query("SELECT * FROM media_contents ORDER BY updatedAt DESC")
    fun observeContents(): Flow<List<MediaContentEntity>>

    @Query("SELECT * FROM media_contents ORDER BY updatedAt DESC")
    suspend fun contents(): List<MediaContentEntity>

    @Query("SELECT * FROM media_contents WHERE id = :id")
    suspend fun content(id: String): MediaContentEntity?

    @Query("DELETE FROM media_contents WHERE id = :id")
    suspend fun deleteContent(id: String)

    @Query("SELECT MAX(updatedAt) FROM media_contents")
    suspend fun latestUpdatedAt(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContents(items: List<MediaContentEntity>)

    @Query("SELECT * FROM seasons WHERE contentId = :contentId ORDER BY seasonNumber")
    fun observeSeasons(contentId: String): Flow<List<SeasonEntity>>

    @Query("SELECT * FROM seasons WHERE contentId = :contentId ORDER BY seasonNumber")
    suspend fun seasons(contentId: String): List<SeasonEntity>

    @Query("DELETE FROM seasons WHERE contentId = :contentId")
    suspend fun clearSeasons(contentId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeasons(items: List<SeasonEntity>)

    @Query("SELECT * FROM episodes WHERE contentId = :contentId ORDER BY seasonNumber, episodeNumber, COALESCE(sortOrder, 0)")
    fun observeEpisodes(contentId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE contentId = :contentId ORDER BY seasonNumber, episodeNumber, COALESCE(sortOrder, 0)")
    suspend fun episodes(contentId: String): List<EpisodeEntity>

    @Query("DELETE FROM episodes WHERE contentId = :contentId")
    suspend fun clearEpisodes(contentId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodes(items: List<EpisodeEntity>)

    @Query("SELECT * FROM media_contents WHERE title LIKE '%' || :query || '%' OR IFNULL(originalTitle, '') LIKE '%' || :query || '%' ORDER BY title")
    fun observeSearch(query: String): Flow<List<MediaContentEntity>>
}

@Dao
interface WatchStateDao {
    @Query("SELECT * FROM history ORDER BY watchedAt DESC")
    fun observeHistory(): Flow<List<HistoryEntity>>

    /** [key] is the episode key for a series item and the content id for a movie. */
    @Query("SELECT * FROM history WHERE key = :key")
    suspend fun historyByKey(key: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE key = :key")
    suspend fun deleteHistory(key: String)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("SELECT * FROM my_list ORDER BY addedAt DESC")
    fun observeMyList(): Flow<List<MyListEntity>>

    @Query("SELECT * FROM my_list WHERE contentId = :contentId")
    suspend fun myListItem(contentId: String): MyListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMyList(item: MyListEntity)

    @Query("DELETE FROM my_list WHERE contentId = :contentId")
    suspend fun deleteMyList(contentId: String)

    @Query("SELECT * FROM favorite_channels ORDER BY title")
    fun observeFavorites(): Flow<List<FavoriteChannelEntity>>

    @Query("SELECT * FROM favorite_channels WHERE contentId = :contentId")
    suspend fun favorite(contentId: String): FavoriteChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(item: FavoriteChannelEntity)

    @Query("DELETE FROM favorite_channels WHERE contentId = :contentId")
    suspend fun deleteFavorite(contentId: String)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 50")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearch(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}

@Dao
interface DownloadsDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun byId(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadEntity)

    @Query("UPDATE downloads SET title = :title WHERE id = :id")
    suspend fun renameTitle(id: String, title: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM downloads WHERE contentId = :contentId AND IFNULL(episodeKey, '') = IFNULL(:episodeKey, '')")
    suspend fun deleteByTarget(contentId: String, episodeKey: String?)

    @Query("DELETE FROM downloads WHERE contentId = :contentId")
    suspend fun deleteByContentId(contentId: String)
}
