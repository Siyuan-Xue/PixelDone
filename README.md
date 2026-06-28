# PixelDone

PixelDone is a private Android todo planner for quickly adding, sorting, completing, hiding, and clearing local tasks.

Developer identity: CODEX & XUE.

## Current Features

- Add todos with a name, priority, and date/time.
- Tap a todo row to edit it in the bottom editor.
- Sort by priority or time.
- Priority sorting uses High, Medium, Low, then due time.
- Tap a row checkbox to mark a todo done or active again.
- Hide completed todos.
- Delete all completed todos from the top list controls.
- Schedule a local alarm notification for each active future todo.
- Use restrained haptic feedback for core task and list-state actions.
- Check GitHub Releases automatically on app start and show a quiet update dot when a release is available.
- Let users permanently hide automatic update dialogs while keeping the update-dot signal.
- Persist todos locally on the device with SharedPreferences JSON.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX
- SharedPreferences
- AlarmManager notifications

## Build

From the project root:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleRelease
```

Release signing is configured through the local, untracked `signing/release-signing.properties` file.

The signed private release APK is copied to:

```text
app/build/outputs/apk/release/PixelDone-1.3.2-release.apk
```

## Install

If the old prototype package is still installed, uninstall it before installing:

```powershell
adb uninstall com.codexue.pixeldone
adb install -r app/build/outputs/apk/release/PixelDone-1.3.2-release.apk
```

The formal package name is:

```text
com.milesxue.pixeldone
```

## Status

Private 1.3.2 release.
