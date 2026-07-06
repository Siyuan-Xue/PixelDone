PixelDone v3.0.0 is the formal signed release for account-based Supabase sync.

Highlights:
- Adds Settings-scoped email/password sign-up, sign-in, sign-out, and manual sync without making login an app entry gate.
- Syncs local-first checklists and todos through the self-hosted Supabase backend while keeping PixelDone fully usable offline and locally.
- Moves the Settings CLOUD section to the top so account and sync state are easy to find.
- Keeps account actions low-emphasis: SIGN IN and OUT live on the ACCOUNT row as borderless text actions, while SYNC remains the only bordered cloud action.
- Adds SHOW/HIDE password visibility in the cloud auth bottom panel.
- Keeps Supabase integration lightweight through native Auth/PostgREST HTTP calls, without adding the Supabase SDK or service-role keys.
- Uses the formal `com.milesxue.pixeldone` package and a signed release APK for direct installation and in-app updates.

Server note: sign-up expects the self-hosted Supabase Auth service to allow email sign-up and auto-confirm email accounts, for example `ENABLE_EMAIL_SIGNUP=true`, `ENABLE_EMAIL_AUTOCONFIRM=true`, and `DISABLE_SIGNUP=false`.

Install note: this release asset is the signed APK `PixelDone-3.0.0-release.apk` for `com.milesxue.pixeldone`. Debug RC builds use the separate package `com.milesxue.pixeldone.debug` and can remain installed separately.