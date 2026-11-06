package com.fmhub24.android.data.provider

import android.content.Context
import android.util.Log
import com.fmhub24.provider.archive.ArchiveProvider
import com.fmhub24.provider.iptv.IptvOrgProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place that knows which providers exist.
 *
 * Catalog reads use the first catalog provider and live reads use the first live provider, so the
 * app never merges two sources into one list (which would duplicate ids and break the cache keys).
 * Installed plugins come first, then the providers compiled into the app, so a plugin can replace a
 * built-in source without an app update.
 */
@Singleton
class ProviderRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val builtIn: List<ContentProvider> = listOf(
        BundledContentProvider(ArchiveProvider()),
        BundledContentProvider(IptvOrgProvider()),
    )

    @Volatile
    private var plugins: List<ContentProvider> = emptyList()

    init {
        reloadPlugins()
    }

    /** Rescans the plugin folder after an install; a broken plugin is logged and skipped. */
    fun reloadPlugins() {
        plugins = PluginLoader.loadAll(context)
            .sortedBy { it.manifest.name }
            .mapNotNull { loaded ->
                runCatching { BundledContentProvider(loaded.provider) }
                    .onFailure { error ->
                        Log.w(TAG, "Skipping plugin ${loaded.manifest.id}: ${error.message}")
                    }
                    .getOrNull()
            }
    }

    /** Installed plugins first, then the built-in providers. */
    fun all(): List<ContentProvider> = plugins + builtIn

    fun catalogProviders(): List<ContentProvider> = all().filter { !it.supportsLive }

    fun liveProviders(): List<ContentProvider> = all().filter { it.supportsLive }

    fun catalogProvider(): ContentProvider? = catalogProviders().firstOrNull()

    fun liveProvider(): ContentProvider? = liveProviders().firstOrNull()

    private companion object {
        const val TAG = "ProviderRegistry"
    }
}
