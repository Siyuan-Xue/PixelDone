PixelDone v2.7.0-debug is a debug prerelease for the bottom action UI/UX refactor.

Highlights:
- Moves normal-checklist view controls into a new bottom-left `VIEW` button beside the `+` action.
- Moves completed-task cleanup and new cross-list batch movement into a bottom-right `BATCH` button.
- Keeps `TRASH` and `SETTINGS` free of the new bottom side buttons.
- Adds explicit multi-select before moving tasks to another normal checklist.
- Simplifies Settings permission rows with clickable status glyphs instead of `CONFIG` buttons.
- Fixes dark-mode clay button foreground color so primary symbols remain readable.
- Consumes todo highlight events once so returning from Settings no longer replays an old row border highlight.

Install note: use the attached debug APK, `PixelDone-2.7.0-debug.apk`, for local validation. This debug build installs as `com.milesxue.pixeldone.debug` and remains separate from the formal signed `com.milesxue.pixeldone` app and its in-app update channel.

CODEX & XUE
