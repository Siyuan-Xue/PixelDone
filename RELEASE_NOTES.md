PixelDone v2.7.4-rc.1 is a beta RC prerelease for Android 14+ full-screen intent permission flow fixes.

Highlights:
- Keeps full-screen intent access on Android 14+ tied to `NotificationManager.canUseFullScreenIntent()`.
- Reopens the dedicated full-screen intent grant page for XHigh reminders when the permission is still missing after returning from exact-alarm setup.
- Falls back through notification settings and app details if the dedicated full-screen settings activity is unavailable.
- Documents that PixelDone-beta uses the separate debug package, so full-screen permission grants are separate from the formal app.

Install note: use the attached beta debug APK, `PixelDone-2.7.4-rc.1-debug.apk`, for PixelDone-beta testing. This build installs as `com.milesxue.pixeldone.debug`; uninstall incompatible older beta/debug builds only if Android reports a package or signature conflict.

CODEX & XUE
