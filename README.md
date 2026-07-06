# PixelDone

PixelDone is a private Android todo planner for quickly managing local tasks across multiple checklists.

Developer identity: CODEX & XUE.

## Collaborator Start

PixelDone is a standalone Android repository. A contributor should be able to clone this repository on Windows, macOS, or Linux and start work without the parent PixelPark workspace.

Before editing, building, reviewing, or releasing, read:

- `AGENTS.md`
- `PROJECT_SPEC.md`
- `PRODUCT_LINE_SPEC.md`
- `DESIGN_SPEC.md`

Repository-scoped Codex workflows live under `.agents/skills/`. Keep local machine configuration out of Git: `local.properties`, signing files, Android Studio local state, Gradle build outputs, APKs, and user-specific paths must remain untracked.

## Current Features

- Add todos with a name, four-step priority, and date/time.
- Keep multiple local checklists, starting from a default `MAIN` list.
- Keep a fixed `TRASH` list for soft-deleted tasks.
- Keep a fixed `SETTINGS` list for app options instead of todo storage.
- Expand the top bar to switch checklists and edit checklist names.
- Use Android system back gestures to return through the current session's checklist history before leaving the app.
- Long-press the `+` control to create a new checklist while short press still creates a task.
- Tap a todo row to edit it in the bottom editor.
- Attach one local image to each todo with the row image button.
- Show completed todos in a compact row that hides subtitles and image actions.
- Enable the `QUICK DELETE` dock function to replace row image slots with direct-to-`TRASH` delete buttons.
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
- Keep an emergency XHigh alarm control panel available in PixelDone while an XHigh alarm is ringing.
- Stop XHigh alarm sound and vibration when the alarm notification is dismissed.
- Use restrained haptic feedback for core task and list-state actions.
- Check GitHub Releases automatically on app start, with synced Gitee Releases as fallback, and show a quiet footer update state when a release is available.
- Show an update prompt dialog for users who have not disabled update prompts.
- Download only the exact latest channel-matching APK through Android DownloadManager: formal release APKs for release builds and RC debug APKs for `PixelDone-beta`, trying GitHub first and synced Gitee second.
- Show `downloading` with live footer progress while an in-app update APK downloads silently.
- Show a dismissible update progress dialog when an update is started from a prompt or footer.
- Keep update downloads running silently when the progress dialog is closed.
- Reuse the active latest update download instead of queueing older release APKs.
- Clean stale or already-installed update APK files from the app-private update directory.
- Open the system install confirmation after an in-app update APK finishes downloading.
- Open Android's install-unknown-apps settings first when update installation permission is missing.
- Check quietly for the latest release on every app start without suppressing an available update.
- Keep Android 14+ full-screen intent permission checks tied to the system grant and preserve STOP/SNOOZE access when Android denies full-screen launch.
- Treat a direct return from Android's Full Screen access page as no grant instead of repeatedly reopening the permission page.
- Keep borderless dialog text actions vertically centered with filled dialog buttons across custom dialogs.
- Use the `SETTINGS` list to switch LIGHT/DARK display mode, configure the dock, control cloud sync, control update prompts, reconfigure permissions, check for updates, and view the current version.
- Sign up, sign in, sign out, and manually sync todos/checklists through the low-key Settings `CLOUD` area.
- Customize the normal-checklist bottom dock with `+` placement, live preview, selected function buttons, and function order.
- Use five atomic dock functions for `SORT`, `DDL`, `HIDE DONE`, `CLEAN DONE`, and `QUICK DELETE`.
- Use redesigned pixel-line dock icons for the dock functions, with a direct `P`/`T` sort-mode glyph and a line-drawn trash can for `QUICK DELETE`.
- Select up to four dock function buttons; selecting a fifth function replaces the first selected function.
- Space dock function buttons from the `+` anchor with fixed gaps; centered odd button counts keep one extra function on the left.
- Keep the default dock centered as `SORT`, `+`, and `DDL`.
- Keep `TRASH` and `SETTINGS` free of the normal-checklist dock.
- Show settings permission state with clickable status glyphs instead of larger config buttons.
- Keep clay primary controls readable in dark mode.
- Consume todo row highlight events once so returning from `SETTINGS` does not replay an old highlight.
- Persist checklists and todos locally on the device with Room, migrating legacy SharedPreferences JSON on first launch.
- Keep system bars aligned with the selected light or dark PixelDone theme.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX
- AndroidX Lifecycle ViewModel
- Room
- DataStore
- SharedPreferences legacy migration reader
- AlarmManager notifications
- Manual dependency injection
- Supabase Auth and PostgREST via native Android HTTP
- Android Keystore-backed session storage

## Architecture Map

- `MainActivity.kt`: Android entry point, system bars, and top-level Compose host only.
- `PixelDoneApplication.kt`: owns the app-level dependency container.
- `di/`: manual dependency injection for local storage, settings, optional Supabase sync, image storage, update service, reminder scheduler, and clock.
- `domain/todo/`: pure Kotlin todo, checklist, sorting, and reminder rules with no Android or Compose imports.
- `data/todo/`: todo repository boundary and legacy SharedPreferences JSON migration reader.
- `data/local/`: Room database, DAO, entities, and mappers for local-first todo/checklist storage.
- `data/settings/`: DataStore-backed local settings for theme, Dock configuration, update prompts, and future sync toggle placeholder.
- `data/sync/` and `domain/sync/`: backend-agnostic auth/sync seams, Supabase Auth/PostgREST implementations, and pure Kotlin conflict-resolution rules.
- `data/image/`: private image-copying, safe file-path handling, and preview bitmap sampling.
- `data/update/`: GitHub-first release checks, synced Gitee fallback, DownloadManager integration, and install-intent preparation.
- `reminder/`: AlarmManager, notification, boot, receiver, foreground service, and XHigh full-screen alarm integration.
- `ui/todo/`: screen route, Dock presentation, update/permission presentation rules, UI state holder, and ViewModel teaching entry point.
- `ui/todo/components/`: reusable pixel-style Compose controls and icons.
- `ui/theme/`: PixelDone Material theme and Claude/Anthropic-inspired color tokens.

## Build

From the project root:

Windows:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

macOS/Linux:

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
```

Release signing is configured through the local, untracked `signing/release-signing.properties` file for formal releases.

Cloud-enabled builds receive public Supabase client configuration at build time through Gradle properties, environment variables, or untracked `local.properties` entries:

```properties
pixeldone.supabaseUrl=http://SERVER_IP:8000
pixeldone.supabasePublishableKey=YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY
pixeldone.requireCloudConfig=true
pixeldone.allowInsecureSupabaseHttp=true
```

`pixeldone.allowInsecureSupabaseHttp=true` is only for the current direct-IP HTTP server phase. Remove it after the service moves to `pixeldone.com` with HTTPS.

## Release And Update Source

Code pushes, tags, formal releases, and beta RC prereleases stay on GitHub:

```text
https://github.com/Siyuan-Xue/PixelDone/releases
```

The in-app update checker reads GitHub Releases first and uses the synced Gitee release mirror as fallback:

```text
https://gitee.com/milesxue/PixelDone/releases
```

Gitee synchronization is configured outside this repository. Publish releases and APK assets to GitHub first, then verify the synced Gitee mirror so fallback update availability is healthy.

The latest formal signed release APK is:

```text
app/build/outputs/apk/release/PixelDone-3.0.0-release.apk
```

The latest beta RC debug APK is:

```text
app/build/outputs/apk/debug/PixelDone-3.0.0-rc.2-debug.apk
```

## Install

Install the current formal signed release build with:

```sh
adb install -r app/build/outputs/apk/release/PixelDone-3.0.0-release.apk
```

Install the beta RC debug build with:

```sh
adb install -r app/build/outputs/apk/debug/PixelDone-3.0.0-rc.2-debug.apk
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

3.0.0 formal signed release for Settings-scoped Supabase sign-up/sign-in, local-first todo/checklist sync, the refined Settings CLOUD entry, and password visibility in the cloud auth panel.