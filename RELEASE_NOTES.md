PixelDone v2.7.4 is a formal signed release for custom dialog action alignment and Android 14+ full-screen intent permission flow fixes.

Highlights:
- Keeps borderless `LATER` and `CLOSE` dialog text actions vertically centered with filled action buttons after border removal.
- Uses a shared dialog action row for paired custom dialog actions, including update prompt and delete confirmation dialogs.
- Keeps single-action and title-row close controls on the same stable dialog action height.
- Keeps full-screen intent access on Android 14+ tied to `NotificationManager.canUseFullScreenIntent()`.
- Reopens the dedicated full-screen intent grant page for XHigh reminders when the permission is still missing after returning from exact-alarm setup.
- Falls back through notification settings and app details if the dedicated full-screen settings activity is unavailable.
- Documents that PixelDone-beta uses a separate debug package, so full-screen permission grants remain separate from the formal app.

Install note: use the attached signed release APK, `PixelDone-2.7.4-release.apk`, for direct installation or in-app update. This build installs as `com.milesxue.pixeldone`; PixelDone-beta remains a separate debug app under `com.milesxue.pixeldone.debug`.

CODEX & XUE
