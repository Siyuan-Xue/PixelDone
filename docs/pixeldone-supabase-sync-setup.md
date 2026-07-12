# PixelDone Supabase 3.1 Sync Setup

PixelDone 3.1 is a hard remote-schema cutover. The Android client requires schema contract `3.1` and uses only the transactional `pixeldone_pull_changes` and `pixeldone_apply_mutation` RPCs. It does not fall back to the pre-3.1 direct-table protocol.

## Manual Migration Checkpoint

Run `docs/pixeldone-supabase-3.1.0-rc.1-migration.sql` once in the Supabase SQL editor as the database owner. The operator performs this step manually. Do not tag or publish a 3.1 RC until the final verification queries return:

- `pixeldone_schema_metadata.schema_version = 3.1`;
- all three functions: pull, apply, and cleanup;
- `todo_checklists`, `todo_items`, `user_settings`, and `sync_tombstones` in `supabase_realtime`;
- one active `pixeldone-expired-trash-daily` cron at `15 3 * * *` UTC.

The client shows `SERVER UPDATE REQUIRED` if these RPCs are absent or return another schema version.

## Runtime Configuration

Configure public client values through Gradle properties, environment variables, or uncommitted `local.properties`:

```properties
pixeldone.supabaseUrl=http://SERVER_IP:8000
pixeldone.supabasePublishableKey=YOUR_PUBLISHABLE_OR_LEGACY_ANON_KEY
pixeldone.requireCloudConfig=true
```

PixelDone currently allows the trusted direct-IP HTTP deployment. Move to HTTPS when available. Never place a service-role key, database password, signing secret, or user password in the APK, repository, or CI logs.

## 3.1 Data Contract

- `todo_checklists` and `todo_items` contain active/recoverable content only.
- During the one-time cutover, legacy checklist rows already marked deleted become minimal checklist tombstones, while legacy deleted todo rows remain recoverable Trash items so no user content is silently lost.
- `trashed_at_millis` means the item is recoverable in Trash. Restore clears it; moving the item to Trash again writes a new timestamp and restarts the exact `30 * 24 hour` retention period.
- `deleted_at_millis` exists only in `sync_tombstones`. Tombstones contain owner, record type, local ID, deletion time, and version; no todo/list content survives permanent deletion.
- Minimal tombstones are retained indefinitely. Do not physically clean them until the protocol has per-device acknowledgement or an equivalent safe-watermark design.
- `user_settings` syncs only `language_mode`: `system`, `en`, `zh-Hans`, `ar`, `fr`, `ru`, or `es`. Theme, Dock, and update preferences remain device-local.
- `system` syncs as a mode; every device resolves it against its own operating-system locale. Unsupported system locales fall back to English.

All millisecond fields use PostgreSQL `bigint` / `int8`. A signed 64-bit integer holds about 292 million years at millisecond resolution, so ordinary epoch milliseconds will not overflow. The practical risks are wrong units, invalid negative values, and JavaScript precision in non-Android clients—not `int8` capacity.

## Transaction And Realtime Rules

The pull RPC returns all changed record types plus one consistent high-water mark. Pulls, mutation commits, and cleanup share a PostgreSQL advisory transaction lock so sequence allocation cannot create a commit-order cursor gap.

The apply RPC derives ownership from `auth.uid()`, applies mutations atomically, stores an idempotent response by mutation UUID, performs optimistic remote-version checks, and applies tombstones first. Authenticated clients have table `SELECT` for RLS-filtered Realtime delivery but no direct table-write grants. The cleanup SECURITY DEFINER function is not executable by app roles; only the database owner/cron job runs it.

Supabase Realtime events are invalidation signals only. While the app is foregrounded and signed in, INSERT/UPDATE events are debounced and trigger the normal cursor pull. Login, foreground resume, token refresh, subscription restart, and Realtime reconnect also trigger catch-up. Background and sign-out tear down the subscription. Local mutations may enqueue a one-time network-constrained WorkManager request, but PixelDone registers no periodic polling job; manual sync remains available.

## Cleanup Rule

`pixeldone_cleanup_expired_trash()` runs daily at 03:15 UTC through `pg_cron`. It selects items whose `trashed_at_millis` is at least 30 days old, inserts/updates minimal tombstones, and deletes active content in one locked transaction. Restored items are not eligible because restore clears `trashed_at_millis`.

No cron deletes tombstones. Establish device acknowledgement and a documented offline-device bound before proposing tombstone cleanup.

## RC Device Verification

After the SQL checkpoint, validate with two signed-in devices:

1. Create, edit, reorder, complete, Trash, restore, and permanently delete items and lists.
2. Confirm the other foreground device updates without manual refresh.
3. Confirm background/resume and token refresh perform catch-up.
4. Dismiss a conflict, create another conflict, and confirm the reopened dialog contains all unresolved conflicts.
5. Confirm non-overlapping field edits merge automatically and overlapping fields require review.
6. Change language, confirm it syncs, and confirm `System` follows each device independently.
7. Verify Arabic RTL, notifications, alarm actions, plurals, and the 30-day Trash message.
