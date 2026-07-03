PixelDone v2.9.1 is a formal signed patch release for reliable XHigh alarm controls when Android full-screen access is unavailable.

Highlights:
- Keeps XHigh alarms ringing when Full Screen access is missing, matching the existing urgent-alarm behavior.
- Stores the currently ringing XHigh alarm so PixelDone can show an emergency control panel after the app is opened.
- Adds `SNOOZE 10` and `STOP` controls in the main PixelDone UI while an XHigh alarm is active.
- Stops alarm sound and vibration when the XHigh alarm notification is dismissed.
- Clears stale active-alarm state after reboot or after the alarm is stopped, snoozed, or the service is destroyed.
- Preserves the existing lock-screen full-screen alarm screen when Android grants Full Screen access.

Install note: use the attached signed release APK, `PixelDone-2.9.1-release.apk`, for direct installation or update of `com.milesxue.pixeldone`. Existing `com.milesxue.pixeldone.debug` beta installs remain separate.

CODEX & XUE
