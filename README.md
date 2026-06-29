# PixelDone

PixelDone is a private Android todo planner for quickly managing local tasks across multiple checklists.

Developer identity: CODEX & XUE.

## Current Features

- Add todos with a name, four-step priority, and date/time.
- Keep multiple local checklists, starting from a default `MAIN` list.
- Keep a fixed `TRASH` list for soft-deleted tasks.
- Expand the top bar to switch checklists and edit checklist names.
- Long-press the `+` control to create a new checklist while short press still creates a task.
- Tap a todo row to edit it in the bottom editor.
- Attach one local image to each todo with the row image button.
- Preview, replace, or remove a todo image without requesting broad photo-library permission.
- Decode todo image previews safely for large camera images while preserving the original attachment file.
- Delete an edited todo from the task editor, matching the checklist editor delete flow.
- Sort by priority or time.
- Switch the todo subtitle row into a DDL countdown view showing days, hours, and minutes until each deadline.
- Adjust priority with a compact slider: Low, Mid, High, and XHigh.
- Priority sorting uses XHigh, High, Mid, Low, then due time.
- Show priority with approved Google status colors: Low green, Mid blue, High yellow, and XHigh red.
- Highlight overdue todo row date/time in the same red used by delete icons.
- Tap a row checkbox to mark a todo done or active again.
- Hide completed todos.
- Move deleted tasks, completed-task batches, and deleted-list tasks into `TRASH`.
- Restore tasks from `TRASH` without changing their completed state, recreating the original list if needed.
- Permanently delete all tasks from `TRASH` and clean their image files.
- Schedule a local alarm notification for each active future todo.
- Set reminder repeat per todo: none, daily, or weekly.
- Advance daily and weekly repeating todos to the next reminder time after a notification fires.
- Use restrained haptic feedback for core task and list-state actions.
- Check GitHub Releases automatically on app start and show a quiet footer update state when a release is available.
- Download only the exact latest formal release APK through Android DownloadManager.
- Avoid repeating the same in-app update download for an already handled release version.
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

The current local debug APK is copied to:

```text
app/build/outputs/apk/debug/PixelDone-2.4.3-debug.apk
```

## Install

Install the local debug build with:

```powershell
adb install -r -d app/build/outputs/apk/debug/PixelDone-2.4.3-debug.apk
```

The debug package name is:

```text
com.milesxue.pixeldone.debug
```

## Status

2.4.3 local debug validation.
