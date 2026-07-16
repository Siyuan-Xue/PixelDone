# PixelDone 3.2.5

PixelDone 3.2.5 hardens the signed in-app updater and keeps visible progress until Android takes over installation.

## Update integrity and fallback

- Requires a source-specific SHA-256 sidecar and verifies the downloaded APK before installation.
- Verifies the package name, version name, increasing version code, installed signer, and pinned formal signing certificate.
- Keeps GitHub as the source of truth and retries the exact mirrored Gitee asset after a download or verification failure.
- Extends slow-network tolerance to 90 seconds without byte progress and a 30-minute total download window.
- Caches successful automatic checks for six hours and failed checks for 30 minutes while manual checks always refresh.

## Installation handoff

- Keeps the update dialog visible through download verification and PackageInstaller session staging.
- Shows staging progress while the APK is copied into Android's installer instead of closing at 100% download.
- Waits for Android's confirmation surface and restores a retryable state after installer failure or cancellation.
- Adds localized verification and installer preparation states for every supported language.

## Release status

- `versionName` is 3.2.5 and `versionCode` is 86.
- The remote data contract remains 3.2 and requires no Supabase migration.
- The release uses the established long-lived PixelDone signing identity.
- Signing certificate SHA-256: `6D146E63D8F96D383FD9BBCFD61C61C343D7D7ECB6C98D33DB5BD7DBF56D2317`.
