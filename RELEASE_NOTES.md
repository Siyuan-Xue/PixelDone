# PixelDone 3.2.8

PixelDone 3.2.8 restores image synchronization for existing 3.2 servers and reports remote failures accurately.

## Attachment sync

- Fixes the 3.2 attachment validator, where PostgreSQL's concatenation operator (`||`) was accidentally used between boolean conditions and caused active attachment mutations to fail with SQLSTATE `42883`.
- Includes the focused, idempotent `docs/pixeldone-supabase-3.2.8-attachment-hotfix.sql` for servers already running schema 3.2.
- Corrects the canonical 3.2 migration so new deployments do not inherit the defect.

## Error handling

- Recognizes PostgREST's standard `code` error field in addition to Supabase Auth's `error_code` field.
- Shows `SERVER UPDATE REQUIRED` only when the required RPC is actually missing or the server returns an incompatible contract, instead of treating every HTTP 400/404 database failure as a schema mismatch.

## Release status

- `versionName` is 3.2.8 and `versionCode` is 89.
- The remote data contract remains 3.2. Existing servers created with the earlier faulty migration require the focused 3.2.8 attachment hotfix; its verification must return `attachment_validator_hotfix = true`.
- The cleartext HTTP deployment contract is unchanged; no HTTPS/TLS migration is required.
- The release uses the established long-lived PixelDone signing identity.
- Signing certificate SHA-256: `6D146E63D8F96D383FD9BBCFD61C61C343D7D7ECB6C98D33DB5BD7DBF56D2317`.
