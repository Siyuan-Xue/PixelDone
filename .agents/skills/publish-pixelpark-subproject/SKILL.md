---
name: publish-pixelpark-subproject
description: Verify and publish the already-standalone PixelDone repository to GitHub, with optional external PixelPark parent synchronization only when explicitly requested. Use for debug APK publication or repository linkage checks, not for creating a new subproject.
---

# Publish PixelDone Repository

## Overview

PixelDone is already a standalone Git repository. Use this skill to verify the repository remote, run debug release checks, push `main`, and optionally create an explicit beta RC GitHub prerelease.

GitHub is the canonical publishing surface for code pushes, tags, and release assets. PixelDone's in-app updater checks GitHub Releases first and uses the user's synced Gitee release mirror as fallback, so create beta RC prereleases on GitHub first and verify the Gitee mirror only after the external sync has run.

Do not treat a PixelDone-only clone as a parent PixelPark workspace. Parent synchronization is optional and requires an external parent checkout.

## Required Reading

Before editing, building, committing, pushing, or releasing, read these files from the PixelDone repository root:

1. `PROJECT_SPEC.md`
2. `PRODUCT_LINE_SPEC.md`
3. `DESIGN_SPEC.md`

Also inspect `.gitignore`, `README.md`, `RELEASE_NOTES.md`, and `app/build.gradle.kts`.

## Repository Verification

1. Confirm the repository state:

```sh
git status -sb
git remote -v
git branch --show-current
```

2. Expected origin:

```text
https://github.com/Siyuan-Xue/PixelDone.git
```

3. Confirm `.gitignore` excludes generated and local files:

```text
.gradle/
.idea/ local state
build/
app/build/
local.properties
signing/
captures/
.externalNativeBuild/
.cxx/
```

## Debug Release Checks

Run the required checks from the PixelDone repository root.

Windows:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

macOS/Linux:

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Verify both APKs exist in `app/build/outputs/apk/debug/`:

```text
app-debug.apk
PixelDone-{versionName}-debug.apk
```

## GitHub Beta RC Prerelease Workflow

Create beta RC GitHub prereleases only when explicitly requested. Formal user updates should use signed release APKs through `$launch-pixelpark-product`.

The beta `versionName` should use the RC form, such as `2.8.0-rc.1`. The GitHub prerelease tag is `v{versionName}`, and the attached debug APK asset is `PixelDone-{versionName}-debug.apk`.

1. Check the target tag first:

```sh
gh release view v{versionName} --repo Siyuan-Xue/PixelDone
```

2. If it does not exist, create it with the versioned debug APK:

```sh
gh release create v{versionName} app/build/outputs/apk/debug/PixelDone-{versionName}-debug.apk --repo Siyuan-Xue/PixelDone --title "PixelDone v{versionName}" --notes-file RELEASE_NOTES.md --prerelease
```

3. Verify the release asset is uploaded:

```sh
gh release view v{versionName} --repo Siyuan-Xue/PixelDone --json tagName,url,assets
```

Treat the release as incomplete if the versioned debug APK asset is missing.

After the user's configured Gitee synchronization runs, verify that the synced Gitee prerelease also contains the same versioned debug APK asset before marking beta in-app update availability complete.

## Optional External Parent Sync

Only perform parent PixelPark synchronization when the user explicitly asks and a parent PixelPark checkout is available outside this repository.

Keep parent changes separate from PixelDone changes. Stage only the parent `.gitmodules`, parent `README.md`, and the PixelDone gitlink in the parent checkout. Never commit parent files from the PixelDone repository.

## Safety Checks

- Keep unrelated changes unstaged.
- Do not commit generated build outputs, APKs, signing secrets, `local.properties`, IDE local state, or OS-specific cache files.
- Use `require_escalated` for commands that push, create releases, write Git refs/indexes, or need network access.
- After finishing, report the PixelDone commit, GitHub release URL if any, uploaded APK name if any, Gitee mirror status if checked, parent sync status if requested, and any remaining unrelated dirty files.
