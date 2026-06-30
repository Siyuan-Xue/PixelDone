PixelDone v2.5.6 is a formal signed bugfix release for in-app update download handling.

Highlights:
- Reuses an already active latest-version update download instead of queueing another APK download.
- Cancels and forgets older active update downloads when a newer latest release is selected.
- Deletes stale `PixelDone-*-release.apk` files from the app-private update download directory, keeping only the latest target APK.
- Cleans the downloaded update APK after the app has successfully resumed on the installed target version.
- Preserves the 2.5.5 footer progress, image preview UI, todo, reminder, storage, and install prompt behavior.

Install note: use the attached signed release APK, `PixelDone-2.5.6-release.apk`, for direct installation or in-app update.

CODEX & XUE
