PixelDone v3.0.0-rc.2 is a beta release candidate for the account-based Supabase sync launch.

Highlights:
- Moves the Settings CLOUD section to the top so sync/account state is easier to find.
- Keeps account actions low-emphasis: SIGN IN and OUT now live on the ACCOUNT row as borderless text actions.
- Keeps SYNC as the only bordered cloud action, preserving visual priority for manual sync.
- Adds SHOW/HIDE password visibility in the cloud auth bottom panel.
- Preserves the RC1 Supabase sign-up/sign-in and local-first todo/checklist sync behavior without adding backend changes.

Server note: sign-up expects the self-hosted Supabase Auth service to allow email sign-up and auto-confirm email accounts, for example `ENABLE_EMAIL_SIGNUP=true`, `ENABLE_EMAIL_AUTOCONFIRM=true`, and `DISABLE_SIGNUP=false`.

Install note: this prerelease asset is the debug RC APK, `PixelDone-3.0.0-rc.2-debug.apk`, for `com.milesxue.pixeldone.debug`. It installs separately from the formal signed app `com.milesxue.pixeldone`; the latest formal signed release remains v2.10.0 until RC validation is complete.