# PixelDone Supabase Sync Setup

PixelDone now has a local-first cloud sync implementation for todos and checklists. The app still works without any cloud configuration. When Supabase configuration is present, the Settings screen exposes email/password sign-in and manual sync, and local todo edits request background sync through the app container.

## Runtime Configuration

Set these values after the Tencent Cloud Lighthouse host and standalone Supabase instance are ready:

- `PIXELDONE_SUPABASE_URL` Gradle property, environment variable, or `local.properties` key `pixeldone.supabaseUrl`.
- `PIXELDONE_SUPABASE_PUBLISHABLE_KEY` Gradle property, environment variable, or `local.properties` key `pixeldone.supabasePublishableKey`.

Self-hosted Supabase deployments may still expose a legacy anon key. PixelDone keeps `PIXELDONE_SUPABASE_ANON_KEY` / `pixeldone.supabaseAnonKey` as a fallback, but new setup notes should use the publishable-key name.

Example `local.properties` entries:

```properties
pixeldone.supabaseUrl=http://SERVER_IP:8000
pixeldone.supabasePublishableKey=YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY
```

`local.properties`, signing files, and CI secret values must not be committed. Release APKs do not receive a runtime `.env` file from users; the developer or CI injects the public Supabase URL and publishable/anon key at build time. These values are client configuration and can be extracted from an APK, so do not place service-role keys, secret keys, database passwords, signing keys, or signing passwords in the Android app.

Debug builds allow cleartext HTTP so a direct IP Supabase endpoint can be tested before DNS, ICP filing, TLS, and reverse proxy work are finished. Release builds keep cleartext HTTP blocked at runtime. When building a cloud-enabled release, set `PIXELDONE_REQUIRE_CLOUD_CONFIG=true` or `pixeldone.requireCloudConfig=true` to fail the build if the Supabase URL/key is missing or the release URL is HTTP. A formal cloud-enabled release should use HTTPS.

## Supabase Auth

The first implementation uses Supabase Auth email/password sign-in. The app does not include sign-up UI yet. Create test users in Supabase Studio or add sign-up later as a separate product decision.

Access tokens are stored with Android Keystore-backed AES-GCM encryption in private app storage. Passwords are never persisted.

## Tables

Run the schema in the Supabase SQL editor after the project is initialized. The table names match the PostgREST endpoints used by the Android app.

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
```

## Current Scope

Included now:

- Email/password sign-in and sign-out.
- Local-first todo/checklist sync through Supabase Auth and PostgREST.
- Last-write-wins merge based on `updated_at_millis` and `deleted_at_millis`.
- Local Room metadata preservation for `remoteId`, `ownerUserId`, `syncState`, `lastSyncedAtMillis`, `remoteVersion`, and `lastSyncError`.
- Debug-only cleartext HTTP support for direct IP testing.

Not included yet:

- Sign-up UI.
- Password reset UI.
- Image upload or Supabase Storage sync.
- Settings sync.
- Push/realtime sync.
- Background WorkManager scheduling.
- Formal release support for cleartext HTTP.

## Android Networking And CORS

PixelDone uses native Android network requests for Supabase Auth and PostgREST. Browser CORS restrictions do not apply to these native requests, so the Android app does not need `Access-Control-Allow-Origin` for sync. CORS would matter only if a future feature loaded web content in a WebView and executed browser-style JavaScript requests.

The relevant Android controls are the `INTERNET` permission, network security config, cleartext HTTP policy, TLS certificate trust, Supabase Auth JWTs, and Postgres RLS policies.

## Operational Notes

Use the publishable key, or a legacy anon key when that is what the self-hosted Supabase deployment exposes, in the Android app. Never use the service role key in Android code, `local.properties`, release assets, or public CI logs. RLS must be enabled before using a shared server. If the Supabase instance is exposed over HTTP, only use debug builds and trusted networks. Move to HTTPS before any broader distribution.
