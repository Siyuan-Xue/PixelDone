PixelDone v1.3.3 restores Material update dialogs and fixes startup update prompts.

Highlights:
- Restores Compose Material 3 `AlertDialog` for update and delete confirmations, using Material's default dialog spacing.
- Reuses one update-check result path for both app startup checks and manual update-button checks.
- Adds a short startup delay and quiet retry so available releases can surface automatically after launch.
- Keeps update dots, `Don't show again`, signed-release update targets, and existing haptics unchanged.
- Adds no new permissions, dependencies, or settings screen.

Install note: if you still have the old `com.codexue.pixeldone` prototype build, uninstall it before installing this private release.

The attached `PixelDone-1.3.3-release.apk` asset is the signed private release APK for direct installation and app-internal updates.

CODEX & XUE
