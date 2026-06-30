PixelDone v2.5.3 refactors the app into a clearer teaching-oriented Android architecture without changing the user experience.

Highlights:
- Moves todo business rules into a pure Kotlin domain package.
- Adds a small manual dependency-injection container, repository boundary, and ViewModel teaching entry point.
- Extracts reusable pixel-style Compose controls into a dedicated components package.
- Keeps the existing SharedPreferences JSON format so existing local data remains compatible.
- Preserves the 2.5.2 UI flow, reminder behavior, image preview behavior, and in-app update behavior.
- Adds Chinese teaching comments around architecture boundaries and complex reminder rules.

Install note: use `PixelDone-2.5.3-release.apk` for private signed release installation. The attached APK is a signed release build for direct installation.

CODEX & XUE
