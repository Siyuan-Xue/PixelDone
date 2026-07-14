PixelDone v3.2.2 is the formal signed Android companion release to Windows 3.2.4.

Highlights:
- Refreshes the exact-alarm permission state immediately after returning from Android system settings; an app restart is no longer required.
- Also listens for the Android exact-alarm permission-state broadcast and reschedules reminders when the grant changes.
- Keeps Trash and Settings as local synthetic destinations: they cannot upload as cloud checklists, produce checklist tombstones, or appear as ambiguous conflicts.
- Resolves normal conflict values to human-readable checklist names, one-based positions, localized status, priority, repeat, and language labels.
- Rewords conflict actions as “This device” and “Cloud version” so the selected source is explicit.
- Bundles the same OFL-licensed Source/Noto serif and sans families used by Windows, with Chinese and Arabic-specific typography.
- Keeps Supabase Realtime as the cloud-change trigger. No fixed-interval cloud polling was added.

Version and verification:
- `versionName` is 3.2.2 and `versionCode` is 83.
- The JVM unit suite, `lintDebug`, `assembleDebug`, and `assembleRelease` pass.
- The formal release asset is `PixelDone-3.2.2-release.apk` (41,296,315 bytes; SHA-256 `74933FB079D651D0014879C2591F8C7FC2D3C8387A61FDA466E658B4BB42DAF5`), signed with the established long-lived PixelDone release certificate.
- The Supabase data contract remains 3.2 and requires no new server migration.
- Existing device-specific exact-alarm and dual-device cloud scenarios remain part of ongoing field verification; this release does not claim those environments were reproduced locally.

Transport note:
- PixelDone intentionally connects to the configured direct-IP Supabase deployment over cleartext HTTP/WS. This transport does not provide confidentiality, integrity, or server identity protection.
