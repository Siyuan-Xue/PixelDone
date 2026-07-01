PixelDone v2.7.2-rc.1 is a beta RC debug prerelease for validating update UI refinements and GitHub-first update fallback behavior.

Highlights:
- Makes the update prompt's `DO NOT SHOW AGAIN` control a lightweight single-line text toggle.
- Unifies dialog close and dismiss buttons as borderless clay text buttons.
- Makes GitHub Releases the primary app update source for both checking and APK downloads.
- Uses the synced Gitee release mirror only as a fallback source when GitHub fails, stalls, or lacks the matching APK asset.
- Retries beta and formal update downloads through a GitHub -> Gitee source queue.
- Treats a download source as stalled when bytes do not grow for 30 seconds.
- Keeps beta matching on prerelease `vX.Y.Z-rc.N` tags with `PixelDone-X.Y.Z-rc.N-debug.apk` assets.
- Documents that code pushes, tags, and releases stay on GitHub while Gitee is a synced fallback mirror.

Install note: use the attached beta debug APK, `PixelDone-2.7.2-rc.1-debug.apk`, for beta validation. This prerelease installs as `com.milesxue.pixeldone.debug` and appears as `PixelDone-beta`, so it can coexist with the formal `com.milesxue.pixeldone` app.

CODEX & XUE
