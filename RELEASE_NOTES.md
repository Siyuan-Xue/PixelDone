# PixelDone 3.3.4

PixelDone 3.3.4 fixes widget completion feedback and hardens XHigh alarms so they remain controllable on locked devices.

## Android widget

- Uses a compact checkbox styled consistently with the in-app todo checkbox while preserving a touch-friendly row.
- Observes both the selected checklist and repository state, so changing lists or completing a task refreshes the active widget immediately.
- Makes completion idempotent and refreshes the exact widget even if reminder or cloud-sync follow-up fails.
- Keeps the scrollable unfinished-task list and stable task IDs.

## XHigh alarm reliability

- Prepares alarm audio asynchronously so the full-screen controls and STOP/SNOOZE remain responsive while playback starts or fails.
- Safely retires preparing or failed players and ignores late callbacks, preventing stopped or snoozed alarms from restarting.
- Stops sound and vibration after a 10-minute hard timeout, then clears the foreground alarm session; Back snoozes for 10 minutes.
- Keeps the alarm controls available over the lock screen without weakening the system lock, handles consecutive alarm intents, and opts into Android 15+ background activity launch rules.
- Rechecks notification, channel, exact-alarm, and full-screen access at delivery time; falls back to a short notification or suppresses uncontrolled sound and vibration when required controls cannot be shown.
- Shows the foreground-service notification immediately so STOP/SNOOZE actions are not deferred.
- Delivers companion short reminders only when their notification is actually posted, including when the XHigh loop reaches its hard timeout.

## Release status

- `versionName` is 3.3.4 and `versionCode` is 94.
- The attached `PixelDone-3.3.4-release.apk` is the signed formal APK for direct installation and in-app update.
- The remote data contract remains 3.2. Existing servers created with the earlier faulty migration still require the focused 3.2.8 attachment hotfix; its verification must return `attachment_validator_hotfix = true`.
- The cleartext HTTP deployment contract is unchanged; no HTTPS/TLS migration is required.
- The release uses the established long-lived PixelDone signing identity.
- Signing certificate SHA-256: `6D146E63D8F96D383FD9BBCFD61C61C343D7D7ECB6C98D33DB5BD7DBF56D2317`.
