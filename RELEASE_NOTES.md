PixelDone v2.8.0-rc.1 is a beta RC prerelease for checklist back-stack navigation through Android system back gestures.

Highlights:
- Maintains a session-only checklist history across normal checklists, `TRASH`, and `SETTINGS`.
- Routes Android system back and edge-swipe gestures to the previous checklist when checklist history exists.
- Lets Android keep the default back-to-home behavior when checklist history is empty.
- Keeps batch move back handling above checklist navigation, so back still closes target selection or batch selection first.

Install note: use the attached beta debug APK, `PixelDone-2.8.0-rc.1-debug.apk`, for PixelDone-beta testing. This build installs as `com.milesxue.pixeldone.debug`; the formal app remains `com.milesxue.pixeldone`.

CODEX & XUE
