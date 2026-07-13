PixelDone v3.2.1 is the formal signed Android synchronization-stability release.

Highlights:
- Fixes the ghost-conflict failure where Settings could report conflicts while Review displayed none. The visible count now comes exclusively from the same strictly decoded conflict list used by Review.
- Upgrades the local todo domain database to schema 7 while preserving checklists, todos, attachment state, remote versions, and deletion tombstones.
- Moves cursor, pristine, mutation, and conflict protocol state into `pixel_done_sync.db`, with format version 1 and atomic generation activation.
- Rebuilds invalid, missing, interrupted, or future-incompatible sync metadata from a full `sinceVersion=0` cloud snapshot and explicit local intent. PixelDone does not carry a camelCase/snake_case legacy dual reader.
- Excludes the rebuildable sync database, WAL, and SHM files from Android cloud backup and device transfer while keeping the todo domain database eligible for backup.
- Separates stable, pending, conflict, network, app-update-required, and server-update-required states. Network failures retain local intent, retry once with the same mutation UUID, and never manufacture conflicts.
- Keeps the PixelDone cloud data contract at 3.2 and the local sync metadata format at 1; the 3.2.1 app patch is not presented as a server schema version.

Cloud prerequisite:
- Read `pixeldone_schema_metadata.schema_version` before release. A reported value of `3.2` requires no remote SQL change for PixelDone 3.2.1.
- A reported value of `3.1` must be upgraded with the existing `docs/pixeldone-supabase-3.2.0-migration.sql` procedure before the app is released.
- A missing, unknown, or newer value is a stop condition; do not infer or generate a migration path.

Verification:
- JVM synchronization, UI-state, backup-rule, schema-direction, and wire-format tests pass.
- Pixel 10a instrumentation covers fresh schema 7, 5-to-6-to-7 and 6-to-7 migrations, domain/tombstone preservation, attachment upload intent, metadata generation activation, interrupted-generation rollback, and invalidation rebuild.
- The attached `PixelDone-3.2.1-release.apk` is the signed formal package for `com.milesxue.pixeldone` and must use the established long-lived PixelDone release certificate.

Transport note:
- PixelDone intentionally connects to the configured direct-IP Supabase deployment over cleartext HTTP/WS. This transport does not provide confidentiality, integrity, or server identity protection, and no HTTPS migration is planned.
