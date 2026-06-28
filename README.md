# PixelDone

PixelDone is a private Android todo planner for quickly managing local tasks across multiple checklists.

Developer identity: CODEX & XUE.

## Current Features

- Add todos with a name, four-step priority, and date/time.
- Keep multiple local checklists, starting from a default `MAIN` list.
- Expand the top bar to switch checklists and edit checklist names.
- Long-press the `+` control to create a new checklist while short press still creates a task.
- Tap a todo row to edit it in the bottom editor.
- Sort by priority or time.
- Adjust priority with a compact slider: Low, Mid, High, and XHigh.
- Priority sorting uses XHigh, High, Mid, Low, then due time.
- Tap a row checkbox to mark a todo done or active again.
- Hide completed todos.
- Delete all completed todos from the top list controls.
- Schedule a local alarm notification for each active future todo.
- Set reminder repeat per todo: none, daily, or weekly.
- Use restrained haptic feedback for core task and list-state actions.
- Check GitHub Releases automatically on app start and show a quiet update dot when a release is available.
- Prompt for an optional update when a newer formal release is detected at app start.
- Let users permanently hide automatic update dialogs while keeping the update-dot signal.
- Persist checklists and todos locally on the device with SharedPreferences JSON.
- Keep dark system bar icons explicit against the light PixelDone surface.

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
.\gradlew.bat assembleDebug
```

Release signing is configured through the local, untracked `signing/release-signing.properties` file for formal releases.

The current debug test APK is copied to:

```text
app/build/outputs/apk/debug/PixelDone-2.1.0-debug.apk
```

## Install

The debug build installs beside the formal app as:

```powershell
adb install -r -d app/build/outputs/apk/debug/PixelDone-2.1.0-debug.apk
```

The formal package name is:

```text
com.milesxue.pixeldone
```

The debug package name is:

```text
com.milesxue.pixeldone.debug
```

## Status

2.1.0 debug candidate. Latest formal private release: 2.0.2.
