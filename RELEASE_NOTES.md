PixelDone v2.10.1-rc.1 is a beta release candidate for Supabase sync reliability after the 2.10.0 cloud-sync launch.

Highlights:
- Fixes the Pixel 10A sync failure diagnosed from local sync metadata as Supabase `PGRST303` / `JWT expired`.
- Refreshes expired Supabase access tokens before sync when a refresh token is available.
- Retries a sync once after a server-side JWT-expired response, then stores the new rotated refresh token for future syncs.
- Adds low-noise `PixelDoneSync` Logcat diagnostics for sync failures without logging bearer tokens.
- Keeps the app local-first and does not change the Supabase table schema.

Install note: this prerelease asset is the debug RC APK, `PixelDone-2.10.1-rc.1-debug.apk`, for `com.milesxue.pixeldone.debug`. It installs separately from the formal signed app `com.milesxue.pixeldone`; the latest formal signed release remains v2.10.0.