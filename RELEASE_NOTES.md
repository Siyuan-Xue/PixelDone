PixelDone v3.1.1-rc.1 is the beta release candidate for Android full-screen intent access preservation.

Highlights:
- Replaces the generic sideload install intent with an Android PackageInstaller session for PixelDone's in-app updater.
- Preserves the existing Android 14+ full-screen intent access state across in-app upgrades: granted remains granted and denied remains denied.
- Keeps the system-controlled install confirmation and install-unknown-apps access flow.
- Keeps denied full-screen intent behavior on the existing expanded heads-up notification fallback.

Upgrade note:
- An app version older than 3.1.1 still launches Android's generic sideload installer, so upgrading from 3.1.0 may turn Full Screen access off one final time. Re-enable it after installing 3.1.1; later PixelDone in-app updates preserve that choice.

Distribution:
- The attached `PixelDone-3.1.1-rc.1-debug.apk` is the beta package for `com.milesxue.pixeldone.debug` and can coexist with the formal app.
- PixelDone intentionally connects to the configured direct-IP Supabase deployment over cleartext HTTP. HTTP does not provide transport confidentiality or server identity verification, and no HTTPS migration is planned.
- Task images remain local to each device and are not uploaded to Supabase Storage.

Before first use, the operator must apply `docs/pixeldone-supabase-3.1.0-rc.1-migration.sql` once and verify its final checkpoint output. PixelDone 3.1 has no legacy schema fallback.
