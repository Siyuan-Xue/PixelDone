PixelDone v3.0.2-rc.2 is the beta RC for manual sync conflict review.

Highlights:
- Adds a Settings `CLOUD` conflict review dialog for checklist and todo conflicts.
- Stores conflict candidates locally in Room with local payload, cloud payload, conflict fields, message, remote version, and created time.
- Lets users resolve each conflict by choosing `KEEP LOCAL` or `KEEP CLOUD` at the whole-record level.
- Keeps either choice out of the old `CONFLICT` state: local choices enter the normal upload path, cloud choices apply locally as `SYNCED`.
- Preserves the rc1 incremental sync base: remote cursors, pristine payloads, mutation UUID replay protection, settings sync, WorkManager scheduling, and password reset requests.
- Does not add or require any Supabase table, RLS policy, RPC, or remote schema change for conflict review.

Install note: this prerelease asset is the debug APK `PixelDone-3.0.2-rc.2-debug.apk` for `com.milesxue.pixeldone.debug`. The latest formal signed release remains `PixelDone-3.0.1-release.apk` for `com.milesxue.pixeldone`.
