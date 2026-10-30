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
- Profile/About, Settings (theme, language, downloads, playback), in-app privacy
  and terms summaries. Automatic update installation is not configured; About
  says so clearly and links to the official GitHub releases page.
- Every list has an empty state; report-sending states honestly that the
  backend endpoint is not implemented yet.

The stream error classifier has unit coverage for offline, timeout/TLS and HTTP
404 cases. Release builds enable R8 shrinking/obfuscation. The GitHub Actions
workflow runs the unit tests and builds a debug APK on pushes to `main`.

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
- Release signing is intentionally not configured until the app owner provides
  and stores a signing key securely; the Actions artifact is the debug APK.

## Supabase access the app needs

The catalog is read through PostgREST as the `anon`/`authenticated` roles, so the
project must allow that. Two things blank the app with an empty catalog if they
are missing, and both were the cause of "no content" on 2026-10-30:

1. **Anonymous sign-in** must be enabled (Dashboard → Authentication → Sign In /
   Up → Allow anonymous sign-ins). The app creates a session with
   `POST /auth/v1/signup` and no credentials; if that fails it keeps reading with
   the publishable key itself, which PostgREST runs as the `anon` role.
2. **Table privileges and read policies** for the four tables the app reads:

```sql
GRANT USAGE ON SCHEMA public TO anon, authenticated;
GRANT SELECT ON public.media_contents, public.seasons,
                public.episodes, public.media_sources TO anon, authenticated;

CREATE POLICY catalog_read ON public.media_contents FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY catalog_read ON public.seasons       FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY catalog_read ON public.episodes       FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY catalog_read ON public.media_sources  FOR SELECT TO anon, authenticated USING (true);
```

Without the `GRANT` PostgREST answers `permission denied for table`; without a
permissive `SELECT` policy it answers `[]` — in both cases the app has nothing to
show. Writes stay blocked: the existing `deny_client_all` policy still covers
`INSERT`/`UPDATE`/`DELETE`, and no permissive write policy exists.

Security note: a broad anonymous `SELECT` policy on `media_sources` can expose
all source URLs and headers to anyone with the public anon key. Use row-level
restrictions or a server-side/edge function for short-lived source resolution;
never embed a `service_role` key in the APK.

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
