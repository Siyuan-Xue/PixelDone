# PixelDone 3.3.1

PixelDone 3.3.1 fixes Android home-screen widget setup, preview, navigation, and dialog affordances.

## Android widget

- Replaces the widget picker's indefinite Glance loading placeholder with a representative PixelDone checklist preview and a matching legacy preview drawable.
- Adds the widget without interrupting the launcher with an automatic list picker. An unconfigured widget now asks the user to choose a list in place.
- Requires an explicit list choice, updates the exact widget instance, and returns to the launcher after configuration.
- Removes implicit jumps into the PixelDone app from the widget header, task labels, empty state, and overflow count.
- Uses the checklist heading only as an explicit list-change control while task checkboxes continue to complete tasks directly.
- Removes the square outer border so launcher-provided rounded clipping remains visually consistent.

## Dialog polish

- Adds a visible checkbox and stronger body/label type hierarchy to the available-update prompt.
- Gives simple and detailed Markdown copy equal-width, equal-emphasis actions.

## Release status

- `versionName` is 3.3.1 and `versionCode` is 91.
- The remote data contract remains 3.2. Existing servers created with the earlier faulty migration still require the focused 3.2.8 attachment hotfix; its verification must return `attachment_validator_hotfix = true`.
- The cleartext HTTP deployment contract is unchanged; no HTTPS/TLS migration is required.
- The release uses the established long-lived PixelDone signing identity.
- Signing certificate SHA-256: `6D146E63D8F96D383FD9BBCFD61C61C343D7D7ECB6C98D33DB5BD7DBF56D2317`.
