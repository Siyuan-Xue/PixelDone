# PixelDone Supabase 3.2 Sync And Storage Setup

PixelDone 3.2 is a hard cutover from the 3.1 remote schema. Android and Windows require contract `3.2`, use the transactional `pixeldone_pull_changes` and `pixeldone_apply_mutation` RPCs, and use native Supabase Storage for private todo images. They do not fall back to 3.1 after the cutover.

## Manual Migration Checkpoint

Back up PostgreSQL and the Storage volume. The official Supabase ownership model keeps `storage.objects` owned by `supabase_storage_admin`, so the migration intentionally has two steps. Do not change the owner of `storage.objects`.

1. Run `docs/pixeldone-supabase-3.2.0-storage-policies.sql` as the actual `storage.objects` owner. For the official Docker stack, open an interactive owner session (replace the container/database names if customized):

   ```sh
   docker exec -it supabase-db psql -h 127.0.0.1 -p 5432 -U supabase_storage_admin -d postgres -W -v ON_ERROR_STOP=1
   ```

   `-h 127.0.0.1` is required to avoid the container's Unix-socket `peer` authentication rule. Enter the deployment's `POSTGRES_PASSWORD` only at the prompt, then paste the Storage policy script. Its final query must show four policies and `applied_by = supabase_storage_admin`.

   The policy script reads the request JWT subject directly from PostgREST's `request.jwt.claim.sub` / `request.jwt.claims` settings. It deliberately does not call `auth.uid()`, because the Storage owner should not be granted access to the managed `auth` schema. If an earlier policy attempt leaves the prompt as `postgres=!>`, run `ROLLBACK;` before pasting the current complete script again.

2. Run `docs/pixeldone-supabase-3.2.0-migration.sql` in Studio SQL Editor, or as the owner of the existing PixelDone objects in `public`. The script refuses to cut over if the four Storage policies are absent.

3. If the server was created with a copy of the 3.2 migration from before PixelDone 3.2.8, run `docs/pixeldone-supabase-3.2.8-attachment-hotfix.sql` as the owner of `public.pixeldone_apply_mutation`. The original attachment validator accidentally used PostgreSQL's concatenation operator (`||`) between boolean expressions, so any mutation containing an active attachment failed with SQLSTATE `42883`. The hotfix changes only those boolean operators, preserves contract `3.2`, reloads PostgREST's schema cache, and is safe to rerun. Its final `attachment_validator_hotfix` value must be `true`.

The earlier single-file candidate attempted `ALTER TABLE storage.objects` and could fail with `42501: must be owner of table objects` in Studio. Because it ran inside `BEGIN`/`COMMIT`, that failure rolled back the attempted migration; do not change the table owner to work around it.

Do not tag or publish 3.2 until the final verification queries return:

- `pixeldone_schema_metadata.schema_version = 3.2`;
- both pull/apply overload pairs and the cleanup function;
- private bucket `pixeldone-todo-images` with a 10 MiB limit and only JPEG/PNG/WebP MIME types;
- four owner-scoped Storage policies (select, insert, update, and delete);
- `todo_checklists`, `todo_items`, `todo_attachments`, `user_settings`, and `sync_tombstones` in `supabase_realtime`;
- one active `pixeldone-expired-trash-daily` cron at `15 3 * * *` UTC.
- `attachment_validator_hotfix = true` when the 3.2.8 hotfix verification query is run.

The client shows `SERVER UPDATE REQUIRED` only if the required transactional RPC is absent or the RPC returns an incompatible schema version. Database execution failures retain their actual PostgREST/SQLSTATE error instead of being mislabeled as a schema upgrade requirement.

## Runtime Configuration

Configure public client values through Gradle properties, environment variables, or uncommitted `local.properties`:

```properties
pixeldone.supabaseUrl=http://SERVER_IP:8000
pixeldone.supabasePublishableKey=YOUR_PUBLISHABLE_OR_LEGACY_ANON_KEY
pixeldone.requireCloudConfig=true
```

PixelDone intentionally allows the configured direct-IP HTTP deployment. HTTP/WS does not provide transport confidentiality, integrity, or server identity; this is an accepted deployment risk rather than an HTTPS migration plan. Never place a service-role key, database password, signing secret, or user password in either client, the repository, or CI logs.

## 3.2 Data Contract

- `todo_checklists` and `todo_items` contain active/recoverable content only.
- `todo_attachments` stores one descriptor per todo: attachment UUID, private object path, SHA-256, MIME type, byte size, update time, delete time, and remote version. Local filenames never enter the cloud contract.
- `todo_image_cleanup_queue` retains replaced/deleted object paths until a client successfully deletes the Storage object and acknowledges the path through the mutation RPC.
- Storage objects use `<owner UUID>/<todo local ID>/<attachment UUID>-<SHA-256>.<extension>` and remain private. Each client keeps only an app-private on-demand cache.
- During the one-time cutover, legacy checklist rows already marked deleted become minimal checklist tombstones, while legacy deleted todo rows remain recoverable Trash items so no user content is silently lost.
- `trashed_at_millis` means the item is recoverable in Trash. Restore clears it; moving the item to Trash again writes a new timestamp and restarts the exact `30 * 24 hour` retention period.
- `deleted_at_millis` exists only in `sync_tombstones`. Tombstones contain owner, record type, local ID, deletion time, and version; no todo/list content survives permanent deletion.
- Minimal tombstones are retained indefinitely. Do not physically clean them until the protocol has per-device acknowledgement or an equivalent safe-watermark design.
- `user_settings` syncs only `language_mode`: `system`, `en`, `zh-Hans`, `ar`, `fr`, `ru`, or `es`. Theme, Dock, and update preferences remain device-local.
- `system` syncs as a mode; every device resolves it against its own operating-system locale. Unsupported system locales fall back to English.

All millisecond fields use PostgreSQL `bigint` / `int8`. A signed 64-bit integer holds about 292 million years at millisecond resolution, so ordinary epoch milliseconds will not overflow. The practical risks are wrong units, invalid negative values, and JavaScript precision in non-Android clients—not `int8` capacity.

## Transaction And Realtime Rules

The pull RPC returns all changed record types, attachment descriptors, pending image cleanup paths, and one consistent high-water mark. Pulls, mutation commits, and cleanup share a PostgreSQL advisory transaction lock so sequence allocation cannot create a commit-order cursor gap.

The apply RPC derives ownership from `auth.uid()`, applies mutations atomically, stores an idempotent response by mutation UUID, performs optimistic remote-version checks, and applies tombstones first. Authenticated clients have table `SELECT` for RLS-filtered Realtime delivery but no direct table-write grants. The cleanup SECURITY DEFINER function is not executable by app roles; only the database owner/cron job runs it.

Supabase Realtime events are invalidation signals only. While the app is foregrounded and signed in, INSERT/UPDATE events are debounced and trigger the normal cursor pull. Login, foreground resume, token refresh, subscription restart, and Realtime reconnect also trigger catch-up. Background and sign-out tear down the subscription. Local mutations may enqueue a one-time network-constrained WorkManager request, but PixelDone registers no periodic polling job; manual sync remains available.

## Cleanup Rule

`pixeldone_cleanup_expired_trash()` runs daily at 03:15 UTC through `pg_cron`. It selects items whose `trashed_at_millis` is at least 30 days old, queues any current image object, inserts/updates minimal tombstones, and deletes active content in one locked transaction. Restored items are not eligible because restore clears `trashed_at_millis`.

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
8. Upload, replace, delete, and re-open JPEG/PNG/WebP images from both platforms; confirm original-byte hashes match, remote-only images download on demand, and cleanup paths disappear after acknowledgement.
9. Change the password using the current password, confirm the initiating device signs out, and confirm previously issued sessions are globally revoked before signing in again.
