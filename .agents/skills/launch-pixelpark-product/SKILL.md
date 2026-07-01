---
name: launch-pixelpark-product
description: Launch PixelDone as a formal private signed release APK for direct distribution. Use when Codex needs to move PixelDone from debug/prototype to signed release, prepare GitHub release APK assets, update release notes and README wording, handle package/signing migration, optionally sync an external PixelPark parent repo, or coordinate optional post-release device deployment.
---

# Launch PixelPark Product

## Overview

Use this skill for formal PixelPark launches that distribute signed release APKs directly through GitHub releases. Keep it separate from debug-only shipping, daily Git cleanup, and local APK deployment.

If the task is only a debug GitHub release, use `$publish-pixelpark-subproject`. If it is only end-of-day Git wrap-up, use `$pixel-daily-ship`. If it is only device installation or file transfer, use `$deploy-android-apks`.

## Required Reading

Before editing, building, committing, pushing, releasing, or deploying, read these files from the PixelDone repository root:

1. `PROJECT_SPEC.md`
2. `PRODUCT_LINE_SPEC.md`
3. `DESIGN_SPEC.md`

Also inspect the PixelDone repository:

- `git status -sb`, current branch, and `git remote -v`.
- `.gitignore`, especially release signing exclusions.
- `README.md`, `RELEASE_NOTES.md`, app Gradle config, package declarations, manifest, tests, and any update-check/release-link code.
- External parent `.gitmodules` and parent `README.md` only when parent synchronization is explicitly in scope and a parent PixelPark checkout is available.

Stop if the three rule documents are unavailable.

## Intake

Confirm or infer these launch decisions before changing files:

- Target app: PixelDone.
- Release kind: first formal release, feature release, patch release, or package/signing migration.
- Target `versionName` and `versionCode` policy. Increment `versionCode`; keep `versionName` semantic and in release order.
- Distribution target: private signed release APK through GitHub release. Do not produce Google Play AAB unless explicitly requested.
- Package migration stance. Formal PixelPark package names should use `com.milesxue.<product>`.
- Data migration stance. If package name or signing certificate changes, default to no local data migration unless the user explicitly asks for it.
- Deploy stance. Do not install to devices unless the user explicitly asks for deployment after the release build.
- Parent sync stance. Update an external PixelPark parent README/submodule pointer only when explicitly requested and only after the app release asset has been verified.

## Formal Release Workflow

1. Enter the PixelDone repository root before app Git wrap-up or release work.
2. Verify the app repo is the expected GitHub repository:

```sh
git status -sb
git remote -v
git branch --show-current
```

Expected origin is `https://github.com/Siyuan-Xue/PixelDone.git`.

3. Apply PixelPark formal identity:
   - Use `com.milesxue.<product>` for `namespace`, `applicationId`, Kotlin packages, test packages, and app-owned identifiers that intentionally include the package.
   - Use `CODEX & XUE` exactly in user-visible product identity, README, release notes, and metadata.
   - Remove old user-visible `CODEX x XUE` wording when touched.

4. Configure release signing with a long-lived key:
   - Use the app's established local pattern, preferably `signing/release-signing.properties` plus a per-app release keystore under `signing/`.
   - Keep keystores, passwords, and local signing properties out of Git.
   - Confirm `.gitignore` excludes signing secrets before building.
   - Never print, commit, or paste passwords into final reports.

5. Set version values in the app module:
   - `versionName` must be the release version used for the tag and APK file name.
   - `versionCode` must increase from the previous installed/released build.
   - For first formal direct APK releases, use the user-approved `1.0.0` target. For later releases, infer patch vs feature from the change when obvious; otherwise ask one concise question.

6. Update product docs:
   - README should describe the app as a private/internal release, not a debug prototype.
   - `RELEASE_NOTES.md` should match the target version and mention that the attached asset is a signed release APK for direct installation.
   - If package name or signing certificate changed, include an uninstall note for incompatible old debug/prototype builds.
   - Build/install commands and APK paths must match the real project.

7. Build and verify from the app repo:

Windows:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleRelease
```

macOS/Linux:

```sh
./gradlew testDebugUnitTest
./gradlew assembleRelease
```

Confirm both files exist in `app/build/outputs/apk/release/`:

```text
app-release.apk
{ProjectName}-{versionName}-release.apk
```

Verify the versioned release APK with `apksigner verify --verbose --print-certs` using the local Android SDK build-tools path.

8. Commit only coherent app release changes. Do not stage generated build outputs, signing secrets, parent repo files, or unrelated workspace changes.
9. Before creating a GitHub release, check the target tag/release:

```sh
gh release view v{versionName} --repo Siyuan-Xue/PixelDone
```

If the tag or release exists, verify it points to the intended commit. Do not overwrite existing tags or releases; choose the next semantic version when needed.

10. Create the app GitHub release from the app repo with the signed APK:

```sh
gh release create v{versionName} app/build/outputs/apk/release/PixelDone-{versionName}-release.apk --repo Siyuan-Xue/PixelDone --title "PixelDone v{versionName}" --notes-file RELEASE_NOTES.md
```

Then verify the uploaded asset:

```sh
gh release view v{versionName} --repo Siyuan-Xue/PixelDone --json tagName,url,assets
```

Treat the release as incomplete if the versioned release APK asset is missing.

## Parent PixelPark Sync

After the app release asset is verified, update an external parent PixelPark checkout only if the user explicitly requested parent synchronization and provided or already has that checkout available.

Update only the relevant parent files, usually:

- Root `README.md` release/download entry.
- `.gitmodules` only if the app submodule mapping changed.
- The app submodule/gitlink pointer.

Stage parent changes separately from app changes inside the external parent checkout:

```sh
git add .gitmodules README.md {ProjectName}
git diff --cached --name-status .gitmodules README.md {ProjectName}
git ls-files --stage {ProjectName} .gitmodules README.md
```

Confirm the app path is mode `160000` when it is a submodule/gitlink. Commit and push the parent update only after the app release URL and APK asset name are known.

## Optional Deployment Handoff

Deploy only when the user explicitly requests it. For signed release APK installs, install the versioned release APK, not the debug APK. If the request is purely local deployment, switch to `$deploy-android-apks` or adapt its device-detection flow while preserving release APK selection.

Before installing, confirm the target device is authorized through `adb devices -l`. After installing, verify with:

```sh
adb shell dumpsys package {applicationId}
adb shell pm list packages com.milesxue
```

Report blocked device installs plainly, including device name, connection state, and the failed command result. Do not mark release publishing as failed just because an optional device install was blocked.

## Safety Lines

- Do not release PixelDone from a parent workspace repository.
- Do not overwrite existing tags, releases, or release assets.
- Do not commit `signing/`, keystores, passwords, `local.properties`, build outputs, or IDE local state.
- Do not publish source archives alone; every formal release must include the versioned signed release APK.
- Do not deploy after a no-deploy update-check release; leave the installed older version available to test in-app update detection.
- Keep debug release wording out of formal release notes unless the task is explicitly a debug release.
- Keep a final audit: app commit, parent commit if any, release URL, uploaded APK name, signing verification status, tests/builds run, deployment status, and any unrelated dirty files left untouched.
