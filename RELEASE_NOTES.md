PixelDone v2.7.1 is a signed formal release for the synced Gitee update mirror and beta RC update channel support.

Highlights:
- Switches in-app update checks to the synced Gitee release mirror while keeping code pushes, tags, and release publishing on GitHub.
- Adds explicit update channels: release builds use `formal`, and debug builds use `beta`.
- Filters formal updates to normal `vX.Y.Z` releases with matching `PixelDone-X.Y.Z-release.apk` assets.
- Filters beta updates to prerelease `vX.Y.Z-rc.N` tags with matching `PixelDone-X.Y.Z-rc.N-debug.apk` assets.
- Updates the downloader to use the matched APK asset name instead of assuming only formal release APKs.
- Gives the debug/Beta app the launcher label `PixelDone-beta`.
- Documents the GitHub-to-Gitee sync model for future release work.

Install note: use the attached signed release APK, `PixelDone-2.7.1-release.apk`, for direct installation and in-app updates. This release installs as `com.milesxue.pixeldone`; uninstall incompatible debug/prototype builds if Android reports a package or signature conflict.

CODEX & XUE
