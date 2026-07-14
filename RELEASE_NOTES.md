PixelDone v3.2.3 is a formal signed Android stability release.

Highlights:
- Reads Supabase Auth failures by the stable `error_code` field instead of matching server message text.
- Treats expired or missing refresh-token sessions as a required sign-in: credentials are cleared, Realtime and background retry stop, and local todos, tombstones, conflicts, and queued mutations remain intact.
- Keeps rate limits, server failures, timeouts, and ordinary network interruption transient; those failures do not sign the user out or manufacture conflicts.
- Keeps manual synchronization state exclusively under the `Sync` row instead of writing a second `Sync failed.` message into the authentication editor.
- Removes the duplicate About-area sync status and aligns the full-width `Change password` action with the Account and Sync rows.
- Uses real 400/500/600/700 instances of the bundled Source and Noto variable fonts, with synthetic weight disabled.
- Keeps UI chrome tied to the selected language while rendering saved user content by Unicode script run, so the same Latin/Cyrillic, CJK, Arabic, mixed-script, punctuation, and emoji content does not change typeface when the UI language changes.
- Raises muted supporting labels to at least 12sp/500 and keeps semantic colors at full opacity.
- Keeps Supabase Realtime as the cloud-change trigger. No fixed-interval cloud polling was added.

Version and verification:
- `versionName` is 3.2.3 and `versionCode` is 84.
- The Supabase data contract remains 3.2, the domain database remains v7, and the sync metadata format remains v1. No server SQL or local database migration is required.
- The release uses the established long-lived PixelDone signing identity.
- `testDebugUnitTest`, `lintDebug`, all 6 connected tests on Pixel 10a / Android 16, `assembleDebug`, and `assembleRelease` pass.
- An in-place signed upgrade from the installed 3.2.2 build preserved app data, classified the real `refresh_token_not_found` session as signed out, and stopped further refresh retries.
- Release asset: `PixelDone-3.2.3-release.apk` (41,296,967 bytes).
- SHA-256: `0BD85F0886FA735954408634B8B90A6ED50A79056B04EC447947765E97454778`.
- Signing certificate SHA-256: `6D146E63D8F96D383FD9BBCFD61C61C343D7D7ECB6C98D33DB5BD7DBF56D2317`.

Transport note:
- PixelDone intentionally connects to the configured direct-IP Supabase deployment over cleartext HTTP/WS. This transport does not provide confidentiality, integrity, or server identity protection.
