PixelDone v3.0.3 is the formal signed patch release for Cloud HTTP availability and remote schema setup.

Highlights:
- Allows formal signed builds to use the current direct-IP HTTP Supabase endpoint, fixing the `Cleartext HTTP is disabled for this build` Cloud state.
- Adds `docs/pixeldone-supabase-remote-schema-update.sql` as the current Supabase remote schema script for checklists, todos, mutation UUID logging, remote versions, triggers, indexes, and RLS policies.
- Keeps theme and Dock preferences local-only on each device; cloud sync covers checklists and todos only.
- Preserves the 3.0.2 conflict review dialog and incremental sync hardening: remote cursors, pristine payloads, mutation UUID replay protection, WorkManager scheduling, and password reset requests.

Install note: this release asset is the signed APK `PixelDone-3.0.3-release.apk` for `com.milesxue.pixeldone`. Users on `PixelDone-3.0.2-release.apk` should update to this build before using Cloud sync against the current HTTP Supabase endpoint.
