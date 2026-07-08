PixelDone v3.0.2-rc.1 is the beta RC for the local/cloud sync hardening milestone.

Highlights:
- Adds local sync metadata for remote cursors, pristine payloads, and retryable mutation UUIDs.
- Moves todo/checklist sync from snapshot push/pull toward cursor-based incremental change batches.
- Adds conflict-aware three-way merge behavior so same-field conflicts remain visible instead of being silently overwritten.
- Adds settings sync for `darkTheme` and Dock configuration while keeping update prompts, auth state, permission state, alarm runtime state, and local image files device-local.
- Queues WorkManager background sync with network constraints and keeps manual Settings sync available.
- Adds password reset email requests through Supabase Auth.
- Updates Supabase setup notes for `user_settings`, `sync_mutation_log`, RLS, tombstones, and cursor-based sync metadata.

Install note: this prerelease asset is the debug APK `PixelDone-3.0.2-rc.1-debug.apk` for `com.milesxue.pixeldone.debug`. The latest formal signed release remains `PixelDone-3.0.1-release.apk` for `com.milesxue.pixeldone`.
