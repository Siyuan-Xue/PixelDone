# PixelDone

PixelDone is a private Android todo planner for quickly managing local tasks across multiple checklists.

Developer identity: CODEX & XUE.

## Current Features

- Add todos with a name, four-step priority, and date/time.
- Keep multiple local checklists, starting from a default `MAIN` list.
- Keep a fixed `TRASH` list for soft-deleted tasks.
- Keep a fixed `SETTINGS` list for app options instead of todo storage.
- Expand the top bar to switch checklists and edit checklist names.
- Long-press the `+` control to create a new checklist while short press still creates a task.
- Tap a todo row to edit it in the bottom editor.
- Attach one local image to each todo with the row image button.
- Show completed todos in a compact row that hides subtitles and image actions.
- Preview, replace, or remove a todo image without requesting broad photo-library permission.
- Decode todo image previews safely for large camera images while preserving the original attachment file.
- Delete an edited todo from the task editor, matching the checklist editor delete flow.
- Sort by priority or time.
- Switch the todo subtitle row into a DDL countdown view showing days, hours, and minutes until each deadline.
- Adjust priority with a compact slider: Low, Mid, High, and XHigh.
- Priority sorting uses XHigh, High, Mid, Low, then due time.
- Show priority with approved Google status colors: Low green, Mid blue, High yellow, and XHigh red.
- Highlight overdue todo row date/time in the same red used by delete icons.
- Treat todos due at the exact current moment as already overdue instead of scheduling a new reminder.
- Tap a row checkbox to mark a todo done or active again.
- Keep completed-task sorting delayed for quick consecutive checks without jumping the list.
- Reveal and briefly border-highlight newly added, edited, reactivated, or image-updated todos.
- Hide completed todos.
- Move deleted tasks, completed-task batches, and deleted-list tasks into `TRASH`.
- Restore tasks from `TRASH` without changing their completed state, recreating the original list if needed.
- Permanently delete all tasks from `TRASH` and clean their image files.
- Schedule a local alarm notification for each active future todo.
- Set reminder repeat per todo: none, daily, or weekly.
- Advance daily and weekly repeating todos to the next reminder time after a notification fires.
- Use restrained haptic feedback for core task and list-state actions.
- Check GitHub Releases automatically on app start and show a quiet footer update state when a release is available.
- Show an update prompt dialog for users who have not disabled update prompts.
- Download only the exact latest formal release APK through Android DownloadManager.
- Show `downloading` with live footer progress while an in-app update APK downloads silently.
- Show a dismissible update progress dialog when an update is started from a prompt or footer.
- Keep update downloads running silently when the progress dialog is closed.
- Reuse the active latest update download instead of queueing older release APKs.
- Clean stale or already-installed update APK files from the app-private update directory.
- Open the system install confirmation after an in-app update APK finishes downloading.
- Open Android's install-unknown-apps settings first when update installation permission is missing.
- Check quietly for the latest release on every app start without suppressing an available update.
- Use the `SETTINGS` list to switch LIGHT/DARK display mode, control update prompts, reconfigure permissions, check for updates, and view the current version.
- Use bottom `VIEW` and `BATCH` controls on normal checklists so sort, DDL, hide-done, completed-task cleanup, and batch movement stay near the `+` action.
- Move selected tasks across normal checklists with explicit batch selection.
- Keep `TRASH` and `SETTINGS` free of the new bottom side buttons.
- Show settings permission state with clickable status glyphs instead of larger config buttons.
- Keep clay primary controls readable in dark mode.
- Consume todo row highlight events once so returning from `SETTINGS` does not replay an old highlight.
- Persist checklists and todos locally on the device with SharedPreferences JSON.
- Keep system bars aligned with the selected light or dark PixelDone theme.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX
- AndroidX Lifecycle ViewModel
- SharedPreferences
- AlarmManager notifications
- Manual dependency injection

## Architecture Map

- `MainActivity.kt`: Android entry point, system bars, and top-level Compose host only.
- `PixelDoneApplication.kt`: owns the app-level dependency container.
- `di/`: manual dependency injection for repositories, image storage, update service, reminder scheduler, and clock.
- `domain/todo/`: pure Kotlin todo, checklist, sorting, and reminder rules with no Android or Compose imports.
- `data/todo/`: SharedPreferences JSON persistence behind a repository boundary.
- `data/image/`: private image-copying, safe file-path handling, and preview bitmap sampling.
- `data/update/`: GitHub release checks, DownloadManager integration, and install-intent preparation.
- `reminder/`: AlarmManager, notification, boot, receiver, foreground service, and XHigh full-screen alarm integration.
- `ui/todo/`: screen route, UI state holder, and ViewModel teaching entry point.
- `ui/todo/components/`: reusable pixel-style Compose controls and icons.
- `ui/theme/`: PixelDone Material theme and Claude/Anthropic-inspired color tokens.

## Build

From the project root:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Release signing is configured through the local, untracked `signing/release-signing.properties` file for formal releases.

The signed release APK is copied to:

```text
app/build/outputs/apk/release/PixelDone-2.7.0-release.apk
```

The current local debug APK is copied to:

```text
app/build/outputs/apk/debug/PixelDone-2.7.0-debug.apk
```

## Install

Install this debug build with:

```powershell
adb install -r app/build/outputs/apk/debug/PixelDone-2.7.0-debug.apk
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

2.7.0 debug prerelease for bottom actions, batch movement, settings permission simplification, dark-mode button contrast, and highlight replay cleanup.
