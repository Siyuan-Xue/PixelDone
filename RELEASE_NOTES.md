# PixelDone 3.3.6

PixelDone 3.3.6 fixes repeating reminders that could be skipped when an overdue DDL was reconciled before Android delivered its pending alarm.

## Repeating reminder reliability

- Keeps unchanged alarms intact during ordinary app, database, widget, and cloud reconciliation instead of cancelling and recreating the complete schedule.
- Immediately recovers only the latest missed daily or weekly occurrence, without flooding the user with every missed period.
- Uses an authorized exact alarm for every reminder priority, retains the XHIGH alarm-clock path, and falls back to a Doze-capable inexact alarm when exact access is unavailable.
- Carries the intended occurrence through delivery, rejects duplicate or stale callbacks, and tolerates a one-period cross-device DDL advance.
- Advances repeating DDL atomically only after successful notification delivery, schedules the next occurrence, and requests cloud synchronization.
- Reconciles reminders after boot, package replacement, exact-alarm access changes, cold start, and permission-screen return.

## Release status

- `versionName` is 3.3.6 and `versionCode` is 96.
- The attached `PixelDone-3.3.6-release.apk` is the signed formal APK for direct installation and in-app update.
- The remote data contract remains 3.2. Existing servers created with the earlier faulty migration still require the focused 3.2.8 attachment hotfix; its verification must return `attachment_validator_hotfix = true`.
- The cleartext HTTP deployment contract is unchanged; no HTTPS/TLS migration is required.
- The release uses the established long-lived PixelDone signing identity.
- Signing certificate SHA-256: `6D146E63D8F96D383FD9BBCFD61C61C343D7D7ECB6C98D33DB5BD7DBF56D2317`.
