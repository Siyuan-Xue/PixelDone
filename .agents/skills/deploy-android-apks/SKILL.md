---
name: deploy-android-apks
description: Deploy or install versioned PixelDone debug Android APKs from this standalone repository to an authorized Android device or emulator. Use when Codex is asked to install, copy, deploy, push, transfer, or refresh PixelDone debug APK packages with adb or an OS-specific file transfer workflow.
---

# Deploy Android APKs

## Overview

Deploy only versioned debug APKs from this PixelDone repository to an authorized Android device or emulator. When `adb devices -l` shows an authorized target, prefer direct installation with `adb install -r -d`. Use APK file transfer only when direct install is unavailable or explicitly requested.

## Required Reading

Before deploying, building, or judging APK freshness, read these repository documents from the PixelDone repository root:

1. `PROJECT_SPEC.md`
2. `PRODUCT_LINE_SPEC.md`
3. `DESIGN_SPEC.md`

Stop if any file is unavailable. Treat the debug APK naming and output rules in `PROJECT_SPEC.md` as authoritative.

## Deployment Rules

- Deploy `{ProjectName}-{versionName}-debug.apk` from `app/build/outputs/apk/debug/`.
- Do not deploy `app-debug.apk` unless the user explicitly overrides the repository rule.
- Do not deploy `androidTest` APKs.
- Do not create releases, tags, commits, or GitHub assets; this skill is local device transfer only.
- This standalone copy defaults to the current PixelDone repository only.
- If the versioned APK is missing, older than `app-debug.apk`, or the user asks for latest packages, announce the reason and run `./gradlew assembleDebug` on macOS/Linux or `.\gradlew.bat assembleDebug` on Windows.
- After `assembleDebug`, confirm both `app-debug.apk` and the versioned debug APK exist in `app/build/outputs/apk/debug/`.

## Method Order

Use these methods in order, falling back only when the earlier method is unavailable or fails:

1. `adb-install`: if `adb devices -l` shows an authorized target, run `adb install -r -d <apk>` so the app is installed directly and no device file browsing is needed.
2. `adb-push`: if direct install is not desired or fails but adb is still available, push the APK to `/sdcard/Download/<apk>` and verify the remote byte size.
3. `mtp-copy`: on Windows only, if adb cannot see the real device, an MTP helper may copy into the requested device's shared storage `Download` folder.
4. `manual`: if neither adb nor MTP is available, report the missing device state and the local APK paths.

## Preferred Script

Use `scripts/deploy_android_apks.ps1` on Windows or PowerShell Core when available. Use `scripts/deploy_android_apks.sh` on macOS/Linux. Both scripts check the PixelDone versioned APK, optionally build stale outputs, choose the best deployment method available on the host, and emit structured result objects. The `mtp-copy` fallback remains Windows-only; the macOS/Linux shell script uses adb install or adb push, otherwise it reports the local APK path.

Common dry run:

Windows:

```powershell
.\.agents\skills\deploy-android-apks\scripts\deploy_android_apks.ps1 -DryRun
```

macOS/Linux:

```sh
.agents/skills/deploy-android-apks/scripts/deploy_android_apks.sh --dry-run
```

Install PixelDone directly when adb is available, otherwise fall back to supported file transfer:

Windows:

```powershell
.\.agents\skills\deploy-android-apks\scripts\deploy_android_apks.ps1
```

macOS/Linux:

```sh
.agents/skills/deploy-android-apks/scripts/deploy_android_apks.sh
```

Use `-DeliveryMode Install` or `--delivery-mode Install` to require direct adb installation. Use `-DeliveryMode Copy` or `--delivery-mode Copy` to skip installation and copy APK files to `Download`. Use `-BuildMode Never` or `--build-mode Never` when the user asks to use only existing APKs. Use `-BuildMode Always` or `--build-mode Always` when the user asks to rebuild first. Pass `-DeviceName` or `--device-name` only when multiple authorized devices are connected.

## Verification

For direct installs, treat a successful `adb install -r -d` exit code as verification and report the app as installed.

For adb file copies, verify the remote file size with `adb shell ls -ln` and compare it with local bytes.

For MTP deployments, do not rely on modified timestamps alone. Windows MTP may show stale or coarse metadata. Verify by confirming the target folder contains the APK name, the item type is `APK File` when available, and the displayed size is close to the local file size.

In the final response, list each project, APK file name, target device, method (`adb-install`, `adb-push`, `mtp-copy`, or `none`), status, and verification status.
