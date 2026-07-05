PixelDone v2.10.0-rc.1 is a beta release candidate for the local-first storage and sync-readiness refactor.

Highlights:
- Moves todo/checklist persistence to Room while keeping the existing SharedPreferences JSON format as a legacy migration reader.
- Moves local settings such as theme, Dock configuration, update prompt preference, and future sync toggle placeholder behind DataStore.
- Routes persisted settings through ViewModel actions and immutable UI state instead of direct Composable storage writes.
- Adds local-only auth/sync seams and pure Kotlin conflict-resolution rules without login UI, Supabase SDKs, server config, or network sync.
- Adds future Tencent Cloud Lighthouse + self-hosted Supabase architecture notes for a later cross-device sync phase.

Install note: this prerelease asset is the debug RC APK, `PixelDone-2.10.0-rc.1-debug.apk`, for `com.milesxue.pixeldone.debug`. It installs separately from the formal signed app `com.milesxue.pixeldone`; the latest formal signed release remains v2.9.3.
