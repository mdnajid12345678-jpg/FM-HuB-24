package com.fmhub24.android

import android.app.Application
import com.fmhub24.android.data.UserPreferencesRepository
import com.fmhub24.android.stream.DownloadEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@HiltAndroidApp
class FMHuB24Application : Application() {
    @Inject lateinit var preferences: UserPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        // Media3's DownloadManager must be built on a thread that owns a Looper, and the
        // download service expects the same single instance (spec 6.8 and 7).
        DownloadEngine.init(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            preferences.settings.collect { settings ->
                DownloadEngine.configure(
                    this@FMHuB24Application,
                    settings.wifiOnlyDownloads,
                    settings.simultaneousDownloads,
                )
            }
        }
    }
}
