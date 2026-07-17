# PixelDone 3.2.7

PixelDone 3.2.7 fixes the Android installer handoff and prevents the app from reopening itself after an in-app upgrade.

## Installer confirmation

- Replaces the transparent installer callback activity with a non-exported status receiver.
- Explicitly authorizes the one trusted Android installer confirmation handoff under the current background activity launch rules.
- Stops waiting after 15 seconds when Android does not accept the handoff, instead of leaving the update dialog stuck indefinitely.

## Upgrade lifecycle

- Consumes the PackageInstaller status callback once after the required user-confirmation event, preventing the final package-replacement result from cold-starting PixelDone.
- Preserves the existing full-screen intent access choice across Android 14+ upgrades.
- Keeps notification, exact-alarm, full-screen alarm, and install-source permission handling unchanged; no permission reconfiguration is required.

## Release status

- `versionName` is 3.2.7 and `versionCode` is 88.
- The remote data contract remains 3.2 and requires no Supabase migration.
- The cleartext HTTP deployment contract is unchanged; no HTTPS/TLS migration is required.
- The release uses the established long-lived PixelDone signing identity.
- Signing certificate SHA-256: `6D146E63D8F96D383FD9BBCFD61C61C343D7D7ECB6C98D33DB5BD7DBF56D2317`.
