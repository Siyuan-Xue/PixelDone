PixelDone v2.10.0 is a formal signed release for local-first storage and optional Supabase cloud sync.

Highlights:
- Keeps PixelDone local-first while moving task/checklist storage to Room and small settings to DataStore.
- Keeps sign-in low in the Settings cloud area, with the same bottom-panel interaction language as todo and checklist editing.
- Adds optional Supabase Auth/PostgREST sync seams for todos and checklists, preserving local ids, remote ids, owner ids, sync state, and sync errors.
- Fixes Supabase `todo_items` bulk upsert payloads so mixed nullable item fields no longer trigger PostgREST `PGRST102`.
- Shows failed manual sync as an error state in Settings instead of a green success-style message.
- Keeps server-side Supabase secret keys out of the Android APK and repository; formal distributed builds require HTTPS cloud configuration for usable sync.
- Documents the required Supabase SQL schema and RLS policies for `todo_checklists` and `todo_items`.

Install note: this release asset is the signed APK, `PixelDone-2.10.0-release.apk`, for `com.milesxue.pixeldone`. Debug RC builds use the separate package `com.milesxue.pixeldone.debug` and can remain installed separately.