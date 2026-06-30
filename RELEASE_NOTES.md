PixelDone v2.4.5 fixes in-app update download and install handling.

Highlights:
- Checks quietly for the latest GitHub Release on every app start without suppressing a previously handled version.
- Starts a fresh in-app DownloadManager task each time the user taps GET for the latest release APK.
- Opens the Android system install confirmation after the APK download completes.
- Removes stale persisted update download reuse so old DownloadManager IDs cannot fake a new download state.
- Keeps the footer update states restrained: get, download, install, latest, and update failed.

Install note: use `PixelDone-2.4.5-release.apk` for private signed release installation. The attached APK is a signed release build for direct installation.

CODEX & XUE
