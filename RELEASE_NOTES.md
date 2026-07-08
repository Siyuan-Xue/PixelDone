PixelDone v3.0.2 is the formal signed release for the local-first cloud sync upgrade.

Highlights:
- Adds Settings `CLOUD` conflict review for checklist and todo conflicts.
- Stores conflict candidates locally in Room with local payload, cloud payload, fields, message, remote version, and created time.
- Lets users resolve each conflict by choosing `KEEP LOCAL` or `KEEP CLOUD` for the whole record.
- Keeps theme and Dock preferences local-only on each device; cloud sync covers checklists and todos only.
- Removes current-client dependence on any Supabase `user_settings` table.
- Preserves incremental sync hardening: remote cursors, pristine payloads, mutation UUID replay protection, WorkManager scheduling, and password reset requests.

Install note: this release asset is the signed APK `PixelDone-3.0.2-release.apk` for `com.milesxue.pixeldone`. Users with old debug or beta builds under `com.milesxue.pixeldone.debug` should uninstall those separately if they no longer need the beta channel.
