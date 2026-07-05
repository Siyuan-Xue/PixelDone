PixelDone v2.10.0-rc.3 is a beta release candidate for the local-first storage and optional Supabase cloud-sync path.

Highlights:
- Keeps the app local-first while adding optional email/password sign-in through the Settings cloud area.
- Opens sign-in in the same bottom-panel interaction language as todo and checklist editing, instead of adding a login landing page.
- Adds Supabase Auth/PostgREST data sources for todo/checklist sync, with Room metadata preserved for local ids, remote ids, owner ids, sync state, and sync errors.
- Fixes Supabase `todo_items` bulk upsert payloads so mixed nullable item fields no longer trigger PostgREST `PGRST102`.
- Shows failed manual sync as an error state in Settings instead of a green success-style message.
- Supports debug-only HTTP Supabase endpoints for early Tencent Cloud Lighthouse beta testing before DNS, TLS, and reverse proxy work are complete.
- Uses build-time public Supabase configuration from ignored local properties, Gradle properties, environment variables, or CI secrets; server-side secret keys stay out of the APK and repository.
- Documents the required Supabase SQL schema and RLS policies for `todo_checklists` and `todo_items`.

Install note: this prerelease asset is the debug RC APK, `PixelDone-2.10.0-rc.3-debug.apk`, for `com.milesxue.pixeldone.debug`. It installs separately from the formal signed app `com.milesxue.pixeldone`; the latest formal signed release remains v2.9.3.