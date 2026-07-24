# PixelDone 3.3.0

PixelDone 3.3.0 adds Markdown checklist export and a configurable Android home-screen widget.

## Markdown export

- Adds `EXPORT MARKDOWN` to the configurable Dock actions.
- Asks for confirmation before copying and offers simple or detailed Markdown.
- Exports every task in the current checklist, including completed tasks hidden by the current view, while preserving the current sort order.
- Simple export contains a checklist heading and Markdown task checkboxes. Detailed export also includes localized priority, due-date, and repeat metadata.
- Escapes Markdown-sensitive characters and flattens line breaks in checklist and task titles.

## Android widget

- Adds a resizable launcher widget implemented with AndroidX Glance.
- Lets each widget instance select one normal checklist.
- Shows unfinished tasks in the checklist's configured sort order and adapts the number of visible rows to the widget size.
- Opens the selected checklist when the widget body is tapped.
- Marks tasks complete directly from the widget; completed rows disappear from the widget.

## Release status

- `versionName` is 3.3.0 and `versionCode` is 90.
- The remote data contract remains 3.2. Existing servers created with the earlier faulty migration still require the focused 3.2.8 attachment hotfix; its verification must return `attachment_validator_hotfix = true`.
- The cleartext HTTP deployment contract is unchanged; no HTTPS/TLS migration is required.
- The release uses the established long-lived PixelDone signing identity.
- Signing certificate SHA-256: `6D146E63D8F96D383FD9BBCFD61C61C343D7D7ECB6C98D33DB5BD7DBF56D2317`.
