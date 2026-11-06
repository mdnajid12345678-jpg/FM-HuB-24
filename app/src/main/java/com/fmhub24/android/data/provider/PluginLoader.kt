package com.fmhub24.android.data.provider

import android.content.Context
import android.util.Log
import com.fmhub24.provider.api.FmHubProvider
import dalvik.system.DexClassLoader
import java.io.File
import java.util.zip.ZipFile
import org.json.JSONObject

/**
 * Loads `.fmkp` provider plugins.
 *
 * A plugin is a zip with the same layout the build tool produces (see
 * `gradle/provider-plugin.gradle.kts`): `manifest.json` and `classes.dex`. The dex is loaded with a
 * [DexClassLoader] whose parent is the app's class loader, so the plugin compiles against
 * `:provider-api` but uses the app's copy of those classes at runtime.
 *
 * A plugin that is corrupt, built for a newer ABI or missing its class is skipped and logged: one
 * bad plugin must never stop the app from starting or from using the built-in providers.
 */
object PluginLoader {
    /** The ABI version this build understands. Bumped only when the interface changes. */
    const val SUPPORTED_ABI = 1

    const val EXTENSION = "fmkp"

    private const val TAG = "ProviderPlugin"

    data class Manifest(
        val id: String,
        val name: String,
        val version: Int,
        val abi: Int,
        val providerClassName: String,
        val requiresLive: Boolean,
    )

    data class Loaded(val manifest: Manifest, val provider: FmHubProvider)

    /** Where the app looks for installed plugins. */
    fun pluginDirectory(context: Context): File =
        File(context.filesDir, "providers").apply { mkdirs() }

    fun loadAll(context: Context): List<Loaded> = pluginDirectory(context)
        .listFiles()
        ?.filter { it.isFile && it.extension.equals(EXTENSION, ignoreCase = true) }
        ?.sortedBy { it.name }
        ?.mapNotNull { file ->
            runCatching { load(context, file) }
                .onFailure { error -> Log.w(TAG, "Skipping plugin ${file.name}: ${error.message}") }
                .getOrNull()
        }
        .orEmpty()
        .distinctBy { it.manifest.id }

    private fun load(context: Context, file: File): Loaded {
        val manifest = ZipFile(file).use { zip ->
            val entry = zip.getEntry("manifest.json")
                ?: throw IllegalStateException("manifest.json is missing")
            val text = zip.getInputStream(entry).use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
            parseManifest(text)
        }
        require(manifest.abi == SUPPORTED_ABI) {
            "plugin ABI ${manifest.abi} is not supported (this build reads $SUPPORTED_ABI)"
        }
        require(manifest.providerClassName.isNotBlank()) { "providerClassName is missing" }

        val dexDirectory = File(context.codeCacheDir, "provider-plugins/${manifest.id.asDirectoryName()}")
            .apply { mkdirs() }
        val dexFile = File(dexDirectory, "${file.nameWithoutExtension}.dex")
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("classes.dex") ?: throw IllegalStateException("classes.dex is missing")
            zip.getInputStream(entry).use { input ->
                dexFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        val classLoader = DexClassLoader(
            dexFile.absolutePath,
            dexDirectory.absolutePath,
            null,
            context.classLoader,
        )
        val instance = Class.forName(manifest.providerClassName, true, classLoader)
            .getDeclaredConstructor()
            .newInstance()
        val provider = instance as? FmHubProvider
            ?: throw IllegalStateException("${manifest.providerClassName} does not implement FmHubProvider")
        return Loaded(manifest, provider)
    }

    private fun parseManifest(text: String): Manifest {
        val json = JSONObject(text)
        val id = json.optString("id").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("manifest id is missing")
        return Manifest(
            id = id,
            name = json.optString("name").takeIf { it.isNotBlank() } ?: id,
            version = json.optInt("version", 1),
            abi = json.optInt("abi", 0),
            providerClassName = json.optString("providerClassName"),
            requiresLive = json.optBoolean("requiresLive", false),
        )
    }

    private fun String.asDirectoryName(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")
}
