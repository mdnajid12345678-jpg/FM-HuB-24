# FMHuB24 — JUST WATCH FREE

Native Android streaming app (Kotlin, Jetpack Compose, Material 3) built from the
FMHuB24 Android Streaming App Build Specification. Android only, distributed as a
direct APK; backend is Supabase, read-only, anonymous sign-in.

## Current status (all build-order stages implemented)

Stage 1 — shell:

- Project setup: AGP 9.3, Kotlin 2.4, Hilt, Room, Media3, Coil, Supabase Kotlin SDK.
- Custom theme with the exact specification palette: Dark (default), Light, System.
- `FmHubLoader`: the five-layer brand animation on a 2.6 s loop, with a static
  fallback when the system animation setting is disabled.
- Android system splash (static mark) + in-app splash with wordmark and tagline.
- First-run language picker (বাংলা / English), persisted per-app language, and
  appearance/language settings rows.

Stage 2 — data layer:

- Supabase PostgREST client (anon key + anonymous sign-in only, never a
  `service_role` key), token refresh and session persistence.
- Room cache for the catalog; `media_sources` is **never** cached — it is always
  read live, filtered to `status = 'resolved'`, and resolved per request with its
  `headers` (Referer/User-Agent).
- Repositories for catalog, sources, watch state and downloads; user
  preferences (downloads, playback, language, appearance).

Stages 3–7 — screens:

- Home rows ("Recently added" — the catalog has no popularity field), Explore
  with filters, Search, See-all, Details with seasons/episodes and source picker.
- Player: Media3 playback on a `SurfaceView` with Compose overlays, source
  switching on failure, track selection, and honest failure states (expired link,
  offline, blocked, report-sent) — `media_url` is never shown or shared.
- Series, My List, History, Downloads (Media3 `DownloadService`, per-item
  progress, delete), Live TV with categories and a channel player.
- Profile/About, Settings (theme, language, downloads, playback), update
  dialogs (fetches the release feed, shows an honest message when unavailable).
- Every list has an empty state; report-sending states honestly that the
  backend endpoint is not implemented yet.

Verified locally: `assembleDebug` and `testDebugUnitTest` both pass (no unit
test sources yet); CI builds the APK on every push to `main`.

Screen layouts, colours, chips, rows, badges and the splash timeline follow the
attached `FMHuB24_Designs.html` mockups (accent `#2F78EE`, background `#05052B`,
elevated `#16165A`, text-only tab bar with `#7DB0FF` active).

## Building the APK on GitHub

Every push to `main` runs [`.github/workflows/android.yml`](.github/workflows/android.yml),
which builds a debug APK and uploads it as the workflow artifact
**`fmhub24-debug-apk`** (Actions → latest run → Artifacts).

Required repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `SUPABASE_URL` | your Supabase project URL (https://…) |
| `SUPABASE_ANON_KEY` | the publishable/anon key only |

Notes:

- Both values are injected into `BuildConfig` at build time; they are never
  committed to this repository. The workflow fails early with a clear message if
  either secret is missing.
- Never put a `service_role` key in a secret here — it would end up inside the APK.

## Local build

Requires JDK 17 and an Android SDK with `platforms;android-37.0` and
`build-tools;36.0.0` (API 37 installs as `android-37.0`, handled by
`compileSdkMinor = 0`).

```bash
export SUPABASE_URL="https://your-project.supabase.co"
export SUPABASE_ANON_KEY="your-anon-key"
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
