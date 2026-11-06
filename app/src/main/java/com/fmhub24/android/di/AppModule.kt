package com.fmhub24.android.di

import android.content.Context
import androidx.room.Room
import com.fmhub24.android.data.local.CatalogDao
import com.fmhub24.android.data.local.DownloadsDao
import com.fmhub24.android.data.local.FmHubDatabase
import com.fmhub24.android.data.local.WatchStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** Shared by the media data sources, Coil and the player's network stack. */
    const val REQUEST_TIMEOUT_SECONDS = 30L

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FmHubDatabase =
        Room.databaseBuilder(context, FmHubDatabase::class.java, "fmhub24.db")
            .build()

    @Provides
    fun provideCatalogDao(database: FmHubDatabase): CatalogDao = database.catalogDao()

    @Provides
    fun provideWatchStateDao(database: FmHubDatabase): WatchStateDao = database.watchStateDao()

    @Provides
    fun provideDownloadsDao(database: FmHubDatabase): DownloadsDao = database.downloadsDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
