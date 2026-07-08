# PixelDone Supabase Sync Setup

PixelDone now has a local-first cloud sync implementation for todos and checklists. The app still works without any cloud configuration. When Supabase configuration is present, the Settings screen exposes email/password sign-up, sign-in, sign-out, and manual sync. Local todo edits request background sync through the app container.

## Runtime Configuration

Set these values after the Tencent Cloud Lighthouse host and standalone Supabase instance are ready:

- `PIXELDONE_SUPABASE_URL` Gradle property, environment variable, or `local.properties` key `pixeldone.supabaseUrl`.
- `PIXELDONE_SUPABASE_PUBLISHABLE_KEY` Gradle property, environment variable, or `local.properties` key `pixeldone.supabasePublishableKey`.

Self-hosted Supabase deployments may still expose a legacy anon key. PixelDone keeps `PIXELDONE_SUPABASE_ANON_KEY` / `pixeldone.supabaseAnonKey` as a fallback, but new setup notes should use the publishable-key name.

Example `local.properties` entries:

```properties
pixeldone.supabaseUrl=http://SERVER_IP:8000
pixeldone.supabasePublishableKey=YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY
pixeldone.requireCloudConfig=true
pixeldone.allowInsecureSupabaseHttp=true
```

`local.properties`, signing files, and CI secret values must not be committed. Release APKs do not receive a runtime `.env` file from users; the developer or CI injects the public Supabase URL and publishable/anon key at build time. These values are client configuration and can be extracted from an APK, so do not place service-role keys, secret keys, database passwords, signing keys, or signing passwords in the Android app.

Debug builds allow cleartext HTTP so a direct IP Supabase endpoint can be tested before DNS, ICP filing, TLS, and reverse proxy work are finished. Release builds keep cleartext HTTP blocked by default. During the current personal beta/direct-IP phase, a cloud-enabled release may explicitly set `PIXELDONE_ALLOW_INSECURE_SUPABASE_HTTP=true` or `pixeldone.allowInsecureSupabaseHttp=true` to allow HTTP. Keep `PIXELDONE_REQUIRE_CLOUD_CONFIG=true` or `pixeldone.requireCloudConfig=true` for cloud release builds so missing URL/key values fail the build. Remove the insecure HTTP flag after moving to `pixeldone.com` with HTTPS.

## Supabase Auth

PixelDone uses Supabase Auth email/password sign-up and sign-in inside the Settings `CLOUD` bottom panel. Sign-up expects the self-hosted Supabase Auth service to auto-confirm email accounts, so the app receives a session immediately after registration. Configure the server with `ENABLE_EMAIL_SIGNUP=true`, `ENABLE_EMAIL_AUTOCONFIRM=true`, and `DISABLE_SIGNUP=false`, then restart the Auth service. Do not implement auto-confirm in the Android app and never ship a service-role key in the APK.

Access tokens are stored with Android Keystore-backed AES-GCM encryption in private app storage. Passwords are never persisted.

## Tables

Run the schema in the Supabase SQL editor after the project is initialized. The table names match the PostgREST endpoints used by the Android app. Theme and Dock preferences are local-only; current clients do not read or write a `user_settings` table.

```sql
create extension if not exists pgcrypto;

create table if not exists public.todo_checklists (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  local_id text not null,
  sort_index integer not null default 0,
  name text not null,
  created_at_millis bigint not null,
  updated_at_millis bigint not null,
  deleted_at_millis bigint,
  remote_version bigint not null default 1,
  unique (owner_user_id, local_id)
);

create table if not exists public.todo_items (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  local_id text not null,
  checklist_local_id text not null,
  sort_index integer not null default 0,
  title text not null,
  priority text not null,
  due_at_millis bigint not null default 0,
  completed boolean not null default false,
  created_at_millis bigint not null,
  updated_at_millis bigint not null,
  deleted_at_millis bigint,
  reminder_repeat text not null default 'NONE',
  image_local_name text,
  image_remote_path text,
  image_sync_state text not null default 'LOCAL_ONLY',
  trashed_from_checklist_id text,
  trashed_from_checklist_name text,
  trashed_at_millis bigint,
  remote_version bigint not null default 1,
  unique (owner_user_id, local_id)
);

create index if not exists todo_checklists_owner_updated_idx
  on public.todo_checklists(owner_user_id, updated_at_millis);

create index if not exists todo_items_owner_updated_idx
  on public.todo_items(owner_user_id, updated_at_millis);

alter table public.todo_checklists enable row level security;
alter table public.todo_items enable row level security;

create policy "Users can read their checklists"
  on public.todo_checklists for select
  using (owner_user_id = auth.uid());

create policy "Users can insert their checklists"
  on public.todo_checklists for insert
  with check (owner_user_id = auth.uid());

create policy "Users can update their checklists"
  on public.todo_checklists for update
  using (owner_user_id = auth.uid())
  with check (owner_user_id = auth.uid());

create policy "Users can read their todo items"
  on public.todo_items for select
  using (owner_user_id = auth.uid());

create policy "Users can insert their todo items"
  on public.todo_items for insert
  with check (owner_user_id = auth.uid());

create policy "Users can update their todo items"
  on public.todo_items for update
  using (owner_user_id = auth.uid())
  with check (owner_user_id = auth.uid());

create table if not exists public.sync_mutation_log (
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  mutation_uuid text not null,
  created_at timestamptz not null default now(),
  primary key (owner_user_id, mutation_uuid)
);


alter table public.sync_mutation_log enable row level security;

create policy "Users can read their mutation log"
  on public.sync_mutation_log for select
  using (owner_user_id = auth.uid());

create policy "Users can insert their mutation log"
  on public.sync_mutation_log for insert
  with check (owner_user_id = auth.uid());
```

## Current Scope

Included now:

- Email/password sign-up, sign-in, sign-out, and password reset email requests.
- Local-first incremental todo/checklist sync through Supabase Auth and PostgREST. Theme and Dock settings remain local-only on each device.
- Cursor-based pull by `remote_version`, idempotent mutation batches, and three-way conflict detection using local pristine payloads.
- Local Room metadata preservation for `remoteId`, `ownerUserId`, `syncState`, `lastSyncedAtMillis`, `remoteVersion`, `lastSyncError`, sync cursors, pristine payloads, and queued mutation UUIDs.
- Debug cleartext HTTP support for direct IP testing, plus an explicit release opt-in for the current personal beta HTTP phase.

Not included yet:

- Image upload or Supabase Storage sync.
- Realtime push subscriptions.


## Android Networking And CORS

PixelDone uses native Android network requests for Supabase Auth and PostgREST. Browser CORS restrictions do not apply to these native requests, so the Android app does not need `Access-Control-Allow-Origin` for sync. CORS would matter only if a future feature loaded web content in a WebView and executed browser-style JavaScript requests.

The relevant Android controls are the `INTERNET` permission, network security config, cleartext HTTP policy, TLS certificate trust, Supabase Auth JWTs, and Postgres RLS policies.

## Operational Notes

Use the publishable key, or a legacy anon key when that is what the self-hosted Supabase deployment exposes, in the Android app. Never use the service role key in Android code, `local.properties`, release assets, or public CI logs. RLS must be enabled before using a shared server. If the Supabase instance is exposed over HTTP, use trusted networks and set the explicit insecure HTTP build flag only for the current personal beta/direct-IP phase. Move to HTTPS before any broader distribution.
