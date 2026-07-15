PixelDone v3.2.4 is a formal signed Android UI consistency release.

Highlights:
- Moves `Change password` out of the Cloud settings card and into the same bottom editor area used by sign-in, sign-up, and todo editing.
- Reuses the shared password field presentation, including rectangular geometry, per-field show/hide actions, keyboard behavior, and the PixelDone primary/secondary button treatment.
- Keeps validation and remote errors inside the active password editor, clears stale feedback when the editor is cancelled or reopened, and closes the editor when a successful password change signs the device out.
- Adds Compose regression coverage for the Cloud settings entry point and the three-field password editor, plus ViewModel coverage for clearing password-change feedback.
- Preserves the established Supabase reauthentication, password update, global sign-out, and local-data retention behavior.

Version and verification:
- `versionName` is 3.2.4 and `versionCode` is 85.
- The Supabase data contract remains 3.2, the domain database remains v7, and the sync metadata format remains v1. No server SQL or local database migration is required.
- The release uses the established long-lived PixelDone signing identity.
- The automated release gate runs JVM tests, Lint, debug assembly, Android emulator instrumentation tests, release signing verification, version metadata checks, and certificate pinning before publication.
- Signing certificate SHA-256: `6D146E63D8F96D383FD9BBCFD61C61C343D7D7ECB6C98D33DB5BD7DBF56D2317`.

Transport note:
- PixelDone intentionally connects to the configured direct-IP Supabase deployment over cleartext HTTP/WS. This transport does not provide confidentiality, integrity, or server identity protection.
