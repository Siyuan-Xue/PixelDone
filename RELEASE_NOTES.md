PixelDone v2.9.2 is a formal signed patch release for one-shot Full Screen permission handling on XHigh tasks.

Highlights:
- Stops repeatedly reopening Android's Full Screen access settings when a user returns without granting permission.
- Treats an unchanged Full Screen permission state as an intentional no-grant result after the one-time request.
- Still queues the Full Screen access page once after Exact Alarm permission is granted when both capabilities are missing.
- Keeps the existing XHigh notification and in-app stop/snooze fallback behavior for devices without Full Screen access.

Install note: use the attached signed release APK, `PixelDone-2.9.2-release.apk`, for direct installation or update of `com.milesxue.pixeldone`. Existing `com.milesxue.pixeldone.debug` beta installs remain separate.

CODEX & XUE
