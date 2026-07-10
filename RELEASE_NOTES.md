PixelDone v3.1.0-rc.1 is the debug prerelease candidate for the Supabase 3.1 sync cutover.

Highlights:
- Replaces the legacy Cloud schema with the 3.1 transaction contract in `docs/pixeldone-supabase-3.1.0-rc.1-migration.sql`.
- Adds Realtime invalidation so signed-in foreground devices refresh checklist and todo changes made on other devices without manual refresh.
- Makes merge conflicts durable and global: new conflict batches open the review dialog on any page, dismissed conflicts remain unresolved, and later conflicts reopen review with all unresolved items.
- Simplifies conflict review to item/list identifiers and actual conflicting fields, with whole-record keep-local and keep-cloud actions.
- Adds cloud-synced language mode with System plus the six UN official languages: Arabic, Chinese, English, French, Russian, and Spanish.
- Adds 30-day Trash retention cleanup through Supabase cron, with restore resetting the countdown and minimal tombstones retained for sync deletion propagation.
- Updates Cloud controls to muted borderless icon buttons and uses clay REVIEW actions for conflict review.

Install note: this prerelease asset is the debug APK `PixelDone-3.1.0-rc.1-debug.apk` for `com.milesxue.pixeldone.debug`. Before using this client against Supabase, run the SQL migration once and verify the final SQL checkpoint output.

PixelDone v3.0.3 is the formal signed patch release for Cloud HTTP availability and remote schema setup.

Highlights:
- Allows formal signed builds to use the current direct-IP HTTP Supabase endpoint, fixing the `Cleartext HTTP is disabled for this build` Cloud state.
- Adds `docs/pixeldone-supabase-remote-schema-update.sql` as the current Supabase remote schema script for checklists, todos, mutation UUID logging, remote versions, triggers, indexes, and RLS policies.
- Keeps theme and Dock preferences local-only on each device; cloud sync covers checklists and todos only.
- Preserves the 3.0.2 conflict review dialog and incremental sync hardening: remote cursors, pristine payloads, mutation UUID replay protection, WorkManager scheduling, and password reset requests.

Install note: this release asset is the signed APK `PixelDone-3.0.3-release.apk` for `com.milesxue.pixeldone`. Users on `PixelDone-3.0.2-release.apk` should update to this build before using Cloud sync against the current HTTP Supabase endpoint.
