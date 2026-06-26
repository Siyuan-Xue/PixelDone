# PixelDone

PixelDone is a bright pixel-style Android todo planner for quickly adding, sorting, completing, hiding, and clearing local tasks.

## Current features

- Add todos with a name, priority, and date/time.
- Tap a todo row to edit it in the bottom editor.
- Sort by priority or time.
- Priority sorting uses High, Medium, Low, then due time.
- Tap a row checkbox to mark a todo done or active again.
- Hide completed todos.
- Delete all completed todos from the top list controls.
- Schedule a local alarm notification for each active future todo.
- Persist todos locally on the device with SharedPreferences JSON.

## Tech stack

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX
- SharedPreferences
- AlarmManager notifications

## Build

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

`assembleDebug` also writes a versioned debug APK next to the default Gradle output:

```text
app/build/outputs/apk/debug/PixelDone-0.1.4-debug.apk
```

## Install

Install the debug APK from:

```text
app/build/outputs/apk/debug/PixelDone-0.1.4-debug.apk
```

## Release

GitHub releases should include both the source tag and the versioned debug APK asset. For the current app version, upload:

```text
app/build/outputs/apk/debug/PixelDone-0.1.4-debug.apk
```

## Status

MVP prototype.

CODEX & XUE
