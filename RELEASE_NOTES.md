# PixelDone 3.3.3

PixelDone 3.3.3 redesigns the Android home-screen widget and fixes list switching and completion refresh.

## Android widget

- Centers the unconfigured and unavailable-list states with a pixel checklist icon, concise guidance, and a clear list-selection action.
- Makes the complete widget header a stable list-change target with visible list and switch icons.
- Gives each widget instance a unique configuration intent so reopening the chooser cannot reuse another widget’s stale target.
- Keeps the explicit “Show this list” confirmation step, adds saving and retry feedback, and commits the selected list before refreshing the exact widget instance.
- Replaces the fixed three-row task preview with a scrollable unfinished-task list while keeping the checklist header fixed.
- Uses stable task item IDs to preserve scroll position when the list changes on Android 12 and later.
- Refreshes the exact widget instance after completing a task so the completed row and remaining count update immediately.

## Release status

- `versionName` is 3.3.3 and `versionCode` is 93.
- The remote data contract remains 3.2. Existing servers created with the earlier faulty migration still require the focused 3.2.8 attachment hotfix; its verification must return `attachment_validator_hotfix = true`.
- The cleartext HTTP deployment contract is unchanged; no HTTPS/TLS migration is required.
- The release uses the established long-lived PixelDone signing identity.
- Signing certificate SHA-256: `6D146E63D8F96D383FD9BBCFD61C61C343D7D7ECB6C98D33DB5BD7DBF56D2317`.
