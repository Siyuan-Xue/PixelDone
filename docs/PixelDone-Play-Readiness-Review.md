# PixelDone Play Readiness Review

Date: 2026-07-05

This review covers the current PixelDone codebase as a Google Play production candidate. It focuses on robustness, security, privacy, Play policy risk, and Dock function boundaries. It is not a legal opinion; final Play Console declarations must reflect the exact artifact submitted.

## Executive Summary

PixelDone is close to a production-quality local todo utility, but it should not be submitted to Google Play with the current direct APK updater unchanged.

The main production blocker is `REQUEST_INSTALL_PACKAGES`. PixelDone's core purpose is task management, not app-package transfer or package installation, so the direct APK install flow is high risk for Play review. A Play production artifact should remove the install-packages permission and the sideload update install path, and rely on Google Play delivery for updates.

The reminder model is defensible if it is presented clearly as an alarm/reminder feature. Exact alarm and full-screen intent use must remain contextual, optional where the platform allows, and accurately declared in Play Console.

## Play Policy And Permission Risks

- **High: direct APK updater**
  - Current manifest declares `REQUEST_INSTALL_PACKAGES`, and `AppUpdateDownloader` opens a package installer prompt for APKs downloaded from GitHub/Gitee.
  - Google Play restricts this permission to apps whose core functionality includes sending/receiving app packages and user-initiated package installation.
  - Required before Play production: create a Play-safe release path that removes this permission and disables APK install prompts for the Play artifact.

- **Medium: exact alarms**
  - `SCHEDULE_EXACT_ALARM` supports XHigh reminder behavior through `AlarmManager`.
  - Current code has fallback behavior and settings routing. Keep requests contextual and document the alarm/reminder value in the store listing and permission declaration.

- **Medium: full-screen intent**
  - `USE_FULL_SCREEN_INTENT` is used for XHigh alarm interruption, with Android 14+ grant checks.
  - Keep full-screen use limited to user-created XHigh reminders. Do not use it for update prompts, marketing, or general notifications.

- **Medium: backup and data extraction**
  - `allowBackup="true"` is enabled, while backup XML files still look template-derived.
  - Before production, choose and document an explicit policy: include local todos/images for device restore, or exclude sensitive local task content from cloud backup.

- **Low: network and file provider**
  - `INTERNET` is used for release checks and update downloads. If the Play build removes direct APK updates, reassess whether release-check networking still has product value.
  - The FileProvider is not exported and only grants URI read access for update APKs in app-specific external files.

## Data Safety And Privacy Notes

- Todo titles, due times, priorities, completion state, checklist names, and attached images are stored locally.
- The app does not currently contain accounts, ads, analytics SDKs, cloud sync, or third-party tracking SDKs.
- Image attachment uses the platform picker and app-private copies instead of broad media permissions.
- Release checking contacts GitHub/Gitee release APIs but does not intentionally transmit user-created todo content.
- A Google Play listing still needs a privacy policy and Data Safety form. If no user data leaves the device, the policy and form should say that clearly and consistently.

## Functional Button Boundary Review

The Dock functions are appropriately atomic after the naming and icon refinements:

- `SORT`: toggles only the ordering policy between priority and time.
- `DDL`: toggles only deadline countdown display in the row subtitle.
- `HIDE DONE`: toggles only completed-task visibility.
- `CLEAN DONE`: performs one destructive batch cleanup of completed tasks only.
- `QUICK DELETE`: toggles row-level delete affordances for direct item deletion.

The two destructive functions are distinct but must stay visually and textually separated. `CLEAN DONE` should keep batch/completed wording; `QUICK DELETE` should keep row/delete-mode wording and the list-plus-trash icon.

## Robustness Notes

- The current local persistence boundary keeps SharedPreferences JSON behind repository/store APIs, which is acceptable for this app size.
- The image store canonicalizes app-private paths before reading or deleting files.
- Reminder scheduling is isolated behind a scheduler boundary and has domain unit tests.
- Before a formal production build, run a release build with real signing, verify the release APK/AAB, and review whether release optimization should stay disabled.

## Sources

- Google Play `REQUEST_INSTALL_PACKAGES` policy: https://support.google.com/googleplay/android-developer/answer/12085295
- Google Play sensitive permissions policy, including exact alarm and full-screen intent: https://support.google.com/googleplay/android-developer/answer/16558241
- Google Play Data Safety guidance: https://support.google.com/googleplay/android-developer/answer/10787469
