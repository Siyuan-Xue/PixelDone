# PixelDone Release Specification

This document contains PixelDone-only release and infrastructure exceptions. The mirrored `PROJECT_SPEC.md`, `PRODUCT_LINE_SPEC.md`, and `DESIGN_SPEC.md` remain governed by the PixelPark parent repository and must not be customized here.

## Repository And Release Surfaces

- Release PixelDone only from this independent repository.
- Canonical Git remote, tags, and releases: `https://github.com/Siyuan-Xue/PixelDone`.
- GitHub Releases is the primary in-app release/update source.
- The Actions-managed mirror `https://gitee.com/milesxue/PixelDone/releases` is the fallback update source. GitHub remains the source of truth, and mirror work must never change local `origin`.

## Channels

- Debug builds use `applicationIdSuffix = ".debug"` and the `beta` update channel so they coexist with the formal app.
- Beta candidates use `vX.Y.Z-rc.N`, GitHub prerelease title `PixelDone vX.Y.Z-rc.N`, and asset `PixelDone-X.Y.Z-rc.N-debug.apk`.
- Formal builds use `vX.Y.Z`, a normal GitHub Release, the `formal` update channel, long-lived release signing, and asset `PixelDone-X.Y.Z-release.apk`.
- Never publish a debug APK as a formal release asset. A prerelease is distribution, not access control.

## Formal Release Automation

- Pull requests and pushes to `main` run `.github/workflows/ci.yml`. Pull requests build without cloud configuration or repository secrets; `main` builds require the public Supabase client configuration stored in GitHub Actions secrets.
- A strict `vX.Y.Z` tag starts `.github/workflows/release-android.yml`. The tag must point to a commit reachable from `main`, match `android.defaultConfig.versionName`, and have a `versionCode` higher than the previous formal APK.
- The release quality gate runs `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and the connected instrumentation tests on an Android emulator before release signing material is restored.
- GitHub Actions builds and signs the formal APK exactly once. GitHub and Gitee must receive the same APK, checksum file, release notes, tag, and commit SHA; Gitee must never rebuild the release independently.
- The workflow verifies the release APK with `apksigner` and requires signing certificate SHA-256 `6D146E63D8F96D383FD9BBCFD61C61C343D7D7ECB6C98D33DB5BD7DBF56D2317`.
- GitHub is published first. The Gitee job waits for the existing GitHub-to-Gitee code mirror to expose the same tag and commit, then creates the Gitee Release through OpenAPI and uploads the identical artifacts.
- Publishing is resumable only for an otherwise matching Release that lacks an expected attachment. Never move a tag, replace an existing attachment, use a clobber option, or modify conflicting Release metadata.
- If GitHub succeeds and Gitee fails, keep the valid GitHub Release and rerun only the failed Gitee job after the mirror or API recovers.
- Automated mirroring starts with the first formal tag published after this workflow is introduced. Do not backfill historical Gitee Releases unless explicitly requested.

Required GitHub repository secrets:

- `PIXELDONE_SUPABASE_URL`
- `PIXELDONE_SUPABASE_PUBLISHABLE_KEY`
- `PIXELDONE_ANDROID_KEYSTORE_BASE64`
- `PIXELDONE_ANDROID_STORE_PASSWORD`
- `PIXELDONE_ANDROID_KEY_ALIAS`
- `PIXELDONE_ANDROID_KEY_PASSWORD`
- `GITEE_ACCESS_TOKEN`

The Gitee token must have only the minimum project permission needed to create Releases and upload attachments. Signing secrets and the Gitee token must never be exposed to pull-request jobs, command output, release artifacts, or Gitee build systems.

## Supabase Release Gate

- PixelDone 3.2 is a hard remote-schema cutover from 3.1. It adds private Supabase Storage image objects, transactional attachment metadata, and a durable image cleanup queue.
- The operator manually runs `docs/pixeldone-supabase-3.2.0-storage-policies.sql` as `supabase_storage_admin`, then runs `docs/pixeldone-supabase-3.2.0-migration.sql` as the PixelDone public-schema owner, and returns the verification output before a 3.2 RC is tagged or published. Never change ownership of Supabase-managed Storage tables.
- The app must report `SERVER UPDATE REQUIRED` when the server does not provide schema `3.2`; do not add a legacy fallback.
- The `pixeldone-todo-images` bucket must remain private. Clients may contain only the publishable/anon key and must never contain a Storage service-role key.
- Formal and debug builds intentionally allow the configured direct-IP Supabase endpoint over cleartext HTTP. HTTP is the durable PixelDone deployment contract; there is no planned HTTPS migration. Keep the risk disclosure in README and release notes, and never broaden the client to unrelated cleartext endpoints.
- Never package a service-role key, database password, signing secret, or refresh token in the APK or repository.

## RC Verification

Before publishing a PixelDone RC:

1. Confirm the Supabase 3.2 migration verification output, five-table Realtime publication, private Storage policies, and daily cleanup cron.
2. Run `lintDebug`, `testDebugUnitTest`, connected/instrumentation tests when a device is available, and `assembleDebug`.
3. Confirm both `app-debug.apk` and `PixelDone-{versionName}-debug.apk` exist in `app/build/outputs/apk/debug/`.
4. Test two signed-in devices: todo/list changes, completion, Trash/restore, permanent deletion, language sync, original-byte image upload/replacement/deletion/on-demand download, Realtime foreground catch-up, token refresh, password change/global logout, and conflict aggregation.
5. Confirm `versionCode` increases and `versionName`, tag, title, and APK asset names match exactly.
6. Push PixelDone `main`, create the immutable GitHub prerelease, and verify the APK asset. Do not move or overwrite an existing tag.
7. Update the PixelPark parent README/spec mirrors/gitlink and push the parent repository without staging unrelated workspace files.
