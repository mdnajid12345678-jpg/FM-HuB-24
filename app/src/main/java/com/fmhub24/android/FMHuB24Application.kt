package com.fmhub24.android

import android.app.Application
import com.fmhub24.android.stream.DownloadEngine
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FMHuB24Application : Application() {
    override fun onCreate() {
        super.onCreate()
        // Media3's DownloadManager must be built on a thread that owns a Looper, and the
        // download service expects the same single instance (spec 6.8 and 7).
        DownloadEngine.init(this)
    }
}
