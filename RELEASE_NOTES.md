PixelDone v3.0.1 is the formal signed patch release for cloud sync stability.

Highlights:
- Fixes todo rebound after quickly deleting multiple items while cloud sync is running.
- Re-reads the latest local Room state before applying pulled remote data, so stale sync snapshots no longer overwrite newer local deletes or edits.
- Applies pushed remote metadata only when the local record still matches what was pushed; edits made during push stay `NOT_SYNCED` for the next sync.
- Adds a 2-second trailing debounce for automatic sync requests and coalesces requests that arrive during an active sync into at most one follow-up sync.
- Removes noisy todo-sync triggers from Settings-only changes, checklist selection changes, and duplicate ViewModel auth-success handling.
- Preserves locally hidden cloud tombstones after clearing `TRASH`, so deletion facts can continue propagating without reappearing in the UI.

Install note: this release asset is the signed APK `PixelDone-3.0.1-release.apk` for `com.milesxue.pixeldone`. Debug RC builds use the separate package `com.milesxue.pixeldone.debug` and can remain installed separately.
