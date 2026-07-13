PixelDone v3.2.0 is the formal signed Android feature release for cross-device image synchronization and authenticated password changes.

Highlights:
- Synchronizes one original JPEG, PNG, or WebP image up to 10 MiB per todo through a private native Supabase Storage bucket.
- Downloads remote images only when opened, validates content signatures and hashes, backfills existing local images, and cleans replaced or deleted cloud objects without blocking ordinary todo synchronization.
- Adds current-password verification, password update, global Supabase logout, local session removal, and fresh sign-in after a successful change.
- Extends Realtime invalidation to attachment metadata while keeping transactional cursor pulls as the only remote merge path.
- Keeps synchronization event-driven; PixelDone does not register a fixed-interval polling job.
- Upgrades local Room storage to schema 6 while preserving existing todos and local image markers.

Cloud prerequisite:
- PixelDone 3.2 is a hard protocol cutover and does not fall back to schema 3.1.
- The operator applied `pixeldone-supabase-3.2.0-storage-policies.sql` as `supabase_storage_admin`, applied the 3.2 public-schema migration, and confirmed every consolidated migration check on 2026-07-13.
- Do not change ownership of Supabase-managed Storage tables.

Distribution and risk:
- The attached `PixelDone-3.2.0-release.apk` is the signed formal package for `com.milesxue.pixeldone` and uses the established long-lived PixelDone release certificate.
- PixelDone intentionally connects to the configured direct-IP Supabase deployment over cleartext HTTP/WS. This transport does not provide confidentiality, integrity, or server identity protection, and no HTTPS migration is planned.
- The operator explicitly authorized formal publication before installed two-device image, password/global-logout, Realtime, and notification regression verification was completed. Those checks remain pending and are not represented as passed.
