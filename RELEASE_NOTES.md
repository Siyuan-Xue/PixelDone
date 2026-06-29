PixelDone v2.4.3 refines editor actions and fixes in-app update download handling.

Highlights:
- Keeps task and list editor save/cancel actions at the bottom of the editor.
- Removes the extra delete-section label from task and list editing.
- Safely decodes large todo image previews to avoid preview-window crashes on memory-sensitive devices.
- Keeps todo image preview zoom reversible after reaching the maximum scale.
- Shows temporary footer update states for latest, available, downloading, and failed checks.
- Downloads only the exact latest formal release APK through Android DownloadManager.
- Records handled update versions and active download IDs to avoid duplicate in-app update downloads.
- Stops falling back to the GitHub release page when an exact APK asset is unavailable.

Install note: use `PixelDone-2.4.3-release.apk` for private signed release installation.

CODEX & XUE
