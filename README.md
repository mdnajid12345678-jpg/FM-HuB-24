# FMHuB24 — JUST WATCH FREE

Native Android streaming app (Kotlin, Jetpack Compose, Material 3) built from the
FMHuB24 Android Streaming App Build Specification. Android only, distributed as a
direct APK; backend is Supabase, read-only, anonymous sign-in.

## Current status (stage 1)

Implemented (build order step 1):

- Project setup: AGP 9.3, Kotlin 2.4, Hilt, Room, Media3, Coil, Supabase Kotlin SDK.
- Custom theme with the exact specification palette: Dark (default), Light, System.
- `FmHubLoader`: the five-layer brand animation on a 2.6 s loop, with a static
  fallback when the system animation setting is disabled.
- Android system splash (static mark) + in-app splash with wordmark and tagline.
- First-run language picker (বাংলা / English), persisted per-app language, and
  appearance/language settings rows.
- Honest empty state: the catalog is **not connected yet**, so the home screen
  shows that message instead of any sample or placeholder titles.

Not built yet (later stages): catalog sync, Home rows, Explore/Search, details,
player, series, downloads, Live TV, Profile/About, update dialogs.

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
