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
```

`local.properties`, signing files, and CI secret values must not be committed. Release APKs do not receive a runtime `.env` file from users; the developer or CI injects the public Supabase URL and publishable/anon key at build time. These values are client configuration and can be extracted from an APK, so do not place service-role keys, secret keys, database passwords, signing keys, or signing passwords in the Android app.

Debug and formal release builds allow cleartext HTTP for the current direct-IP Supabase endpoint. Keep `PIXELDONE_REQUIRE_CLOUD_CONFIG=true` or `pixeldone.requireCloudConfig=true` for cloud release builds so missing URL/key values fail the build. Move the configured URL to HTTPS when the service is ready, but do not disable Cloud solely because the current URL uses `http://`.

## Supabase Auth

PixelDone uses Supabase Auth email/password sign-up and sign-in inside the Settings `CLOUD` bottom panel. Sign-up expects the self-hosted Supabase Auth service to auto-confirm email accounts, so the app receives a session immediately after registration. Configure the server with `ENABLE_EMAIL_SIGNUP=true`, `ENABLE_EMAIL_AUTOCONFIRM=true`, and `DISABLE_SIGNUP=false`, then restart the Auth service. Do not implement auto-confirm in the Android app and never ship a service-role key in the APK.

Access tokens are stored with Android Keystore-backed AES-GCM encryption in private app storage. Passwords are never persisted.

## Tables

Run `docs/pixeldone-supabase-remote-schema-update.sql` in the Supabase SQL editor after the project is initialized or when updating the remote schema. The script owns the current PostgREST tables, remote version sequence, triggers, indexes, RLS policies, and grants used by the Android app. Theme and Dock preferences are local-only; current clients do not read or write a `user_settings` table.

## Current Scope

Included now:

- Email/password sign-up, sign-in, sign-out, and password reset email requests.
- Local-first incremental todo/checklist sync through Supabase Auth and PostgREST. Theme and Dock settings remain local-only on each device.
- Cursor-based pull by `remote_version`, idempotent mutation batches, and three-way conflict detection using local pristine payloads.
- Local Room metadata preservation for `remoteId`, `ownerUserId`, `syncState`, `lastSyncedAtMillis`, `remoteVersion`, `lastSyncError`, sync cursors, pristine payloads, and queued mutation UUIDs.
- Debug and formal release cleartext HTTP support for the current direct-IP Supabase phase.

Not included yet:

- Image upload or Supabase Storage sync.
- Realtime push subscriptions.


## Android Networking And CORS

PixelDone uses native Android network requests for Supabase Auth and PostgREST. Browser CORS restrictions do not apply to these native requests, so the Android app does not need `Access-Control-Allow-Origin` for sync. CORS would matter only if a future feature loaded web content in a WebView and executed browser-style JavaScript requests.

The relevant Android controls are the `INTERNET` permission, network security config, cleartext HTTP policy, TLS certificate trust, Supabase Auth JWTs, and Postgres RLS policies.

## Operational Notes

Use the publishable key, or a legacy anon key when that is what the self-hosted Supabase deployment exposes, in the Android app. Never use the service role key in Android code, `local.properties`, release assets, or public CI logs. RLS must be enabled before using a shared server. If the Supabase instance is exposed over HTTP, keep it on trusted infrastructure and networks. PixelDone formal releases intentionally allow the current direct-IP HTTP endpoint; move the configured URL to HTTPS when the service is ready.
