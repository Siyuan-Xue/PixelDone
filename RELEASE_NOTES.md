PixelDone v3.0.0-rc.1 is a beta release candidate for the account-based Supabase sync launch.

Highlights:
- Adds email/password sign-up in the Settings CLOUD bottom panel, alongside sign-in.
- Keeps accounts scoped to cloud sync only; PixelDone still opens directly into the todo tool and works locally without signing in.
- Saves a returned Supabase session after sign-up and immediately requests local-first checklist/todo sync.
- Keeps Supabase integration lightweight through native Auth/PostgREST HTTP calls, without adding the Supabase SDK or service-role keys.
- Adds an explicit release-build switch for temporary HTTP Supabase endpoints while the server is still direct-IP and pre-HTTPS.
- Updates tests for sign-up requests, session persistence, ViewModel auth state, and error handling.

Server note: sign-up expects the self-hosted Supabase Auth service to allow email sign-up and auto-confirm email accounts, for example `ENABLE_EMAIL_SIGNUP=true`, `ENABLE_EMAIL_AUTOCONFIRM=true`, and `DISABLE_SIGNUP=false`.

Install note: this prerelease asset is the debug RC APK, `PixelDone-3.0.0-rc.1-debug.apk`, for `com.milesxue.pixeldone.debug`. It installs separately from the formal signed app `com.milesxue.pixeldone`; the latest formal signed release remains v2.10.0 until RC validation is complete.