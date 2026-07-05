# PixelDone Local-First Sync Readiness

The previous stage kept PixelDone fully local. The current cloud-sync stage adds optional Supabase Auth and PostgREST sync while preserving local-first behavior when cloud configuration is absent. See `docs/pixeldone-supabase-sync-setup.md` for the active server setup notes.

## Current Boundary

- Todo/checklist data is owned by the local data layer.
- Room entities include future sync metadata such as `localId`, `remoteId`, `ownerUserId`, `updatedAtMillis`, `deletedAtMillis`, `syncState`, `lastSyncedAtMillis`, `remoteVersion`, and `lastSyncError`.
- DataStore owns small local preferences: theme, Dock configuration, update prompt preference, and a disabled future sync placeholder.
- The UI may display `LOCAL ONLY`, but it must not offer login or account management in this stage.
- `AuthSessionRepository`, `SyncCoordinator`, and `RemoteTodoDataSource` are backend-agnostic seams. Production currently uses local-only/no-op implementations.

## Future Cloud Direction

The planned cloud option is Tencent Cloud Lighthouse running a self-hosted Supabase stack.

- Supabase Auth should start with email/password sign-in.
- Supabase Postgres should store todos, checklists, settings, and tombstones.
- Row Level Security should isolate rows with `owner_user_id = auth.uid()`.
- Client-generated UUID strings should remain valid as `local_id` and can map to server `id` values.
- Time fields should remain millisecond timestamps to match the current app model and simplify conflict tests.
- The first sync version should include tasks, checklists, settings, and deletion tombstones.
- Images should stay local in the first sync version. Supabase Storage can be added later with separate image sync state.

## Expected First Server Tables

A future Supabase schema can mirror the local entity shape:

- `todo_checklists`: `id`, `local_id`, `owner_user_id`, `name`, `sort_index`, `created_at_millis`, `updated_at_millis`, `deleted_at_millis`, `remote_version`.
- `todo_items`: `id`, `local_id`, `owner_user_id`, `checklist_local_id`, `title`, `priority`, `due_at_millis`, `completed`, `created_at_millis`, `updated_at_millis`, `deleted_at_millis`, `reminder_repeat`, `image_remote_path`, `remote_version`.
- `user_settings`: `owner_user_id`, `dark_theme`, `dock_plus_placement`, `dock_actions`, `updated_at_millis`, `remote_version`.

## Conflict Rule Placeholder

The current pure Kotlin `ConflictResolver` uses deterministic last-write-wins and keeps local data on exact timestamp ties. Before cloud launch, this rule should be revisited with product-specific decisions for delete-vs-edit conflicts and stale-device recovery.
