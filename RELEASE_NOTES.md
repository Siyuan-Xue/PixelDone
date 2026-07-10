PixelDone v3.1.0 is the formal signed release for shared Android and Windows Cloud sync.

Highlights:
- Introduces the Supabase 3.1 atomic mutation, global cursor, CAS, tombstone, Realtime invalidation, and durable conflict-review contract.
- Adds System plus Arabic, Chinese, English, French, Russian, and Spanish language modes, with a compact two-column selector and each concrete language shown using its native name.
- Redraws the Cloud sign-in, sign-out, and sync controls as restrained PixelDone line icons.
- Keeps language mode synced while theme, Dock, update preferences, and local task images remain device-local.
- Reschedules Android reminders whenever a Cloud pull changes task timing or state.
- Retains recoverable Trash items for 30 days before scrubbing them into minimal sync tombstones.
- Uses GitHub Releases as the primary update source with the user-managed Gitee mirror as fallback.

Distribution:
- The attached `PixelDone-3.1.0-release.apk` is the signed formal package for `com.milesxue.pixeldone`.
- PixelDone intentionally connects to the configured direct-IP Supabase deployment over cleartext HTTP. HTTP does not provide transport confidentiality or server identity verification, and no HTTPS migration is planned.
- Task images remain local to each device and are not uploaded to Supabase Storage.

Before first use, the operator must apply `docs/pixeldone-supabase-3.1.0-rc.1-migration.sql` once and verify its final checkpoint output. PixelDone 3.1 has no legacy schema fallback.
