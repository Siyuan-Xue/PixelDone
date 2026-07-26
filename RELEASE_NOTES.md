# PixelDone 3.3.5

PixelDone 3.3.5 fixes home-screen widget list switching, checklist rendering, and completion feedback.

## Android widget

- Stores each widget's selected checklist in Glance state and publishes one configuration update, preventing the list from clearing and rebuilding during a switch.
- Maps stable todo IDs into Glance's supported collection range so MAIN, PAPER, and every other normal checklist render reliably instead of showing "Can't show content."
- Uses one continuous Surface background without a darker task panel.
- Shows a completed row with the app-aligned checked box and struck-through title, confirms the action with a restrained click vibration, updates the remaining count immediately, and removes the row after two seconds.
- Keeps completion idempotent, preserves stable scrolling IDs, and retains the unrestricted scrollable task list.

## Release status

- `versionName` is 3.3.5 and `versionCode` is 95.
- The attached `PixelDone-3.3.5-release.apk` is the signed formal APK for direct installation and in-app update.
- The remote data contract remains 3.2. Existing servers created with the earlier faulty migration still require the focused 3.2.8 attachment hotfix; its verification must return `attachment_validator_hotfix = true`.
- The cleartext HTTP deployment contract is unchanged; no HTTPS/TLS migration is required.
- The release uses the established long-lived PixelDone signing identity.
- Signing certificate SHA-256: `6D146E63D8F96D383FD9BBCFD61C61C343D7D7ECB6C98D33DB5BD7DBF56D2317`.
