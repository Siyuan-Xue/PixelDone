# PixelDone 3.2.6

PixelDone 3.2.6 fixes Cloud configuration loading and prevents priority edits from creating duplicate todos.

## Cloud configuration

- Removes boundary BOM and whitespace from the Supabase URL and publishable key at both build time and runtime.
- Validates that configured endpoints use HTTP or HTTPS and include a host while continuing to support the established direct-IP cleartext HTTP deployment.
- Uses the normalized URL and key consistently for REST, attachment, and Realtime clients without exposing the key in diagnostics.

## Todo editing

- Saves a new todo only while the editor is explicitly in new-task mode.
- Updates only the active todo ID while editing; a missing or cancelled target now fails safely instead of falling back to a new UUID.
- Closes the active edit target before clearing its draft, preventing a cancelled edit followed by a priority change on another todo from duplicating an item.
- Preserves legitimate todos that share the same title; no title-based deduplication was added.

## Release status

- `versionName` is 3.2.6 and `versionCode` is 87.
- The remote data contract remains 3.2 and requires no Supabase migration.
- The cleartext HTTP deployment contract is unchanged; no HTTPS/TLS migration is required.
- The release uses the established long-lived PixelDone signing identity.
- Signing certificate SHA-256: `6D146E63D8F96D383FD9BBCFD61C61C343D7D7ECB6C98D33DB5BD7DBF56D2317`.
