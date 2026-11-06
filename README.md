# FMHuB24 — JUST WATCH FREE

Native Android streaming app (Kotlin, Jetpack Compose, Material 3) built from the
FMHuB24 Android Streaming App Specification. Android only, distributed as a
direct APK; content comes from pluggable providers (no backend of our own).

## Current status (all build-order stages implemented)

Stage 1 — shell:

- Project setup: AGP 9.3, Kotlin 2.4, Hilt, Room, Media3, Coil, provider ABI.
- Custom theme with the exact specification palette: Dark (default), Light, System.
- `FmHubLoader`: the five-layer brand animation on a 2.6 s loop, with a static
  fallback when the system animation setting is disabled.
- Android system splash (static mark) + in-app splash with wordmark and tagline.
- First-run language picker (বাংলা / English), persisted per-app language, and
  appearance/language settings rows.

Stage 2 — data layer:

- **Provider layer** (see below): catalog, seasons/episodes and playable links are
  read through `ContentProvider`, implemented by the shared ABI in `:provider-api`
  and served by `ProviderRegistry`.
- Room cache for the catalog, so Home, Explore, Search, My List and History keep
  working offline. Playable links are **never** cached: they are read fresh for
  every playback or download, together with their request headers
  (Referer/User-Agent).
- Repositories for catalog, sources, watch state and downloads; user
  preferences (downloads, playback, language, appearance).

Stages 3–7 — screens:

- Home rows ("Recently added" — the catalog has no popularity field), Explore
  with filters, Search, See-all, Details with seasons/episodes and source picker.
- Player: Media3 playback on a `SurfaceView` with Compose overlays, source
  switching on failure, track selection, and honest failure states (expired link,
  offline, blocked, report-sent) — the stream URL is never shown or shared.
- Series, My List, History, Downloads (Media3 `DownloadService`, per-item
  progress, delete), Live TV with categories and a channel player.
- Profile/About, Settings (theme, language, downloads, playback), in-app privacy
  and terms summaries. Automatic update installation is not configured; About
  says so clearly and links to the official GitHub releases page.
- Every list has an empty state; report-sending states honestly that the
  report endpoint is not implemented yet.

The stream error classifier has unit coverage for offline, timeout/TLS and HTTP
404 cases. Release builds enable R8 shrinking/obfuscation. The GitHub Actions
workflow runs the unit tests, builds a debug APK **and** builds both provider
plugins on pushes to `main`.

Screen layouts, colours, chips, rows and badges follow the attached
`FMHuB24_Designs.html` mockups through the palette in `ui/theme/Color.kt`: forest
green surfaces (background `#08130F`, surface `#102019`, elevated `#14221D`),
warm amber accent `#F4B84A` and muted `#9BAAA2` secondary text. The bottom bar is
a floating pill with the active destination labelled in amber.

## Content providers

Nothing in the UI knows where a title comes from. The app asks
`com.fmhub24.android.data.provider.ContentProvider` for a catalog, details,
episodes and playable links; `ProviderRegistry` picks the first catalog provider
and the first live provider from:

1. plugins installed in `filesDir/providers/*.fmkp` (a plugin can replace a
   built-in source without an app update), then
2. the providers compiled into the APK.

Providers shipped in the APK:

| Provider | Module | What it serves |
| --- | --- | --- |
| Internet Archive (`archive`) | `:provider-archive` | Public-domain / openly licensed films from `archive.org` (advancedsearch → metadata → download) |
| Public IPTV (`iptv`) | `:provider-iptv` | Live channels from the public `iptv-org` playlist, including each stream's declared User-Agent/Referer |

Structure (seasons and episodes) is fetched on demand when a title is opened, so
refreshing the catalog never means one request per title. A provider that cannot
reach its source reports an offline error instead of an empty catalog.

### The ABI (`:provider-api`)

`FmHubProvider` is plain Kotlin with no Android or coroutine types, so the same
interface is compiled into the app and into a plugin dex:

```kotlin
interface FmHubProvider {
    val id: String
    val name: String
    val supportsLive: Boolean
    fun catalog(): List<ProviderMedia>
    fun search(query: String): List<ProviderMedia>
    fun media(id: String): ProviderMedia?
    fun seasons(contentId: String): List<ProviderSeason>
    fun episodes(contentId: String): List<ProviderEpisode>
    fun sources(contentId: String, episode: ProviderEpisode?): List<ProviderSource>
    fun liveChannels(): List<ProviderMedia>
    fun liveSources(channelId: String): List<ProviderSource>
}
```

Calls block; the app runs them on its IO dispatcher. `ProviderHttp` (same module)
offers a small `HttpURLConnection` helper so a provider needs no extra library.

### Plugins (`.fmkp`)

A plugin is a zip with the same layout Cloudstream uses for `.cs3`:

```
archive.fmkp
├── manifest.json
└── classes.dex
```

```json
{
  "id": "archive",
  "name": "Internet Archive",
  "version": 1,
  "abi": 1,
  "providerClassName": "com.fmhub24.provider.archive.ArchiveProvider",
  "requiresLive": false
}
```

`PluginLoader` copies `classes.dex` into `codeCacheDir` and loads it with a
`DexClassLoader` whose parent is the app class loader, so the plugin compiles
against `:provider-api` and uses the app's copy of those classes at runtime. A
plugin with a newer ABI, a missing class or a corrupt zip is logged and skipped —
one bad plugin never stops the app.

**Security note:** plugins run in-process with the app's permissions. Only install
`.fmkp` files you built or trust, and never ship a plugin you have not reviewed.

## Building a provider plugin

A provider module applies the shared packaging script and keeps its manifest in
`plugin/manifest.json`:

```kotlin
// provider-archive/build.gradle.kts
plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":provider-api"))
    compileOnly("org.json:json:20240303") // Android provides org.json at runtime
}

apply(from = rootProject.file("gradle/provider-plugin.gradle.kts"))
```

Then:

```bash
./gradlew :provider-archive:buildPlugin
# -> provider-archive/build/plugin/archive.fmkp
```

`buildPlugin` (in `gradle/provider-plugin.gradle.kts`) dexes the module jar with
`d8` from the Android build-tools and zips it with the manifest; it fails loudly
when `d8` or `android.jar` is missing instead of producing a broken plugin. CI
runs both `buildPlugin` tasks and uploads the plugins as the
**`fmhub24-provider-plugins`** artifact, so the tooling is verified on every push.

## Building the APK on GitHub

Every push to `main` runs [`.github/workflows/android.yml`](.github/workflows/android.yml),
which runs the unit tests, builds a debug APK and uploads it as the workflow
artifact **`fmhub24-debug-apk`** (Actions → latest run → Artifacts). No repository
secrets are required.

Release signing is intentionally not configured until the app owner provides and
stores a signing key securely; the Actions artifact is the debug APK.

## Local build

Requires JDK 17 and an Android SDK with `platforms;android-37.0` and
`build-tools;36.0.0` (API 37 installs as `android-37.0`, handled by
`compileSdkMinor = 0`; the same build-tools supply `d8` for the plugin tasks).

```bash
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

./gradlew :provider-archive:buildPlugin :provider-iptv:buildPlugin
# plugins: provider-*/build/plugin/*.fmkp
```
