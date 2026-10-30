package com.fmhub24.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaContentEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        HistoryEntity::class,
        MyListEntity::class,
        FavoriteChannelEntity::class,
        SearchHistoryEntity::class,
        DownloadEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FmHubDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun watchStateDao(): WatchStateDao
    abstract fun downloadsDao(): DownloadsDao
}
